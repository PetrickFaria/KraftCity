package br.com.kraftcity.industry;

import br.com.kraftcity.employment.EmploymentData;
import br.com.kraftcity.employment.Profession;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class IndustrialIdle {
    private record Walk(Path path,long resumeAfter){}
    private static final Map<Villager,Walk> WALKS=new WeakHashMap<>();

    private IndustrialIdle(){}

    public static void interrupt(Villager worker){
        var walk=WALKS.remove(worker);
        if(walk!=null&&walk.path()!=null&&worker.getNavigation().getPath()==walk.path())
            worker.getNavigation().stop();
    }

    public static void tick(ServerLevel level,EmploymentData.Station station,Villager worker){
        if(worker.isNoAi()||worker.isTrading()||worker.isPassenger()||worker.isInWater())return;
        var area=IndustrialWork.area(level,station);
        if(station.profession==Profession.BEEKEEPER)area=area.expandTowards(0,-1,0);
        var navigation=worker.getNavigation();
        var walk=WALKS.get(worker);
        long now=level.getGameTime();
        if(!area.contains(worker.position().add(0,.01,0))){
            interrupt(worker);
            FarmerLogistics.stayInField(level,station,worker,area);
            return;
        }
        if(walk!=null&&walk.path()!=null){
            if(navigation.getPath()==walk.path()&&!navigation.isDone()
                    &&area.contains(Vec3.atBottomCenterOf(walk.path().getTarget())))return;
            interrupt(worker);
            pause(worker,now);
            return;
        }
        if(!navigation.isDone())return;
        if(walk==null){
            pause(worker,now);
            return;
        }
        if(now<walk.resumeAfter())return;
        var foreign=EmploymentData.get(level).stations().stream()
            .filter(other->other!=station&&other.active&&other.worker!=null&&station.dimension.equals(other.dimension))
            .map(other->IndustrialWork.area(level,other)).toList();
        var start=worker.blockPosition();
        for(int i=0;i<14;i++){
            var destination=start.offset(worker.getRandom().nextInt(9)-4,
                worker.getRandom().nextInt(3)-1,worker.getRandom().nextInt(9)-4);
            if(destination.distSqr(start)<4||!valid(level,destination,area,foreign))continue;
            var path=navigation.createPath(destination,0);
            if(path!=null&&path.canReach()&&validPath(level,path,area,foreign)&&navigation.moveTo(path,.55)){
                WALKS.put(worker,new Walk(path,0));
                return;
            }
        }
        pause(worker,now);
    }

    private static void pause(Villager worker,long now){
        WALKS.put(worker,new Walk(null,now+35+worker.getRandom().nextInt(66)));
        if(worker.getRandom().nextBoolean())
            worker.getLookControl().setLookAt(worker.getX()+worker.getRandom().nextInt(7)-3,
                worker.getEyeY(),worker.getZ()+worker.getRandom().nextInt(7)-3);
    }

    private static boolean validPath(ServerLevel level,Path path,AABB area,List<AABB> foreign){
        for(int i=0;i<path.getNodeCount();i++){
            var node=path.getNode(i);
            if(!valid(level,new BlockPos(node.x,node.y,node.z),area,foreign))return false;
        }
        return true;
    }

    private static boolean valid(ServerLevel level,BlockPos pos,AABB area,List<AABB> foreign){
        var center=Vec3.atBottomCenterOf(pos);
        if(!area.contains(center)||!level.hasChunkAt(pos)||foreign.stream().anyMatch(bounds->bounds.contains(center)))return false;
        var feet=level.getBlockState(pos);
        var floor=level.getBlockState(pos.below());
        return !(feet.getBlock() instanceof BushBlock)&&!(floor.getBlock() instanceof FarmBlock)
            &&!floor.is(Blocks.MAGMA_BLOCK)&&!feet.is(Blocks.POWDER_SNOW)
            &&!(floor.getBlock() instanceof net.minecraft.world.level.block.CampfireBlock)
            &&!feet.isBurning(level,pos)&&!floor.isBurning(level,pos.below())
            &&level.getFluidState(pos).isEmpty()&&feet.getCollisionShape(level,pos).isEmpty()
            &&level.getBlockState(pos.above()).getCollisionShape(level,pos.above()).isEmpty()
            &&floor.isFaceSturdy(level,pos.below(),Direction.UP);
    }
}
