package br.com.kraftcity.builder.client;

import br.com.kraftcity.employment.client.WorkerPose;
import br.com.kraftcity.employment.client.WorkerTextures;
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
    private static boolean reportedHoe,reportedBack;

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
        if(worker.isInvisible())return;
        var stowed=WorkerPose.stowed(worker);
        if(!stowed.isEmpty()&&!worker.isSleeping())renderBack(pose,buffers,light,worker,getParentModel().root().getChild("body"));
        boolean building=BuilderClient.working(worker);
        if(!building&&!WorkerPose.active(worker))return;
        float action=WorkerPose.action(worker,age);
        float cycle=building?Mth.sin(age*.55F)*.8F:WorkerPose.walking(stride,amount)*(1-action);
        var right=root.getChild("right");
        var left=root.getChild("left");
        right.xRot=building?-.9F+cycle:cycle-1.1F*action;
        left.xRot=building?-.9F-cycle:-cycle;
        var skin=VillagerSkinTextures.get(worker.getUUID());
        var texture=skin==null?getTextureLocation(worker):skin;
        var villagerType=net.minecraft.core.Registry.VILLAGER_TYPE.getKey(worker.getVillagerData().getType());
        var robe=new net.minecraft.resources.ResourceLocation(villagerType.getNamespace(),
            "textures/entity/villager/type/"+villagerType.getPath()+".png");
        root.render(pose,buffers.getBuffer(RenderType.entityTranslucent(WorkerTextures.arms(texture,robe))),light,OverlayTexture.NO_OVERLAY,1,1,1,1);
        if(WorkerPose.active(worker))renderTool(pose,buffers,light,worker,right);
    }

    public static void renderBack(PoseStack pose,MultiBufferSource buffers,int light,Villager worker,ModelPart body){
        var item=WorkerPose.stowed(worker);
        if(item.isEmpty()||worker.isSleeping()||worker.isInvisible())return;
        pose.pushPose();
        body.translateAndRotate(pose);
        pose.translate(0,.43,.27);
        pose.mulPose(Vector3f.YP.rotationDegrees(180));
        pose.mulPose(Vector3f.ZP.rotationDegrees(32));
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(worker,item,
            ItemTransforms.TransformType.THIRD_PERSON_RIGHT_HAND,false,pose,buffers,light);
        pose.popPose();
        if(Boolean.getBoolean("residencias.visualCheck")&&!reportedBack){
            reportedBack=true;
            System.out.println("WORKER_STOWED_TOOL_RENDERED");
        }
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
            if(Boolean.getBoolean("residencias.visualCheck")&&!reportedHoe){
                reportedHoe=true;
                System.out.println("FARMER_HELD_HOE_RENDERED_AT_NORMAL_SCALE");
            }
        }
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(worker,item,
            ItemTransforms.TransformType.THIRD_PERSON_RIGHT_HAND,false,pose,buffers,light);
        pose.popPose();
    }
}
