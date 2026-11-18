package br.com.kraftcity.builder;

import br.com.kraftcity.employment.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

public final class BuilderEvents {
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e){var server=ServerLifecycleHooks.getCurrentServer();if(server==null||e.phase!=TickEvent.Phase.END)return;BuilderWork.tick(server.overworld());BlueprintLibrary.tickCaptures(server);BuilderNetwork.pump(server);
        for(var s:EmploymentData.get(server.overworld()).stations())if(s.active&&s.profession==Profession.BUILDER&&s.worker!=null){var l=server.getLevel(s.dimension);if(l==null||!(l.getEntity(s.worker) instanceof Villager v)||v.isNoAi()||v.isSleeping())continue;long phase=Math.floorMod(l.getDayTime(),24000);if(phase>=12000&&phase<13000&&!v.getPersistentData().contains("residenciasaldeoes_emerald_transfer")&&!v.getPersistentData().contains("residenciasaldeoes_night_door_task")){var home=br.com.residenciasaldeoes.data.ResidenceData.get(l).owner(v.getUUID());if(home!=null&&l.hasChunkAt(home.pos)&&v.distanceToSqr(Vec3.atCenterOf(home.pos))>9)v.getNavigation().moveTo(home.pos.getX()+.5,home.pos.getY()+1,home.pos.getZ()+.5,.9);}}
        if(server.getTickCount()%10==0)for(var l:server.getAllLevels())BuilderNetwork.works(l);
    }
    @SubscribeEvent public void stop(ServerStoppedEvent e){BuilderWork.stop();BuilderNetwork.stop();BlueprintLibrary.stop();}
}
