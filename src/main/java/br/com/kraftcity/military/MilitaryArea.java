package br.com.kraftcity.military;

import br.com.kraftcity.employment.EmploymentData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class MilitaryArea {
    static final double WALK=.5;
    static final double CHASE=.75;
    private final AABB bounds;

    MilitaryArea(EmploymentData.Station station){
        bounds=new AABB(station.pos).inflate(100,10,100);
    }

    boolean contains(Vec3 point){return bounds.contains(point);}
    boolean intersects(AABB area){return bounds.intersects(area);}

    Vec3 nearest(Vec3 point){
        return new Vec3(Math.max(bounds.minX+.5,Math.min(bounds.maxX-.5,point.x)),
            Math.max(bounds.minY,Math.min(bounds.maxY-1,point.y)),
            Math.max(bounds.minZ+.5,Math.min(bounds.maxZ-.5,point.z)));
    }

    Vec3 limit(Vec3 origin,Vec3 movement){
        if(!contains(origin)||contains(origin.add(movement)))return movement;
        var next=origin.add(movement);
        return new Vec3(Math.max(bounds.minX,Math.min(bounds.maxX-.001,next.x))-origin.x,
            Math.max(bounds.minY,Math.min(bounds.maxY-.001,next.y))-origin.y,
            Math.max(bounds.minZ,Math.min(bounds.maxZ-.001,next.z))-origin.z);
    }

    boolean approach(Villager soldier,Vec3 destination,double speed){
        var target=nearest(destination);
        if(move(soldier,target,speed))return true;
        var toSoldier=soldier.position().subtract(target);
        if(toSoldier.lengthSqr()<.01)return false;
        var direction=toSoldier.normalize();
        for(int step=2;step<=6;step+=2){
            var next=nearest(target.add(direction.scale(step)));
            if(move(soldier,next,speed)||move(soldier,nearest(next.add(0,1,0)),speed))return true;
        }
        return false;
    }

    boolean permits(Path path){
        if(path==null||!contains(Vec3.atBottomCenterOf(path.getTarget())))return false;
        for(int i=path.getNextNodeIndex();i<path.getNodeCount();i++)
            if(!contains(Vec3.atBottomCenterOf(path.getNode(i).asBlockPos())))return false;
        return true;
    }

    boolean canReach(Villager soldier,Vec3 destination){
        var position=new BlockPos(destination);
        if(!contains(destination)||!soldier.level.hasChunkAt(position))return false;
        ensureGroundContact(soldier);
        var path=soldier.getNavigation().createPath(position,0);
        return path!=null&&path.canReach()&&permits(path);
    }

    boolean move(Villager soldier,Vec3 destination,double speed){
        if(!contains(destination))return false;
        var pos=new BlockPos(destination);
        if(!soldier.level.hasChunkAt(pos))return false;
        var navigation=soldier.getNavigation();
        if(!navigation.isDone()&&pos.equals(navigation.getTargetPos())
            &&(permits(navigation.getPath())||!contains(soldier.position())&&enters(navigation.getPath())))return true;
        ensureGroundContact(soldier);
        Path path=navigation.createPath(pos,0);
        return path!=null&&path.canReach()&&(permits(path)||!contains(soldier.position())&&enters(path))
            &&navigation.moveTo(path,speed);
    }
    private static void ensureGroundContact(Villager soldier){
        if(!soldier.isOnGround()&&Math.abs(soldier.getDeltaMovement().y)<.08
            &&!soldier.level.noCollision(soldier,soldier.getBoundingBox().move(0,-.02,0)))soldier.setOnGround(true);
    }

    private boolean enters(Path path){
        if(!contains(Vec3.atBottomCenterOf(path.getTarget())))return false;
        boolean entered=false;
        double previousDistance=Double.POSITIVE_INFINITY;
        for(int i=path.getNextNodeIndex();i<path.getNodeCount();i++){
            var point=Vec3.atBottomCenterOf(path.getNode(i).asBlockPos());
            if(contains(point))entered=true;
            else{
                double distance=point.distanceToSqr(nearest(point));
                if(entered||distance>previousDistance+1e-5)return false;
                previousDistance=distance;
            }
        }
        return entered;
    }
}
