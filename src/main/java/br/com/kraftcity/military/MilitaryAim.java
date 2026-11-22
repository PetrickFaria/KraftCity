package br.com.kraftcity.military;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class MilitaryAim {
    private static final double DRAG=.99;
    private static final double GRAVITY=.05;

    static Vec3 trajectory(ServerLevel level,Villager shooter,LivingEntity target,float speed){
        var base=shooter.getEyePosition().add(0,-.1,0);
        var box=target.getBoundingBox();
        var center=box.getCenter();
        for(double y:new double[]{center.y,box.minY+box.getYsize()*.7,box.minY+box.getYsize()*.35}){
            var aim=new Vec3(center.x,y,center.z);
            var delta=aim.subtract(base);
            if(delta.lengthSqr()<1e-6)continue;
            // Find the first low arc at the weapon's launch speed using the arrow's drag and gravity.
            double previous=.01;
            for(double time=.5;time<=32;time+=.5){
                if(initialVelocity(delta,time).lengthSqr()>speed*speed){previous=time;continue;}
                double low=previous,high=time;
                for(int i=0;i<15;i++){
                    double mid=(low+high)/2;
                    if(initialVelocity(delta,mid).lengthSqr()>speed*speed)low=mid;
                    else high=mid;
                }
                var velocity=initialVelocity(delta,high);
                if(clear(level,shooter,base,velocity,high))return velocity.normalize();
                break;
            }
        }
        return null;
    }

    private static Vec3 initialVelocity(Vec3 delta,double ticks){
        double travel=(1-Math.pow(DRAG,ticks))/(1-DRAG);
        return delta.add(0,GRAVITY*(ticks-travel)/(1-DRAG),0).scale(1/travel);
    }

    private static boolean clear(ServerLevel level,Villager shooter,Vec3 from,Vec3 velocity,double duration){
        for(double ticks=duration;ticks>0;ticks--){
            var step=velocity.scale(Math.min(ticks,1));
            var next=from.add(step);
            if(!level.hasChunkAt(new net.minecraft.core.BlockPos(next))
                ||level.clip(new ClipContext(from,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,shooter)).getType()!=HitResult.Type.MISS)return false;
            from=next;
            velocity=velocity.scale(DRAG).add(0,-GRAVITY,0);
        }
        return true;
    }

    private MilitaryAim(){}
}
