package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.builder.client.BuilderClient;
import br.com.kraftcity.employment.client.WorkerPose;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class BuilderSteveArmsMixin {
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
    private void kraftcity$pose(LivingEntity entity,float swing,float amount,float age,float yaw,float pitch,CallbackInfo ci){
        if(!(entity instanceof Villager worker))return;
        var model=(PlayerModel<?>)(Object)this;
        if(BuilderClient.working(worker)){
            float cycle=Mth.sin(age*.55F)*.8F;
            model.rightArm.xRot=-.9F+cycle;
            model.leftArm.xRot=-.9F-cycle;
        }else if(WorkerPose.active(worker)){
            float action=WorkerPose.action(worker,age);
            float cycle=WorkerPose.walking(swing,amount)*(1-action);
            model.body.xRot=.27F*action;
            model.jacket.copyFrom(model.body);
            model.rightArm.xRot=cycle-1.1F*action;
            model.leftArm.xRot=-cycle;
        }else return;
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
    }
}
