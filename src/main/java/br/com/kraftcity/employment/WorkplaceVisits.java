package br.com.kraftcity.employment;

import br.com.kraftcity.industry.IndustrialWork;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Prefer destinations outside occupied workplaces for idle visitors. */
public final class WorkplaceVisits {
    private record Exit(BlockPos destination, long deadline){}
    private static final Map<Villager,Exit> EXITS=new WeakHashMap<>();
    private static final Map<Villager,Long> RETRIES=new WeakHashMap<>();
    private static final Map<Villager,Long> AUTHORIZED_UNTIL=new WeakHashMap<>();

    private WorkplaceVisits(){}

    public static boolean leaving(Villager villager){
        var exit=EXITS.get(villager);
        return exit!=null&&villager.level instanceof ServerLevel level&&IndustrialWork.day(level)&&!Employment.fleeing(villager)
            &&level.getGameTime()<exit.deadline&&!villager.getNavigation().isDone()
            &&exit.destination.equals(villager.getNavigation().getTargetPos());
    }

    public static void allowSupplyVisit(Villager villager){
        AUTHORIZED_UNTIL.put(villager,villager.level.getGameTime()+30);
    }

    static void tick(ServerLevel level,Villager villager){
        if(!IndustrialWork.day(level)||level.isThundering()||Employment.fleeing(villager)||villager.isSleeping()
            ||villager.isNoAi()||villager.isTrading()||villager.isPassenger()
            ||villager.getPersistentData().getBoolean("residenciasaldeoes_home_return_forced")
            ||villager.getPersistentData().contains("residenciasaldeoes_emerald_transfer")
            ||villager.getPersistentData().contains("residenciasaldeoes_night_door_task")
            ||villager.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE)){
            EXITS.remove(villager);
            return;
        }
        if(leaving(villager))return;
        EXITS.remove(villager);
        long now=level.getGameTime();
        if(villager.tickCount%10!=0||RETRIES.getOrDefault(villager,0L)>now)return;
        var employment=EmploymentData.get(level);
        var own=employment.assigned(villager.getUUID());
        if(own!=null&&(own.profession==Profession.BUILDER||own.profession==Profession.SOLDIER
                ||!villager.getNavigation().isDone()||AUTHORIZED_UNTIL.getOrDefault(villager,0L)>now))return;
        var position=villager.position().add(0,.125,0);
        var walk=villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        var destination=walk==null?position:Vec3.atBottomCenterOf(walk.getTarget().currentBlockPosition());
        var foreign=new ArrayList<AABB>();
        for(var station:employment.stations()){
            if(station==own||!station.active||station.worker==null||!station.dimension.equals(level.dimension())
                ||!level.hasChunkAt(station.pos)||!(level.getBlockEntity(station.pos) instanceof WorkBlockEntity block)
                ||!station.id.equals(block.identity()))continue;
            var bounds=IndustrialWork.area(level,station);
            if(bounds.inflate(14,5,14).contains(position))foreign.add(bounds);
        }
        var ownArea=own==null?null:IndustrialWork.area(level,own);
        var occupied=foreign.stream().filter(area->unauthorized(area,ownArea,position)
            ||unauthorized(area,ownArea,destination)).findFirst().orElse(null);
        if(occupied==null)return;
        var candidates=perimeter(occupied,villager.blockPosition().getY());
        candidates.sort(Comparator.comparingDouble(pos->villager.distanceToSqr(Vec3.atBottomCenterOf(pos))));
        for(var base:candidates){
            for(int dy:new int[]{0,-1,1}){
                var pos=base.above(dy);
                var target=Vec3.atBottomCenterOf(pos);
                if(foreign.stream().anyMatch(area->unauthorized(area,ownArea,target))||!level.hasChunkAt(pos)
                    ||!level.getFluidState(pos).isEmpty()||level.getBlockState(pos).isBurning(level,pos)
                    ||!level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()
                    ||!level.getBlockState(pos.above()).getCollisionShape(level,pos.above()).isEmpty()
                    ||!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),net.minecraft.core.Direction.UP))continue;
                var path=villager.getNavigation().createPath(pos,0);
                if(path==null||!path.canReach()||!villager.getNavigation().moveTo(path,.8))continue;
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                villager.getBrain().eraseMemory(MemoryModuleType.PATH);
                EXITS.put(villager,new Exit(pos,now+100));
                return;
            }
        }
        RETRIES.put(villager,now+80);
    }

    private static boolean unauthorized(AABB area,AABB own,Vec3 point){
        return area.contains(point)&&(own==null||!own.contains(point));
    }

    private static List<BlockPos> perimeter(AABB area,int y){
        int west=(int)area.minX-1,east=(int)area.maxX,north=(int)area.minZ-1,south=(int)area.maxZ;
        var positions=new ArrayList<BlockPos>();
        for(int x=west;x<=east;x++){
            positions.add(new BlockPos(x,y,north));
            positions.add(new BlockPos(x,y,south));
        }
        for(int z=north+1;z<south;z++){
            positions.add(new BlockPos(west,y,z));
            positions.add(new BlockPos(east,y,z));
        }
        return positions;
    }
}
