package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.employment.Profession;
import br.com.kraftcity.employment.client.WorkerPose;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class LumberjackSwingMixin {
    @Shadow protected abstract void updateSwingTime();
    @Unique private boolean kraftcity$lumberjackSwing;

    @Inject(method = "tick", at = @At("TAIL"))
    private void kraftcity$animateAxe(CallbackInfo callback) {
        if (!((Object)this instanceof Villager worker) || !worker.level.isClientSide) return;
        if (kraftcity$lumberjackSwing || WorkerPose.profession(worker) == Profession.LUMBERJACK) {
            // Villagers receive the swing packet but do not advance the vanilla timer.
            updateSwingTime();
            kraftcity$lumberjackSwing = worker.swinging;
        }
    }
}
