package br.com.residenciasaldeoes.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(HumanoidArmorLayer.class)
public interface SoldierArmorLayerInvoker {
    @Invoker("renderArmorPiece")
    void kraftcity$renderPiece(PoseStack pose,MultiBufferSource buffers,LivingEntity wearer,
                              EquipmentSlot slot,int light,HumanoidModel<?> model);
}
