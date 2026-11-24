package br.com.residenciasaldeoes.mixin;

import br.com.residenciasaldeoes.client.ResidentOpacity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Supplies alpha to optimized model parts that write vertices without Cube.compile. */
@Mixin(ModelPart.class)
public abstract class ResidentPartOpacityMixin {
    @Shadow
    private void compile(PoseStack.Pose pose, VertexConsumer output, int light, int overlay,
                         float red, float green, float blue, float alpha) {
        throw new AssertionError();
    }

    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;compile(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"))
    private void residenciasaldeoes$applyPartAlpha(ModelPart part, PoseStack.Pose pose,
            VertexConsumer output, int light, int overlay, float red, float green, float blue, float alpha) {
        int opacity = ResidentOpacity.renderingPercent();
        if (opacity == 100 || ResidentOpacity.isPreviewRendering() || ResidentOpacity.hasPartAlpha()) {
            compile(pose, output, light, overlay, red, green, blue, alpha);
            return;
        }
        ResidentOpacity.beginPartAlpha();
        try {
            compile(pose, output, light, overlay, red, green, blue, alpha * (opacity / 100.0F));
        } finally {
            ResidentOpacity.endPartAlpha();
        }
    }
}
