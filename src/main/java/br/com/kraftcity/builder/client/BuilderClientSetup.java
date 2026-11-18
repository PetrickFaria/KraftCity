package br.com.kraftcity.builder.client;

import br.com.kraftcity.builder.BuilderModule;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.world.entity.EntityType;

@Mod.EventBusSubscriber(modid="residenciasaldeoes",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class BuilderClientSetup {
    @SubscribeEvent public static void setup(FMLClientSetupEvent e){e.enqueueWork(()->net.minecraft.client.gui.screens.MenuScreens.register(BuilderModule.MENU.get(),BuilderScreen::new));}
    @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers e){var renderer=e.getRenderer(EntityType.VILLAGER);if(renderer instanceof VillagerRenderer villager)villager.addLayer(new BuilderArmsLayer(villager));}
}
