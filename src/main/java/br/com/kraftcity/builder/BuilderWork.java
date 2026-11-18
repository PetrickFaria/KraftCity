package br.com.kraftcity.builder;

import br.com.kraftcity.employment.*;
import br.com.kraftcity.workforce.WorkforceData;
import br.com.kraftcity.industry.IndustrialWork;
import br.com.kraftcity.logistics.DiscardChests;
import br.com.residenciasaldeoes.data.ResidenceData;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.*;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.ForgeEventFactory;

/** Main-thread cooperative scheduler. No physical ItemEntities represent flying blocks. */
public final class BuilderWork {
    private record Sample(long time,int age){}
    private static final Map<UUID,Sample> CLOCKS=new HashMap<>();
    private static final Map<UUID,Integer> STALLED=new HashMap<>();
    private static final Map<UUID,Integer> MISSING=new HashMap<>();
    public static boolean builder(EmploymentData.Station s){return s!=null&&s.profession==Profession.BUILDER;}
    public static boolean ownsNavigation(Villager v){return v.level instanceof ServerLevel l&&builder(EmploymentData.get(l).assigned(v.getUUID()))&&IndustrialWork.day(l)&&!v.isSleeping()&&!v.getPersistentData().contains("residenciasaldeoes_emerald_transfer")&&!v.getPersistentData().contains("residenciasaldeoes_night_door_task")&&!v.getPersistentData().getBoolean("residenciasaldeoes_home_return_forced");}
    public static int level(long xp){return xp>=500?5:xp>=300?4:xp>=150?3:xp>=50?2:1;}
    public static int duration(long xp){return (6-level(xp))*20;}
    public static void release(ServerLevel l,UUID worker){var d=BuilderData.get(l);for(var j:d.jobs.values()){j.workers.remove(worker);j.tasks.values().removeIf(t->t.worker.equals(worker));}CLOCKS.remove(worker);MISSING.remove(worker);STALLED.remove(worker);d.setDirty();}
    public static BlockPos target(Blueprint b,BuilderData.Job j,int index){return j.origin.offset(Blueprint.transform(b.cells.get(index).pos(),b.size,j.rotation,j.mirror));}
    public static BlockState expected(Blueprint b,BuilderData.Job j,int index){return Blueprint.transform(b.cells.get(index).state(),j.rotation,j.mirror);}
    private static final Set<String> DERIVED=Set.of("powered","power","lit","distance","shape","north","south","east","west","up","down");
    private static boolean available(BlockState old,BlockState desired){return old.isAir()||old.is(Blocks.WATER)&&desired.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)&&desired.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED);}
    public static boolean matches(BlockState actual,BlockState desired){if(actual.getBlock()!=desired.getBlock())return false;for(var property:desired.getProperties())if(!DERIVED.contains(property.getName())&&!Objects.equals(actual.getValue(property),desired.getValue(property)))return false;return true;}
    public static void tick(ServerLevel origin){var d=BuilderData.get(origin);d.printTick();int budget=BuilderModule.SCAN_BUDGET.get();
        for(var j:d.jobs.values()){
            var p=d.papers.get(j.paper);var b=p==null?null:d.library.get(p.schema);var l=origin.getServer().getLevel(j.dimension);if(b==null||l==null||j.cancelled){j.workers.clear();j.tasks.clear();continue;}if(j.paused){for(UUID id:j.workers.keySet())CLOCKS.remove(id);continue;}
            if(j.done.cardinality()>=b.cells.size()){j.status="Concluída.";j.workers.clear();j.tasks.clear();continue;}
            if(!l.hasChunkAt(j.origin)){for(UUID id:j.workers.keySet())CLOCKS.remove(id);j.status="Pausa: chunk da obra descarregado.";continue;}
            reconcile(l,d,j);recruit(l,d,j,b);
            int active=0;for(var entry:List.copyOf(j.workers.entrySet())){
                var s=EmploymentData.get(l).assigned(entry.getKey());if(!builder(s))continue;if(!(l.getEntity(entry.getKey()) instanceof Villager v))continue;
                var old=CLOCKS.put(v.getUUID(),new Sample(l.getGameTime(),v.tickCount));int delta=old==null?0:(int)Math.max(0,Math.min(20,Math.min(l.getGameTime()-old.time,v.tickCount-old.age)));
                if(v.isNoAi()||!ownsNavigation(v))continue;
                String fetch=s.industry.getString("builder_fetch");if(!fetch.isEmpty()){var key=net.minecraft.resources.ResourceLocation.tryParse(fetch);var item=key==null?Items.AIR:net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(key);if(item!=null&&item!=Items.AIR&&!has(d.stock,item)&&supply(l,d,j,s,v,item))continue;s.industry.remove("builder_fetch");s.setChanged();}
                int role=entry.getValue();if(role<0){var spot=spot(l,b,j,0,v);if(spot==null){j.status="Sem acesso à posição de trabalho.";continue;}if(!reach(v,spot,1.15)){if(v.getNavigation().isDone()&&STALLED.merge(v.getUUID(),1,Integer::sum)>=40){j.workers.remove(v.getUUID());s.industry.putLong("builder_retry",l.getGameTime()+200);s.setChanged();STALLED.remove(v.getUUID());}continue;}STALLED.remove(v.getUUID());var occupied=new HashSet<>(j.workers.values());role=0;while(role<4&&occupied.contains(role))role++;if(role>=4){j.workers.remove(v.getUUID());returnToPost(l,v.getUUID());continue;}j.workers.put(v.getUUID(),role);if(j.workers.values().stream().filter(r->r>=0).count()>=4){for(var extra:List.copyOf(j.workers.entrySet()))if(extra.getValue()<0){returnToPost(l,extra.getKey());j.workers.remove(extra.getKey());}}d.setDirty();}
                var spot=spot(l,b,j,role,v);if(spot==null||!reach(v,spot,1.05))continue;active++;
                var task=j.tasks.values().stream().filter(t->t.worker.equals(v.getUUID())).findFirst().orElse(null);
                if(task==null){boolean fetching=false;for(var blocked:j.tasks.values()){var material=cost(b,j,blocked.index);if(material!=Items.AIR&&!has(d.stock,material)&&supply(l,d,j,s,v,material)){s.industry.putString("builder_fetch",net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(material).toString());s.setChanged();fetching=true;break;}}if(fetching)continue;}
                if(task==null&&budget>0){int attempts=Math.min(b.cells.size(),budget);while(attempts-->0){budget--;int i=Math.floorMod(j.cursor++,b.cells.size());if(j.tasks.containsKey(i)||j.done.get(i))continue;var pos=target(b,j,i);if(!l.hasChunkAt(pos)){j.status="Pausa: chunk de destino descarregado.";continue;}if(matches(l.getBlockState(pos),expected(b,j,i))){j.done.set(i);d.setDirty();continue;}task=new BuilderData.Task(i,v.getUUID());j.tasks.put(i,task);break;}}
                if(task!=null)step(l,d,j,b,s,v,task,delta);
            }
            if(!IndustrialWork.day(l))j.status="Pausa noturna/chuva; rotinas residenciais preservadas.";
            if(active==0&&j.workers.isEmpty()){j.status=IndustrialWork.day(l)?"Aguardando acesso/trabalhadores elegíveis.":"Pausa noturna; rotinas residenciais preservadas.";alert(l,j,"workers","Faltam Construtores ativos/com acesso.");}
        }
        for(var s:EmploymentData.get(origin).stations())if(s.active&&s.profession==Profession.BUILDER&&s.worker!=null){var l=origin.getServer().getLevel(s.dimension);if(l==null||!(l.getEntity(s.worker) instanceof Villager v)||v.isNoAi()||!Employment.valid(l,s.worker)||!ownsNavigation(v))continue;if(d.assigned(s.worker)!=null&&!d.jobs.get(d.assigned(s.worker)).paused)continue;
            if(!l.hasChunkAt(s.pos))continue;if(s.industry.getBoolean("builder_return")){if(reach(v,s.pos.above(),.9)){s.industry.remove("builder_return");s.setChanged();}continue;}if(v.distanceToSqr(Vec3.atCenterOf(s.pos))>625){reach(v,s.pos.above(),.9);continue;}if(v.getNavigation().isDone()||v.tickCount%100==0){int x=v.getRandom().nextInt(35)-17,z=v.getRandom().nextInt(35)-17;var dest=s.pos.offset(x,0,z);if(x*x+z*z<=625&&l.hasChunkAt(dest))v.getNavigation().moveTo(dest.getX()+.5,dest.getY()+1,dest.getZ()+.5,.6);}
        }
    }
    private static void returnToPost(ServerLevel l,UUID worker){var s=EmploymentData.get(l).assigned(worker);if(builder(s)){s.industry.putBoolean("builder_return",true);s.setChanged();}}
    private static void reconcile(ServerLevel l,BuilderData d,BuilderData.Job j){for(UUID id:List.copyOf(j.workers.keySet())){var s=EmploymentData.get(l).assigned(id);if(!builder(s)||!s.dimension.equals(l.dimension())||!Employment.valid(l,id)){j.workers.remove(id);j.tasks.values().removeIf(t->t.worker.equals(id));d.setDirty();continue;}if(l.getEntity(id)==null){int n=MISSING.merge(id,1,Integer::sum);if(n>=200){j.workers.remove(id);j.tasks.values().removeIf(t->t.worker.equals(id));MISSING.remove(id);d.setDirty();}}else MISSING.remove(id);}}
    public static void recruit(ServerLevel l,BuilderData d,BuilderData.Job j,Blueprint b){if(j.workers.size()>=6||j.workers.values().stream().filter(i->i>=0).count()>=4||!IndustrialWork.day(l))return;
        var center=Vec3.atCenterOf(j.origin.offset(b.transformedSize(j.rotation).getX()/2,0,b.transformedSize(j.rotation).getZ()/2));
        EmploymentData.get(l).stations().stream().filter(s->s.active&&s.profession==Profession.BUILDER&&s.worker!=null&&s.dimension.equals(l.dimension())&&s.pos.distSqr(j.origin)<=22500&&s.industry.getLong("builder_retry")<=l.getGameTime()&&d.assigned(s.worker)==null&&l.getEntity(s.worker) instanceof Villager v&&v.isAlive()&&!v.isSleeping()&&!v.isNoAi()&&Employment.valid(l,s.worker)).sorted(Comparator.<EmploymentData.Station>comparingDouble(s->s.pos.distSqr(j.origin)).thenComparing(s->s.id.toString())).limit(6-j.workers.size()).forEach(s->{j.workers.put(s.worker,-1);d.setDirty();});
    }
    public static BlockPos spot(ServerLevel l,Blueprint b,BuilderData.Job j,int role,Villager v){var size=b.transformedSize(j.rotation);int front=Math.floorMod(j.rotation,4);int[][] directions={{0,-1},{-1,0},{1,0},{0,1}};int dx=directions[role][0],dz=directions[role][1];if(j.mirror==1)dx=-dx;if(j.mirror==2)dz=-dz;for(int i=0;i<front;i++){int old=dx;dx=-dz;dz=old;}int x=dx<0?-2:dx>0?size.getX()+1:size.getX()/2,z=dz<0?-2:dz>0?size.getZ()+1:size.getZ()/2;
        var base=j.origin.offset(x,0,z);for(int y=2;y>=-3;y--){var at=base.offset(0,y,0);if(l.hasChunkAt(at)&&l.getBlockState(at).getCollisionShape(l,at).isEmpty()&&l.getBlockState(at.above()).getCollisionShape(l,at.above()).isEmpty()&&l.getBlockState(at.below()).isFaceSturdy(l,at.below(),Direction.UP))return at;}return null;
    }
    private static boolean reach(Villager v,BlockPos p,double speed){if(v.distanceToSqr(Vec3.atBottomCenterOf(p))<=2.25){v.getNavigation().stop();return true;}v.getNavigation().moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,speed);return false;}
    public static void step(ServerLevel l,BuilderData d,BuilderData.Job j,Blueprint b,EmploymentData.Station s,Villager v,BuilderData.Task task,int ticks){
        if(!l.getServer().isSameThread()||ticks<0||ticks>20)throw new IllegalArgumentException("Invalid builder tick");if(j.paused||j.cancelled||!ownsNavigation(v)||!Employment.valid(l,v.getUUID())||!v.getUUID().equals(s.worker)||!task.worker.equals(v.getUUID()))return;
        int i=task.index;if(j.tasks.get(i)!=task||j.done.get(i)||!j.workers.containsKey(v.getUUID())||j.workers.get(v.getUUID())<0)return;var workplace=spot(l,b,j,j.workers.get(v.getUUID()),v);if(workplace==null||v.distanceToSqr(Vec3.atBottomCenterOf(workplace))>2.25)return;if(i<0||i>=b.cells.size()){j.tasks.remove(i);return;}var pos=target(b,j,i);var state=expected(b,j,i);if(!l.hasChunkAt(pos)||!l.getWorldBorder().isWithinBounds(pos)||l.isOutsideBuildHeight(pos)){j.status="Destino inacessível/descarregado.";return;}
        if(matches(l.getBlockState(pos),state)){j.done.set(i);j.tasks.remove(i);d.setDirty();return;}
        var old=l.getBlockState(pos);if(l.getBlockEntity(pos)!=null||old.getDestroySpeed(l,pos)<0||!j.replace&&!available(old,state)){j.status="Conflito: posição preservada "+pos.toShortString();alert(l,j,"conflict",j.status);return;}
        if(!ready(l,pos,state)){j.status="Aguardando suporte/parte anterior em "+pos.toShortString();return;}
        var wanted=cost(b,j,i);boolean waterNeeded=state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)&&state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)&&!(l.getFluidState(pos).is(Fluids.WATER)&&l.getFluidState(pos).isSource());if(waterNeeded&&!has(d.stock,Items.WATER_BUCKET)){s.industry.putString("builder_fetch","minecraft:water_bucket");s.setChanged();if(supply(l,d,j,s,v,Items.WATER_BUCKET))return;j.status="Falta balde de água para bloco alagado.";alert(l,j,"material",j.status);return;}if(!has(d.stock,wanted)){s.industry.putString("builder_fetch",net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(wanted).toString());s.setChanged();if(supply(l,d,j,s,v,wanted))return;j.status="Faltam materiais: "+wanted.getDescription().getString();if(l.getGameTime()-j.alertAt>=300)alert(l,j,"material",missing(l,d,j,b,wanted));return;}
        if(d.charge<=0){j.status="Faltam esmeraldas/carga de construção.";alert(l,j,"charge",j.status);return;}
        v.getLookControl().setLookAt(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,30F,30F);var progress=WorkforceData.get(l).progress(v.getUUID(),Profession.BUILDER.id());int duration=duration(progress.xp());if(ticks>0)task.lastActive=l.getGameTime();task.elapsed=Math.min(duration,task.elapsed+ticks);j.status="Construindo "+b.name+".";d.setDirty();
        if(task.elapsed>=Math.max(0,duration-20)&&(task.flightAt<0||l.getGameTime()-task.flightAt>40)){task.flightAt=l.getGameTime();BuilderNetwork.flight(l,v,pos,state,Math.max(1,duration-task.elapsed));}
        if(task.elapsed<duration)return;if(!place(l,d,j,b,s,v,task,wanted))return;
        j.done.set(i);j.placed++;j.tasks.remove(i);j.credits.clear(i);int peer=b.peer(i);if(wanted!=Items.AIR&&peer>=0&&!j.done.get(peer))j.credits.set(peer);
        long xp=progress.xp()+1;WorkforceData.get(l).setProgress(v.getUUID(),Profession.BUILDER.id(),level(xp),xp);d.setDirty();
    }
    public static Item cost(Blueprint b,BuilderData.Job j,int index){return j.credits.get(index)?Items.AIR:Blueprint.material(b.cells.get(index).state());}
    public static boolean has(Container stock,Item item){if(item==Items.AIR)return true;for(int i=0;i<27;i++)if(stock.getItem(i).is(item))return true;return false;}
    private static int slot(Container stock,Item item){for(int i=0;i<27;i++)if(stock.getItem(i).is(item))return i;return -1;}
    private static boolean ready(ServerLevel l,BlockPos p,BlockState state){if(state.getBlock() instanceof FallingBlock&&!l.getBlockState(p.below()).isFaceSturdy(l,p.below(),Direction.UP))return false;for(var side:Direction.values())if(!l.hasChunkAt(p.relative(side)))return false;
        if(state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)&&state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)return l.getBlockState(p.below()).getBlock()==state.getBlock();
        if(state.getBlock() instanceof BedBlock&&state.getValue(BedBlock.PART)==net.minecraft.world.level.block.state.properties.BedPart.HEAD)return l.getBlockState(p.relative(state.getValue(BedBlock.FACING).getOpposite())).getBlock()==state.getBlock();
        return state.canSurvive(l,p);
    }
    private static boolean place(ServerLevel l,BuilderData d,BuilderData.Job j,Blueprint b,EmploymentData.Station s,Villager v,BuilderData.Task task,Item material){
        var pos=target(b,j,task.index);var state=expected(b,j,task.index);var realOwner=l.getServer().getPlayerList().getPlayer(j.owner);var owner=realOwner!=null?realOwner:net.minecraftforge.common.util.FakePlayerFactory.get(l,new com.mojang.authlib.GameProfile(j.owner,"KCBuilder"));
        if(!owner.mayBuild()||!l.mayInteract(owner,pos)||!ForgeEventFactory.getMobGriefingEvent(l,v)||!IndustrialWork.allowed(l,s,v,pos,null,"builder_place"))return false;
        var old=l.getBlockState(pos);if(l.getBlockEntity(pos)!=null||!j.replace&&!available(old,state))return false;
        var actor=net.minecraftforge.common.util.FakePlayerFactory.get(l,owner.getGameProfile());actor.setPos(v.position());
        if(!old.isAir()&&MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(l,pos,old,actor)))return false;
        if(!Employment.valid(l,v.getUUID())||!v.getUUID().equals(s.worker)||!has(d.stock,material)||d.charge<=0)return false;
        boolean water=state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)&&state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)&&!(l.getFluidState(pos).is(Fluids.WATER)&&l.getFluidState(pos).isSource());
        var simulated=new SimpleContainer(32);for(int k=0;k<32;k++)simulated.setItem(k,d.stock.getItem(k).copy());if(material!=Items.AIR)simulated.removeItem(slot(simulated,material),1);if(water){int waterSlot=slot(simulated,Items.WATER_BUCKET);if(waterSlot<0){j.status="Falta balde de água para bloco alagado.";return false;}simulated.removeItem(waterSlot,1);if(!insert(simulated,new ItemStack(Items.BUCKET),true))return false;}
        if((material==Items.WATER_BUCKET||material==Items.LAVA_BUCKET)&&!insert(simulated,new ItemStack(Items.BUCKET),true))return false;
        var snapshot=BlockSnapshot.create(l.dimension(),l,pos);if(!l.setBlock(pos,state,2))return false;
        boolean denied=ForgeEventFactory.onBlockPlace(actor,snapshot,Direction.UP)||!Employment.valid(l,v.getUUID())||!v.getUUID().equals(s.worker)||!matches(l.getBlockState(pos),state)||!has(d.stock,material)||d.charge<=0;
        if(denied){snapshot.restore(true,false);j.status="Colocação recusada; recursos preservados.";return false;}
        var finalStock=new SimpleContainer(32);for(int k=0;k<32;k++)finalStock.setItem(k,d.stock.getItem(k).copy());if(material!=Items.AIR)finalStock.removeItem(slot(finalStock,material),1);
        boolean room=true;if(water){int n=slot(finalStock,Items.WATER_BUCKET);if(n<0)room=false;else{finalStock.removeItem(n,1);room=insert(finalStock,new ItemStack(Items.BUCKET),true);}}if(material==Items.WATER_BUCKET||material==Items.LAVA_BUCKET)room&=insert(finalStock,new ItemStack(Items.BUCKET),true);
        if(!room){snapshot.restore(true,false);j.status="Sem espaço para remanescente; recursos preservados.";return false;}for(int k=0;k<32;k++)d.stock.setItem(k,finalStock.getItem(k));
        d.charge--;d.refill();l.sendBlockUpdated(pos,old,state,3);l.updateNeighborsAt(pos,state.getBlock());return true;
    }
    public static boolean insert(Container c,ItemStack incoming,boolean commit){var stack=incoming.copy();for(int pass=0;pass<2;pass++)for(int i=0;i<27&&!stack.isEmpty();i++){var at=c.getItem(i);if(pass==0&&ItemStack.isSameItemSameTags(at,stack)&&!at.isEmpty()){int n=Math.min(stack.getCount(),Math.min(c.getMaxStackSize(),at.getMaxStackSize())-at.getCount());if(n>0){if(commit){at.grow(n);c.setChanged();}stack.shrink(n);}}else if(pass==1&&at.isEmpty()){int n=Math.min(stack.getCount(),stack.getMaxStackSize());if(commit){var copy=stack.copy();copy.setCount(n);c.setItem(i,copy);}stack.shrink(n);}}return stack.isEmpty();}
    private static boolean supply(ServerLevel l,BuilderData d,BuilderData.Job j,EmploymentData.Station s,Villager v,Item item){
        if(item==Items.AIR)return false;var candidates=DiscardChests.around(l,s,new AABB(s.pos)).stream().filter(c->c.pos().distSqr(s.pos)<=25&&DiscardChests.canOpen(l,c.pos(),IndustrialWork.actor(l,s,v))).toList();
        for(var c:candidates){int n=slotAny(c.inventory(),item);if(n<0)continue;if(!reach(v,c.pos(),.85)){j.status="Buscando material no baú do posto.";return true;}if(!IndustrialWork.allowed(l,s,v,c.pos(),null,"builder_supply")||!Employment.valid(l,v.getUUID()))return true;var live=DiscardChests.at(l,c.pos());if(live==null)return true;n=slotAny(live,item);if(n<0)return true;var blueprint=d.library.get(d.papers.get(j.paper).schema);int needed=requirements(l,j,blueprint).getOrDefault(item,0);int amount=Math.min(needed,Math.min(live.getItem(n).getCount(),item.getMaxStackSize()));while(amount>0&&!insert(d.stock,new ItemStack(item,amount),false))amount--;if(amount<=0){j.status="Estoque global cheio.";return true;}var removed=live.removeItem(n,amount);if(removed.getCount()==amount){insert(d.stock,removed,true);live.setChanged();d.setDirty();}return true;
        }return false;
    }
    private static int slotAny(Container c,Item item){for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(item))return i;return -1;}
    public static String missing(ServerLevel l,BuilderData d,BuilderData.Job j,Blueprint b,Item item){int total=requirements(l,j,b).getOrDefault(item,0),have=0;for(int i=0;i<27;i++)if(d.stock.getItem(i).is(item))have+=d.stock.getItem(i).getCount();return "faltam "+Math.max(1,total-have)+" × "+item.getDescription().getString();}
    public static Map<Item,Integer> requirements(ServerLevel l,BuilderData.Job j,Blueprint b){var result=new LinkedHashMap<Item,Integer>();var groups=new HashSet<Integer>();for(int i=0;i<b.cells.size();i++)if(!j.done.get(i)){var state=expected(b,j,i);var target=target(b,j,i);if(l!=null&&l.hasChunkAt(target)&&matches(l.getBlockState(target),state))continue;if(!j.credits.get(i)){int peer=b.peer(i);if(peer<0||groups.add(Math.min(i,peer))){var item=Blueprint.material(state);if(item!=Items.AIR)result.merge(item,1,Integer::sum);}}if(state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)&&state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)){var pos=target(b,j,i);if(l==null||!l.hasChunkAt(pos)||!l.getFluidState(pos).is(Fluids.WATER)||!l.getFluidState(pos).isSource())result.merge(Items.WATER_BUCKET,1,Integer::sum);}}return result;}
    private static void alert(ServerLevel l,BuilderData.Job j,String kind,String message){long now=l.getGameTime();if(now>=j.alertAt&&now-j.alertAt<300)return;j.alertAt=now;j.alert=kind;var p=l.getServer().getPlayerList().getPlayer(j.owner);if(p!=null){var d=BuilderData.get(l);var b=d.library.get(d.papers.get(j.paper).schema);p.sendSystemMessage(net.minecraft.network.chat.Component.literal("[KraftCity] Construção "+b.name+": "+message));}}
    public static void stop(){CLOCKS.clear();MISSING.clear();STALLED.clear();}
}
