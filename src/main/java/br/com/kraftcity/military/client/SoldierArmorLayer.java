package br.com.kraftcity.military.client;

import br.com.kraftcity.builder.client.BuilderArmsLayer;
import br.com.kraftcity.employment.client.WorkerPose;
import br.com.residenciasaldeoes.client.ResidentAppearanceModels;
import br.com.residenciasaldeoes.mixin.SoldierArmorLayerInvoker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;

public final class SoldierArmorLayer extends RenderLayer<Villager,VillagerModel<Villager>> {
    private final HumanoidModel<Villager> poseModel;
    private final HumanoidArmorLayer<Villager,HumanoidModel<Villager>,HumanoidModel<Villager>> armor;
    private final HumanoidModel<Villager> innerArmor,outerArmor;
    private boolean steve;
    private enum Piece { STANDARD, WAIST, LEFT, RIGHT }
    private Piece piece=Piece.STANDARD;

    public SoldierArmorLayer(RenderLayerParent<Villager,VillagerModel<Villager>> parent,EntityModelSet models){
        super(parent);
        poseModel=new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        var adapter=new RenderLayerParent<Villager,HumanoidModel<Villager>>(){
            @Override public HumanoidModel<Villager> getModel(){return poseModel;}
            @Override public ResourceLocation getTextureLocation(Villager villager){
                return parent.getTextureLocation(villager);
            }
        };
        innerArmor=new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        outerArmor=new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
        armor=new HumanoidArmorLayer<>(adapter,innerArmor,outerArmor){
            @Override protected void setPartVisibility(HumanoidModel<Villager> model,EquipmentSlot slot){
                super.setPartVisibility(model,slot);
                if(slot==EquipmentSlot.HEAD&&!steve){
                    offsetAlongPartY(model.head,-2.8F);
                    model.head.yScale=.86F;
                    model.head.zScale=.94F;
                    model.hat.copyFrom(model.head);
                }
                if(!steve&&(slot==EquipmentSlot.LEGS||slot==EquipmentSlot.FEET)){
                    float padding=slot==EquipmentSlot.LEGS?.5F:1F;
                    fitLegHeight(model.leftLeg,padding);
                    fitLegHeight(model.rightLeg,padding);
                }
                if(slot==EquipmentSlot.LEGS&&!steve){
                    model.body.zScale=1.55F;
                    model.leftLeg.zScale=1.55F;
                    model.rightLeg.zScale=1.55F;
                }
                if(piece!=Piece.STANDARD){
                    model.body.visible=piece==Piece.WAIST;
                    model.leftLeg.visible=piece==Piece.LEFT;
                    model.rightLeg.visible=piece==Piece.RIGHT;
                }
            }
        };
    }

    private static void fitLegHeight(ModelPart leg,float padding){
        // The villager's leg runs from local Y=0 to 12. Armor deformation extends both ends.
        leg.yScale*=12F/(12F+2F*padding);
        offsetAlongPartY(leg,padding*leg.yScale);
        // Seat the foot-facing shell slightly inside the visible sole rather than on its edge.
        leg.y-=padding*.5F;
        leg.xScale*=1.06F;
    }

    private static void offsetAlongPartY(ModelPart part,float distance){
        var offset=new Vector3f(0,distance,0);
        offset.transform(Vector3f.XP.rotation(part.xRot));
        offset.transform(Vector3f.YP.rotation(part.yRot));
        offset.transform(Vector3f.ZP.rotation(part.zRot));
        part.x+=offset.x();
        part.y+=offset.y();
        part.z+=offset.z();
    }

    @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,Villager soldier,
                                 float stride,float amount,float partial,float age,float yaw,float pitch){
        if(soldier.isInvisible()||!WorkerPose.soldier(soldier))return;
        steve=ResidentAppearanceModels.usesSteve(soldier);
        if(steve)ResidentAppearanceModels.steveModel().copyPropertiesTo(poseModel);
        else{
            var villager=getParentModel();
            var root=villager.root();
            poseModel.young=villager.young;
            poseModel.riding=villager.riding;
            poseModel.head.copyFrom(root.getChild("head"));
            poseModel.body.copyFrom(root.getChild("body"));
            poseModel.body.zScale=1.3F;
            poseModel.rightLeg.copyFrom(root.getChild("right_leg"));
            poseModel.leftLeg.copyFrom(root.getChild("left_leg"));
            poseModel.rightArm.loadPose(poseModel.rightArm.getInitialPose());
            poseModel.leftArm.loadPose(poseModel.leftArm.getInitialPose());
            BuilderArmsLayer.poseArms(soldier,poseModel.rightArm,poseModel.leftArm,stride,amount,age,false);
        }
        if(steve){
            armor.render(pose,buffers,light,soldier,stride,amount,partial,age,yaw,pitch);
            return;
        }

        var root=getParentModel().root();
        var pieces=(SoldierArmorLayerInvoker)armor;
        pieces.kraftcity$renderPiece(pose,buffers,soldier,EquipmentSlot.CHEST,light,outerArmor);
        var left=FreshVillagerParts.leg(root,"left_leg");
        var right=FreshVillagerParts.leg(root,"right_leg");
        if(left==null||right==null){
            pieces.kraftcity$renderPiece(pose,buffers,soldier,EquipmentSlot.LEGS,light,innerArmor);
            pieces.kraftcity$renderPiece(pose,buffers,soldier,EquipmentSlot.FEET,light,outerArmor);
        }else{
            try{
                piece=Piece.WAIST;
                pieces.kraftcity$renderPiece(pose,buffers,soldier,EquipmentSlot.LEGS,light,innerArmor);
                renderLower(pose,buffers,soldier,light,left,Piece.LEFT,poseModel.leftLeg);
                renderLower(pose,buffers,soldier,light,right,Piece.RIGHT,poseModel.rightLeg);
            }finally{
                piece=Piece.STANDARD;
            }
        }

        var head=soldier.getItemBySlot(EquipmentSlot.HEAD).isEmpty()?null:FreshVillagerParts.head(root);
        if(head==null){
            pieces.kraftcity$renderPiece(pose,buffers,soldier,EquipmentSlot.HEAD,light,outerArmor);
            return;
        }
        pose.pushPose();
        try{
            head.transform(pose,true);
            reset(poseModel.head);
            pieces.kraftcity$renderPiece(pose,buffers,soldier,EquipmentSlot.HEAD,light,outerArmor);
        }finally{
            pose.popPose();
        }
    }

    private void renderLower(PoseStack pose,MultiBufferSource buffers,Villager soldier,int light,
                             FreshVillagerParts.Anchor leg,Piece side,ModelPart armorLeg){
        pose.pushPose();
        try{
            piece=side;
            leg.transform(pose,false);
            reset(armorLeg);
            var pieces=(SoldierArmorLayerInvoker)armor;
            pieces.kraftcity$renderPiece(pose,buffers,soldier,EquipmentSlot.LEGS,light,innerArmor);
            pieces.kraftcity$renderPiece(pose,buffers,soldier,EquipmentSlot.FEET,light,outerArmor);
        }finally{
            pose.popPose();
        }
    }

    private static void reset(ModelPart part){
        part.setPos(0,0,0);
        part.setRotation(0,0,0);
        part.xScale=part.yScale=part.zScale=1F;
    }
}
