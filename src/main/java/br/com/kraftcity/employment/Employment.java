package br.com.kraftcity.employment;

import br.com.kraftcity.workforce.*;
import br.com.residenciasaldeoes.data.ResidenceData;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/** All mutations run on the server thread; no client price/identity is trusted. */
public final class Employment {
    static final ThreadLocal<Boolean> CONVERTING=ThreadLocal.withInitial(()->false);
    private Employment(){}
    public static boolean eligible(ServerLevel level,Villager v){
        var home=ResidenceData.get(level).owner(v.getUUID());
        return v.isAlive()&&!v.isBaby()&&v.level==level&&home!=null&&home.dimension.equals(level.dimension())
                &&(!level.hasChunkAt(home.pos)||level.getBlockEntity(home.pos) instanceof br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity)
                &&v.getVillagerData().getProfession()==VillagerProfession.NONE
                &&WorkforceData.get(level).worker(v.getUUID()).map(w->w.inventory().isEmpty()).orElse(true)
                &&EmploymentData.get(level).assigned(v.getUUID())==null;
    }
    public static List<Villager> candidates(ServerLevel level){
        var out=new ArrayList<Villager>();for(var home:ResidenceData.get(level).homes())
            if(home.dimension.equals(level.dimension()))for(UUID id:home.residents)
                if(level.getEntity(id) instanceof Villager v&&eligible(level,v))out.add(v);
        out.sort(Comparator.comparing(v->v.getUUID().toString()));return out;
    }
    public static boolean active(Villager v){
        return v.level instanceof ServerLevel level&&EmploymentData.get(level).assigned(v.getUUID())!=null;
    }
    private static final Map<Villager,Long> FLEE_UNTIL=new WeakHashMap<>();
    private static final Map<Villager,net.minecraft.world.phys.AABB> ESCAPE_BOUNDS=new WeakHashMap<>();
    private static final Set<Villager> NIGHT_DETOUR=Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Villager> HOMEWARD_ESCAPE=Collections.newSetFromMap(new WeakHashMap<>());

    static boolean seekingShelter(Villager worker){return HOMEWARD_ESCAPE.contains(worker)&&fleeing(worker);}

    public static boolean fleeing(Villager v){
        return FLEE_UNTIL.getOrDefault(v,Long.MIN_VALUE)>v.level.getGameTime();
    }

