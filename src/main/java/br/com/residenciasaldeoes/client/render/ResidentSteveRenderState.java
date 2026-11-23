package br.com.residenciasaldeoes.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.npc.Villager;

import java.util.List;

/** Prior model and layers for one invocation of the shared villager renderer. */
public record ResidentSteveRenderState(EntityModel<Villager> model, List<? extends RenderLayer<?,?>> layers) {}
