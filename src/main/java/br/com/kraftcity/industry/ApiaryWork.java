package br.com.kraftcity.industry;

import br.com.kraftcity.employment.Employment;
import br.com.kraftcity.employment.EmploymentData;
import br.com.kraftcity.employment.Profession;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="residenciasaldeoes")
public final class ApiaryWork {
    private static final String HIVES="apiary_hives";
    static final String BEES="apiary_bees", ARCHIVED_BEES="apiary_retired_bees";
    static final String OWNER="KCApiary", ID="KCApiaryBee";
    private static final String WORKER="KCApiaryWorker", COLONY="apiary_colony_worker";
    public static final String POLLINATE_AFTER="KCApiaryPollinateAfter";
    private static final String ATTEMPT="KCApiaryAttempt", BREED="KCApiaryBreed";
    private static final TagKey<net.minecraft.world.item.Item> EMPTY_BOTTLES=
        TagKey.create(Registry.ITEM_REGISTRY,new ResourceLocation("residenciasaldeoes","apiary_bottles"));
    private static final boolean TRACE = Boolean.getBoolean("kraftcity.debugApiaryTaming");
    private record Candidate(Bee bee,boolean canFeed,boolean lured,boolean visible,double distance){}
    private record Scan(String task,int found,int eligible,int flowers,int population,int capacity,UUID target){}
    private record LoggedScan(Scan state,long tick){}
    private static final Map<Villager,LoggedScan> LAST_SCAN=new WeakHashMap<>();
    private static final Map<Villager,Long> FLOWER_SWINGS=new WeakHashMap<>();
    private static final int SHEARS=EmploymentData.TOOL, BOTTLES=EmploymentData.INPUT_A, FLOWERS=EmploymentData.INPUT_B;

    public static boolean validInput(int slot,ItemStack stack){
        return switch(slot){
            case 0->stack.canPerformAction(ToolActions.SHEARS_HARVEST);
            case 1->stack.is(Items.GLASS_BOTTLE)||stack.is(EMPTY_BOTTLES);
            case 2->stack.is(ItemTags.FLOWERS);
            default->false;
        };
    }

    static void migrateInputs(EmploymentData.Station station){
        var tool=station.getItem(SHEARS);
        var previousFlowers=station.getItem(BOTTLES);
        if(validInput(2,previousFlowers)){
            if(station.getItem(FLOWERS).isEmpty())station.setItem(FLOWERS,previousFlowers);
            else if(!IndustrialWork.insert(station,previousFlowers,false))return;
            else IndustrialWork.insert(station,previousFlowers,true);
            station.setItem(BOTTLES,ItemStack.EMPTY);
        }
        if(validInput(1,tool)){
            var bottles=station.getItem(BOTTLES);
            if(bottles.isEmpty())station.setItem(BOTTLES,tool);
            else if(ItemStack.isSameItemSameTags(bottles,tool)&&bottles.getCount()+tool.getCount()<=bottles.getMaxStackSize()){
                bottles.grow(tool.getCount());
            }else if(!IndustrialWork.insert(station,tool,true))return;
            station.setItem(SHEARS,ItemStack.EMPTY);
        }
        var legacy=station.getItem(EmploymentData.EQUIPMENT);
        if(!legacy.isEmpty()&&IndustrialWork.insert(station,legacy,true))
            station.setItem(EmploymentData.EQUIPMENT,ItemStack.EMPTY);
    }