    static void flee(ServerLevel level,Villager v){
        if(v.tickCount%5!=0||v.isNoAi()||br.com.kraftcity.military.Military.isSoldier(v))return;
        boolean worker=active(v);
        if(!worker&&ResidenceData.get(level).owner(v.getUUID())==null)return;
        var zombie=level.getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class,
            v.getBoundingBox().inflate(8),z->z.isAlive()&&(worker||z instanceof br.com.kraftcity.military.SoldierZombie)
                &&(z.getTarget()==v||v.hasLineOfSight(z))).stream()
            .min(Comparator.comparingDouble(v::distanceToSqr)).orElse(null);
        if(zombie==null){
            HOMEWARD_ESCAPE.remove(v);
            NIGHT_DETOUR.remove(v);
            if(!fleeing(v)&&FLEE_UNTIL.remove(v)!=null){
                ESCAPE_BOUNDS.remove(v);
                v.getNavigation().stop();
                v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                v.getBrain().eraseMemory(MemoryModuleType.PATH);
            }
            return;
        }
        boolean starting=!fleeing(v);
        boolean nightShelter=br.com.residenciasaldeoes.VillagerResidenceEvents.hasNightShelter(level,v);
        if(starting){
            if(nightShelter)br.com.kraftcity.industry.FarmerLogistics.cancelClosingForDanger(level,v);
            br.com.kraftcity.industry.FarmerLogistics.release(v);
            com.mojang.logging.LogUtils.getLogger().debug("Worker {} at {} fleeing zombie at {}",
                v.getUUID(),v.position(),zombie.position());
        }
        if(starting)ESCAPE_BOUNDS.remove(v);
        var bounds=escapeBounds(level,v,nightShelter);
        if(bounds==null&&zombie instanceof br.com.kraftcity.military.SoldierZombie){
            var home=ResidenceData.get(level).owner(v.getUUID());
            if(home!=null&&home.dimension.equals(level.dimension())){
                int r=home.size/2;
                var residential=new net.minecraft.world.phys.AABB(home.pos.offset(-r,1,-r),home.pos.offset(r+1,home.size+1,r+1));
                if(residential.contains(v.position().add(0,.125,0)))bounds=residential;
            }
        }
        if(bounds!=null){
            ESCAPE_BOUNDS.put(v,bounds);
            HOMEWARD_ESCAPE.remove(v);
            NIGHT_DETOUR.remove(v);
            FLEE_UNTIL.put(v,level.getGameTime()+40);
            if(v.isSleeping())v.stopSleeping();
            v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            v.getBrain().eraseMemory(MemoryModuleType.PATH);
            if(starting||v.getNavigation().isDone()||v.tickCount%15==0
                ||!within(v.getNavigation().getPath(),bounds))boundedEscape(level,v,zombie,bounds);
            return;
        }
        if(br.com.residenciasaldeoes.VillagerResidenceEvents.seekResidentialSafety(level,v,zombie)){
            HOMEWARD_ESCAPE.add(v);
            if(!v.hasLineOfSight(zombie)&&zombie.distanceToSqr(v)>9){
                FLEE_UNTIL.remove(v);
                return;
            }
            if(v.isSleeping())v.stopSleeping();
            FLEE_UNTIL.put(v,level.getGameTime()+20);
            return;
        }
        HOMEWARD_ESCAPE.remove(v);
        FLEE_UNTIL.put(v,level.getGameTime()+40);
        // A completed or interrupted detour must not suppress replanning while the zombie is nearby.
        if(!nightShelter||v.getNavigation().isDone())NIGHT_DETOUR.remove(v);
        if(NIGHT_DETOUR.contains(v))return;
        if(starting||v.getNavigation().isDone()||v.tickCount%15==0){
            if(v.isSleeping())v.stopSleeping();
            v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            v.getBrain().eraseMemory(MemoryModuleType.PATH);
            var away=net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(v,12,5,zombie.position());
            if(away!=null){
                if(v.getNavigation().moveTo(away.x,away.y,away.z,escapeSpeed(level,v))&&nightShelter)NIGHT_DETOUR.add(v);
            }
            else if(starting)v.getNavigation().stop();
        }
    }

    private static double escapeSpeed(ServerLevel level,Villager worker){
        var station=EmploymentData.get(level).assigned(worker.getUUID());
        return br.com.kraftcity.industry.FarmerLogistics.fieldWorker(station)?.95:1.25;
    }

    private static net.minecraft.world.phys.AABB escapeBounds(ServerLevel level,Villager worker,boolean nightShelter){
        net.minecraft.world.phys.AABB area=null;
        if(nightShelter){
            var home=ResidenceData.get(level).owner(worker.getUUID());
            int r=home.size/2;
            area=new net.minecraft.world.phys.AABB(home.pos.offset(-r,1,-r),home.pos.offset(r+1,home.size+1,r+1));
        }else if(br.com.kraftcity.industry.IndustrialWork.day(level)){
            var station=EmploymentData.get(level).assigned(worker.getUUID());
            if(station!=null&&station.sector==Profession.Sector.INDUSTRIAL){
                area=br.com.kraftcity.industry.IndustrialWork.area(level,station);
            }
        }
        if(area==null)return null;
        return area.contains(worker.position().add(0,.125,0))||area.equals(ESCAPE_BOUNDS.get(worker))?area:null;
    }

    private static boolean within(net.minecraft.world.level.pathfinder.Path path,net.minecraft.world.phys.AABB bounds){
        if(path==null||!path.canReach())return false;
        for(int i=path.getNextNodeIndex();i<path.getNodeCount();i++)
            if(!bounds.contains(net.minecraft.world.phys.Vec3.atBottomCenterOf(path.getNode(i).asBlockPos())))return false;
        return true;
    }

    private static void boundedEscape(ServerLevel level,Villager worker,net.minecraft.world.entity.LivingEntity threat,
                                      net.minecraft.world.phys.AABB bounds){
        var origin=worker.blockPosition();
        var candidates=new ArrayList<BlockPos>();
        var interior=bounds.deflate(.85,0,.85);
        for(var at:BlockPos.betweenClosed(origin.offset(-7,-1,-7),origin.offset(7,1,7))){
            var feet=net.minecraft.world.phys.Vec3.atBottomCenterOf(at);
            if(!interior.contains(feet)||!level.hasChunkAt(at)||!level.getFluidState(at).isEmpty()
                ||!level.noCollision(worker,worker.getBoundingBox().move(feet.subtract(worker.position())))
                ||level.getBlockState(at.below()).getCollisionShape(level,at.below()).isEmpty())continue;
            candidates.add(at.immutable());
        }
        candidates.sort(Comparator.<BlockPos>comparingDouble(at->threat.distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(at))).reversed());
        var nav=worker.getNavigation();
        for(int i=0;i<Math.min(20,candidates.size());i++){
            var path=nav.createPath(candidates.get(i),0);
            if(within(path,bounds)&&!path.getTarget().equals(origin)&&nav.moveTo(path,escapeSpeed(level,worker)))return;
        }
        if(!within(nav.getPath(),bounds)){
            nav.stop();
            worker.getMoveControl().setWantedPosition(worker.getX(),worker.getY(),worker.getZ(),0);
            worker.setSpeed(0);
        }
    }

    static void recoverFarmerStation(ServerLevel level,EmploymentData.Station station){
        if(!station.active)return;
        int area=br.com.kraftcity.industry.IndustrialRules.size(level,station);
        if(station.worker!=null)release(level,station.worker);
        station.active=false;
        EmploymentData.get(level).remove(station.id);
        var recovery=new SimpleContainer(EmploymentData.SLOTS);
        for(int slot=0;slot<EmploymentData.SLOTS;slot++)recovery.setItem(slot,station.removeItemNoUpdate(slot));
        Containers.dropContents(level,station.pos,recovery);
        var block=new ItemStack(WorkModule.INDUSTRIAL.get());
        if(area>5){
            block.getOrCreateTag().putInt("KraftCityFarmerArea",area);
            if(station.industry.hasUUID("farmer_area_owner"))
                block.getOrCreateTag().putUUID("KraftCityFarmerAreaOwner",station.industry.getUUID("farmer_area_owner"));
        }
        net.minecraft.world.level.block.Block.popResource(level,station.pos,block);
    }

    static boolean pristineIndustrial(EmploymentData.Station station){
        return station.sector==Profession.Sector.INDUSTRIAL&&station.profession==null&&station.worker==null
            &&station.isEmpty()&&station.equipment.isEmpty()&&station.industry.isEmpty()
            &&station.commerce.isEmpty()&&station.military.isEmpty();
    }

    public static void makePortable(ServerLevel level,EmploymentData.Station s){
        if(!s.active)return;
        if(pristineIndustrial(s)){
            s.active=false;
            EmploymentData.get(level).remove(s.id);
            net.minecraft.world.level.block.Block.popResource(level,s.pos,new ItemStack(WorkModule.INDUSTRIAL.get()));
            return;
        }
        if(s.worker!=null)release(level,s.worker);
        s.active=false;s.setChanged();
        var block=s.profession!=null?WorkModule.PROFESSIONAL.get(s.profession).get():WorkModule.generic(s.sector);
        ItemStack token=new ItemStack(block);token.getOrCreateTag().putUUID("KraftCityStation",s.id);
        ItemEntity item=new ItemEntity(level,s.pos.getX()+.5,s.pos.getY()+.5,s.pos.getZ()+.5,token);
        item.setInvulnerable(true);item.setUnlimitedLifetime();level.addFreshEntity(item);
    }
    public static void reconcile(ServerLevel origin,EmploymentData.Station s){
        if(!s.active)return;
        var level=origin.getServer().getLevel(s.dimension);if(level==null||!level.hasChunkAt(s.pos))return;
        if(level.getBlockEntity(s.pos) instanceof WorkBlockEntity existing&&existing.identity()==null
                &&existing.getBlockState().getBlock() instanceof WorkBlock block&&block.sector==s.sector&&block.profession==s.profession)existing.bind(s.id);
        if(!(level.getBlockEntity(s.pos) instanceof WorkBlockEntity be)||!s.id.equals(be.identity()))makePortable(level,s);
        else if(s.worker!=null)valid(level,s.worker);
    }
    public static void release(ServerLevel level,UUID worker){
        br.com.kraftcity.builder.BuilderWork.release(level,worker);
        br.com.kraftcity.military.Military.release(level,worker);
        var data=EmploymentData.get(level);
        for(ServerLevel world:level.getServer().getAllLevels())if(world.getEntity(worker) instanceof Villager v)br.com.kraftcity.industry.IndustrialWork.restoreHand(v);
        for(var s:data.stations())if(worker.equals(s.worker)){
            if(s.profession==Profession.LUMBERJACK&&level.getEntity(worker) instanceof Villager lumberjack)br.com.kraftcity.industry.LumberjackWork.pause(lumberjack);
            if(s.profession==Profession.BEEKEEPER)br.com.kraftcity.industry.ApiaryWork.releaseColony(level,s);
            if(s.profession==Profession.FARMER){
                br.com.kraftcity.industry.IndustrialRules.size(level,s);
                s.industry.putUUID("farmer_area_owner",worker);
            }
            s.worker=null;s.industry.putLong("employment_epoch",epoch(s)+1);if(s.sector==Profession.Sector.COMMERCIAL)s.commerce.putLong("epoch",s.commerce.getLong("epoch")+1);s.setChanged();}
        var workforce=WorkforceData.get(level);var old=workforce.worker(worker).orElse(null);
        if(old!=null&&old.workstation().isPresent()){
            old.profession().ifPresent(p->{var progress=workforce.progress(worker,p);workforce.setProgress(worker,p,old.level(),progress.xp());});
            workforce.put(new WorkerRecord(worker,old.profession(),old.level(),Optional.empty(),old.area(),old.inventory()));
        }
        for(ServerLevel world:level.getServer().getAllLevels())if(world.getEntity(worker) instanceof Villager v)
            br.com.kraftcity.military.Military.residentHealth(world,v);
    }
    public static boolean valid(ServerLevel level,UUID worker){
        var station=EmploymentData.get(level).assigned(worker);if(station==null)return false;
        var home=ResidenceData.get(level).owner(worker);
        if(home==null||!home.dimension.equals(station.dimension)){release(level,worker);return false;}
        var housing=level.getServer().getLevel(home.dimension);
        if(housing==null||(housing.hasChunkAt(home.pos)&&!(housing.getBlockEntity(home.pos) instanceof br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity))){release(level,worker);return false;}
        ServerLevel target=level.getServer().getLevel(station.dimension);if(target==null){release(level,worker);return false;}
        if(target.hasChunkAt(station.pos)){
            if(!(target.getBlockEntity(station.pos) instanceof WorkBlockEntity be)||!station.id.equals(be.identity())){release(level,worker);return false;}
        }
        for(ServerLevel world:level.getServer().getAllLevels())if(world.getEntity(worker) instanceof Villager v){
            if(!v.isAlive()||!world.dimension().equals(station.dimension)){release(level,worker);return false;}
        }
        return true;
    }
    public static boolean indicator(ServerLevel level,UUID worker,boolean legacy){
        // Preserve every legacy indicator for villagers never participating in the new system.
        return WorkforceData.get(level).worker(worker).filter(w->w.profession().isPresent()).isPresent()?valid(level,worker):legacy;
    }
    public static long epoch(EmploymentData.Station station){
        return station.industry.getLong("employment_epoch");
    }

    public static int cost(ServerLevel level,Profession.Sector sector,UUID worker){
        int first=EmploymentConfig.cost(sector);
        return first>=0&&worker!=null&&WorkforceData.get(level).hasEmploymentHistory(worker)?1:first;
    }

    public static boolean dismiss(ServerPlayer player,EmploymentData.Station station,long expectedEpoch){
        var level=player.getLevel();
        if(!level.getServer().isSameThread())throw new IllegalStateException("Dismissal off server thread");
        if(station==null||station.worker==null||epoch(station)!=expectedEpoch||!station.at(level,station.pos)
            ||!level.hasChunkAt(station.pos)||!player.mayBuild()||player.isSpectator()||!level.mayInteract(player,station.pos)
            ||!(level.getBlockEntity(station.pos) instanceof WorkBlockEntity be)||!station.id.equals(be.identity()))return false;
        var worker=station.worker;
        if(player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(station.pos))>=64
            &&(level.getEntity(worker)==null||level.getEntity(worker).distanceToSqr(player)>=64))return false;
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(station.pos),
            net.minecraft.core.Direction.UP,station.pos,false);
        var permission=net.minecraftforge.common.ForgeHooks.onRightClickBlock(player,net.minecraft.world.InteractionHand.MAIN_HAND,station.pos,hit);
        if(permission.isCanceled()||permission.getUseBlock()==net.minecraftforge.eventbus.api.Event.Result.DENY
            ||!worker.equals(station.worker)||epoch(station)!=expectedEpoch
            ||level.getBlockEntity(station.pos)!=be)return false;
        if(station.profession==Profession.SOLDIER){
            br.com.kraftcity.military.Military.detachZombie(level,station);
            station.military.remove("orderedTarget");
            station.military.remove("orderAt");
            station.military.remove("orders");
        }
        if(level.getEntity(worker) instanceof Villager villager){
            FLEE_UNTIL.remove(villager);
            br.com.kraftcity.industry.FarmerLogistics.release(villager);
            villager.getNavigation().stop();
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.getBrain().eraseMemory(MemoryModuleType.PATH);
        }
        station.industry.remove("farmer_discard_until");
        station.industry.remove("farmer_discard_pos");
        station.industry.remove("farmer_pickup_until");
        station.industry.remove("farmer_discard_tried");
        station.commerce.remove("discard_tried");
        release(level,worker);
        return true;
    }

    public static String hire(ServerPlayer player,EmploymentData.Station s,UUID worker,Profession profession){
        ServerLevel level=player.getLevel();
        if(!level.getServer().isSameThread())throw new IllegalStateException("Hiring off server thread");
        if(s==null)return "invalid";
        if(s.military.getBoolean("zombie"))return "ineligible";
        if(!s.at(level,s.pos)||s.worker!=null||(s.profession!=null&&s.profession!=profession)||profession==null||profession.sector!=s.sector)return "invalid";
        if(!(level.getBlockEntity(s.pos) instanceof WorkBlockEntity be)||!s.id.equals(be.identity())
                ||player.distanceToSqr(s.pos.getX()+.5,s.pos.getY()+.5,s.pos.getZ()+.5)>64)return "invalid";
        if(!(level.getEntity(worker) instanceof Villager villager)||!eligible(level,villager))return "ineligible";
        int price=cost(level,s.sector,worker);if(price<0)return "pending";
        var payment=s.getItem(EmploymentData.PAYMENT);
        if(price>0&&(!payment.is(Items.EMERALD)||payment.getCount()<price))return "payment";
        // Physical replacement first. A failed block operation never consumes payment.
        if(s.profession==null){
            CONVERTING.set(true);
            try { if(!level.setBlock(s.pos,WorkModule.PROFESSIONAL.get(profession).get().defaultBlockState().setValue(WorkBlock.FACING,level.getBlockState(s.pos).getValue(WorkBlock.FACING)),3))return "invalid"; }
            finally { CONVERTING.remove(); }
        }
        if(!(level.getBlockEntity(s.pos) instanceof WorkBlockEntity converted))throw new IllegalStateException("Missing professional block entity");
        if(profession==Profession.FARMER){
            if(!s.industry.hasUUID("farmer_area_owner")||!worker.equals(s.industry.getUUID("farmer_area_owner"))){
                s.industry.putInt("size",5);
                s.industry.remove("zigzag_size");
                s.industry.remove("zigzag_harvest");
                s.industry.remove("zigzag_plant");
                s.industry.remove("cursor");
            }
            s.industry.putUUID("farmer_area_owner",worker);
        }
        if(profession==Profession.LUMBERJACK&&(!s.industry.hasUUID("lumberjack_area_owner")
                ||!worker.equals(s.industry.getUUID("lumberjack_area_owner")))){
            s.industry.putInt("size",5);
            s.industry.putUUID("lumberjack_area_owner",worker);
        }
        if(profession==Profession.BEEKEEPER&&(!s.industry.hasUUID("apiary_worker")
                ||!worker.equals(s.industry.getUUID("apiary_worker")))){
            s.industry.putInt("size",7);
            s.industry.putUUID("apiary_worker",worker);
        }
        converted.bind(s.id);s.profession=profession;s.worker=worker;s.industry.putLong("employment_epoch",epoch(s)+1);s.setChanged();
        var workforce=WorkforceData.get(level);var progress=workforce.progress(worker,profession.id());
        var previous=workforce.worker(worker).orElse(null);
        workforce.setProgress(worker,profession.id(),progress.level(),progress.xp());
        workforce.put(new WorkerRecord(worker,Optional.of(profession.id()),progress.level(),
                Optional.of(new Workstation(profession.id(),level.dimension(),s.pos)),previous==null?Optional.empty():previous.area(),List.of()));
        // The custom block is the workstation. Never assign a conflicting vanilla career/POI.
        villager.releasePoi(MemoryModuleType.JOB_SITE);villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
        villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
        if(profession==Profession.BUILDER)workforce.setProgress(worker,profession.id(),br.com.kraftcity.builder.BuilderWork.level(progress.xp()),progress.xp());
        payment.shrink(price);s.setChanged();if(profession==Profession.SOLDIER)br.com.kraftcity.military.Military.trained(level,s,villager,player);else br.com.kraftcity.military.Military.residentHealth(level,villager);return "ok";
    }
}
