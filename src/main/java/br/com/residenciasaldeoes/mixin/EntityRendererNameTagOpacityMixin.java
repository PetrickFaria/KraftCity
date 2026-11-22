package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.employment.client.WorkerPose;
import br.com.residenciasaldeoes.client.ResidentOpacity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.npc.Villager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents the model's vertex-alpha override from fading the entity name tag. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererNameTagOpacityMixin {
    @Inject(method = "renderNameTag", at = @At("HEAD"))
    private void residenciasaldeoes$beginOpaqueNameTag(Entity entity, Component name, PoseStack poses,
                                                        MultiBufferSource buffers, int packedLight,
                                                        CallbackInfo ci) {
        ResidentOpacity.beginNameTag();
    }

    @Inject(method = "renderNameTag", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void residenciasaldeoes$professionNameTag(Entity entity, Component name, PoseStack poses,
                                                     MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!(entity instanceof Villager villager)) return;
        var profession = WorkerPose.profession(villager);
        if (profession == null) return;
        var font = ((EntityRenderer<?>)(Object)this).getFont();
        var label = Component.literal("(" + profession.label + ")");
        var matrix = poses.last().pose();
        float x = -font.width(label) / 2F;
        int y = name.getString().equals("deadmau5") ? 0 : 10;
        int background = (int)(Minecraft.getInstance().options.getBackgroundOpacity(.25F) * 255) << 24;
        boolean through = !entity.isDiscrete();
        font.drawInBatch(label, x, y, 0x20ffffff, false, matrix, buffers, through, background, light);
        if (through) font.drawInBatch(label, x, y, -1, false, matrix, buffers, false, 0, light);
    }

    @Inject(method = "renderNameTag", at = @At("RETURN"))
    private void residenciasaldeoes$endOpaqueNameTag(Entity entity, Component name, PoseStack poses,
                                                      MultiBufferSource buffers, int packedLight,
                                                      CallbackInfo ci) {
        ResidentOpacity.endNameTag();
    }
}
