package br.com.kraftcity.industry;

import br.com.kraftcity.employment.Employment;
import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ApiaryBeeFlight {
    private record Lure(Villager worker,long expires){}
    private static final Map<Bee,Lure> LURES=new WeakHashMap<>();
    private static final Map<Bee,Boolean> INSTALLED=new WeakHashMap<>();

    static void install(Bee bee){
        if(INSTALLED.put(bee,Boolean.TRUE)!=null)return;
        bee.getGoalSelector().addGoal(0,new Goal(){
            {setFlags(EnumSet.of(Flag.MOVE));}
            @Override public boolean canUse(){
                var lure=LURES.get(bee);
                return lure!=null&&bee.level.getGameTime()<lure.expires&&lure.worker.isAlive()
                    &&lure.worker.level==bee.level&&bee.distanceToSqr(lure.worker)<=144
                    &&IndustrialWork.ownsNavigation(lure.worker)&&!Employment.fleeing(lure.worker)
                    &&ApiaryWork.validInput(2,lure.worker.getMainHandItem())
                    &&!bee.isAngry()&&!bee.isInLove()&&!bee.level.isRaining()&&!bee.level.isNight();
            }
            @Override public boolean requiresUpdateEveryTick(){return true;}
            @Override public void start(){follow();}
            @Override public void tick(){if(bee.tickCount%5==0)follow();}
            private void follow(){
                var active=LURES.get(bee);
                if(active==null)return;
                var worker=active.worker;
                var eye=worker.getEyePosition();
                if(bee.position().distanceToSqr(eye)>.49){
                    var navigation=bee.getNavigation();
                    if(!navigation.moveTo(eye.x,eye.y,eye.z,1.05)&&bee.hasLineOfSight(worker))
                        bee.getMoveControl().setWantedPosition(eye.x,eye.y,eye.z,1.0);
                }
            }
            @Override public void stop(){bee.getNavigation().stop();}
        });
    }

    static void lure(Bee bee,Villager worker){
        LURES.put(bee,new Lure(worker,bee.level.getGameTime()+30));
    }

    static boolean luredBy(Bee bee,Villager worker){
        var lure=LURES.get(bee);
        return lure!=null&&lure.worker==worker&&lure.expires>bee.level.getGameTime()
            &&bee.distanceToSqr(worker)<=144;
    }

    static void finishLure(Bee bee){LURES.remove(bee);}

    private static AABB feetBounds(Bee bee,AABB area){
        double width=bee.getBbWidth()/2+.01;
        return new AABB(area.minX+width,area.minY,area.minZ+width,
            area.maxX-width,area.maxY-bee.getBbHeight()-.01,area.maxZ-width);
    }

    public static Vec3 limitMovement(Bee bee,Vec3 movement){
        if(!(bee.level instanceof ServerLevel level)||movement.lengthSqr()==0)return movement;
        var territory=ApiaryWork.territory(level,bee);
        if(territory==null)return movement;
        var limits=feetBounds(bee,territory);
        var here=bee.position();
        // A displaced bee can take a real return path, including a detour around an obstacle.
        if(!limits.inflate(.001).contains(here))return movement;
        var next=here.add(movement);
        return nearest(limits,next).subtract(here);
    }

    static void guide(ServerLevel level,Bee bee,AABB territory){
        if(bee.tickCount%5!=0)return;
        var flower=bee.getSavedFlowerPos();
        if(flower!=null&&!territory.contains(Vec3.atCenterOf(flower)))bee.setSavedFlowerPos(null);
        var nav=bee.getNavigation();
        var target=nav.getTargetPos();
        var bounds=feetBounds(bee,territory);
        if(bounds.inflate(.001).contains(bee.position())
            &&(nav.isDone()||target==null||territory.contains(Vec3.atCenterOf(target))))return;
        var center=territory.getCenter();
        var projected=nearest(bounds,bee.position()).lerp(center,.35);
        for(var point:new Vec3[]{projected,center}){
            var pos=new BlockPos(point);
            if(!level.hasChunkAt(pos)||!level.getBlockState(pos).getCollisionShape(level,pos).isEmpty())continue;
            var path=nav.createPath(pos,0);
            if(path!=null&&path.canReach()&&nav.moveTo(path,1.05))return;
        }
        if(target!=null&&!territory.contains(Vec3.atCenterOf(target)))nav.stop();
    }

    private static Vec3 nearest(AABB area,Vec3 point){
        return new Vec3(Mth.clamp(point.x,area.minX,area.maxX),
            Mth.clamp(point.y,area.minY,area.maxY),Mth.clamp(point.z,area.minZ,area.maxZ));
    }

    private ApiaryBeeFlight(){}
}
