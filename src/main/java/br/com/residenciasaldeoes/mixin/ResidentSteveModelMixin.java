package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.employment.client.WorkerPose;
import br.com.kraftcity.military.client.SoldierArmorLayer;
import br.com.residenciasaldeoes.client.ResidentAppearanceModels;
import br.com.residenciasaldeoes.client.SteveWorkerToolLayer;
import br.com.residenciasaldeoes.client.VillagerSkinLayer;
import br.com.residenciasaldeoes.client.render.ResidentSteveRenderState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Mixin(LivingEntityRenderer.class)
public abstract class ResidentSteveModelMixin implements RenderLayerParent<Villager,EntityModel<Villager>> {
    @Shadow protected EntityModel<Villager> model;
    @Shadow @Final @Mutable protected List<? extends RenderLayer<?,?>> layers;

    @Unique private Deque<ResidentSteveRenderState> kraftcity$villagerRenderFrames;
    @Unique private SteveWorkerToolLayer kraftcity$steveTool;

    @Inject(method = "render", at = @At("HEAD"))
    private void residenciasaldeoes$useSteveModel(LivingEntity entity, float yaw, float partialTick,
            com.mojang.blaze3d.vertex.PoseStack poses, net.minecraft.client.renderer.MultiBufferSource buffers,
            int packedLight, CallbackInfo ci) {
        if (!(entity instanceof Villager villager)) return;
        var outer=kraftcity$villagerRenderFrames==null?null:kraftcity$villagerRenderFrames.peekLast();
        var originalModel=outer==null?model:outer.model();
        var originalLayers=outer==null?layers:outer.layers();
        boolean useSteve=originalModel instanceof VillagerModel<?> && ResidentAppearanceModels.usesSteve(villager);
        if(outer==null&&!useSteve)return;

        if(kraftcity$villagerRenderFrames==null)kraftcity$villagerRenderFrames=new ArrayDeque<>();
        kraftcity$villagerRenderFrames.push(new ResidentSteveRenderState(model,layers));

        if(!useSteve){
            model=originalModel;
            layers=originalLayers;
            return;
        }

        model=ResidentAppearanceModels.steveModel();
        List<RenderLayer<?,?>> compatibleLayers=new ArrayList<>();
        for (var layer : originalLayers) {
            if(layer instanceof VillagerSkinLayer || layer instanceof SoldierArmorLayer)compatibleLayers.add(layer);
        }
        if(WorkerPose.active(villager)||!WorkerPose.stowed(villager).isEmpty()){
            if(kraftcity$steveTool==null)kraftcity$steveTool=new SteveWorkerToolLayer(this);
            compatibleLayers.add(kraftcity$steveTool);
        }
        layers=compatibleLayers;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void residenciasaldeoes$restoreVillagerRenderer(LivingEntity entity, float yaw, float partialTick,
            com.mojang.blaze3d.vertex.PoseStack poses, net.minecraft.client.renderer.MultiBufferSource buffers,
            int packedLight, CallbackInfo ci) {
        if (!(entity instanceof Villager) || kraftcity$villagerRenderFrames==null
                || kraftcity$villagerRenderFrames.isEmpty()) return;
        var previous=kraftcity$villagerRenderFrames.pop();
        model=previous.model();
        layers=previous.layers();
    }
}
