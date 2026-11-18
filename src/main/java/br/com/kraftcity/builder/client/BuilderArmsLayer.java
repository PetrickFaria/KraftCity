package br.com.kraftcity.builder.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.npc.Villager;

/** Separate arms only for working vanilla-model builders; resident skin/render passes stay unchanged. */
public final class BuilderArmsLayer extends RenderLayer<Villager,VillagerModel<Villager>> {
    private final ModelPart root;
    public BuilderArmsLayer(RenderLayerParent<Villager,VillagerModel<Villager>> parent){super(parent);var mesh=new MeshDefinition();var r=mesh.getRoot();r.addOrReplaceChild("right",CubeListBuilder.create().texOffs(44,22).addBox(-3,-2,-2,4,12,4),PartPose.offset(-5,2,0));r.addOrReplaceChild("left",CubeListBuilder.create().texOffs(44,22).mirror().addBox(-1,-2,-2,4,12,4),PartPose.offset(5,2,0));root=LayerDefinition.create(mesh,64,64).bakeRoot();}
    @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,Villager v,float swing,float amount,float partial,float age,float yaw,float pitch){if(!BuilderClient.working(v)||v.isInvisible()||br.com.residenciasaldeoes.client.ResidentAppearanceModels.usesSteve(v))return;float cycle=(float)Math.sin(age*.55F)*.8F;root.getChild("right").xRot=-.9F+cycle;root.getChild("left").xRot=-.9F-cycle;root.render(pose,buffers.getBuffer(RenderType.entityTranslucent(getTextureLocation(v))),light,OverlayTexture.NO_OVERLAY,1,1,1,1);}
}
