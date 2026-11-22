package br.com.kraftcity.military;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;

/** Keeps the direction set by the path navigator while facing the threat in reverse or sideways. */
public final class MilitaryCombatMovement {
    private record Aim(LivingEntity enemy,long until){}
    private static final Map<Villager,Aim> AIMS=new WeakHashMap<>();

    static void aim(Villager soldier,LivingEntity threat){
        AIMS.put(soldier,new Aim(threat,soldier.level.getGameTime()+1));
    }

    static void clear(Villager soldier){AIMS.remove(soldier);}

    public static boolean preserveFacing(Mob mob,boolean rotateInput,float movementYaw){
        if(!(mob instanceof Villager soldier)||!(soldier.level instanceof ServerLevel)||soldier.isSleeping())return false;
        var aim=AIMS.get(soldier);
        if(aim==null||aim.until<soldier.level.getGameTime()||!aim.enemy.isAlive())return false;
        var offset=aim.enemy.getEyePosition().subtract(soldier.getEyePosition());
        float direction=(float)Math.toDegrees(Math.atan2(offset.z,offset.x))-90F;
        float facing=Mth.approachDegrees(soldier.yBodyRot,direction,30F);
        if(rotateInput){
            float delta=(movementYaw-facing)*Mth.DEG_TO_RAD;
            float strafe=soldier.xxa,forward=soldier.zza;
            soldier.xxa=strafe*Mth.cos(delta)-forward*Mth.sin(delta);
            soldier.zza=forward*Mth.cos(delta)+strafe*Mth.sin(delta);
        }
        soldier.setYRot(facing);
        soldier.setYBodyRot(facing);
        soldier.setYHeadRot(Mth.approachDegrees(soldier.getYHeadRot(),direction,30F));
        return true;
    }

    private MilitaryCombatMovement(){}
}
