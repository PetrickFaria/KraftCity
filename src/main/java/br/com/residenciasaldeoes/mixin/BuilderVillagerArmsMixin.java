package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.builder.client.BuilderClient;
import br.com.kraftcity.employment.client.WorkerPose;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerModel.class)
public abstract class BuilderVillagerArmsMixin {
    @Unique private boolean kraftcity$changedArms,kraftcity$previous;
    @Unique private float kraftcity$bodyBefore;

    @Inject(method="setupAnim",at=@At("HEAD"))
    private void kraftcity$restorePose(Entity entity,float swing,float amount,float age,float yaw,float pitch,CallbackInfo ci){
        if(!kraftcity$changedArms)return;
        var root=((VillagerModel<?>)(Object)this).root();
        root.getChild("body").xRot=kraftcity$bodyBefore;
        root.getChild("arms").visible=kraftcity$previous;
        kraftcity$changedArms=false;
    }

    @Inject(method="setupAnim",at=@At("TAIL"))
    private void kraftcity$pose(Entity entity,float swing,float amount,float age,float yaw,float pitch,CallbackInfo ci){
        if(!(entity instanceof Villager worker)||!WorkerPose.active(worker)&&!BuilderClient.working(worker))return;
        var root=((VillagerModel<?>)(Object)this).root();
        var arms=root.getChild("arms");
        var body=root.getChild("body");
        kraftcity$previous=arms.visible;
        kraftcity$bodyBefore=body.xRot;
        kraftcity$changedArms=true;
        arms.visible=false;
        body.xRot+=.27F*WorkerPose.action(worker,age);
    }
}
