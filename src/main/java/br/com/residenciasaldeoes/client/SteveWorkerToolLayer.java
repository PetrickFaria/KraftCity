package br.com.residenciasaldeoes.client;

import br.com.kraftcity.builder.client.BuilderArmsLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.npc.Villager;

public final class SteveWorkerToolLayer extends RenderLayer<Villager,SteveVillagerModel> {
    public SteveWorkerToolLayer(RenderLayerParent<Villager,SteveVillagerModel> parent){super(parent);}

    @Override
    public void render(PoseStack pose,MultiBufferSource buffers,int light,Villager worker,float stride,float amount,
                       float partial,float age,float yaw,float pitch){
        if(br.com.kraftcity.employment.client.WorkerPose.active(worker))
            BuilderArmsLayer.renderTool(pose,buffers,light,worker,getParentModel().rightArm);
        else BuilderArmsLayer.renderBack(pose,buffers,light,worker,getParentModel().body,getParentModel().jacket.visible?2.25F:2F);
    }
}
