package br.com.kraftcity.industry;

import br.com.kraftcity.employment.Employment;
import br.com.kraftcity.employment.EmploymentData;
import br.com.kraftcity.employment.Profession;
import br.com.kraftcity.logistics.DiscardChests;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

public final class FarmerLogistics {
    public static final String HARVEST_STATION="KraftCityHarvestStation";

    static UUID harvestStation(ItemEntity item){
        var data=item.getPersistentData();
        return data.hasUUID(HARVEST_STATION)?data.getUUID(HARVEST_STATION):null;
    }

    public static boolean sameHarvestOrigin(ItemEntity first,ItemEntity second){
        return Objects.equals(harvestStation(first),harvestStation(second))
            && br.com.kraftcity.military.MilitaryDropOrigin.same(first,second);
    }

    public static boolean blocksVanillaPickup(Villager worker,ItemEntity item){
        if(!(worker.level instanceof ServerLevel level))return false;
        var jobs=EmploymentData.get(level);
        var station=jobs.assigned(worker.getUUID());
        if(station!=null&&IndustrialWork.industrial(station))return true;
        var source=jobs.station(harvestStation(item));
        return source!=null&&source.active&&source.worker!=null;
    }

    private static final Map<Villager,Route> ROUTES=new WeakHashMap<>();
    private static final Map<Villager,Pickup> PICKUPS=new WeakHashMap<>();
    private static final Map<Villager,Long> NEXT_DISCARD=new WeakHashMap<>();
    private static final Map<Villager,Long> AIRBORNE_SINCE=new WeakHashMap<>();
    private record Route(BlockPos target,BlockPos endpoint,double remaining,long progress,int retries){}
    private record Pickup(UUID item,long nextScan){}
    private FarmerLogistics(){}

    private static boolean visible(ServerLevel l,Villager v,Vec3 target,BlockPos allowedHit){
        var eye=v.getEyePosition();
        int minX=(int)Math.floor(Math.min(eye.x,target.x))>>4,maxX=(int)Math.floor(Math.max(eye.x,target.x))>>4;
        int minZ=(int)Math.floor(Math.min(eye.z,target.z))>>4,maxZ=(int)Math.floor(Math.max(eye.z,target.z))>>4;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(l.getChunkSource().getChunkNow(x,z)==null)return false;
        var hit=l.clip(new ClipContext(v.getEyePosition(),target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,v));
        return hit.getType()==HitResult.Type.MISS||allowedHit!=null&&hit.getBlockPos().equals(allowedHit);
    }

    private static final Map<Villager,Map<BlockPos,Long>> BLOCKED=new WeakHashMap<>();
    private static final Map<Villager,Jump> JUMPS=new WeakHashMap<>();
    private static final Map<Villager,Map<UUID,Long>> UNREACHABLE_ITEMS=new WeakHashMap<>();
    private record Jump(BlockPos soil,BlockPos stem,long until){}

    /** 1 arrived, 0 navigating, -1 inaccessible. */
    public static int approachCell(ServerLevel l,Villager v,BlockPos pos,boolean adjacent){
        return approach(l,v,pos,adjacent?1.38:.68,adjacent?1:0);
    }

    private static int approach(ServerLevel l,Villager v,BlockPos pos,double radius,int accuracy){
        return approach(l,v,pos,radius,accuracy,path->true);
    }

    private static int approach(ServerLevel l,Villager v,BlockPos pos,double radius,int accuracy,java.util.function.Predicate<Path> permitted){
        if(!l.hasChunkAt(pos))return -1;
        var feet=Vec3.atBottomCenterOf(pos);
        var center=Vec3.atCenterOf(pos);
        double dx=v.getX()-feet.x,dz=v.getZ()-feet.z;
        boolean sameColumn=new BlockPos(v.getX(),pos.getY(),v.getZ()).equals(pos);
        if((accuracy!=0||sameColumn)&&dx*dx+dz*dz<=radius*radius&&Math.abs(v.getY()-feet.y)<=.85&&visible(l,v,center,pos)){
            ROUTES.remove(v);
            AIRBORNE_SINCE.remove(v);
            v.getNavigation().stop();
            v.getLookControl().setLookAt(center.x,center.y,center.z);
            return 1;
        }
        if(v.isNoAi())return -1;
        // Ground navigation rejects paths before the first physics tick or mid-jump.
        // A temporary lack of footing does not make a destination inaccessible.
        long now=l.getGameTime();
        if(!v.isOnGround()&&!v.isInWaterOrBubble()&&!v.isInLava()){
            return now-AIRBORNE_SINCE.computeIfAbsent(v,key->now)<20?0:-1;
        }
        AIRBORNE_SINCE.remove(v);
        var blocked=BLOCKED.computeIfAbsent(v,k->new HashMap<>());
        blocked.values().removeIf(until->until<=now);
        if(blocked.containsKey(pos))return -1;
        var route=ROUTES.get(v);
        boolean same=route!=null&&route.target.equals(pos);
        if(same&&remaining(v)<=route.remaining-.25){
            route=new Route(route.target,route.endpoint,remaining(v),now,route.retries);
            ROUTES.put(v,route);
        }
        if(same&&route.endpoint.equals(v.getNavigation().getTargetPos())&&!v.getNavigation().isDone()&&now-route.progress<(v.horizontalCollision?12:45)&&permitted.test(v.getNavigation().getPath()))return 0;
        int retries=same?route.retries+1:0;
        BlockPos avoid=same&&accuracy>0?route.endpoint:null;
        v.getNavigation().stop();
        var path=retries<=2?workPath(l,v,pos,accuracy,avoid,permitted):null;
        if(path==null&&avoid!=null&&retries<=2)path=workPath(l,v,pos,accuracy,null,permitted);
        if(path!=null&&path.canReach()&&permitted.test(path)&&v.getNavigation().moveTo(path,Employment.WALK_SPEED)){
            ROUTES.put(v,new Route(pos.immutable(),path.getTarget(),remaining(v),now,retries));
            return 0;
        }
        ROUTES.remove(v);
        blocked.put(pos.immutable(),now+(retries==0?40:100));
        com.mojang.logging.LogUtils.getLogger().debug("Farmer {} at {} released inaccessible route to {} after {} retries",
            v.getUUID(),v.position(),pos,retries);
        return -1;
    }

