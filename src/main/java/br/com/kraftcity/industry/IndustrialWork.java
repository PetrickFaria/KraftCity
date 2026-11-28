package br.com.kraftcity.industry;

import br.com.kraftcity.employment.*;
import br.com.kraftcity.logistics.DiscardChests;
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
        return s!=null&&industrial(s)&&(day(l)||FarmerLogistics.ending(v))&&!v.isSleeping()&&!l.isThundering()&&!Employment.fleeing(v)
            &&!v.getPersistentData().getBoolean("residenciasaldeoes_home_return_forced")
            &&!v.getPersistentData().contains("residenciasaldeoes_emerald_transfer")
            &&!v.getPersistentData().contains("residenciasaldeoes_night_door_task");
    }
    public static boolean day(ServerLevel l){long t=Math.floorMod(l.getDayTime(),24000);return t<12000||t>=23000;}
    public static AABB area(ServerLevel l,EmploymentData.Station s){if(IndustrialRules.centeredArea(s.profession))return LumberjackWork.bounds(s.pos,IndustrialRules.size(l,s));int r=IndustrialRules.size(l,s)/2;if(s.profession==Profession.BEEKEEPER)return new AABB(s.pos.offset(-r,1,-r),s.pos.offset(r+1,6,r+1));return s.profession==Profession.FARMER ? new AABB(s.pos.offset(-r,1,-r),s.pos.offset(r+1,FarmerWork.HEIGHT+1,r+1)) : new AABB(s.pos.offset(-r,-3,-r),s.pos.offset(r+1,4,r+1));}
    public static void tick(ServerLevel l,EmploymentData.Station s){
        if(!industrial(s)||!l.hasChunkAt(s.pos)||!Employment.valid(l,s.worker)||!(l.getEntity(s.worker) instanceof Villager v))return;
        boolean returnsToPost=IndustrialRules.centeredArea(s.profession);
        if(returnsToPost&&(l.isThundering()||!day(l)||v.isSleeping()))
            v.getPersistentData().putBoolean("KCIndustrialReturnToPost",true);
        if(l.isThundering()||Employment.fleeing(v)){
            if(s.profession==Profession.LUMBERJACK){
                LumberjackWork.trace(v,"blocked: storm or danger");
                LumberjackWork.pause(v);
            }
            if(!FarmerLogistics.fieldWorker(s))FarmerLogistics.release(v);
            IndustrialIdle.interrupt(v);
            if(s.profession==Profession.BEEKEEPER){restoreHand(v);ApiaryWork.blocked(l,s,v,"storm_or_danger");}
            s.industry.remove("fish_elapsed");
            return;
        }
        if(!day(l)||v.isSleeping()){
            if(s.profession==Profession.LUMBERJACK){
                LumberjackWork.trace(v,"blocked: off duty or sleeping");
                LumberjackWork.pause(v);
            }
            IndustrialIdle.interrupt(v);
            if(s.profession==Profession.BEEKEEPER){restoreHand(v);ApiaryWork.blocked(l,s,v,"rest");}
            s.industry.remove("fish_elapsed");
            return;
        }
        if(!ownsNavigation(v)||br.com.kraftcity.employment.WorkplaceVisits.leaving(v)){
            if(s.profession==Profession.LUMBERJACK){
                LumberjackWork.trace(v,"blocked: residential or safety route");
                LumberjackWork.pause(v);
            }
            if(!FarmerLogistics.fieldWorker(s))FarmerLogistics.release(v);
            IndustrialIdle.interrupt(v);
            if(s.profession==Profession.BEEKEEPER)ApiaryWork.blocked(l,s,v,"residential_or_safety_route");
            return;
        }
        if(returnsToPost&&v.getPersistentData().getBoolean("KCIndustrialReturnToPost")){
            if(resumeAtPost(l,s,v))return;
            v.getPersistentData().remove("KCIndustrialReturnToPost");
        }
        long day=Math.floorDiv(l.getDayTime()+1000,24000);
        if((s.profession==Profession.BUTCHER||s.profession==Profession.SHEPHERD)&&s.industry.getLong("supplied_day")!=day+1){
            dawnSupply(l,s,v);s.industry.putLong("supplied_day",day+1);s.setChanged();
        }
        replenish(s);showTool(v,s);
        boolean mobGriefing=ForgeEventFactory.getMobGriefingEvent(l,v);
        if(!mobGriefing&&s.profession!=Profession.BEEKEEPER){
            if(s.profession==Profession.LUMBERJACK){
                LumberjackWork.trace(v,"blocked: mob griefing disabled");
                LumberjackWork.pause(v);
            }
            alert(l,s,"protection","Trabalho bloqueado pela proteção/mobGriefing.");
            IndustrialIdle.tick(l,s,v);
            return;
        }
        boolean busy;
        if(!v.isNoAi()&&!FarmerLogistics.fieldWorker(s)&&s.profession!=Profession.LUMBERJACK&&mobGriefing
                &&(FarmerLogistics.collect(l,s,v)||FarmerLogistics.deliverCollected(l,s,v)))busy=true;
        else busy=switch(s.profession){
            case FARMER->{
                if(FarmerLogistics.isJumping(v)||FarmerLogistics.leaveWater(l,v)||FarmerLogistics.collect(l,s,v))yield true;
                if(v.distanceToSqr(area(l,s).getCenter())<=400)yield FarmerWork.tick(l,s,v);
                FarmerLogistics.stayInField(l,s,v);
                yield true;
            }
            case BUTCHER,SHEPHERD,BREEDER->animals(l,s,v,s.profession);
            case FISHERMAN->fish(l,s,v);
            case BEEKEEPER->ApiaryWork.tick(l,s,v,mobGriefing);
            case LUMBERJACK->LumberjackWork.tick(l,s,v);
            default->false;
        };
        if(busy)IndustrialIdle.interrupt(v);
        else IndustrialIdle.tick(l,s,v);
    }
    private static boolean resumeAtPost(ServerLevel level,EmploymentData.Station station,Villager worker){
        if(worker.isNoAi())return true;
        if(worker.distanceToSqr(Vec3.atCenterOf(station.pos))<=9)return false;
        var navigation=worker.getNavigation();
        var active=navigation.getPath();
        if(active!=null&&!navigation.isDone()&&active.getTarget().distSqr(station.pos)<=9)return true;
        if(worker.tickCount%10!=0)return true;
        navigation.setMaxVisitedNodesMultiplier(5);
        try{
            var targets=new java.util.HashSet<BlockPos>();
            for(var direction:Direction.Plane.HORIZONTAL)
                for(int y=-1;y<=1;y++) {
                    var feet=station.pos.relative(direction).above(y);
                    if(level.hasChunkAt(feet)&&level.getBlockState(feet).getCollisionShape(level,feet).isEmpty()
                            &&level.getBlockState(feet.below()).isFaceSturdy(level,feet.below(),Direction.UP))targets.add(feet);
                }
            for(var target:targets.stream().sorted(java.util.Comparator.comparingDouble(worker.blockPosition()::distSqr)).toList()){
                var path=navigation.createPath(target,0,112);
                if(path!=null&&path.canReach()&&navigation.moveTo(path,.8))return true;
            }
            FarmerLogistics.stayInField(level,station,worker);
        }finally{
            navigation.resetMaxVisitedNodesMultiplier();
        }
        return true;
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
        if(action.equals("apiary_tame"))p.setItemInHand(InteractionHand.MAIN_HAND,v.getMainHandItem().copy());
        boolean denied=!br.com.kraftcity.logistics.DiscardChests.canOpen(l,pos,p);
        denied|=MinecraftForge.EVENT_BUS.post(new WorkActionEvent(l,s,v,pos,target,action));
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
        if(!v.getPersistentData().contains("KCFarmerOriginalPickup")){
            v.getPersistentData().putBoolean("KCFarmerOriginalPickup",v.canPickUpLoot());
            v.setCanPickUpLoot(false);
        }
        if(s.profession==Profession.BEEKEEPER||s.profession==Profession.LUMBERJACK)return;
        ItemStack view=s.getItem(27).copy();if(s.profession==Profession.FARMER&&view.getItem() instanceof HoeItem)view.getOrCreateTag().putBoolean("KCFarmerDisplay",true);if(!ItemStack.matches(v.getMainHandItem(),view)){
            // A visual mirror, never a second inventory; empty drop chance, restored on release.
            v.setItemSlot(EquipmentSlot.MAINHAND,view);v.setDropChance(EquipmentSlot.MAINHAND,0);
        }
    }
    static void displayTool(Villager worker,ItemStack stack){
        if(!ItemStack.matches(worker.getMainHandItem(),stack)){
            worker.setItemSlot(EquipmentSlot.MAINHAND,stack.copy());
            worker.setDropChance(EquipmentSlot.MAINHAND,0);
        }
    }
    public static void restoreHand(Villager v){
        if(v.getPersistentData().contains("KCFarmerOriginalPickup")){
            v.setCanPickUpLoot(v.getPersistentData().getBoolean("KCFarmerOriginalPickup"));
            v.getPersistentData().remove("KCFarmerOriginalPickup");
        }
        if(v.getPersistentData().contains("KCOriginalHand")){v.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.of(v.getPersistentData().getCompound("KCOriginalHand")));v.setDropChance(EquipmentSlot.MAINHAND,.085F);v.getPersistentData().remove("KCOriginalHand");}
    }
    private static void move(EmploymentData.Station s,int slot,Predicate<ItemStack> match){
        if(!s.getItem(slot).isEmpty())return;
        for(int i=0;i<27;i++)if(match.test(s.getItem(i))){s.setItem(slot,s.removeItemNoUpdate(i));s.setChanged();return;}
    }
    public static void restoreFarmerInput(EmploymentData.Station station){
        var legacy=station.getItem(EmploymentData.INPUT_B);
        if(!legacy.isEmpty()&&insert(station,legacy.copy(),true))station.setItem(EmploymentData.INPUT_B,ItemStack.EMPTY);
    }

    public static void replenish(EmploymentData.Station s){
        switch(s.profession){
            case FARMER->{restoreFarmerInput(s);move(s,27,x->x.getItem() instanceof HoeItem);move(s,28,x->x.is(Items.BONE_MEAL));move(s,30,x->x.is(Items.WATER_BUCKET));}
            case BUTCHER->{move(s,27,x->x.getItem() instanceof AxeItem);for(int i=28;i<=30;i++)move(s,i,IndustrialWork::food);}
            case SHEPHERD->{move(s,27,x->x.getItem() instanceof ShearsItem);move(s,28,x->x.is(Items.WHEAT));}
            case LUMBERJACK->{move(s,27,x->LumberjackWork.cutTicks(x)>0);move(s,28,LumberjackWork::sapling);}
            case FISHERMAN->move(s,27,x->x.getItem() instanceof FishingRodItem);
            case BREEDER->{move(s,28,x->x.is(Items.BONE));move(s,29,x->x.getItem().isEdible()&&x.getItem().getFoodProperties()!=null&&x.getItem().getFoodProperties().isMeat());}
            case BEEKEEPER->{
                ApiaryWork.migrateInputs(s);
                for(int i=0;i<3;i++){
                    int input=i;
                    move(s,27+i,stack->ApiaryWork.validInput(input,stack));
                }
            }
            default->{}
        }
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
    public static void deposit(ServerLevel l,EmploymentData.Station s,Villager v){
        if(s.profession==Profession.LUMBERJACK)return;
        if(FarmerLogistics.fieldWorker(s)){FarmerLogistics.depositDirect(l,s,v);return;}
        var offers=FarmerLogistics.offers(s);
        var destinations=DiscardChests.destinations(l,s,area(l,s));
        for(var pos:destinations){
            if(!DiscardChests.hasOffers(offers))break;
            DiscardChests.deposit(l,s,v,pos,offers,"deposit");
        }
        if(destinations.isEmpty()&&DiscardChests.hasOffers(offers))
            alert(l,s,"discard","Falta baú de descarte a até cinco blocos do limite; itens continuam no posto.");
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
        int remaining=5;var sources=new ArrayList<br.com.kraftcity.logistics.DiscardChests.Access>();Set<BlockPos> seen=new HashSet<>();
        for(var farmer:EmploymentData.get(l).stations()){
            if(!farmer.active||farmer.profession!=Profession.FARMER||!farmer.dimension.equals(s.dimension)||!l.hasChunkAt(farmer.pos)||farmer.pos.distSqr(s.pos)>4096)continue;
            if(!(l.getBlockEntity(farmer.pos) instanceof WorkBlockEntity be)||!farmer.id.equals(be.identity()))continue;
            for(var access:br.com.kraftcity.logistics.DiscardChests.around(l,farmer,area(l,farmer)))if(seen.add(access.pos()))sources.add(access);
        }
        sources.sort(Comparator.<br.com.kraftcity.logistics.DiscardChests.Access>comparingDouble(a->a.pos().distSqr(s.pos)).thenComparingInt(a->a.pos().getX()).thenComparingInt(a->a.pos().getY()).thenComparingInt(a->a.pos().getZ()));
        for(var access:sources){
            if(remaining<=0)break;Container source=access.inventory();BlockPos p=access.pos();if(!allowed(l,s,v,p,null,"dawn_supply"))continue;
            for(int i=0;i<source.getContainerSize()&&remaining>0;i++){
                var item=source.getItem(i);if(s.profession==Profession.SHEPHERD?!item.is(Items.WHEAT):!food(item))continue;
                int n=Math.min(remaining,item.getCount());while(n>0){ItemStack take=item.copy();take.setCount(n);if(insert(s,take,false)){insert(s,take,true);source.removeItem(i,n);source.setChanged();remaining-=n;break;}n--;}
            }
        }
    }
    static boolean breakAllowed(ServerLevel l,EmploymentData.Station s,Villager v,BlockPos p){
        if(!allowed(l,s,v,p,null,"harvest"))return false;
        var actor=actor(l,s,v);
        if(s.profession!=Profession.FARMER&&s.profession!=Profession.LUMBERJACK){
            return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(l,p,l.getBlockState(p),actor));
        }
        // This event is a permission probe. Exposing the hoe makes player-tool
        // listeners perform their own harvest before KraftCity commits its target.
        var tool=actor.getMainHandItem();
        actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        try{
            return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(l,p,l.getBlockState(p),actor));
        }finally{
            actor.setItemInHand(InteractionHand.MAIN_HAND,tool);
        }
    }
    static boolean placeAllowed(ServerLevel l,EmploymentData.Station s,Villager v,BlockPos p,BlockState next){
        if(!allowed(l,s,v,p,null,"plant"))return false;
        var snapshot=BlockSnapshot.create(l.dimension(),l,p);
        // Event listeners can inspect the proposed state without observing a
        // premature replacement in the world or running its block callbacks.
        return !MinecraftForge.EVENT_BUS.post(new ProposedPlacement(snapshot,l.getBlockState(p.below()),actor(l,s,v),next));
    }

    @net.minecraftforge.eventbus.api.Cancelable
    private static final class ProposedPlacement extends BlockEvent.EntityPlaceEvent {
        private final BlockState proposed;

        ProposedPlacement(BlockSnapshot snapshot,BlockState support,Player actor,BlockState proposed){
            super(snapshot,support,actor);
            this.proposed=proposed;
        }

        @Override public BlockState getState(){return proposed==null?super.getState():proposed;}
        @Override public BlockState getPlacedBlock(){return proposed==null?super.getPlacedBlock():proposed;}
    }

    private static boolean livestock(Animal a){return a instanceof Cow||a instanceof Pig||a instanceof Sheep||a instanceof Chicken||a instanceof Rabbit;}
    private static boolean animals(ServerLevel l,EmploymentData.Station s,Villager v,Profession profession){
        List<Animal> animals=l.getEntitiesOfClass(Animal.class,area(l,s),a->a.isAlive()&&(profession==Profession.SHEPHERD?a instanceof Sheep:profession==Profession.BREEDER?a instanceof Wolf:livestock(a)&&s.id.equals(a.getPersistentData().hasUUID("KCIndustryStation")?a.getPersistentData().getUUID("KCIndustryStation"):null)));
        for(var a:animals)if(!a.getPersistentData().contains("KCIndustryBorn"))a.getPersistentData().putLong("KCIndustryBorn",l.getGameTime()-a.tickCount-(a.isBaby()?24000+a.getAge():24000));
        int adults=(int)animals.stream().filter(a->!a.isBaby()).count(),capacity=IndustrialRules.capacity(IndustrialRules.level(l,s));
        if(animals.isEmpty())alert(l,s,"animals","Faltam animais elegíveis dentro da área de trabalho.");
        if(profession==Profession.BREEDER){
            int cared=(int)animals.stream().filter(a->a.getPersistentData().hasUUID("KraftCityCaretaker")&&s.worker.equals(a.getPersistentData().getUUID("KraftCityCaretaker"))).count();
            for(var a:animals)if(cared<capacity&&a instanceof Wolf wolf&&!wolf.isTame()&&!wolf.isAngry()&&!wolf.getPersistentData().hasUUID("KraftCityCaretaker")){
                if(!s.getItem(28).is(Items.BONE)){alert(l,s,"bones","Faltam ossos para cuidar dos lobos sem dono.");break;}
                if(!reach(v,wolf.blockPosition()))return true;
                if(!allowed(l,s,v,wolf.blockPosition(),wolf,"tame"))continue;
                s.getItem(28).shrink(1);s.setChanged();
                if(l.random.nextInt(3)==0){wolf.getPersistentData().putUUID("KraftCityCaretaker",s.worker);wolf.setTame(true);wolf.setPersistenceRequired();IndustrialRules.progress(l,s);}
                return true;
            }
        }
        if(profession==Profession.SHEPHERD){
            var tool=s.getItem(27);if(!(tool.getItem() instanceof ShearsItem)){alert(l,s,"tool","Faltam tesouras utilizáveis.");return false;}
            for(var animal:animals)if(animal instanceof Sheep sheep&&sheep.readyForShearing()){
                if(!reach(v,sheep.blockPosition()))return true;if(!allowed(l,s,v,sheep.blockPosition(),sheep,"shear"))continue;
                Item wool=net.minecraft.core.Registry.BLOCK.get(new net.minecraft.resources.ResourceLocation(sheep.getColor().getName()+"_wool")).asItem();
                var result=new ItemStack(wool,1+l.random.nextInt(3));
                if(!insert(s,result,false)){alert(l,s,"space","Sem espaço para lã.");return false;}
                sheep.setSheared(true);insert(s,result,true);IndustrialRules.wear(l,s,tool);IndustrialRules.progress(l,s);return true;
            }
        }
        if(profession==Profession.BUTCHER){
            var tool=s.getItem(27);if(!(tool.getItem() instanceof AxeItem)){alert(l,s,"tool","Falta machado utilizável.");return false;}
            if(adults>capacity){
                var chosen=animals.stream().filter(a->!a.isBaby()&&animals.stream().filter(b->!b.isBaby()&&b.getType()==a.getType()).count()>=3)
                    .min(Comparator.comparingLong(a->a.getPersistentData().getLong("KCIndustryBorn"))).orElse(null);
                if(chosen!=null){
                    if(!reach(v,chosen.blockPosition()))return true;if(!allowed(l,s,v,chosen.blockPosition(),chosen,"slaughter")||MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(actor(l,s,v),chosen)))return true;
                    // Calculate the real entity loot table once, without world drops. Then apply the explicit meat tier rule.
                    var ctx=new LootContext.Builder(l).withParameter(LootContextParams.THIS_ENTITY,chosen).withParameter(LootContextParams.ORIGIN,chosen.position())
                        .withParameter(LootContextParams.DAMAGE_SOURCE,net.minecraft.world.damagesource.DamageSource.mobAttack(v)).withOptionalParameter(LootContextParams.KILLER_ENTITY,v);
                    var drops=new ArrayList<>(l.getServer().getLootTables().get(chosen.getLootTable()).getRandomItems(ctx.create(LootContextParamSets.ENTITY)));
                    for(var out:drops)if(out.getItem().isEdible()&&out.getItem().getFoodProperties().isMeat())out.setCount(1+l.random.nextInt(IndustrialTools.tier(tool)+1));
                    if(!outputs(s,drops,false)){alert(l,s,"space","Sem espaço para o abate; animal preservado.");return false;}
                    // Forge death cancellation is respected; discard avoids invoking vanilla loot a second time.
                    if(MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingDeathEvent(chosen,net.minecraft.world.damagesource.DamageSource.mobAttack(v))))return true;
                    outputs(s,drops,true);chosen.discard();IndustrialRules.wear(l,s,tool);IndustrialRules.progress(l,s);return true;
                }
            }
            int bucket=s.industry.contains("milk")&&l.getGameTime()-s.industry.getLong("milk")<1200?-1:find(s,x->x.is(Items.BUCKET),0,27);
            if(bucket>=0)for(var a:animals)if(a instanceof Cow&&!a.isBaby()&&(!a.getPersistentData().contains("KCIndustryMilk")||l.getGameTime()-a.getPersistentData().getLong("KCIndustryMilk")>=1200)){
                if(!reach(v,a.blockPosition()))return true;
                if(!exchange(s,bucket,1,List.of(new ItemStack(Items.MILK_BUCKET)),false)||!allowed(l,s,v,a.blockPosition(),a,"milk"))continue;
                exchange(s,bucket,1,List.of(new ItemStack(Items.MILK_BUCKET)),true);a.getPersistentData().putLong("KCIndustryMilk",l.getGameTime());s.industry.putLong("milk",l.getGameTime());s.setChanged();return true;
            }
        }
        boolean breed=profession==Profession.BUTCHER?adults<=capacity&&animals.size()-adults<2:animals.size()<capacity;
        if(breed&&breed(l,s,v,animals,profession))return true;
        if(profession==Profession.BREEDER){
            for(var a:animals)if(a instanceof Wolf w&&w.getOwnerUUID()==null&&w.getPersistentData().hasUUID("KraftCityCaretaker")&&s.worker.equals(w.getPersistentData().getUUID("KraftCityCaretaker"))
                    &&(!w.getPersistentData().contains("KCCareAt")||l.getGameTime()-w.getPersistentData().getLong("KCCareAt")>=EmploymentConfig.CARE_TICKS.get())){
                if(!s.getItem(28).is(Items.BONE)){alert(l,s,"bones","Faltam ossos para o cuidado dos lobos.");return false;}
                if(!reach(v,w.blockPosition()))return true;if(!allowed(l,s,v,w.blockPosition(),w,"care"))return true;
                s.removeItem(28,1);w.getPersistentData().putLong("KCCareAt",l.getGameTime());IndustrialRules.progress(l,s);return true;
            }
        }
        return false;
    }
    private static boolean breed(ServerLevel l,EmploymentData.Station s,Villager v,List<Animal> animals,Profession profession){
        if(s.industry.contains("bred")&&l.getGameTime()-s.industry.getLong("bred")<EmploymentConfig.BREED_TICKS.get())return false;
        for(Animal a:animals){
            if(a.isBaby()||a.getAge()!=0||a.isInLove())continue;
            if(a instanceof Wolf w&&(w.getOwnerUUID()!=null||!w.getPersistentData().hasUUID("KraftCityCaretaker")||!s.worker.equals(w.getPersistentData().getUUID("KraftCityCaretaker"))))continue;
            for(Animal b:animals){
                if(b==a||b.getType()!=a.getType()||b.isBaby()||b.getAge()!=0||b.isInLove()||a.distanceToSqr(b)>64)continue;
                if(b instanceof Wolf w&&(w.getOwnerUUID()!=null||!w.getPersistentData().hasUUID("KraftCityCaretaker")||!s.worker.equals(w.getPersistentData().getUUID("KraftCityCaretaker"))))continue;
                int available=0;for(int slot=0;slot<31;slot++)if(slot!=27&&a.isFood(s.getItem(slot))&&b.isFood(s.getItem(slot)))available+=s.getItem(slot).getCount();
                if(available<2){alert(l,s,"food","Faltam dois alimentos adequados à mesma espécie para reprodução.");return false;}
                if(!reach(v,a.blockPosition()))return true;
                if(!allowed(l,s,v,a.blockPosition(),a,"breed")||!allowed(l,s,v,b.blockPosition(),b,"breed"))return true;
                AgeableMob baby=a.getBreedOffspring(l,b);if(baby==null)return true;
                var event=new net.minecraftforge.event.entity.living.BabyEntitySpawnEvent(a,b,baby);
                if(MinecraftForge.EVENT_BUS.post(event)||event.getChild()==null)return true;baby=event.getChild();
                baby.setBaby(true);baby.moveTo(a.getX(),a.getY(),a.getZ(),0,0);
                baby.getPersistentData().putLong("KCIndustryBorn",l.getGameTime());baby.getPersistentData().putUUID("KCIndustryStation",s.id);
                if(profession==Profession.BREEDER)baby.getPersistentData().putUUID("KraftCityCaretaker",s.worker);
                if(!l.addFreshEntity(baby))return true;
                a.setAge(6000);b.setAge(6000);a.resetLove();b.resetLove();
                int needed=2;for(int pass=0;pass<2&&needed>0;pass++)for(int slot=pass==0?28:0;slot<(pass==0?31:27)&&needed>0;slot++)if(a.isFood(s.getItem(slot))&&b.isFood(s.getItem(slot))){int n=Math.min(needed,s.getItem(slot).getCount());s.removeItem(slot,n);needed-=n;}
                s.industry.putLong("bred",l.getGameTime());s.setChanged();
                if(profession==Profession.BREEDER)IndustrialRules.progress(l,s);return true;
            }
        }
        return false;
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
    private static boolean fish(ServerLevel l,EmploymentData.Station s,Villager v){
        ItemStack rod=s.getItem(27);if(!(rod.getItem() instanceof FishingRodItem)){alert(l,s,"tool","Falta vara de pesca utilizável.");s.industry.remove("fish_elapsed");return false;}
        BlockPos water=water(l,s);if(water==null){alert(l,s,"water","Falta volume de água livre de pelo menos 3×3×3, abaixo ou na altura do posto.");return false;}
        if(!reach(v,s.pos)){s.industry.remove("fish_elapsed");return true;}
        if(!allowed(l,s,v,water,null,"fish")){s.industry.remove("fish_elapsed");return true;}
        int elapsed=s.industry.getInt("fish_elapsed")+Math.max(1,EmploymentConfig.ACTION_TICKS.get());s.industry.putInt("fish_elapsed",elapsed);s.setChanged();
        if(elapsed<IndustrialRules.fishingTicks(EmploymentConfig.FISH_TICKS.get(),IndustrialRules.level(l,s)))return true;
        // Real vanilla fish table, no treasure or pretend bobber/open-water loot.
        var ctx=new LootContext.Builder(l).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(water)).withParameter(LootContextParams.TOOL,rod).withParameter(LootContextParams.THIS_ENTITY,v);
        var drops=l.getServer().getLootTables().get(new net.minecraft.resources.ResourceLocation("gameplay/fishing/fish")).getRandomItems(ctx.create(LootContextParamSets.FISHING));
        if(!outputs(s,drops,false)){alert(l,s,"space","Sem espaço para pescado.");return false;}
        outputs(s,drops,true);IndustrialRules.wear(l,s,rod);IndustrialRules.progress(l,s);s.industry.putInt("fish_elapsed",0);s.setChanged();
        return true;
    }
    private IndustrialWork(){}
}