    static boolean tick(ServerLevel level,EmploymentData.Station station,Villager worker,boolean mobGriefing){
        prepareColony(station);
        var work=IndustrialWork.area(level,station);
        if(FLOWER_SWINGS.getOrDefault(worker,0L)<=level.getGameTime())show(worker,collectionItem(station));
        var hives=registeredHives(level,station,work);
        var roster=station.industry.getCompound(BEES);
        for(var id:roster.getAllKeys()){
            if(!roster.contains(id,Tag.TAG_LONG))continue;
            var home=BlockPos.of(roster.getLong(id));
            if(!work.contains(Vec3.atCenterOf(home))||level.hasChunkAt(home)&&!(level.getBlockEntity(home) instanceof BeehiveBlockEntity)){
                roster.putBoolean(id,true);
                station.setChanged();
            }
        }
        var bees=level.getEntitiesOfClass(Bee.class,work,bee->work.contains(bee.position())&&bee.isAlive());
        for(var bee:bees)syncBond(level,bee);
        if(mobGriefing)for(var pos:hives){
            if(!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive))continue;
            if(hive.getBlockState().getValue(BeehiveBlock.HONEY_LEVEL)!=5)continue;
            if(hive.isEmpty()&&bees.stream().noneMatch(bee->pos.equals(bee.getHivePos())))continue;
            if(harvest(level,station,worker,hive))return true;
        }
        if(mobGriefing&&FarmerLogistics.collect(level,station,worker))return true;
        if(mobGriefing&&ApiaryHiveStock.work(level,station,worker,work,hives))return true;
        var flowers=station.getItem(FLOWERS);
        int population=beeCount(station)+reservedBirth(level,station);
        int capacity=capacity(level,station);
        if(!validInput(2,flowers)||population>=capacity){
            if(TRACE){
                var observed=level.getEntitiesOfClass(Bee.class,worker.getBoundingBox().inflate(10),
                    bee->bee.distanceToSqr(worker)<=100);
                int eligible=(int)observed.stream().filter(ApiaryWork::wildCandidate).count();
                scan(worker,population>=capacity?"capacity":"no_flowers",
                    observed.size(),eligible,flowers,population,capacity,null);
            }
        }else{
            var nearby=level.getEntitiesOfClass(Bee.class,worker.getBoundingBox().inflate(12),
                bee->bee.distanceToSqr(worker)<=100||ApiaryBeeFlight.luredBy(bee,worker));
            for(var bee:nearby)syncBond(level,bee);
            var eligible=nearby.stream().filter(bee->wildCandidate(bee)&&ApiaryBeeFlight.available(bee,worker)).toList();
            var candidates=eligible.stream()
                .map(bee->new Candidate(bee,canFeed(level,worker,bee,work),
                    ApiaryBeeFlight.luredBy(bee,worker),worker.hasLineOfSight(bee),bee.distanceToSqr(worker)))
                .filter(candidate->!level.isRaining()||candidate.canFeed())
                .sorted(Comparator.comparing((Candidate candidate)->!candidate.canFeed())
                    .thenComparing(candidate->!candidate.lured())
                    .thenComparing(candidate->!candidate.visible())
                    .thenComparingDouble(Candidate::distance))
                .limit(capacity-population).map(Candidate::bee).toList();
            if(!candidates.isEmpty()){
                var wild=candidates.get(0);
                scan(worker,"taming",nearby.size(),eligible.size(),flowers,population,capacity,wild);
                if(!level.isRaining()&&work.expandTowards(0,-1,0).contains(worker.position())){
                    // Keep the colony candidates nearby while the worker feeds them one at a time.
                    for(var candidate:candidates)ApiaryBeeFlight.lure(candidate,worker,work);
                }
                domesticate(level,station,worker,wild,flowers,work);
                return true;
            }
            scan(worker,level.isRaining()?"rain":"no_eligible_bee",nearby.size(),eligible.size(),flowers,population,capacity,null);
            if(mobGriefing&&breed(level,station,worker,bees,flowers))return true;
        }
        return false;
    }

    static void blocked(ServerLevel level,EmploymentData.Station station,Villager worker,String reason){
        if(TRACE)scan(worker,reason,0,0,station.getItem(FLOWERS),beeCount(station)+reservedBirth(level,station),capacity(level,station),null);
    }

    private static void scan(Villager worker,String task,int found,int eligible,ItemStack flowers,int population,int capacity,Bee target){
        if(!TRACE)return;
        var state=new Scan(task,found,eligible,flowers.getCount(),population,capacity,target==null?null:target.getUUID());
        long now=worker.level.getGameTime();
        var last=LAST_SCAN.get(worker);
        if(last!=null&&(state.equals(last.state())||state.task().equals(last.state().task())&&now-last.tick()<20))return;
        LAST_SCAN.put(worker,new LoggedScan(state,now));
        com.mojang.logging.LogUtils.getLogger().info("Apiary taming worker={} {}",worker.getUUID(),state);
    }

    private static List<BlockPos> registeredHives(ServerLevel level,EmploymentData.Station station,AABB area){
        int limit=hiveCapacity(level,station);
        var stored=station.industry.getList(HIVES,Tag.TAG_LONG);
        var candidates=new java.util.HashSet<BlockPos>();
        for(Tag tag:stored){
            var pos=BlockPos.of(((LongTag)tag).getAsLong());
            if(area.contains(Vec3.atCenterOf(pos))&&!level.hasChunkAt(pos))candidates.add(pos);
        }
        for(var pos:BlockPos.betweenClosed(new BlockPos(area.minX,area.minY,area.minZ),
                new BlockPos(area.maxX-1,area.maxY-1,area.maxZ-1))){
            if(level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof BeehiveBlockEntity
                    &&ApiaryHiveStock.belongsHere(level,station,pos))candidates.add(pos.immutable());
        }
        var result=candidates.stream().sorted(Comparator.comparingDouble((BlockPos pos)->station.pos.distSqr(pos))
            .thenComparingLong(BlockPos::asLong)).limit(limit).toList();
        var next=new ListTag();
        for(var pos:result)next.add(LongTag.valueOf(pos.asLong()));
        if(!next.equals(stored)){
            station.industry.put(HIVES,next);
            station.setChanged();
        }
        return result;
    }

    static boolean claimed(ServerLevel level,EmploymentData.Station station,BlockPos pos){
        for(var other:EmploymentData.get(level).stations()){
            if(other==station||!other.active||other.worker==null||other.profession!=Profession.BEEKEEPER||!station.dimension.equals(other.dimension))continue;
            if(IndustrialWork.area(level,other).contains(Vec3.atCenterOf(pos))&&other.industry.getList(HIVES,Tag.TAG_LONG).contains(LongTag.valueOf(pos.asLong())))return true;
        }
        return false;
    }

    private static int bottleSlot(EmploymentData.Station station){
        return validInput(1,station.getItem(BOTTLES))?BOTTLES:IndustrialWork.find(station,stack->validInput(1,stack),0,27);
    }

    private static ItemStack collectionItem(EmploymentData.Station station){
        int bottle=bottleSlot(station);
        var shears=station.getItem(SHEARS);
        return bottle>=0?station.getItem(bottle):validInput(0,shears)?shears:ItemStack.EMPTY;
    }

    private static boolean harvest(ServerLevel level,EmploymentData.Station station,Villager worker,BeehiveBlockEntity hive){
        var pos=hive.getBlockPos();
        int bottleSlot=bottleSlot(station);
        boolean bottling=bottleSlot>=0;
        var resource=bottling?station.getItem(bottleSlot):station.getItem(SHEARS);
        if(!validInput(bottling?1:0,resource)){
            IndustrialWork.alert(level,station,"apiary_tool","Faltam frascos vazios ou tesoura.");
            return false;
        }
        show(worker,resource);
        if(!visible(level,worker,Vec3.atCenterOf(pos),pos))return approachHive(level,station,worker,pos);
        if(!IndustrialWork.allowed(level,station,worker,pos,null,bottling?"honey":"honeycomb")
            ||level.getBlockEntity(pos)!=hive)return false;
        var state=hive.getBlockState();
        if(state.getValue(BeehiveBlock.HONEY_LEVEL)!=5)return false;
        var products=List.of(new ItemStack(bottling?Items.HONEY_BOTTLE:Items.HONEYCOMB,bottling?1:3));
        if(!IndustrialWork.exchange(station,bottleSlot,1,products,false)){
            IndustrialWork.alert(level,station,"apiary_space","Sem espaço para a produção da colmeia.");
            return false;
        }
        long previousProtection=station.industry.getLong("apiary_protected_until");
        station.industry.putLong("apiary_protected_until",level.getGameTime()+200);
        if(!level.setBlock(pos,state.setValue(BeehiveBlock.HONEY_LEVEL,0),3)){
            station.industry.putLong("apiary_protected_until",previousProtection);
            return false;
        }
        IndustrialWork.exchange(station,bottleSlot,1,products,true);
        if(bottling&&bottleSlot>=27)station.removeItem(bottleSlot,1);
        if(!bottling)IndustrialRules.wear(level,station,resource);
        if(!CampfireBlock.isSmokeyPos(level,pos))defendHive(level,station,worker,hive,state);
        for(var bee:level.getEntitiesOfClass(Bee.class,new AABB(pos).inflate(12),
                candidate->owned(candidate.getPersistentData(),station)))clearWorkerAnger(bee,worker);
        worker.getNavigation().stop();
        worker.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,Vec3.atCenterOf(pos));
        worker.swing(InteractionHand.MAIN_HAND);
        level.playSound(null,pos,bottling?SoundEvents.BOTTLE_FILL:SoundEvents.BEEHIVE_SHEAR,SoundSource.BLOCKS,1,1);
        IndustrialRules.progress(level,station);
        return true;
    }

    static boolean approachHive(ServerLevel level,EmploymentData.Station station,Villager worker,BlockPos hive){
        var bounds=IndustrialWork.area(level,station).expandTowards(0,-1,0);
        var destination=Vec3.atCenterOf(hive);
        var options=new ArrayList<BlockPos>();
        for(var direction:Direction.Plane.HORIZONTAL){
            for(int down=0;down<=2;down++)options.add(hive.relative(direction).below(down));
        }
        options.sort(Comparator.comparingDouble(pos->worker.distanceToSqr(Vec3.atBottomCenterOf(pos))));
        for(var pos:options){
            if(!bounds.contains(Vec3.atCenterOf(pos))||!level.hasChunkAt(pos))continue;
            var eyes=Vec3.atBottomCenterOf(pos).add(0,worker.getEyeHeight(),0);
            if(!hasView(level,worker,eyes,destination,hive))continue;
            var path=worker.getNavigation().createPath(pos,0);
            if(path==null||!path.canReach())continue;
            boolean inside=true;
            for(int i=0;i<path.getNodeCount();i++){
                if(bounds.contains(Vec3.atBottomCenterOf(path.getNode(i).asBlockPos())))continue;
                inside=false;
                break;
            }
            if(inside&&worker.getNavigation().moveTo(path,Employment.WALK_SPEED))return true;
        }
        return false;
    }

    private static void defendHive(ServerLevel level,EmploymentData.Station station,Villager worker,
                                   BeehiveBlockEntity hive,net.minecraft.world.level.block.state.BlockState state){
        // Leave an exclusively bonded hive undisturbed. Mixed occupants use the
        // vanilla emergency exit, with retaliation limited to unbonded bees.
        boolean mixed=false;
        for(Tag tag:hive.writeBees()){
            var entity=((CompoundTag)tag).getCompound(BeehiveBlockEntity.ENTITY_DATA);
            if(!owned(entity.getCompound("ForgeData"),station)){mixed=true;break;}
        }
        if(mixed)hive.emptyAllLivingFromHive(null,state,BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
        for(var bee:level.getEntitiesOfClass(Bee.class,new AABB(hive.getBlockPos()).inflate(8,6,8),
                candidate->!owned(candidate.getPersistentData(),station)&&!candidate.hasStung()
                    &&(candidate.getHivePos()==null||hive.getBlockPos().equals(candidate.getHivePos())))){
            if(bee.getTarget()!=null)continue;
            bee.setTarget(worker);
            bee.setPersistentAngerTarget(worker.getUUID());
            bee.startPersistentAngerTimer();
        }
    }

    private static boolean wildCandidate(Bee bee){
        return bee.isAlive()&&!bee.getPersistentData().hasUUID(OWNER)
            &&!bee.isAngry()&&!bee.hasStung()&&!bee.isInLove();
    }

    private static boolean canFeed(ServerLevel level,Villager worker,Bee bee,AABB area){
        return area.contains(bee.position())&&visible(level,worker,bee.getEyePosition(),null);
    }

    private static void domesticate(ServerLevel level,EmploymentData.Station station,Villager worker,Bee bee,ItemStack flowers,AABB area){
        show(worker,flowers);
        worker.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,bee.getEyePosition());
        if(!area.expandTowards(0,-1,0).contains(worker.position())){
            FarmerLogistics.stayInField(level,station,worker);
            return;
        }
        worker.getNavigation().stop();
        if(!canFeed(level,worker,bee,area))return;
        var data=bee.getPersistentData();
        if(data.hasUUID(OWNER)||beeCount(station)>=capacity(level,station))return;
        if(!IndustrialWork.allowed(level,station,worker,bee.blockPosition(),bee,"apiary_tame")){
            blocked(level,station,worker,"interaction_protected");
            return;
        }
        var attempt=data.getCompound(ATTEMPT);
        if(!attempt.hasUUID("station")||!station.id.equals(attempt.getUUID("station"))){
            attempt=new CompoundTag();
            attempt.putUUID("station",station.id);
            attempt.putInt("remaining",1+bee.getRandom().nextInt(5));
        }
        flowers.shrink(1);
        if(TRACE)com.mojang.logging.LogUtils.getLogger().info("Apiary worker={} fed bee={} remaining={} bound={}",
            worker.getUUID(),bee.getUUID(),attempt.getInt("remaining")-1,attempt.getInt("remaining")<=1);
        FLOWER_SWINGS.put(worker,level.getGameTime()+8);
        int remaining=attempt.getInt("remaining")-1;
        worker.getNavigation().stop();
        worker.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,bee.getEyePosition());
        worker.swing(InteractionHand.MAIN_HAND);
        if(remaining<=0){
            ApiaryBeeFlight.finishLure(bee);
            bind(level,station,bee);
            clearWorkerAnger(bee,worker);
            data.remove(ATTEMPT);
            IndustrialRules.progress(level,station);
        }else{
            attempt.putInt("remaining",remaining);
            data.put(ATTEMPT,attempt);
        }
        level.sendParticles(ParticleTypes.HEART,bee.getX(),bee.getY()+bee.getBbHeight()+.22,bee.getZ(),
            remaining<=0?3:1,.18,.12,.18,0);
        station.setChanged();
    }

    private static boolean breed(ServerLevel level,EmploymentData.Station station,Villager worker,List<Bee> bees,ItemStack flowers){
        long now=level.getGameTime();
        if(station.industry.getLong("apiary_breed_until")>now||flowers.getCount()<2)return false;
        var parents=bees.stream().filter(bee->owned(bee.getPersistentData(),station)
            &&bee.getAge()==0&&bee.canFallInLove()&&!bee.isAngry()&&!bee.hasStung())
            .sorted(Comparator.comparingDouble(worker::distanceToSqr)).toList();
        var pair=parents.stream().flatMap(first->parents.stream().filter(second->second!=first
            &&first.distanceToSqr(second)<=16).map(second->List.of(first,second))).findFirst().orElse(List.of());
        if(pair.isEmpty())return false;
        show(worker,flowers);
        for(var bee:pair){
            if(!visible(level,worker,bee.getEyePosition(),null)){
                worker.getNavigation().moveTo(bee,Employment.WALK_SPEED);
                return true;
            }
            if(!IndustrialWork.allowed(level,station,worker,bee.blockPosition(),bee,"apiary_breed"))return false;
        }
        var attempt=UUID.randomUUID();
        for(var bee:pair){
            bee.getPersistentData().putUUID(BREED,attempt);
            bee.setInLove(null);
        }
        flowers.shrink(2);
        FLOWER_SWINGS.put(worker,now+8);
        worker.getNavigation().stop();
        worker.swing(InteractionHand.MAIN_HAND);
        station.industry.putLong("apiary_breed_until",now+600);
        station.industry.putUUID("apiary_breed_pair",attempt);
        station.setChanged();
        return true;
    }

    static boolean visible(ServerLevel level,Villager worker,Vec3 target,BlockPos block){
        return hasView(level,worker,worker.getEyePosition(),target,block);
    }

    private static boolean hasView(ServerLevel level,Villager worker,Vec3 eyes,Vec3 target,BlockPos block){
        if(eyes.distanceToSqr(target)>9)return false;
        var hit=level.clip(new ClipContext(eyes,target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,worker));
        return hit.getType()==HitResult.Type.MISS||block!=null&&block.equals(hit.getBlockPos());
    }

    private static void show(Villager worker,ItemStack stack){
        var display=stack.copy();
        if(!display.isEmpty())display.setCount(1);
        if(ItemStack.matches(worker.getMainHandItem(),display))return;
        worker.setItemSlot(EquipmentSlot.MAINHAND,display);
        worker.setDropChance(EquipmentSlot.MAINHAND,0);
    }

    private static int reservedBirth(ServerLevel level,EmploymentData.Station station){
        return station.industry.getLong("apiary_breed_until")>level.getGameTime()?1:0;
    }

    static int hiveCapacity(ServerLevel level,EmploymentData.Station station){
        return Math.min(IndustrialRules.level(level,station),1+(IndustrialRules.size(level,station)-7)/2);
    }

    private static int capacity(ServerLevel level,EmploymentData.Station station){return 3*hiveCapacity(level,station);}

    // Legacy registry entries are booleans. A long in the same entry records a known habitual hive.
    public static void recordHive(ServerLevel level,CompoundTag data,BlockPos pos){
        if(pos==null||!data.hasUUID(OWNER))return;
        var station=EmploymentData.get(level).station(data.getUUID(OWNER));
        if(!owned(data,station)||!station.dimension.equals(level.dimension()))return;
        var roster=station.industry.getCompound(BEES);
        String id=data.getUUID(ID).toString();
        if(!IndustrialWork.area(level,station).contains(Vec3.atCenterOf(pos))
                ||level.hasChunkAt(pos)&&!(level.getBlockEntity(pos) instanceof BeehiveBlockEntity)){
            if(roster.contains(id,Tag.TAG_LONG)&&roster.getLong(id)==pos.asLong()){
                roster.putBoolean(id,true);
                station.setChanged();
            }
        }else if(!roster.contains(id,Tag.TAG_LONG)||roster.getLong(id)!=pos.asLong()){
            roster.putLong(id,pos.asLong());
            station.setChanged();
        }
    }

    static boolean allHome(ServerLevel level,EmploymentData.Station station,BeehiveBlockEntity hive){
        var present=new java.util.HashSet<String>();
        for(var occupant:hive.writeBees()){
            var data=((CompoundTag)occupant).getCompound(BeehiveBlockEntity.ENTITY_DATA).getCompound("ForgeData");
            if(owned(data,station))present.add(data.getUUID(ID).toString());
        }
        var roster=station.industry.getCompound(BEES);
        long pos=hive.getBlockPos().asLong();
        for(var id:roster.getAllKeys()){
            if(roster.contains(id,Tag.TAG_LONG)&&roster.getLong(id)==pos&&!present.contains(id))return false;
        }
        return level.getEntitiesOfClass(Bee.class,new AABB(hive.getBlockPos()).inflate(48),
            bee->bee.isAlive()&&hive.getBlockPos().equals(bee.getHivePos())&&owned(bee.getPersistentData(),station)).isEmpty();
    }

    public static boolean canKeepHive(ServerLevel level,Bee bee,BlockPos pos){
        var area=territory(level,bee);
        return area==null||area.contains(Vec3.atCenterOf(pos));
    }

    public static boolean canDiscoverHive(ServerLevel level,Bee bee,BlockPos pos){
        var station=owner(level,bee);
        if(!owned(bee.getPersistentData(),station))return true;
        if(!station.dimension.equals(level.dimension())||!IndustrialWork.area(level,station).contains(Vec3.atCenterOf(pos)))return false;
        if(station.industry.getList(HIVES,Tag.TAG_LONG).contains(LongTag.valueOf(pos.asLong())))return true;
        var roster=station.industry.getCompound(BEES);
        String id=bee.getPersistentData().getUUID(ID).toString();
        return roster.contains(id,Tag.TAG_LONG)&&roster.getLong(id)==pos.asLong();
    }

    public static boolean holdHiveOccupant(ServerLevel level,BlockPos pos,CompoundTag data){
        if(!data.hasUUID(OWNER))return false;
        var station=EmploymentData.get(level).station(data.getUUID(OWNER));
        if(!owned(data,station)||!station.dimension.equals(level.dimension())||!ApiaryHiveStock.hasVacancy(station)
                ||!IndustrialWork.area(level,station).contains(Vec3.atCenterOf(pos)))return false;
        var registered=station.industry.getList(HIVES,Tag.TAG_LONG);
        return registered.size()>=hiveCapacity(level,station)&&!registered.contains(LongTag.valueOf(pos.asLong()))
            &&ApiaryHiveStock.belongsHere(level,station,pos)
            &&level.getEntity(station.worker) instanceof Villager worker&&worker.isAlive()&&!worker.isNoAi()
            &&ForgeEventFactory.getMobGriefingEvent(level,worker);
    }
    private static int beeCount(EmploymentData.Station station){return station.industry.getCompound(BEES).size();}
    private static void prepareColony(EmploymentData.Station station){
        if(station.industry.hasUUID(COLONY)&&station.industry.getUUID(COLONY).equals(station.worker))return;
        boolean knownLegacyOwner=!station.industry.hasUUID(COLONY)&&station.worker!=null
            &&station.industry.hasUUID("apiary_worker")&&station.worker.equals(station.industry.getUUID("apiary_worker"))
            &&br.com.kraftcity.employment.Employment.epoch(station)==1;
        if(!knownLegacyOwner)clearColony(station);
        if(station.worker!=null)station.industry.putUUID(COLONY,station.worker);
        station.setChanged();
    }

    private static void clearColony(EmploymentData.Station station){
        station.industry.remove(COLONY);
        station.industry.remove(BEES);
        station.industry.remove(ARCHIVED_BEES);
        station.industry.remove("apiary_protected_until");
        station.industry.remove("apiary_breed_until");
        station.industry.remove("apiary_breed_pair");
        station.setChanged();
    }

    /** A release ends this contract. Vanilla zombification retains the assigned worker UUID. */
    public static void releaseColony(ServerLevel level,EmploymentData.Station station){
        clearColony(station);
        for(var world:level.getServer().getAllLevels()){
            for(var entity:world.getAllEntities()){
                if(entity instanceof Bee bee&&bee.getPersistentData().hasUUID(OWNER)
                        &&station.id.equals(bee.getPersistentData().getUUID(OWNER))){
                    clearBond(bee.getPersistentData());
                    ((ApiaryBeeAppearance)bee).kraftcity$setBonded(false);
                }
            }
        }
    }

    private static void clearBond(CompoundTag data){
        for(var key:List.of(OWNER,ID,WORKER,BREED,POLLINATE_AFTER))data.remove(key);
    }

    private static boolean owned(CompoundTag data,EmploymentData.Station station){
        return station!=null&&station.active&&station.worker!=null&&data.hasUUID(OWNER)
            &&station.id.equals(data.getUUID(OWNER))&&data.hasUUID(ID)
            &&(!data.hasUUID(WORKER)||station.worker.equals(data.getUUID(WORKER)))
            &&station.industry.getCompound(BEES).contains(data.getUUID(ID).toString());
    }

    /** Also normalizes legacy or ended contracts when they are encountered inside a hive. */
    public static boolean reconcileStoredBond(ServerLevel level,CompoundTag data){
        if(!data.hasUUID(OWNER)||!data.hasUUID(ID))return false;
        var station=EmploymentData.get(level).station(data.getUUID(OWNER));
        if(station!=null&&station.worker!=null)prepareColony(station);
        if(owned(data,station)){
            data.putUUID(WORKER,station.worker);
            return true;
        }
        if(station==null||!station.active||station.worker==null
                ||data.hasUUID(WORKER)&&!station.worker.equals(data.getUUID(WORKER))
                ||!station.industry.getCompound(ARCHIVED_BEES).contains(data.getUUID(ID).toString()))
            clearBond(data);
        return false;
    }

    private static void syncBond(ServerLevel level,Bee bee){
        var data=bee.getPersistentData();
        boolean bonded=reconcileStoredBond(level,data);
        if(bonded)recordHive(level,data,bee.getHivePos());
        ((ApiaryBeeAppearance)bee).kraftcity$setBonded(bonded);
    }

    private static void bind(ServerLevel level,EmploymentData.Station station,Bee bee){
        var data=bee.getPersistentData();
        if(!data.hasUUID(ID))data.putUUID(ID,UUID.randomUUID());
        prepareColony(station);
        data.putUUID(OWNER,station.id);
        data.putUUID(WORKER,station.worker);
        var bees=station.industry.getCompound(BEES);
        bees.putBoolean(data.getUUID(ID).toString(),true);
        ((ApiaryBeeAppearance)bee).kraftcity$setBonded(true);
        station.industry.put(BEES,bees);
        recordHive(level,data,bee.getHivePos());
        station.setChanged();
    }

    private static EmploymentData.Station owner(ServerLevel level,Bee bee){
        var data=bee.getPersistentData();
        return data.hasUUID(OWNER)?EmploymentData.get(level).station(data.getUUID(OWNER)):null;
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void onBirth(BabyEntitySpawnEvent event){
        if(!(event.getParentA() instanceof Bee first)||!(event.getParentB() instanceof Bee second)
            ||!(event.getChild() instanceof Bee child)||!(first.level instanceof ServerLevel level))return;
        var station=owner(level,first);
        var a=first.getPersistentData();
        var b=second.getPersistentData();
        if(station==null||!station.active||station.worker==null||!owned(a,station)||!owned(b,station)
            ||!a.hasUUID(BREED)||!b.hasUUID(BREED)||!a.getUUID(BREED).equals(b.getUUID(BREED)))return;
        var pair=a.getUUID(BREED);
        a.remove(BREED);
        b.remove(BREED);
        if(station.industry.hasUUID("apiary_breed_pair")&&!pair.equals(station.industry.getUUID("apiary_breed_pair"))
            ||beeCount(station)>=capacity(level,station)){
            event.setCanceled(true);
            return;
        }
        child.getPersistentData().putUUID("KCApiaryNewborn",station.id);
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void onJoin(EntityJoinLevelEvent event){
        if(!(event.getEntity() instanceof Bee bee)||!(event.getLevel() instanceof ServerLevel level))return;
        ApiaryBeeFlight.install(bee);
        syncBond(level,bee);
        restoreArchivedBond(level,owner(level,bee),bee);
        var data=bee.getPersistentData();
        if(!data.hasUUID("KCApiaryNewborn"))return;
        var station=EmploymentData.get(level).station(data.getUUID("KCApiaryNewborn"));
        data.remove("KCApiaryNewborn");
        if(station!=null&&station.active&&station.worker!=null&&station.dimension.equals(level.dimension())
            &&beeCount(station)<capacity(level,station)){
            station.industry.remove("apiary_breed_until");
            station.industry.remove("apiary_breed_pair");
            bind(level,station,bee);
            IndustrialRules.progress(level,station);
        }
    }

    private static void restoreArchivedBond(ServerLevel level,EmploymentData.Station station,Bee bee){
        var data=bee.getPersistentData();
        if(station==null||!station.active||station.worker==null||!station.dimension.equals(level.dimension())
                ||!data.hasUUID(ID)||data.hasUUID(WORKER)&&!station.worker.equals(data.getUUID(WORKER)))return;
        var archived=station.industry.getCompound(ARCHIVED_BEES);
        var id=data.getUUID(ID).toString();
        if(!archived.contains(id)||beeCount(station)+reservedBirth(level,station)>=capacity(level,station))return;
        archived.remove(id);
        bind(level,station,bee);
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event){
        if(!(event.getEntity() instanceof Bee bee)||!(bee.level instanceof ServerLevel level))return;
        var station=owner(level,bee);
        if(station==null||!owned(bee.getPersistentData(),station))return;
        station.industry.getCompound(BEES).remove(bee.getPersistentData().getUUID(ID).toString());
        station.setChanged();
    }

    static AABB territory(ServerLevel level,Bee bee){
        var station=owner(level,bee);
        return station!=null&&station.active&&station.dimension.equals(level.dimension())
            &&owned(bee.getPersistentData(),station)?IndustrialWork.area(level,station):null;
    }

    private static void clearWorkerAnger(Bee bee,Villager worker){
        if(bee.getTarget()==worker)bee.setTarget(null);
        if(worker.getUUID().equals(bee.getPersistentAngerTarget())){
            bee.setPersistentAngerTarget(null);
            bee.setRemainingPersistentAngerTime(0);
        }
        if(bee.getLastHurtByMob()==worker)bee.setLastHurtByMob(null);
    }

    private static boolean protectedWorker(Bee bee,net.minecraft.world.entity.LivingEntity target){
        if(!(target instanceof Villager worker)||!(bee.level instanceof ServerLevel level))return false;
        var station=owner(level,bee);
        return station!=null&&station.active&&worker.getUUID().equals(station.worker)
            &&owned(bee.getPersistentData(),station)
            &&station.industry.getLong("apiary_protected_until")>level.getGameTime();
    }

    @SubscribeEvent
    public static void preventRetargeting(LivingChangeTargetEvent event){
        if(event.getEntity() instanceof Bee bee&&protectedWorker(bee,event.getNewTarget()))event.setCanceled(true);
    }

    @SubscribeEvent
    public static void preventSting(LivingAttackEvent event){
        if(event.getSource().getEntity() instanceof Bee bee&&protectedWorker(bee,event.getEntity()))event.setCanceled(true);
    }

    @SubscribeEvent
    public static void guide(LivingEvent.LivingTickEvent event){
        if(!(event.getEntity() instanceof Bee bee)||!(bee.level instanceof ServerLevel level))return;
        var anger=bee.getPersistentAngerTarget();
        var target=anger!=null&&level.getEntity(anger) instanceof Villager worker?worker:bee.getTarget();
        if(protectedWorker(bee,target))clearWorkerAnger(bee,(Villager)target);
        if(bee.tickCount%20==0&&bee.getPersistentData().hasUUID(OWNER)){
            syncBond(level,bee);
            restoreArchivedBond(level,owner(level,bee),bee);
        }
        var area=territory(level,bee);
        if(area==null)return;
        ApiaryBeeFlight.guide(level,bee,area);

    }

    private ApiaryWork(){}
}