    private static double remaining(Villager worker){
        var path=worker.getNavigation().getPath();
        if(path==null||path.isDone())return 0;
        int next=path.getNextNodeIndex();
        double distance=worker.position().distanceTo(path.getEntityPosAtNode(worker,next));
        for(int i=next+1;i<path.getNodeCount();i++)
            distance+=path.getEntityPosAtNode(worker,i).distanceTo(path.getEntityPosAtNode(worker,i-1));
        return distance;
    }

    /** Keep the ground navigator and jump controller in charge of climbing the bank. */
    public static boolean leaveWater(ServerLevel level,Villager worker){
        if(!worker.isInWaterOrBubble()||worker.isPassenger()||worker.isSleeping()||worker.isNoAi())return false;
        var station=EmploymentData.get(level).assigned(worker.getUUID());
        if(!fieldWorker(station))return false;
        worker.getJumpControl().jump();
        var route=ROUTES.get(worker);
        if(route!=null&&isBank(level,route.target,worker.blockPosition())&&approachCell(level,worker,route.target,false)==0)return true;
        if(worker.tickCount%4!=0)return true;
        var origin=worker.blockPosition();
        var banks=new ArrayList<BlockPos>();
        for(var at:BlockPos.betweenClosed(origin.offset(-4,0,-4),origin.offset(4,2,4))){
            if(!isBank(level,at,origin))continue;
            banks.add(at.immutable());
        }
        banks.sort(Comparator.comparingDouble(at->worker.distanceToSqr(Vec3.atBottomCenterOf(at))));
        for(var bank:banks.stream().limit(12).toList()){
            if(approachCell(level,worker,bank,false)==0)return true;
        }
        if(!banks.isEmpty()){
            var bank=Vec3.atBottomCenterOf(banks.get(0));
            var across=new Vec3(bank.x,worker.getY()+.5,bank.z);
            if(worker.position().distanceToSqr(bank)<4&&visible(level,worker,across,null)){
                worker.getMoveControl().setWantedPosition(bank.x,worker.getY(),bank.z,.8);
            }
        }
        return true;
    }

