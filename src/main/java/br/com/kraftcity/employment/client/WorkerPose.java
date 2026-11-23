package br.com.kraftcity.employment.client;

import br.com.kraftcity.employment.Profession;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.client.model.geom.ModelPart;

public final class WorkerPose {
    private record Activity(Profession profession,boolean working,ItemStack stowed,long expires) {}
    private static final Map<Villager,Activity> ACTIVE=new WeakHashMap<>();

    public static void receive(int id,UUID identity,boolean farmer,boolean working,ItemStack stowed){
        receive(id,identity,farmer?Profession.FARMER:null,working,stowed);
    }

    public static void receive(int id,UUID identity,Profession profession,boolean working,ItemStack stowed){
        var level=Minecraft.getInstance().level;
        if(level!=null&&level.getEntity(id) instanceof Villager worker&&identity.equals(worker.getUUID()))
            ACTIVE.put(worker,new Activity(profession,working,stowed,level.getGameTime()+18));
    }

    public static boolean active(Entity entity){
        if(!(entity instanceof Villager worker)||worker.isSleeping()||worker.isPassenger())return false;
        var state=current(worker);
        return state!=null&&state.working;
    }

    private static Activity current(Villager worker){
        var state=ACTIVE.get(worker);
        return state!=null&&state.expires>=worker.level.getGameTime()?state:null;
    }

    public static ItemStack stowed(Villager worker){
        var state=current(worker);
        return state!=null&&!state.working?state.stowed:ItemStack.EMPTY;
    }

    public static float action(Villager worker,float age){
        var activity=ACTIVE.get(worker);
        return activity==null||activity.profession!=Profession.FARMER&&activity.profession!=Profession.SOLDIER&&activity.profession!=Profession.BEEKEEPER?0:Mth.sin(Mth.PI*Mth.clamp(worker.getAttackAnim(age-worker.tickCount),0,1));
    }

    public static boolean soldier(Villager worker){
        var state=current(worker);
        return state!=null&&state.profession==Profession.SOLDIER;
    }

    public static Profession profession(Villager worker){
        var state=current(worker);
        return state==null?null:state.profession;
    }

    public static void aim(Villager worker,ModelPart right,ModelPart left){
        if(!soldier(worker))return;
        right.yRot=left.yRot=0;
        if(worker.isUsingItem()&&worker.getUsedItemHand()==InteractionHand.OFF_HAND){
            left.xRot=-1.15F;
            left.yRot=.4F;
            return;
        }
        var item=worker.getMainHandItem();
        boolean bow=item.getItem() instanceof BowItem;
        boolean crossbow=item.getItem() instanceof CrossbowItem;
        float yaw=Mth.clamp(Mth.wrapDegrees(worker.getYHeadRot()-worker.yBodyRot),-50,50)*Mth.DEG_TO_RAD;
        float pitch=worker.getXRot()*Mth.DEG_TO_RAD;
        if(crossbow&&(worker.isUsingItem()||CrossbowItem.isCharged(item))){
            int duration=CrossbowItem.getChargeDuration(item);
            if(worker.isUsingItem()&&worker.getTicksUsingItem()<duration-4){
                net.minecraft.client.model.AnimationUtils.animateCrossbowCharge(right,left,worker,true);
            }else{
                var head=new ModelPart(java.util.List.of(),java.util.Map.of());
                head.xRot=pitch;head.yRot=yaw;
                net.minecraft.client.model.AnimationUtils.animateCrossbowHold(right,left,head,true);
            }
        }else if(bow&&worker.isUsingItem()){
            float draw=Mth.clamp(worker.getTicksUsingItem()/20F,0,1);
            right.xRot=-Mth.HALF_PI+pitch;
            left.xRot=right.xRot;
            right.yRot=yaw-.15F-.25F*(1-draw);
            left.yRot=yaw+.45F;
        }
    }

    public static float walking(float stride,float amount){
        return Mth.cos(stride*.6662F)*Mth.clamp(amount,0,1);
    }

    private WorkerPose(){}
}
