package br.com.kraftcity.military.client;

import br.com.kraftcity.builder.client.BuilderArmsLayer;
import br.com.kraftcity.employment.client.WorkerPose;
import br.com.residenciasaldeoes.client.ResidentAppearanceModels;
import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.world.item.ItemStack;

public final class SoldierArmorLayer extends RenderLayer<Villager,VillagerModel<Villager>> {
    private final HumanoidModel<Villager> poseModel;
    private final HumanoidArmorLayer<Villager,HumanoidModel<Villager>,HumanoidModel<Villager>> armor;
    private boolean steve;

    public SoldierArmorLayer(RenderLayerParent<Villager,VillagerModel<Villager>> parent,EntityModelSet models){
        super(parent);
        poseModel=new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        var adapter=new RenderLayerParent<Villager,HumanoidModel<Villager>>(){
            @Override public HumanoidModel<Villager> getModel(){return poseModel;}
            @Override public ResourceLocation getTextureLocation(Villager villager){
                return parent.getTextureLocation(villager);
            }
        };
        armor=new HumanoidArmorLayer<>(adapter,new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
            new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR))){
            @Override protected void setPartVisibility(HumanoidModel<Villager> model,EquipmentSlot slot){
                super.setPartVisibility(model,slot);
                if(slot==EquipmentSlot.HEAD&&!steve){
                    model.head.y-=2.8F;
                    model.head.yScale=.86F;
                    model.head.zScale=.94F;
                    model.hat.copyFrom(model.head);
                }
                if(slot==EquipmentSlot.LEGS&&!steve){model.leftLeg.y-=1;model.rightLeg.y-=1;}
                if(slot==EquipmentSlot.FEET&&!steve){model.leftLeg.y-=1.2F;model.rightLeg.y-=1.2F;}
                if(slot==EquipmentSlot.LEGS&&!steve){
                    // The robe is deeper than the vanilla leggings mesh at rest.
                    model.body.zScale=1.55F;
                    model.leftLeg.zScale=1.55F;
                    model.rightLeg.zScale=1.55F;
                }
            }
        };
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
        armor.render(pose,buffers,light,soldier,stride,amount,partial,age,yaw,pitch);
    }
}
