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
import net.minecraft.world.item.Items;

public final class BuilderArmsLayer extends RenderLayer<Villager,VillagerModel<Villager>> {
    private static final ItemTransforms.TransformType HAND=ItemTransforms.TransformType.THIRD_PERSON_RIGHT_HAND;
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
        if(!stowed.isEmpty()&&!worker.isSleeping())renderBack(pose,buffers,light,worker,getParentModel().root().getChild("body"),3.5F);
        boolean building=BuilderClient.working(worker);
        if(!building&&!WorkerPose.active(worker))return;
        var right=root.getChild("right");
        var left=root.getChild("left");
        poseArms(worker,right,left,stride,amount,age,building);
        var skin=VillagerSkinTextures.get(worker.getUUID());
        var texture=skin==null?getTextureLocation(worker):skin;
        var villagerType=net.minecraft.core.Registry.VILLAGER_TYPE.getKey(worker.getVillagerData().getType());
        var robe=new net.minecraft.resources.ResourceLocation(villagerType.getNamespace(),
            "textures/entity/villager/type/"+villagerType.getPath()+".png");
        root.render(pose,buffers.getBuffer(RenderType.entityTranslucent(WorkerTextures.arms(texture,robe))),light,OverlayTexture.NO_OVERLAY,1,1,1,1);
        if(WorkerPose.active(worker)){
            renderTool(pose,buffers,light,worker,right);
            if(WorkerPose.soldier(worker))renderShield(pose,buffers,light,worker,left);
        }
    }

    public static void poseArms(Villager worker,ModelPart right,ModelPart left,float stride,float amount,float age,boolean building){
        float action=WorkerPose.action(worker,age);
        float cycle=building?Mth.sin(age*.55F)*.8F:WorkerPose.walking(stride,amount)*(1-action);
        right.xRot=building?-.9F+cycle:cycle-1.1F*action;
        left.xRot=building?-.9F-cycle:-cycle;
        right.yRot=left.yRot=0;
        WorkerPose.aim(worker,right,left);
    }

    public static void renderBack(PoseStack pose,MultiBufferSource buffers,int light,Villager worker,ModelPart body,float rearPixels){
        var item=WorkerPose.stowed(worker);
        if(item.isEmpty()||worker.isSleeping()||worker.isInvisible())return;
        var renderer=Minecraft.getInstance().getItemRenderer();
        var model=renderer.getModel(item,worker.level,worker,worker.getId()+HAND.ordinal());
        var scale=model.getTransforms().getTransform(HAND).scale;
        pose.pushPose();
        body.translateAndRotate(pose);
        // Generated item models are one texture pixel thick around their centered Z plane.
        // Keep the hand's display scale, but lay the sprite flat against the back.
        pose.translate(0,.43,(rearPixels+.25+.5*Math.abs(scale.z()))/16);
        pose.mulPose(Vector3f.ZP.rotationDegrees(32));
        pose.scale(scale.x(),scale.y(),scale.z());
        // The trident's custom entity mesh starts at a pole pivot instead of an item sprite center.
        if(item.is(Items.TRIDENT))pose.translate(.5,.5+23.0/32,.5);
        renderer.render(item,ItemTransforms.TransformType.NONE,false,pose,buffers,light,OverlayTexture.NO_OVERLAY,model);
        pose.popPose();
        if(Boolean.getBoolean("residencias.visualCheck")&&!reportedBack){
            reportedBack=true;
            System.out.println("WORKER_STOWED_TOOL_RENDERED");
        }
    }

    public static void renderShield(PoseStack pose,MultiBufferSource buffers,int light,Villager worker,ModelPart arm){
        var stack=worker.getOffhandItem();
        if(stack.isEmpty())return;
        var weapon=worker.getMainHandItem().getItem();
        if(worker.isUsingItem()&&worker.getUsedItemHand()==net.minecraft.world.InteractionHand.MAIN_HAND
            &&(weapon instanceof net.minecraft.world.item.BowItem||weapon instanceof net.minecraft.world.item.CrossbowItem))return;
        pose.pushPose();
        arm.translateAndRotate(pose);
        pose.translate(1.0/16,9.0/16,0);
        pose.mulPose(Vector3f.XP.rotationDegrees(-90));
        pose.mulPose(Vector3f.YP.rotationDegrees(180));
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(worker,stack,
            ItemTransforms.TransformType.THIRD_PERSON_LEFT_HAND,true,pose,buffers,light);
        pose.popPose();
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
            HAND,false,pose,buffers,light);
        pose.popPose();
    }
}
