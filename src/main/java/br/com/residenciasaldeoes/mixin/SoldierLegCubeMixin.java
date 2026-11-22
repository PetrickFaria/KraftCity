package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.military.client.SoldierLegPoseCapture;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.Cube.class)
public abstract class SoldierLegCubeMixin {
    @Inject(method = "compile", at = @At("HEAD"))
    private void kraftcity$captureVisibleCube(PoseStack.Pose pose, VertexConsumer vertices, int light,
                                            int overlay, float red, float green, float blue, float alpha,
                                            CallbackInfo callback) {
        SoldierLegPoseCapture.record((ModelPart.Cube)(Object)this, pose);
    }
}