    private static boolean isBank(ServerLevel level,BlockPos pos,BlockPos origin){
        return Math.abs(pos.getY()-origin.getY())<=2&&level.hasChunkAt(pos)
            &&level.getFluidState(pos).isEmpty()&&level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()
            &&level.getBlockState(pos.above()).getCollisionShape(level,pos.above()).isEmpty()
            &&level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP);
    }

    private static Path workPath(ServerLevel l,Villager v,BlockPos target,int accuracy,BlockPos avoid,java.util.function.Predicate<Path> permitted){
        if(accuracy==0){
            var exact=v.getNavigation().createPath(target,0);
            return exact!=null&&target.equals(exact.getTarget())?exact:null;
        }
        var options=new ArrayList<BlockPos>();
        if(l.getBlockState(target).getCollisionShape(l,target).isEmpty())options.add(target);
        for(var direction:Direction.Plane.HORIZONTAL){
            var at=target.relative(direction);
            if(l.hasChunkAt(at)&&l.getBlockState(at).getCollisionShape(l,at).isEmpty()
                &&l.getBlockState(at.above()).getCollisionShape(l,at.above()).isEmpty()
                &&!l.getBlockState(at.below()).getCollisionShape(l,at.below()).isEmpty())options.add(at);
        }
        options.sort(Comparator.comparingDouble(at->v.distanceToSqr(Vec3.atBottomCenterOf(at))));
        for(var at:options){
            if(at.equals(avoid))continue;
            var path=v.getNavigation().createPath(at,0);
            if(path!=null&&path.canReach()&&at.equals(path.getTarget())&&permitted.test(path))return path;
        }
        return null;
    }

    static void stayInField(ServerLevel l,EmploymentData.Station s,Villager v){
        stayInField(l,s,v,IndustrialWork.area(l,s));
    }

    static void stayInField(ServerLevel l,EmploymentData.Station s,Villager v,AABB area){
        if(area.contains(v.position().add(0,.125,0))){
            if(ROUTES.remove(v)!=null)v.getNavigation().stop();
            return;
        }
        if(v.isNoAi())return;
        var route=ROUTES.get(v);
        if(route!=null&&!area.contains(Vec3.atCenterOf(route.target))&&v.distanceToSqr(area.getCenter())>400
            &&approachCell(l,v,route.target,false)==0)return;
        int size=IndustrialRules.size(l,s);
        var cells=FarmerWork.zigzag(s.pos,size);
        var candidates=new ArrayList<BlockPos>();
        for(var base:cells)for(int y=0;y<(int)area.getYsize();y++){
            var pos=new BlockPos(base.getX(),(int)area.minY+y,base.getZ());
            if(l.hasChunkAt(pos)&&l.getBlockState(pos).getCollisionShape(l,pos).isEmpty()
                &&l.getBlockState(pos.above()).getCollisionShape(l,pos.above()).isEmpty()
                &&!l.getBlockState(pos.below()).getCollisionShape(l,pos.below()).isEmpty())candidates.add(pos);
        }
        candidates.sort(Comparator.comparingDouble(pos->v.distanceToSqr(Vec3.atBottomCenterOf(pos))));
        for(var pos:candidates.stream().limit(8).toList())if(approachCell(l,v,pos,false)>=0)return;
        var center=area.getCenter();
        if(v.distanceToSqr(center)>400){
            var waypoint=DefaultRandomPos.getPosTowards(v,16,5,center,Math.PI/4);
            if(waypoint!=null&&l.hasChunkAt(new BlockPos(waypoint))
                &&waypoint.distanceToSqr(center)<v.distanceToSqr(center))approachCell(l,v,new BlockPos(waypoint),false);
        }
    }

    static boolean jumpOn(Villager v,BlockPos soil,BlockPos stem){
        if(!v.isOnGround()||BLOCKED.getOrDefault(v,Map.of()).getOrDefault(soil.above(),0L)>v.level.getGameTime())return false;
        v.getNavigation().stop();
        v.setDeltaMovement(0,v.getDeltaMovement().y,0);
        JUMPS.put(v,new Jump(soil.immutable(),stem.immutable(),v.level.getGameTime()+30));
        v.getJumpControl().jump();
        return true;
    }

    static boolean isJumping(Villager v){
        var jump=JUMPS.get(v);
        if(jump==null)return false;
        if(v.level.getGameTime()<=jump.until&&v.level.getBlockState(jump.soil).is(Blocks.FARMLAND))return true;
        JUMPS.remove(v);
        return false;
    }

    public static boolean landOnFruitSoil(ServerLevel level,Villager v,BlockPos soil,float distance){
        var jump=JUMPS.get(v);
        if(jump==null||level.getGameTime()>jump.until||!jump.soil.equals(soil))return false;
        var station=EmploymentData.get(level).assigned(v.getUUID());
        if(station==null||station.profession!=Profession.FARMER||!IndustrialWork.day(level)||!IndustrialWork.ownsNavigation(v)
            ||!FarmerWork.inside(station,IndustrialRules.size(level,station),soil.above())
            ||!level.getBlockState(soil.above()).isAir())return false;
        if(!level.hasChunkAt(jump.stem))return false;
        var stem=level.getBlockState(jump.stem);
        if(!(stem.getBlock() instanceof StemBlock)||stem.getValue(StemBlock.AGE)!=7)return false;
        boolean allowed=ForgeEventFactory.getMobGriefingEvent(level,v)
            &&!net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.level.BlockEvent.FarmlandTrampleEvent(
                level,soil,Blocks.DIRT.defaultBlockState(),distance,v));
        JUMPS.remove(v);
        BLOCKED.computeIfAbsent(v,k->new HashMap<>()).put(soil.above().immutable(),level.getGameTime()+100);
        com.mojang.logging.LogUtils.getLogger().debug("Farmer {} landed at {} on soil {} with fall distance {}, allowed={}",
            v.getUUID(),v.position(),soil,distance,allowed);
        return allowed&&EmploymentData.get(level).assigned(v.getUUID())==station
            &&level.getBlockState(jump.stem)==stem&&level.getBlockState(soil).is(Blocks.FARMLAND)
            &&level.getBlockState(soil.above()).isAir();
    }

    public static boolean protectedSoil(ServerLevel level,Villager worker,BlockPos soil){
        var jobs=EmploymentData.get(level);
        if(jobs.assigned(worker.getUUID())!=null)return true;
        for(var station:jobs.stations()){
            if(station.active&&station.worker!=null&&station.profession==Profession.FARMER
                &&station.dimension.equals(level.dimension())&&Math.abs(station.pos.getX()-soil.getX())<=6
                &&Math.abs(station.pos.getZ()-soil.getZ())<=6
                &&FarmerWork.inside(station,IndustrialRules.size(level,station),soil.above()))return true;
        }
        return false;
    }

    public static boolean allowsTrample(Villager v,BlockPos soil){
        var jump=JUMPS.get(v);
        return jump!=null&&v.level.getGameTime()<=jump.until&&jump.soil.equals(soil)
            &&v.level.getBlockState(soil.above()).isAir();
    }

    public static void release(Villager worker){
        if(ROUTES.containsKey(worker))stopRoute(worker);
        PICKUPS.remove(worker);
        NEXT_DISCARD.remove(worker);
        BLOCKED.remove(worker);
        JUMPS.remove(worker);
        UNREACHABLE_ITEMS.remove(worker);
        AIRBORNE_SINCE.remove(worker);
    }

    static void rescan(Villager v){PICKUPS.remove(v);}

    static void areaChanged(Villager worker){
        if(ending(worker))return;
        stopRoute(worker);
        BLOCKED.remove(worker);
        UNREACHABLE_ITEMS.remove(worker);
        rescan(worker);
    }
    static boolean collect(ServerLevel l,EmploymentData.Station s,Villager v){
        return collect(l,s,v,false);
    }
    private static boolean collect(ServerLevel l,EmploymentData.Station s,Villager v,boolean closing){
        var state=PICKUPS.get(v);
        long now=l.getGameTime();
        var area=IndustrialWork.area(l,s);
        boolean field=fieldWorker(s);
        var search=field?pickupArea(area):area;
        var reservations=s.profession==Profession.COLLECTOR?ProfessionalLoot.reservations(l,search):List.<ProfessionalLoot.Reservation>of();
        var farms=EmploymentData.get(l).stations().stream().filter(other->other!=s&&ProfessionalLoot.active(l,other)
            &&fieldWorker(other)&&other.dimension.equals(l.dimension()))
            .map(other->new NeighborFarm(other,IndustrialWork.area(l,other)))
            .filter(other->pickupArea(other.area).intersects(search)).toList();
        var rejected=UNREACHABLE_ITEMS.computeIfAbsent(v,k->new HashMap<>());
        rejected.values().removeIf(until->until<=now);
        ItemEntity item=state!=null&&state.item!=null&&l.getEntity(state.item) instanceof ItemEntity e?e:null;
        if(!eligible(l,s,area,search,farms,reservations,item,false)){
            if(item!=null)stopRoute(v);
            if(state!=null&&state.nextScan>now)return closing;
            var candidates=l.getEntitiesOfClass(ItemEntity.class,search,
                e->!rejected.containsKey(e.getUUID())&&eligible(l,s,area,search,farms,reservations,e,false));
            var order=Comparator.<ItemEntity>comparingInt(e->area.expandTowards(0,-1,0).contains(e.position())?0:1);
            if(s.profession==Profession.LUMBERJACK){
                // Collect this tree's timber near the current footing before descending for its outer drops.
                order=order.thenComparingInt(e->s.id.equals(harvestStation(e))
                    &&e.getItem().is(net.minecraft.tags.ItemTags.LOGS)?0:1)
                    .thenComparingInt(e->e.getY()>=v.getY()-1.01?0:1);
            }
            item=candidates.stream().min(order.thenComparingDouble(v::distanceToSqr)).orElse(null);
            if(item!=null||!closing)PICKUPS.put(v,new Pickup(item==null?null:item.getUUID(),now+(closing?10:20)));
            else PICKUPS.remove(v);
        }
        if(item==null){
            if(!closing)return false;
            // Tile drops may still have their vanilla pickup delay at a time jump.
            boolean pending=!l.getEntitiesOfClass(ItemEntity.class,search,e->e.hasPickUpDelay()
                &&eligible(l,s,area,search,farms,reservations,e,true)&&!rejected.containsKey(e.getUUID())).isEmpty();
            if(pending)PICKUPS.put(v,new Pickup(null,now+5));
            return pending;
        }
        boolean raised=s.profession==Profession.LUMBERJACK&&s.id.equals(harvestStation(item))
            &&item.getItem().is(net.minecraft.tags.ItemTags.LOGS);
        if(!pickupReach(l,v,item,raised)||!field&&!area.contains(v.position())){
            int travel=approachPickup(l,v,item,area,farms,field,raised);
            if(travel==0)return true;
            if(!pickupReach(l,v,item,raised)||!field&&!area.contains(v.position())){
                rejected.put(item.getUUID(),now+30);
                rescan(v);
                return closing;
            }
        }
        if(IndustrialWork.allowed(l,s,v,item.blockPosition(),item,switch(s.profession){case BEEKEEPER->"apiary_collect";case FARMER->"farmer_collect";case LUMBERJACK->"lumberjack_collect";default->"worker_collect";})
            &&eligible(l,s,area,search,farms,s.profession==Profession.COLLECTOR?ProfessionalLoot.reservations(l,search):reservations,item,false)&&(field||area.contains(v.position()))&&pickupReach(l,v,item,raised)
            &&Employment.valid(l,v.getUUID())&&v.getUUID().equals(s.worker)){
            var take=item.getItem().copy();
            while(!take.isEmpty()&&!IndustrialWork.insert(s,take,false))take.shrink(1);
            if(!take.isEmpty()&&IndustrialWork.insert(s,take,true)){
                var remaining=item.getItem().copy();
                remaining.shrink(take.getCount());
                v.take(item,take.getCount());
                if(s.profession==Profession.COLLECTOR)IndustrialRules.progress(l,s);
                if(remaining.isEmpty())item.discard();else item.setItem(remaining);
                stopRoute(v);
                rescan(v);
                return true;
            }
        }
        rejected.put(item.getUUID(),now+100);
        rescan(v);
        return closing;
    }
    private static boolean pickupReach(ServerLevel level,Villager worker,ItemEntity item,boolean raised){
        var box=item.getBoundingBox();
        var position=worker.position();
        double x=net.minecraft.util.Mth.clamp(position.x,box.minX,box.maxX);
        double handY=raised?net.minecraft.util.Mth.clamp(box.getCenter().y,position.y+.45,position.y+1.45):position.y+.45;
        double y=net.minecraft.util.Mth.clamp(handY,box.minY,box.maxY);
        double z=net.minecraft.util.Mth.clamp(position.z,box.minZ,box.maxZ);
        return new Vec3(position.x,handY,position.z).distanceToSqr(x,y,z)<=1.21
            &&(visible(level,worker,new Vec3(x,y,z),null)||visible(level,worker,box.getCenter(),null));
    }

    private static int approachPickup(ServerLevel level,Villager worker,ItemEntity item,net.minecraft.world.phys.AABB area,List<NeighborFarm> farms,boolean field,boolean raised){
        var origin=new BlockPos(item.position().add(0,.2,0));
        var current=ROUTES.get(worker);
        var cells=new ArrayList<BlockPos>();
        for(var mutable:BlockPos.betweenClosed(origin.offset(-1,raised?-2:-1,-1),origin.offset(1,1,1))){
            var at=mutable.immutable();
            if(!level.hasChunkAt(at)||!level.getFluidState(at).isEmpty())continue;
            var foot=Vec3.atBottomCenterOf(at);
            if((!field&&!area.contains(foot))||exclusiveNeighbor(area,farms,foot)||item.getY()-foot.y>(raised?2.05:.95)||foot.y-item.getY()>.95||item.position().distanceToSqr(foot.x,item.getY(),foot.z)>1.56
                ||!level.noCollision(worker,worker.getBoundingBox().move(foot.subtract(worker.position())))
                ||level.getBlockState(at.below()).getCollisionShape(level,at.below()).isEmpty())continue;
            cells.add(at);
        }
        cells.sort(Comparator.<BlockPos>comparingInt(at->current!=null&&current.target.equals(at)?0:1)
            .thenComparingDouble(at->worker.distanceToSqr(Vec3.atBottomCenterOf(at))));
        for(var at:cells){
            int travel=approach(level,worker,at,.68,0,path->{
                var start=worker.position();
                boolean startsOutside=!field&&!area.contains(start)||exclusiveNeighbor(area,farms,start);
                boolean enteredOwnRoute=!startsOutside;
                int previousY=worker.blockPosition().getY();
                for(int i=path.getNextNodeIndex();i<path.getNodeCount();i++){
                    var node=path.getNode(i).asBlockPos();
                    // A descent that cannot be walked back can trap the worker below the job.
                    if(!field&&node.getY()<previousY-1)return false;
                    previousY=node.getY();
                    var foot=Vec3.atBottomCenterOf(node);
                    boolean exclusive=(!field&&!area.contains(foot))||exclusiveNeighbor(area,farms,foot);
                    if(!exclusive)enteredOwnRoute=true;
                    else if(enteredOwnRoute)return false;
                }
                return true;
            });
            if(travel==0||travel==1&&moveToPickupEdge(level,worker,item,at))return 0;
            if(travel==1&&raised&&item.getY()>worker.getY()+.95){
                var eye=worker.getEyePosition();
                var jumpEye=eye.add(0,.85,0);
                if(visible(level,worker,jumpEye,null)
                        &&level.clip(new ClipContext(jumpEye,item.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,worker)).getType()==HitResult.Type.MISS){
                    if(worker.isOnGround())worker.getJumpControl().jump();
                    return 0;
                }
            }
        }
        return -1;
    }

    private static boolean moveToPickupEdge(ServerLevel level,Villager worker,ItemEntity item,BlockPos cell){
        if(!worker.isOnGround())return false;
        var foot=Vec3.atBottomCenterOf(cell);
        var horizontal=new Vec3(item.getX()-foot.x,0,item.getZ()-foot.z);
        if(horizontal.lengthSqr()<.2)return false;
        var edge=foot.add(horizontal.normalize().scale(.36));
        var offset=new Vec3(edge.x-worker.getX(),0,edge.z-worker.getZ());
        if(offset.lengthSqr()<.013||!level.noCollision(worker,worker.getBoundingBox().move(offset))
            ||!visible(level,worker,worker.getEyePosition().add(offset),null))return false;
        worker.getMoveControl().setWantedPosition(edge.x,worker.getY(),edge.z,.55);
        return true;
    }

    private record NeighborFarm(EmploymentData.Station station,net.minecraft.world.phys.AABB area){}
    private static net.minecraft.world.phys.AABB pickupArea(net.minecraft.world.phys.AABB area){
        return area.inflate(1,0,1).expandTowards(0,-1,0);
    }

    private static boolean exclusiveNeighbor(net.minecraft.world.phys.AABB area,List<NeighborFarm> farms,Vec3 point){
        return !area.expandTowards(0,-1,0).contains(point)
            &&farms.stream().anyMatch(farm->farm.area.expandTowards(0,-1,0).contains(point));
    }

    private static boolean eligible(ServerLevel l,EmploymentData.Station s,net.minecraft.world.phys.AABB area,
                                    net.minecraft.world.phys.AABB search,List<NeighborFarm> farms,List<ProfessionalLoot.Reservation> reservations,ItemEntity e,boolean allowDelay){
        if(e==null||e.getOwner()!=null||!e.isAlive()||!allowDelay&&e.hasPickUpDelay()||!search.contains(e.position())||!l.hasChunkAt(e.blockPosition()))return false;
        if(!ProfessionalLoot.eligible(l,s,e,reservations))return false;
        var point=e.position();
        if(exclusiveNeighbor(area,farms,point))return false;
        UUID source=harvestStation(e);
        if(source!=null&&!source.equals(s.id)){
            var owner=EmploymentData.get(l).station(source);
            if(s.profession!=Profession.COLLECTOR&&ProfessionalLoot.active(l,owner))return false;
        }
        if(s.profession!=Profession.COLLECTOR&&!s.id.equals(source)){
            boolean inside=area.expandTowards(0,-1,0).contains(point);
            double distance=point.distanceToSqr(Vec3.atCenterOf(s.pos));
            for(var other:farms){
                if(!pickupArea(other.area).contains(point)||!ProfessionalLoot.matches(other.station.profession,e.getItem()))continue;
                boolean otherInside=other.area.expandTowards(0,-1,0).contains(point);
                double otherDistance=point.distanceToSqr(Vec3.atCenterOf(other.station.pos));
                if(otherInside&&!inside||otherInside==inside&&(otherDistance<distance
                    ||otherDistance==distance&&other.station.id.compareTo(s.id)<0))return false;
            }
        }
        var stack=e.getItem();
        for(int i=0;i<27;i++){
            var stored=s.getItem(i);
            if(stored.isEmpty()||ItemStack.isSameItemSameTags(stored,stack)&&stored.getCount()<stored.getMaxStackSize())return true;
        }
        return false;
    }

    public static boolean supports(ServerLevel level,BlockPos pos){
        return DiscardChests.at(level,pos)!=null||DiscardChests.capability(level,pos)!=null;
    }
    private static ItemStack excess(EmploymentData.Station s,int slot,Map<Item,Integer> remaining){
        var stack=s.getItem(slot);
        if(!fieldWorker(s)){
            boolean reserved=switch(s.profession){
                case LUMBERJACK->LumberjackWork.sapling(stack)||LumberjackWork.cutTicks(stack)>0;
                case BUTCHER->stack.getItem() instanceof net.minecraft.world.item.AxeItem||IndustrialWork.food(stack)
                    ||stack.is(net.minecraft.world.item.Items.BUCKET)||stack.is(net.minecraft.world.item.Items.DANDELION);
                case SHEPHERD->stack.is(net.minecraft.world.item.Items.SHEARS)||stack.is(net.minecraft.world.item.Items.WHEAT);
                case BREEDER->stack.is(net.minecraft.world.item.Items.BONE)
                    ||stack.getItem().isEdible()&&stack.getItem().getFoodProperties().isMeat();
                case FISHERMAN->stack.is(net.minecraft.world.item.Items.FISHING_ROD);
                case COLLECTOR->false;
                default->true;
            };
            return reserved?ItemStack.EMPTY:stack;
        }
        if(s.profession==Profession.BEEKEEPER){
            if(ApiaryHiveStock.isHive(stack)||stack.is(net.minecraft.tags.ItemTags.PLANKS))return ItemStack.EMPTY;
            for(int input=0;input<3;input++)if(ApiaryWork.validInput(input,stack))return ItemStack.EMPTY;
            if(!stack.is(net.minecraft.world.item.Items.HONEYCOMB))return stack;
            int left=remaining.getOrDefault(stack.getItem(),ApiaryHiveStock.COMB_RESERVE);
            int keep=Math.min(left,stack.getCount());
            remaining.put(stack.getItem(),left-keep);
            var surplus=stack.copy();
            surplus.shrink(keep);
            return surplus;
        }
        if(stack.isEmpty()||stack.getItem() instanceof net.minecraft.world.item.HoeItem
            ||stack.is(net.minecraft.world.item.Items.BONE_MEAL)||stack.is(net.minecraft.world.item.Items.WATER_BUCKET))return ItemStack.EMPTY;
        int keep=0;
        if(FarmerWork.plant(stack)!=null){
            int left=remaining.getOrDefault(stack.getItem(),10);
            keep=Math.min(left,stack.getCount());
            remaining.put(stack.getItem(),left-keep);
        }
        var result=stack.copy();
        result.shrink(keep);
        return result;
    }
    static boolean deliverCollected(ServerLevel level,EmploymentData.Station station,Villager worker){
        long now=level.getGameTime();
        if(now<NEXT_DISCARD.getOrDefault(worker,0L))return false;
        var items=offers(station);
        if(!DiscardChests.hasOffers(items))return false;
        var destinations=br.com.kraftcity.logistics.DiscardBinding.destinations(level,station,IndustrialWork.area(level,station));
        for(var destination:destinations){
            if(!DiscardChests.accepts(level,station,worker,destination,items))continue;
            int travel=approachDiscard(level,worker,destination,station,items);
            if(travel==0)return true;
            if(travel>0){
                stopRoute(worker);
                DiscardChests.deposit(level,station,worker,destination,items,"deposit");
                NEXT_DISCARD.put(worker,now+40);
                return true;
            }
        }
        if(ROUTES.containsKey(worker))stopRoute(worker);
        NEXT_DISCARD.put(worker,now+40);
        return false;
    }

    static int[] offers(EmploymentData.Station station){
        Map<Item,Integer> remaining=new HashMap<>();
        int[] amounts=new int[EmploymentData.COMMON];
        for(int slot=0;slot<amounts.length;slot++)amounts[slot]=excess(station,slot,remaining).getCount();
        return amounts;
    }

    public static int approachDiscard(ServerLevel level,Villager worker,BlockPos pos){
        return approachDiscard(level,worker,pos,null,null);
    }

    public static int approachSupply(ServerLevel level, Villager worker, BlockPos pos,
                                     java.util.function.Predicate<net.minecraftforge.items.IItemHandler> hasSupply) {
        return approachInventory(level, worker, pos, hasSupply);
    }

    private static int approachDiscard(ServerLevel level,Villager worker,BlockPos pos,EmploymentData.Station station,int[] offers){
        return approachInventory(level,worker,pos,handler->station==null||DiscardChests.accepts(handler,station,offers));
    }

    private static int approachInventory(ServerLevel level,Villager worker,BlockPos pos,
                                         java.util.function.Predicate<net.minecraftforge.items.IItemHandler> accepts){
        var be=DiscardChests.capability(level,pos);
        if(be==null){
            int result=approach(level,worker,pos,1.65,1,path->safeDiscardPath(level,worker,path));
            return result==1&&!visible(level,worker,Vec3.atCenterOf(pos),pos)?-1:result;
        }
        var current=DiscardChests.accessibleHandler(be,worker);
        if(current!=null&&accepts.test(current)
            &&worker.distanceToSqr(Vec3.atCenterOf(pos))<=2.72&&visible(level,worker,Vec3.atCenterOf(pos),pos))return 1;
        var access=new ArrayList<BlockPos>();
        for(var side:Direction.Plane.HORIZONTAL){
            var at=pos.relative(side);
            var handler=DiscardChests.handler(be,worker,side);
            if(!level.hasChunkAt(at)||handler==null||!accepts.test(handler))continue;
            for(int y=0;y>=-1;y--){
                var feet=at.above(y);
                if(level.getBlockState(feet).getCollisionShape(level,feet).isEmpty()
                    &&!level.getBlockState(feet.below()).getCollisionShape(level,feet.below()).isEmpty())access.add(feet);
            }
        }
        for(var face:List.of(Direction.UP,Direction.DOWN)){
            var handler=DiscardChests.handler(be,worker,face);
            if(handler==null||!accepts.test(handler))continue;
            var feet=face==Direction.UP?pos.above():pos.below(2);
            if(level.hasChunkAt(feet)&&level.getBlockState(feet).getCollisionShape(level,feet).isEmpty()
                &&level.getBlockState(feet.above()).getCollisionShape(level,feet.above()).isEmpty()
                &&!level.getBlockState(feet.below()).getCollisionShape(level,feet.below()).isEmpty())access.add(feet);
        }
        access.sort(Comparator.comparingDouble(at->worker.distanceToSqr(Vec3.atBottomCenterOf(at))));
        for(var feet:access){
            int travel=approach(level,worker,feet,.68,0,path->safeDiscardPath(level,worker,path));
            if(travel==0)return 0;
            var handler=DiscardChests.accessibleHandler(be,worker);
            if(travel==1&&handler!=null&&accepts.test(handler)
                &&visible(level,worker,Vec3.atCenterOf(pos),pos))return 1;
        }
        return -1;
    }

    private static boolean safeDiscardPath(ServerLevel level,Villager worker,Path path){
        // Farmland's walking surface is one sixteenth below the normal path node height.
        BlockPos previous=new BlockPos(worker.position().add(0,.125,0));
        for(int i=path.getNextNodeIndex();i<path.getNodeCount();i++){
            var next=path.getNode(i).asBlockPos();
            if(!level.hasChunkAt(next))return false;
            if(next.getY()!=previous.getY()&&(farmlandAt(level,previous)||farmlandAt(level,next)))return false;
            previous=next;
        }
        return true;
    }

    private static boolean farmlandAt(ServerLevel level,BlockPos feet){
        return level.getBlockState(feet.below()).is(net.minecraft.world.level.block.Blocks.FARMLAND);
    }

    static void depositDirect(ServerLevel level,EmploymentData.Station station,Villager worker){
        var items=offers(station);
        for(var pos:br.com.kraftcity.logistics.DiscardBinding.destinations(level,station,IndustrialWork.area(level,station))){
            if(!DiscardChests.hasOffers(items))break;
            DiscardChests.deposit(level,station,worker,pos,items,"deposit");
        }
    }

    public static boolean fieldWorker(EmploymentData.Station station){
        return station!=null&&(station.profession==Profession.FARMER||station.profession==Profession.BEEKEEPER);
    }

    public static boolean ending(Villager v){
        if(!(v.level instanceof ServerLevel l))return false;
        var s=EmploymentData.get(l).assigned(v.getUUID());
        return s!=null&&IndustrialWork.industrial(s)&&s.industry.contains("farmer_discard_until")
            &&s.industry.getLong("farmer_discard_until")>l.getGameTime();
    }

    private static void stopRoute(Villager v){
        ROUTES.remove(v);
        AIRBORNE_SINCE.remove(v);
        v.getNavigation().stop();
        v.getMoveControl().setWantedPosition(v.getX(),v.getY(),v.getZ(),0);
        v.setSpeed(0);
        v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        v.getBrain().eraseMemory(MemoryModuleType.PATH);
    }

    public static void cancelClosingForDanger(ServerLevel level,Villager worker){
        var station=EmploymentData.get(level).assigned(worker.getUUID());
        if(!fieldWorker(station))return;
        if(station.industry.contains("farmer_discard_until"))finishClosing(station,worker);
        station.industry.putLong("farmer_discard_day",Math.floorDiv(level.getDayTime(),24000)+1);
        station.setChanged();
    }

    public static boolean nightFarmer(ServerLevel level,Villager worker){
        if(IndustrialWork.day(level))return false;
        var station=EmploymentData.get(level).assigned(worker.getUUID());
        return station!=null&&IndustrialWork.industrial(station);
    }

    /** Called before the resident sleep routine, after emergency navigation. */
    public static boolean beforeHome(ServerLevel l,Villager v){
        if(v.isNoAi())return false;
        var s=EmploymentData.get(l).assigned(v.getUUID());
        if(s==null||!IndustrialWork.industrial(s)||!l.hasChunkAt(s.pos)||!Employment.valid(l,v.getUUID()))return false;
        if(IndustrialWork.day(l)){
            if(s.industry.contains("farmer_discard_day")){
                if(s.industry.contains("farmer_discard_until"))finishClosing(s,v);
                s.industry.remove("farmer_discard_day");
                s.setChanged();
            }
            return false;
        }
        if(Employment.fleeing(v)||v.isSleeping()||l.isThundering()
            ||v.getPersistentData().getBoolean("residenciasaldeoes_home_return_forced")
            ||v.getLastHurtByMob()!=null&&v.getLastHurtByMob().isAlive())return false;
        long cycle=Math.floorDiv(l.getDayTime(),24000)+1;
        long now=l.getGameTime();
        if(s.industry.getLong("farmer_discard_day")!=cycle){
            s.industry.putLong("farmer_discard_day",cycle);
            s.industry.putLong("farmer_discard_until",now+220);
            s.industry.putLong("farmer_pickup_until",now+120);
            s.industry.remove("farmer_discard_pos");
            s.industry.remove("farmer_discard_tried");
            stopRoute(v);
            rescan(v);
            com.mojang.logging.LogUtils.getLogger().debug("Farmer {} at {} starts closing at time {}",
                v.getUUID(),v.position(),l.getDayTime());
            s.setChanged();
        }
        if(!s.industry.contains("farmer_discard_until"))return false;
        if(now>=s.industry.getLong("farmer_discard_until")){
            finishClosing(s,v);
            return false;
        }
        if(leaveWater(l,v))return true;
        if(s.industry.contains("farmer_pickup_until")){
            if(now<s.industry.getLong("farmer_pickup_until")&&ForgeEventFactory.getMobGriefingEvent(l,v)){
                if(collect(l,s,v,true))return true;
            }
            s.industry.remove("farmer_pickup_until");
            stopRoute(v);
            s.setChanged();
        }
        var tried=new HashSet<Long>();
        for(long at:s.industry.getLongArray("farmer_discard_tried"))tried.add(at);
        if(seekDiscard(l,s,v,tried))return true;
        finishClosing(s,v);
        return false;
    }

    /** A successful transfer releases the walking command before another task can take control. */
    private static boolean seekDiscard(ServerLevel level,EmploymentData.Station station,Villager worker,Set<Long> tried){
        long now=level.getGameTime();
        if(now<NEXT_DISCARD.getOrDefault(worker,0L))return false;
        var items=offers(station);
        if(!DiscardChests.hasOffers(items))return false;
        var destinations=new ArrayList<>(br.com.kraftcity.logistics.DiscardBinding.destinations(level,station,IndustrialWork.area(level,station)));
        if(station.industry.contains("farmer_discard_pos")){
            var previous=BlockPos.of(station.industry.getLong("farmer_discard_pos"));
            if(destinations.remove(previous))destinations.add(0,previous);
        }
        for(var pos:destinations){
            if(!DiscardChests.hasOffers(items))break;
            if(tried.contains(pos.asLong())||!DiscardChests.accepts(level,station,worker,pos,items))continue;
            station.industry.putLong("farmer_discard_pos",pos.asLong());
            int result=approachDiscard(level,worker,pos,station,items);
            if(result==0){
                station.setChanged();
                return true;
            }
            int offered=Arrays.stream(items).sum();
            if(result>0){
                stopRoute(worker);
                DiscardChests.deposit(level,station,worker,pos,items,"deposit");
            }
            int left=Arrays.stream(items).sum();
            com.mojang.logging.LogUtils.getLogger().debug("Farmer {} at {} finished discard at {}, arrival={}, inserted={}",
                worker.getUUID(),worker.position(),pos,result,offered-left);
            tried.add(pos.asLong());
            station.industry.putLongArray("farmer_discard_tried",tried.stream().mapToLong(Long::longValue).toArray());
        }
        return false;
    }

    private static void finishClosing(EmploymentData.Station s,Villager v){
        stopRoute(v);
        BLOCKED.remove(v);
        PICKUPS.remove(v);
        s.industry.remove("farmer_pickup_until");
        s.industry.remove("farmer_discard_until");
        s.industry.remove("farmer_discard_pos");
        s.industry.remove("farmer_discard_tried");
        s.setChanged();
    }
}
