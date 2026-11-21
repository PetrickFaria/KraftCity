package br.com.kraftcity.employment.client;

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
    private record Activity(boolean farmer,boolean soldier,boolean working,ItemStack stowed,long expires) {}
    private static final Map<Villager,Activity> ACTIVE=new WeakHashMap<>();

    public static void receive(int id,UUID identity,boolean farmer,boolean working,ItemStack stowed){
        receive(id,identity,farmer,false,working,stowed);
    }

    public static void receive(int id,UUID identity,boolean farmer,boolean soldier,boolean working,ItemStack stowed){
        var level=Minecraft.getInstance().level;
        if(level!=null&&level.getEntity(id) instanceof Villager worker&&identity.equals(worker.getUUID()))
            ACTIVE.put(worker,new Activity(farmer,soldier,working,stowed,level.getGameTime()+18));
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
        return activity==null||!activity.farmer&&!activity.soldier?0:Mth.sin(Mth.PI*Mth.clamp(worker.getAttackAnim(age-worker.tickCount),0,1));
    }

    public static boolean soldier(Villager worker){
        var state=current(worker);
        return state!=null&&state.soldier;
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
        if(!(bow||crossbow)||!worker.isUsingItem())return;
        float yaw=Mth.clamp(Mth.wrapDegrees(worker.getYHeadRot()-worker.yBodyRot),-50,50)*Mth.DEG_TO_RAD;
        right.xRot=(crossbow?-1.08F:-Mth.HALF_PI)+worker.getXRot()*Mth.DEG_TO_RAD;
        left.xRot=right.xRot;
        right.yRot=yaw-.15F;
        left.yRot=yaw+.45F;
    }

    public static float walking(float stride,float amount){
        return Mth.cos(stride*.6662F)*Mth.clamp(amount,0,1);
    }

    private WorkerPose(){}
}
