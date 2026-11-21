package br.com.kraftcity.builder.client;

import br.com.kraftcity.employment.client.WorkerPose;
import br.com.residenciasaldeoes.client.VillagerSkinTextures;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.HoeItem;

public final class BuilderArmsLayer extends RenderLayer<Villager,VillagerModel<Villager>> {
    private final ModelPart root;
    private static boolean reportedHoe;

    public BuilderArmsLayer(RenderLayerParent<Villager,VillagerModel<Villager>> parent){
        super(parent);
        var mesh=new MeshDefinition();
        var parts=mesh.getRoot();
        parts.addOrReplaceChild("right",CubeListBuilder.create().texOffs(44,22).addBox(-3,-2,-2,4,12,4),PartPose.offset(-5,2,0));
        parts.addOrReplaceChild("left",CubeListBuilder.create().texOffs(44,22).mirror().addBox(-1,-2,-2,4,12,4),PartPose.offset(5,2,0));
        root=LayerDefinition.create(mesh,64,64).bakeRoot();
    }

    @Override
    public void render(PoseStack pose,MultiBufferSource buffers,int light,Villager worker,float stride,float amount,float partial,float age,float yaw,float pitch){
        boolean building=BuilderClient.working(worker);
        if(!building&&!WorkerPose.active(worker)||worker.isInvisible())return;
        float action=WorkerPose.action(worker,age);
        float cycle=building?Mth.sin(age*.55F)*.8F:WorkerPose.walking(stride,amount)*(1-action);
        var right=root.getChild("right");
        var left=root.getChild("left");
        right.xRot=building?-.9F+cycle:cycle-1.1F*action;
        left.xRot=building?-.9F-cycle:-cycle;
        var skin=VillagerSkinTextures.get(worker.getUUID());
        var texture=skin==null?getTextureLocation(worker):skin;
        root.render(pose,buffers.getBuffer(RenderType.entityTranslucent(texture)),light,OverlayTexture.NO_OVERLAY,1,1,1,1);
        if(WorkerPose.active(worker))renderTool(pose,buffers,light,worker,right);
    }

    public static void renderTool(PoseStack pose,MultiBufferSource buffers,int light,Villager worker,ModelPart arm){
        var item=worker.getMainHandItem();
        if(item.isEmpty())return;
        pose.pushPose();
        arm.translateAndRotate(pose);
        pose.translate(-1.0/16,9.0/16,0);
        pose.mulPose(Vector3f.XP.rotationDegrees(-90));
        pose.mulPose(Vector3f.YP.rotationDegrees(180));
        if(item.getItem() instanceof HoeItem&&item.hasTag()&&item.getTag().getBoolean("KCFarmerDisplay")){
            pose.scale(2,2,2);
            if(Boolean.getBoolean("residencias.visualCheck")&&!reportedHoe){
                reportedHoe=true;
                System.out.println("FARMER_HELD_HOE_RENDERED_AT_DOUBLE_SCALE");
            }
        }
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(worker,item,
            ItemTransforms.TransformType.THIRD_PERSON_RIGHT_HAND,false,pose,buffers,light);
        pose.popPose();
    }
}
