package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.employment.client.WorkerPose;
import br.com.kraftcity.military.client.SoldierLegPoseCapture;
import br.com.residenciasaldeoes.client.ResidentAppearanceModels;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class SoldierLegCaptureMixin {
    @Shadow protected EntityModel<?> model;

    @Inject(method = "render", at = @At("HEAD"))
    private void kraftcity$beginLegCapture(LivingEntity entity, float yaw, float partialTick, PoseStack poses,
                                          MultiBufferSource buffers, int light, CallbackInfo callback) {
        if (entity instanceof Villager villager && model instanceof VillagerModel<?> villagerModel
            && !ResidentAppearanceModels.usesSteve(villager) && WorkerPose.soldier(villager)
            && !villager.isInvisible() && (!villager.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                || !villager.getItemBySlot(EquipmentSlot.FEET).isEmpty())) {
            SoldierLegPoseCapture.begin(villager, villagerModel.root());
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void kraftcity$endLegCapture(LivingEntity entity, float yaw, float partialTick, PoseStack poses,
                                        MultiBufferSource buffers, int light, CallbackInfo callback) {
        if (entity instanceof Villager villager) SoldierLegPoseCapture.end(villager);
    }
}
