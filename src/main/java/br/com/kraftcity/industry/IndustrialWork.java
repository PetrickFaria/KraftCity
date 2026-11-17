package br.com.kraftcity.industry;

import br.com.kraftcity.employment.*;
import br.com.residenciasaldeoes.data.ResidenceData;
import com.mojang.authlib.GameProfile;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.*;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.*;
import net.minecraftforge.common.util.*;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.BlockEvent;

/** Server-thread jobs. Scans are bounded and persisted; no chunk is force loaded. */
public final class IndustrialWork {
    public static boolean industrial(EmploymentData.Station s){return s.active&&s.worker!=null&&s.profession!=null&&s.sector==Profession.Sector.INDUSTRIAL;}
    public static boolean ownsNavigation(Villager v){
        if(!(v.level instanceof ServerLevel l))return false;
        var s=EmploymentData.get(l).assigned(v.getUUID());
        return s!=null&&industrial(s)&&day(l)&&!v.isSleeping()&&!l.isRaining()
            &&!v.getPersistentData().getBoolean("residenciasaldeoes_home_return_forced")
            &&!v.getPersistentData().contains("residenciasaldeoes_emerald_transfer")
            &&!v.getPersistentData().contains("residenciasaldeoes_night_door_task");
    }
    public static boolean day(ServerLevel l){long t=Math.floorMod(l.getDayTime(),24000);return t<12000||t>=23000;}
    public static AABB area(ServerLevel l,EmploymentData.Station s){int r=IndustrialRules.size(l,s)/2;return new AABB(s.pos.offset(-r,-3,-r),s.pos.offset(r+1,4,r+1));}
    public static void tick(ServerLevel l,EmploymentData.Station s){
        if(!industrial(s)||!l.hasChunkAt(s.pos)||!Employment.valid(l,s.worker)||!(l.getEntity(s.worker) instanceof Villager v))return;
        if(!day(l)||l.isRaining()||v.isSleeping()){
            s.industry.remove("fish_elapsed");
            if(!day(l)){
                if(s.industry.getLong("deposited_day")!=Math.floorDiv(l.getDayTime(),24000)+1){
                    deposit(l,s,v);s.industry.putLong("deposited_day",Math.floorDiv(l.getDayTime(),24000)+1);s.setChanged();
                }
                // Existing residential lifecycle owns sleeping, rain, doors and bed return.
                var home=ResidenceData.get(l).owner(v.getUUID());
                if(home!=null&&Math.floorMod(l.getDayTime(),24000)<13000&&!v.getPersistentData().contains("residenciasaldeoes_night_door_task"))
                    v.getNavigation().moveTo(home.pos.getX()+.5,home.pos.getY()+1,home.pos.getZ()+.5,.85);
            }
            return;
        }
        if(!ownsNavigation(v))return;
        long day=Math.floorDiv(l.getDayTime()+1000,24000);
        if((s.profession==Profession.BUTCHER||s.profession==Profession.SHEPHERD)&&s.industry.getLong("supplied_day")!=day+1){
            dawnSupply(l,s,v);s.industry.putLong("supplied_day",day+1);s.setChanged();
        }
        replenish(s);showTool(v,s);
        if(!ForgeEventFactory.getMobGriefingEvent(l,v)){alert(l,s,"protection","Trabalho bloqueado pela proteção/mobGriefing.");return;}
        switch(s.profession){
            case FARMER->farm(l,s,v);
            case BUTCHER->animals(l,s,v,Profession.BUTCHER);
            case SHEPHERD->animals(l,s,v,Profession.SHEPHERD);
            case FISHERMAN->fish(l,s,v);
            case BREEDER->animals(l,s,v,Profession.BREEDER);
            case BEEKEEPER->bees(l,s,v);
            default->{}
        }
    }
    public static void alert(ServerLevel l,EmploymentData.Station s,String cause,String message){
        CompoundTag alerts=s.industry.getCompound("alerts");long now=l.getGameTime();
        if(alerts.contains(cause)&&now>=alerts.getLong(cause)&&now-alerts.getLong(cause)<300)return;
        alerts.putLong(cause,now);s.industry.put("alerts",alerts);s.setChanged();
        for(ServerPlayer p:l.players())if(p.distanceToSqr(Vec3.atCenterOf(s.pos))<=4096)
            p.sendSystemMessage(Component.literal("[KraftCity · "+s.profession.label+" · "+s.worker.toString().substring(0,6)+"] "+message));
    }
    public static FakePlayer actor(ServerLevel l,EmploymentData.Station s,Villager v){
        var p=FakePlayerFactory.get(l,new GameProfile(v.getUUID(),"KCWorker"));p.setPos(v.getX(),v.getY(),v.getZ());p.setItemInHand(InteractionHand.MAIN_HAND,s.getItem(27).copy());return p;
    }
    public static boolean allowed(ServerLevel l,EmploymentData.Station s,Villager v,BlockPos pos,Entity target,String action){
        if(!l.hasChunkAt(pos)||!l.getWorldBorder().isWithinBounds(pos))return false;
        var p=actor(l,s,v);
        boolean denied=MinecraftForge.EVENT_BUS.post(new WorkActionEvent(l,s,v,pos,target,action));
        if(target!=null)denied|=MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(p,InteractionHand.MAIN_HAND,target));
        if(denied)alert(l,s,"protection","A proteção recusou esta ação de trabalho.");
        return !denied;
    }
    private static boolean reach(Villager v,BlockPos p){
        if(v.distanceToSqr(Vec3.atCenterOf(p))<=9){v.getNavigation().stop();v.lookAt(EntityAnchorArgument.Anchor.EYES,Vec3.atCenterOf(p));return true;}
        v.getNavigation().moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,.75);return false;
    }
    private static void showTool(Villager v,EmploymentData.Station s){
        if(!v.getPersistentData().contains("KCOriginalHand"))v.getPersistentData().put("KCOriginalHand",v.getMainHandItem().save(new CompoundTag()));
        ItemStack view=s.getItem(27).copy();if(!ItemStack.matches(v.getMainHandItem(),view)){
            // A visual mirror, never a second inventory; empty drop chance, restored on release.
            v.setItemSlot(EquipmentSlot.MAINHAND,view);v.setDropChance(EquipmentSlot.MAINHAND,0);
        }
    }
    public static void restoreHand(Villager v){
        if(v.getPersistentData().contains("KCOriginalHand")){v.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.of(v.getPersistentData().getCompound("KCOriginalHand")));v.setDropChance(EquipmentSlot.MAINHAND,.085F);v.getPersistentData().remove("KCOriginalHand");}
    }
    private static void move(EmploymentData.Station s,int slot,Predicate<ItemStack> match){
        if(!s.getItem(slot).isEmpty())return;
        for(int i=0;i<27;i++)if(match.test(s.getItem(i))){s.setItem(slot,s.removeItemNoUpdate(i));s.setChanged();return;}
    }
    public static void replenish(EmploymentData.Station s){
        switch(s.profession){
            case FARMER->{move(s,27,x->x.getItem() instanceof HoeItem);move(s,28,x->x.is(Items.BONE_MEAL));move(s,30,x->x.is(Items.WATER_BUCKET));}
            case BUTCHER->{move(s,27,x->x.getItem() instanceof AxeItem);for(int i=28;i<=30;i++)move(s,i,IndustrialWork::food);}
            case SHEPHERD->{move(s,27,x->x.getItem() instanceof ShearsItem);move(s,28,x->x.is(Items.WHEAT));}
            case FISHERMAN->move(s,27,x->x.getItem() instanceof FishingRodItem);
            case BREEDER->{move(s,28,x->x.is(Items.BONE));move(s,29,x->x.getItem().isEdible()&&x.getItem().getFoodProperties()!=null&&x.getItem().getFoodProperties().isMeat());}
            case BEEKEEPER->{
                move(s,28,x->x.is(net.minecraft.tags.ItemTags.FLOWERS));
                if(s.getItem(27).isEmpty()){moveBottle(s);move(s,27,x->x.getItem() instanceof ShearsItem);}
                if(!s.getItem(27).is(Items.GLASS_BOTTLE)&&find(s,x->x.is(Items.GLASS_BOTTLE),0,27)>=0){
                    if(insert(s,s.getItem(27).copy(),true)){s.setItem(27,ItemStack.EMPTY);moveBottle(s);}
                }
            }
            default->{}
        }
    }
    private static void moveBottle(EmploymentData.Station s){
        if(!s.getItem(27).isEmpty())return;int i=find(s,x->x.is(Items.GLASS_BOTTLE),0,27);
        if(i>=0)s.setItem(27,s.removeItem(i,1));
    }
    public static int find(EmploymentData.Station s,Predicate<ItemStack> p,int min,int max){for(int i=min;i<max;i++)if(!s.getItem(i).isEmpty()&&p.test(s.getItem(i)))return i;return -1;}
    public static boolean food(ItemStack s){return s.is(Items.WHEAT)||s.is(Items.CARROT)||s.is(Items.POTATO)||s.is(Items.BEETROOT)||s.is(Items.WHEAT_SEEDS)||s.is(Items.BEETROOT_SEEDS)||s.is(Items.MELON_SEEDS)||s.is(Items.PUMPKIN_SEEDS);}
    /** Simulate all outputs first, then commit exactly once to the shared 3x9. */
    public static boolean outputs(EmploymentData.Station s,List<ItemStack> outputs,boolean commit){
        var copy=new SimpleContainer(27);for(int i=0;i<27;i++)copy.setItem(i,s.getItem(i).copy());
        for(ItemStack out:outputs)if(!copy.addItem(out.copy()).isEmpty())return false;
        if(commit){for(int i=0;i<27;i++)s.setItem(i,copy.getItem(i));s.setChanged();}return true;
    }
    public static boolean exchange(EmploymentData.Station s,int consumed,int amount,List<ItemStack> outputs,boolean commit){
        var copy=new SimpleContainer(27);for(int i=0;i<27;i++)copy.setItem(i,s.getItem(i).copy());
        if(consumed>=0&&consumed<27){if(copy.getItem(consumed).getCount()<amount)return false;copy.removeItem(consumed,amount);}
        for(var out:outputs)if(!copy.addItem(out.copy()).isEmpty())return false;
        if(commit){for(int i=0;i<27;i++)s.setItem(i,copy.getItem(i));s.setChanged();}return true;
    }
    public static boolean insert(EmploymentData.Station s,ItemStack out,boolean commit){return outputs(s,List.of(out),commit);}
    public static Container chest(ServerLevel l,EmploymentData.Station s){
        if(!s.industry.contains("discard"))return null;BlockPos p=BlockPos.of(s.industry.getLong("discard"));
        if(!l.hasChunkAt(p)||!area(l,s).contains(Vec3.atCenterOf(p)))return null;
        var be=l.getBlockEntity(p);
        if(be instanceof ChestBlockEntity&&l.getBlockState(p).getBlock() instanceof ChestBlock b)return ChestBlock.getContainer(b,l.getBlockState(p),l,p,true);
        return be instanceof BarrelBlockEntity c?c:null;
    }
    public static void deposit(ServerLevel l,EmploymentData.Station s,Villager v){
        Container chest=chest(l,s);if(chest==null){alert(l,s,"discard","Associe um Baú de Descarte dentro da área; itens continuam no posto.");return;}
        BlockPos p=BlockPos.of(s.industry.getLong("discard"));if(!allowed(l,s,v,p,null,"deposit"))return;
        int reserve=IndustrialRules.size(l,s)*IndustrialRules.size(l,s);
        for(int i=0;i<27;i++){
            var stack=s.getItem(i);int keep=s.profession==Profession.FARMER&&plant(stack)!=null?Math.min(reserve,stack.getCount()):0;
            if(keep>0)reserve-=keep;
            int count=stack.getCount()-keep;if(count<=0)continue;
            ItemStack excess=stack.copy();excess.setCount(count);ItemStack remainder=transfer(chest,excess);
            stack.shrink(count-remainder.getCount());s.setChanged();
        }
    }
    public static ItemStack transfer(Container target,ItemStack source){
        ItemStack rest=source.copy();for(int i=0;i<target.getContainerSize()&&!rest.isEmpty();i++){
            ItemStack old=target.getItem(i);if(!target.canPlaceItem(i,rest))continue;
            int capacity=Math.min(target.getMaxStackSize(),rest.getMaxStackSize());
            if(old.isEmpty()){ItemStack n=rest.split(Math.min(rest.getCount(),capacity));target.setItem(i,n);}
            else if(ItemStack.isSameItemSameTags(old,rest)){int n=Math.min(rest.getCount(),capacity-old.getCount());if(n>0){old.grow(n);rest.shrink(n);target.setChanged();}}
        }return rest;
    }
    private static void dawnSupply(ServerLevel l,EmploymentData.Station s,Villager v){
        int remaining=5;
        for(var farmer:EmploymentData.get(l).stations()){
            if(remaining<=0)break;if(!farmer.active||farmer.profession!=Profession.FARMER||!farmer.dimension.equals(s.dimension)||farmer.pos.distSqr(s.pos)>4096)continue;
            Container source=chest(l,farmer);if(source==null)continue;BlockPos p=BlockPos.of(farmer.industry.getLong("discard"));
            if(!allowed(l,s,v,p,null,"dawn_supply"))continue;
            for(int i=0;i<source.getContainerSize()&&remaining>0;i++){
                var item=source.getItem(i);if(s.profession==Profession.SHEPHERD?!item.is(Items.WHEAT):!food(item))continue;
                int n=Math.min(remaining,item.getCount());while(n>0){ItemStack take=item.copy();take.setCount(n);if(insert(s,take,false)){insert(s,take,true);source.removeItem(i,n);source.setChanged();remaining-=n;break;}n--;}
            }
        }
    }
    // Vanilla crop semantics: seeds are reserved before bonus produce, no second loot invocation.
    public static BlockState plant(ItemStack item){
        if(item.getItem() instanceof BlockItem b&&b.getBlock() instanceof CropBlock)return b.getBlock().defaultBlockState();return null;
    }
    private static boolean breakAllowed(ServerLevel l,EmploymentData.Station s,Villager v,BlockPos p){
        return allowed(l,s,v,p,null,"harvest")&&!MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(l,p,l.getBlockState(p),actor(l,s,v)));
    }
    private static boolean placeAllowed(ServerLevel l,EmploymentData.Station s,Villager v,BlockPos p,BlockState next){
        if(!allowed(l,s,v,p,null,"plant"))return false;
        var snap=BlockSnapshot.create(l.dimension(),l,p);var old=l.getBlockState(p);
        // Place event observes proposed state without publishing updates. Restore before transaction.
        l.setBlock(p,next,16);boolean denied=ForgeEventFactory.onBlockPlace(actor(l,s,v),snap,Direction.UP);l.setBlock(p,old,16);return !denied;
    }
    private static void farm(ServerLevel l,EmploymentData.Station s,Villager v){
        var tool=s.getItem(27);if(!(tool.getItem() instanceof HoeItem)){alert(l,s,"tool","Falta enxada/arador utilizável.");return;}
        int size=IndustrialRules.size(l,s),total=size*size*5,cursor=s.industry.getInt("cursor");
        for(int scan=0;scan<32;scan++){
            int index=Math.floorMod(cursor+scan,total),column=index/5;
            BlockPos p=s.pos.offset(column%size-size/2,index%5-2,column/size-size/2);
            if(!l.hasChunkAt(p))continue;BlockState state=l.getBlockState(p);
            if(state.getBlock() instanceof CropBlock crop){
                if(!crop.isMaxAge(state)){
                    if(!s.getItem(28).is(Items.BONE_MEAL))continue;
                    if(!reach(v,p)){s.industry.putInt("cursor",index);s.setChanged();return;}
                    if(allowed(l,s,v,p,null,"bonemeal")&&crop.isValidBonemealTarget(l,p,state,false)){
                        if(crop.isBonemealSuccess(l,l.random,p,state))crop.performBonemeal(l,l.random,p,state);
                        s.getItem(28).shrink(1);s.industry.putInt("cursor",index);s.setChanged();return;
                    }continue;
                }
                if(!reach(v,p)){s.industry.putInt("cursor",index);s.setChanged();return;}
                if(!breakAllowed(l,s,v,p))continue;
                Item seed=state.getBlock()==Blocks.WHEAT?Items.WHEAT_SEEDS:state.getBlock()==Blocks.BEETROOTS?Items.BEETROOT_SEEDS:state.getBlock()==Blocks.POTATOES?Items.POTATO:state.getBlock()==Blocks.CARROTS?Items.CARROT:state.getBlock()==Blocks.NETHER_WART?Items.NETHER_WART:null;
                var loot=new ArrayList<>(Block.getDrops(state,l,p,l.getBlockEntity(p),v,tool));
                ItemStack reserve=ItemStack.EMPTY;
                if(seed!=null)for(var drop:loot)if(drop.is(seed)&&!drop.isEmpty()){reserve=drop.split(1);break;}
                int input=find(s,x->x.getItem()==seed,0,27);
                boolean fromInventory=reserve.isEmpty();
                if(reserve.isEmpty()&&input>=0){reserve=s.getItem(input).copy();reserve.setCount(1);}
                boolean replant=!reserve.isEmpty()&&placeAllowed(l,s,v,p,crop.getStateForAge(0));
                // If blocked replant, don't consume/reserve a seed or alter this configured bed.
                if(!replant)continue;
                Item produce=state.is(Blocks.WHEAT)?Items.WHEAT:state.is(Blocks.BEETROOTS)?Items.BEETROOT:state.is(Blocks.POTATOES)?Items.POTATO:state.is(Blocks.CARROTS)?Items.CARROT:null;
                if(produce!=null){loot.removeIf(x->x.is(produce));loot.add(new ItemStack(produce,1+l.random.nextInt(IndustrialTools.tier(tool)+1)));}
                if(!exchange(s,fromInventory?input:-1,1,loot,false)){alert(l,s,"space","Inventário cheio; colheita preservada no terreno.");return;}
                if(l.setBlock(p,crop.getStateForAge(0),3)){
                    // A seed taken from storage is used only when loot had none.
                    exchange(s,fromInventory?input:-1,1,loot,true);IndustrialRules.wear(l,s,tool);IndustrialRules.progress(l,s);
                }
                s.industry.putInt("cursor",(index+1)%total);s.setChanged();return;
            }
            if(state.isAir()&&l.hasChunkAt(p.below())){
                int input=find(s,x->plant(x)!=null,0,27);if(input<0)continue;
                BlockState crop=plant(s.getItem(input));BlockState soil=l.getBlockState(p.below());
                boolean till=soil.is(Blocks.DIRT)||soil.is(Blocks.GRASS_BLOCK)||soil.is(Blocks.DIRT_PATH);
                if(!soil.is(Blocks.FARMLAND)&&!till)continue;
                if(!reach(v,p)){s.industry.putInt("cursor",index);s.setChanged();return;}
                if(!placeAllowed(l,s,v,p,crop))continue;
                if(till){if(!breakAllowed(l,s,v,p.below())||!placeAllowed(l,s,v,p.below(),Blocks.FARMLAND.defaultBlockState()))continue;if(!l.setBlock(p.below(),Blocks.FARMLAND.defaultBlockState(),3))continue;IndustrialRules.wear(l,s,tool);}
                if(crop.canSurvive(l,p)&&l.setBlock(p,crop,3)){s.removeItem(input,1);s.setChanged();}
                s.industry.putInt("cursor",(index+1)%total);return;
            }
        }
        s.industry.putInt("cursor",(cursor+32)%total);s.setChanged();
    }
    private static boolean livestock(Animal a){return a instanceof Cow||a instanceof Pig||a instanceof Sheep||a instanceof Chicken||a instanceof Rabbit;}
    private static void animals(ServerLevel l,EmploymentData.Station s,Villager v,Profession profession){
        List<Animal> animals=l.getEntitiesOfClass(Animal.class,area(l,s),a->a.isAlive()&&(profession==Profession.SHEPHERD?a instanceof Sheep:profession==Profession.BREEDER?a instanceof Wolf:livestock(a)&&s.id.equals(a.getPersistentData().hasUUID("KCIndustryStation")?a.getPersistentData().getUUID("KCIndustryStation"):null)));
        for(var a:animals)if(!a.getPersistentData().contains("KCIndustryBorn"))a.getPersistentData().putLong("KCIndustryBorn",l.getGameTime()-a.tickCount-(a.isBaby()?24000+a.getAge():24000));
        int adults=(int)animals.stream().filter(a->!a.isBaby()).count(),capacity=IndustrialRules.capacity(IndustrialRules.level(l,s));
        if(animals.isEmpty())alert(l,s,"animals","Faltam animais elegíveis dentro da área de trabalho.");
        if(profession==Profession.BREEDER){
            int cared=(int)animals.stream().filter(a->a.getPersistentData().hasUUID("KraftCityCaretaker")&&s.worker.equals(a.getPersistentData().getUUID("KraftCityCaretaker"))).count();
            for(var a:animals)if(cared<capacity&&a instanceof Wolf wolf&&!wolf.isTame()&&!wolf.isAngry()&&!wolf.getPersistentData().hasUUID("KraftCityCaretaker")){
                if(!s.getItem(28).is(Items.BONE)){alert(l,s,"bones","Faltam ossos para cuidar dos lobos sem dono.");break;}
                if(!reach(v,wolf.blockPosition()))return;
                if(!allowed(l,s,v,wolf.blockPosition(),wolf,"tame"))continue;
                s.getItem(28).shrink(1);s.setChanged();
                if(l.random.nextInt(3)==0){wolf.getPersistentData().putUUID("KraftCityCaretaker",s.worker);wolf.setTame(true);wolf.setPersistenceRequired();IndustrialRules.progress(l,s);}
                return;
            }
        }
        if(profession==Profession.SHEPHERD){
            var tool=s.getItem(27);if(!(tool.getItem() instanceof ShearsItem)){alert(l,s,"tool","Faltam tesouras utilizáveis.");return;}
            for(var animal:animals)if(animal instanceof Sheep sheep&&sheep.readyForShearing()){
                if(!reach(v,sheep.blockPosition()))return;if(!allowed(l,s,v,sheep.blockPosition(),sheep,"shear"))continue;
                Item wool=net.minecraft.core.Registry.BLOCK.get(new net.minecraft.resources.ResourceLocation(sheep.getColor().getName()+"_wool")).asItem();
                var result=new ItemStack(wool,1+l.random.nextInt(3));
                if(!insert(s,result,false)){alert(l,s,"space","Sem espaço para lã.");return;}
                sheep.setSheared(true);insert(s,result,true);IndustrialRules.wear(l,s,tool);IndustrialRules.progress(l,s);return;
            }
        }
        if(profession==Profession.BUTCHER){
            var tool=s.getItem(27);if(!(tool.getItem() instanceof AxeItem)){alert(l,s,"tool","Falta machado utilizável.");return;}
            if(adults>capacity){
                var chosen=animals.stream().filter(a->!a.isBaby()&&animals.stream().filter(b->!b.isBaby()&&b.getType()==a.getType()).count()>=3)
                    .min(Comparator.comparingLong(a->a.getPersistentData().getLong("KCIndustryBorn"))).orElse(null);
                if(chosen!=null){
                    if(!reach(v,chosen.blockPosition()))return;if(!allowed(l,s,v,chosen.blockPosition(),chosen,"slaughter")||MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(actor(l,s,v),chosen)))return;
                    // Calculate the real entity loot table once, without world drops. Then apply the explicit meat tier rule.
                    var ctx=new LootContext.Builder(l).withParameter(LootContextParams.THIS_ENTITY,chosen).withParameter(LootContextParams.ORIGIN,chosen.position())
                        .withParameter(LootContextParams.DAMAGE_SOURCE,net.minecraft.world.damagesource.DamageSource.mobAttack(v)).withOptionalParameter(LootContextParams.KILLER_ENTITY,v);
                    var drops=new ArrayList<>(l.getServer().getLootTables().get(chosen.getLootTable()).getRandomItems(ctx.create(LootContextParamSets.ENTITY)));
                    for(var out:drops)if(out.getItem().isEdible()&&out.getItem().getFoodProperties().isMeat())out.setCount(1+l.random.nextInt(IndustrialTools.tier(tool)+1));
                    if(!outputs(s,drops,false)){alert(l,s,"space","Sem espaço para o abate; animal preservado.");return;}
                    // Forge death cancellation is respected; discard avoids invoking vanilla loot a second time.
                    if(MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingDeathEvent(chosen,net.minecraft.world.damagesource.DamageSource.mobAttack(v))))return;
                    outputs(s,drops,true);chosen.discard();IndustrialRules.wear(l,s,tool);IndustrialRules.progress(l,s);return;
                }
            }
            int bucket=s.industry.contains("milk")&&l.getGameTime()-s.industry.getLong("milk")<1200?-1:find(s,x->x.is(Items.BUCKET),0,27);
            if(bucket>=0)for(var a:animals)if(a instanceof Cow&&!a.isBaby()&&(!a.getPersistentData().contains("KCIndustryMilk")||l.getGameTime()-a.getPersistentData().getLong("KCIndustryMilk")>=1200)){
                if(!reach(v,a.blockPosition()))return;
                if(!exchange(s,bucket,1,List.of(new ItemStack(Items.MILK_BUCKET)),false)||!allowed(l,s,v,a.blockPosition(),a,"milk"))continue;
                exchange(s,bucket,1,List.of(new ItemStack(Items.MILK_BUCKET)),true);a.getPersistentData().putLong("KCIndustryMilk",l.getGameTime());s.industry.putLong("milk",l.getGameTime());s.setChanged();return;
            }
        }
        boolean breed=profession==Profession.BUTCHER?adults<=capacity&&animals.size()-adults<2:animals.size()<capacity;
        long before=s.industry.contains("bred")?s.industry.getLong("bred"):Long.MIN_VALUE;
        if(breed)breed(l,s,v,animals,profession);
        if(profession==Profession.BREEDER&&before==(s.industry.contains("bred")?s.industry.getLong("bred"):Long.MIN_VALUE)){
            for(var a:animals)if(a instanceof Wolf w&&w.getOwnerUUID()==null&&w.getPersistentData().hasUUID("KraftCityCaretaker")&&s.worker.equals(w.getPersistentData().getUUID("KraftCityCaretaker"))
                    &&(!w.getPersistentData().contains("KCCareAt")||l.getGameTime()-w.getPersistentData().getLong("KCCareAt")>=EmploymentConfig.CARE_TICKS.get())){
                if(!s.getItem(28).is(Items.BONE)){alert(l,s,"bones","Faltam ossos para o cuidado dos lobos.");return;}
                if(!reach(v,w.blockPosition()))return;if(!allowed(l,s,v,w.blockPosition(),w,"care"))return;
                s.removeItem(28,1);w.getPersistentData().putLong("KCCareAt",l.getGameTime());IndustrialRules.progress(l,s);return;
            }
        }
    }
    private static void breed(ServerLevel l,EmploymentData.Station s,Villager v,List<Animal> animals,Profession profession){
        if(s.industry.contains("bred")&&l.getGameTime()-s.industry.getLong("bred")<EmploymentConfig.BREED_TICKS.get())return;
        for(Animal a:animals){
            if(a.isBaby()||a.getAge()!=0||a.isInLove())continue;
            if(a instanceof Wolf w&&(w.getOwnerUUID()!=null||!w.getPersistentData().hasUUID("KraftCityCaretaker")||!s.worker.equals(w.getPersistentData().getUUID("KraftCityCaretaker"))))continue;
            for(Animal b:animals){
                if(b==a||b.getType()!=a.getType()||b.isBaby()||b.getAge()!=0||b.isInLove()||a.distanceToSqr(b)>64)continue;
                if(b instanceof Wolf w&&(w.getOwnerUUID()!=null||!w.getPersistentData().hasUUID("KraftCityCaretaker")||!s.worker.equals(w.getPersistentData().getUUID("KraftCityCaretaker"))))continue;
                int available=0;for(int slot=0;slot<31;slot++)if(slot!=27&&a.isFood(s.getItem(slot))&&b.isFood(s.getItem(slot)))available+=s.getItem(slot).getCount();
                if(available<2){alert(l,s,"food","Faltam dois alimentos adequados à mesma espécie para reprodução.");return;}
                if(!reach(v,a.blockPosition()))return;
                if(!allowed(l,s,v,a.blockPosition(),a,"breed")||!allowed(l,s,v,b.blockPosition(),b,"breed"))return;
                AgeableMob baby=a.getBreedOffspring(l,b);if(baby==null)return;
                var event=new net.minecraftforge.event.entity.living.BabyEntitySpawnEvent(a,b,baby);
                if(MinecraftForge.EVENT_BUS.post(event)||event.getChild()==null)return;baby=event.getChild();
                baby.setBaby(true);baby.moveTo(a.getX(),a.getY(),a.getZ(),0,0);
                baby.getPersistentData().putLong("KCIndustryBorn",l.getGameTime());baby.getPersistentData().putUUID("KCIndustryStation",s.id);
                if(profession==Profession.BREEDER)baby.getPersistentData().putUUID("KraftCityCaretaker",s.worker);
                if(!l.addFreshEntity(baby))return;
                a.setAge(6000);b.setAge(6000);a.resetLove();b.resetLove();
                int needed=2;for(int pass=0;pass<2&&needed>0;pass++)for(int slot=pass==0?28:0;slot<(pass==0?31:27)&&needed>0;slot++)if(a.isFood(s.getItem(slot))&&b.isFood(s.getItem(slot))){int n=Math.min(needed,s.getItem(slot).getCount());s.removeItem(slot,n);needed-=n;}
                s.industry.putLong("bred",l.getGameTime());s.setChanged();
                if(profession==Profession.BREEDER)IndustrialRules.progress(l,s);return;
            }
        }
    }
    public static boolean validWater(ServerLevel l,BlockPos center){
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){
            BlockPos p=center.offset(x,y,z);if(!l.hasChunkAt(p)||!l.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER)||!l.getBlockState(p).getCollisionShape(l,p).isEmpty())return false;
        }return true;
    }
    private static BlockPos water(ServerLevel l,EmploymentData.Station s){
        if(s.industry.contains("water")){var p=BlockPos.of(s.industry.getLong("water"));int r=IndustrialRules.size(l,s)/2;
            if(Math.abs(p.getX()-s.pos.getX())+1<=r&&Math.abs(p.getZ()-s.pos.getZ())+1<=r&&p.getY()+1<=s.pos.getY()&&p.getY()-1>=s.pos.getY()-10&&validWater(l,p))return p;
            s.industry.remove("water");s.industry.remove("fish_elapsed");}
        int size=IndustrialRules.size(l,s),width=Math.max(1,size-2),total=width*width*12,c=s.industry.getInt("water_cursor");
        for(int i=0;i<16;i++){
            int n=Math.floorMod(c+i,total),column=n/12;
            BlockPos p=s.pos.offset(column%width-width/2,n%12-9,column/width-width/2);
            if(p.getY()+1>s.pos.getY())continue;
            if(validWater(l,p)){s.industry.putLong("water",p.asLong());s.industry.putInt("fish_elapsed",0);s.setChanged();return p;}
        }
        s.industry.putInt("water_cursor",(c+16)%total);s.setChanged();return null;
    }
    private static void fish(ServerLevel l,EmploymentData.Station s,Villager v){
        ItemStack rod=s.getItem(27);if(!(rod.getItem() instanceof FishingRodItem)){alert(l,s,"tool","Falta vara de pesca utilizável.");s.industry.remove("fish_elapsed");return;}
        BlockPos water=water(l,s);if(water==null){alert(l,s,"water","Falta volume de água livre de pelo menos 3×3×3, abaixo ou na altura do posto.");return;}
        if(!reach(v,s.pos)){s.industry.remove("fish_elapsed");return;}
        if(!allowed(l,s,v,water,null,"fish")){s.industry.remove("fish_elapsed");return;}
        int elapsed=s.industry.getInt("fish_elapsed")+Math.max(1,EmploymentConfig.ACTION_TICKS.get());s.industry.putInt("fish_elapsed",elapsed);s.setChanged();
        if(elapsed<IndustrialRules.fishingTicks(EmploymentConfig.FISH_TICKS.get(),IndustrialRules.level(l,s)))return;
        // Real vanilla fish table, no treasure or pretend bobber/open-water loot.
        var ctx=new LootContext.Builder(l).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(water)).withParameter(LootContextParams.TOOL,rod).withParameter(LootContextParams.THIS_ENTITY,v);
        var drops=l.getServer().getLootTables().get(new net.minecraft.resources.ResourceLocation("gameplay/fishing/fish")).getRandomItems(ctx.create(LootContextParamSets.FISHING));
        if(!outputs(s,drops,false)){alert(l,s,"space","Sem espaço para pescado.");return;}
        outputs(s,drops,true);IndustrialRules.wear(l,s,rod);IndustrialRules.progress(l,s);s.industry.putInt("fish_elapsed",0);s.setChanged();
    }
    private static void bees(ServerLevel l,EmploymentData.Station s,Villager v){
        int size=IndustrialRules.size(l,s),total=size*size*5,c=s.industry.getInt("cursor");
        for(int i=0;i<32;i++){
            int n=Math.floorMod(c+i,total),column=n/5;BlockPos p=s.pos.offset(column%size-size/2,n%5-2,column/size-size/2);
            if(!l.hasChunkAt(p))continue;BlockState state=l.getBlockState(p);
            if(!(state.getBlock() instanceof BeehiveBlock hive))continue;
            var known=s.industry.getList("hives",Tag.TAG_LONG);boolean exists=false;for(Tag raw:known)if(((LongTag)raw).getAsLong()==p.asLong())exists=true;
            if(!exists){known.add(LongTag.valueOf(p.asLong()));s.industry.put("hives",known);}
            if(state.getValue(BeehiveBlock.HONEY_LEVEL)!=5)continue;
            if(!CampfireBlock.isSmokeyPos(l,p)){alert(l,s,"smoke","Colmeia cheia requer fumaça de fogueira para coleta segura.");continue;}
            ItemStack tool=s.getItem(27);boolean bottle=tool.is(Items.GLASS_BOTTLE),shears=tool.getItem() instanceof ShearsItem;
            if(!bottle&&find(s,x->x.is(Items.GLASS_BOTTLE),0,27)>=0){alert(l,s,"space","Libere espaço para priorizar os frascos de mel.");return;}
            if(!bottle&&!shears){alert(l,s,"tool","Faltam frascos vazios ou tesouras para coleta.");return;}
            if(!reach(v,p)){s.industry.putInt("cursor",n);s.setChanged();return;}
            if(!allowed(l,s,v,p,null,"honey"))continue;
            ItemStack result=new ItemStack(bottle?Items.HONEY_BOTTLE:Items.HONEYCOMB,bottle?1:3);
            if(!insert(s,result,false)){alert(l,s,"space","Sem espaço; mel e frascos preservados.");return;}
            if(!l.setBlock(p,state.setValue(BeehiveBlock.HONEY_LEVEL,0),3))continue;
            if(bottle)tool.shrink(1);else IndustrialRules.wear(l,s,tool);
            insert(s,result,true);IndustrialRules.progress(l,s);s.industry.putInt("cursor",(n+1)%total);s.setChanged();return;
        }
        s.industry.putInt("cursor",(c+32)%total);s.setChanged();
        var animals=l.getEntitiesOfClass(Animal.class,area(l,s),a->a instanceof Bee&&!((Bee)a).isAngry());
        // Capacity counts live bees plus hive occupants; no reproduction when full.
        int occupants=animals.size();boolean unloaded=false;
        var known=s.industry.getList("hives",Tag.TAG_LONG);
        for(int i=known.size()-1;i>=0;i--){BlockPos p=BlockPos.of(((LongTag)known.get(i)).getAsLong());
            if(!l.hasChunkAt(p)){unloaded=true;continue;}
            if(!area(l,s).contains(Vec3.atCenterOf(p))||!(l.getBlockEntity(p) instanceof BeehiveBlockEntity)){known.remove(i);continue;}
            occupants+=((BeehiveBlockEntity)l.getBlockEntity(p)).getOccupantCount();
        }
        if(unloaded)return;
        if(occupants<IndustrialRules.capacity(IndustrialRules.level(l,s)))breed(l,s,v,animals,Profession.BEEKEEPER);
    }
    private IndustrialWork(){}
}
