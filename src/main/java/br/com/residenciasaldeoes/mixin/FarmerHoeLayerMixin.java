package br.com.residenciasaldeoes.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.HoeItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CrossedArmsItemLayer.class)
public abstract class FarmerHoeLayerMixin {
    @Unique private static boolean kraftcity$visualHoeSeen;
    @Inject(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemTransforms$TransformType;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void kraftcity$farmerHoe(PoseStack pose,MultiBufferSource buffers,int light,LivingEntity entity,
                                    float limb,float speed,float partial,float age,float yaw,float pitch,CallbackInfo ci){
        var stack=entity.getMainHandItem();
        if(entity instanceof Villager&&stack.getItem() instanceof HoeItem&&stack.hasTag()&&stack.getTag().getBoolean("KCFarmerDisplay")){
            pose.translate(.05,0,-.12);
            pose.mulPose(Vector3f.ZP.rotationDegrees(-12));
            pose.mulPose(Vector3f.XP.rotationDegrees(30*(float)Math.sin(Math.PI*entity.getAttackAnim(partial))));
            pose.scale(2,2,2);
            if(Boolean.getBoolean("residencias.visualCheck")&&!kraftcity$visualHoeSeen){
                kraftcity$visualHoeSeen=true;
                System.out.println("FARMER_HELD_HOE_RENDERED_AT_DOUBLE_SCALE");
            }
        }
    }
}
