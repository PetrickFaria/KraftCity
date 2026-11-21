package br.com.kraftcity.employment.client;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

public final class WorkerPose {
    private record Activity(boolean farmer,long expires) {}
    private static final Map<Villager,Activity> ACTIVE=new WeakHashMap<>();

    public static void receive(int id,UUID identity,boolean farmer){
        var level=Minecraft.getInstance().level;
        if(level!=null&&level.getEntity(id) instanceof Villager worker&&identity.equals(worker.getUUID()))
            ACTIVE.put(worker,new Activity(farmer,level.getGameTime()+18));
    }

    public static boolean active(Entity entity){
        if(!(entity instanceof Villager worker)||worker.isSleeping()||worker.isPassenger())return false;
        var state=ACTIVE.get(worker);
        return state!=null&&state.expires>=worker.level.getGameTime();
    }

    public static float action(Villager worker,float age){
        var activity=ACTIVE.get(worker);
        return activity==null||!activity.farmer?0:Mth.sin(Mth.PI*Mth.clamp(worker.getAttackAnim(age-worker.tickCount),0,1));
    }

    public static float walking(float stride,float amount){
        return Mth.cos(stride*.6662F)*Mth.clamp(amount,0,1);
    }

    private WorkerPose(){}
}
