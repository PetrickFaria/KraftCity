package br.com.kraftcity.test;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="residenciasaldeoes")
public final class GameTestWorld {
    private GameTestWorld(){}

    @SubscribeEvent
    public static void isolateNaturalSpawns(ServerStartedEvent event){
        var server=event.getServer();
        if(!(server instanceof GameTestServer))return;
        // Threat tests place their own mobs. Random spawns can otherwise attack
        // sleeping residents in unrelated batches that share the same world.
        server.overworld().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
    }
}
