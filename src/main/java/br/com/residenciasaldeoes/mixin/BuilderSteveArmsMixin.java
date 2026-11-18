package br.com.residenciasaldeoes.mixin;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerModel.class)
public abstract class BuilderSteveArmsMixin {
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("TAIL")) private void kraftcity$steveArms(LivingEntity e,float swing,float amount,float age,float yaw,float pitch,CallbackInfo ci){if(!(e instanceof Villager)||!br.com.kraftcity.builder.client.BuilderClient.working(e))return;var m=(PlayerModel<?>)(Object)this;float cycle=(float)Math.sin(age*.55F)*.8F;m.rightArm.xRot=-.9F+cycle;m.leftArm.xRot=-.9F-cycle;m.rightSleeve.copyFrom(m.rightArm);m.leftSleeve.copyFrom(m.leftArm);}
}
