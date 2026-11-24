package br.com.residenciasaldeoes.mixin;

import br.com.residenciasaldeoes.client.ResidentOpacity;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Scales each resident cube once, including cubes emitted by EMF model parts. */
@Mixin(ModelPart.Cube.class)
public abstract class ModelPartOpacityMixin {
    @ModifyVariable(
            method = "compile(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private float residenciasaldeoes$applyResidentOpacity(float alpha) {
        // The preview has its own buffer wrapper; only change in-world model geometry here.
        if (ResidentOpacity.isPreviewRendering() || ResidentOpacity.renderingNameTag()
                || ResidentOpacity.hasPartAlpha()) return alpha;
        return alpha * (ResidentOpacity.renderingPercent() / 100.0F);
    }
}
