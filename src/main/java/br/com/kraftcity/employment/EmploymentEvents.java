package br.com.kraftcity.employment;

import br.com.kraftcity.api.ResidentUnlinkedEvent;
import br.com.kraftcity.api.ResidentIdentityChangedEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.server.ServerLifecycleHooks;

public final class EmploymentEvents {
    @SubscribeEvent public void unlinked(ResidentUnlinkedEvent event){var server=ServerLifecycleHooks.getCurrentServer();if(server!=null)Employment.release(server.overworld(),event.resident);}
    @SubscribeEvent public void identityChanged(ResidentIdentityChangedEvent event){
        var server=ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        Employment.release(server.overworld(),event.previous);
        br.com.kraftcity.workforce.WorkforceData.get(server.overworld()).rebindHistory(event.previous,event.current);
    }
    @SubscribeEvent public void removed(EntityLeaveLevelEvent event){
        if(event.getEntity() instanceof Villager v&&event.getLevel() instanceof ServerLevel l
                &&!br.com.kraftcity.military.Military.transferring(v.getUUID())&&v.getRemovalReason()!=null&&v.getRemovalReason().shouldDestroy())Employment.release(l,v.getUUID());
    }
    @SubscribeEvent public void workerBreeding(net.minecraftforge.event.entity.living.BabyEntitySpawnEvent event){
        if(event.getParentA() instanceof Villager first&&Employment.active(first)
            ||event.getParentB() instanceof Villager second&&Employment.active(second))event.setCanceled(true);
    }
    @SubscribeEvent public void died(LivingDeathEvent event){if(event.getEntity() instanceof Villager v&&v.level instanceof ServerLevel l)Employment.release(l,v.getUUID());}
    @SubscribeEvent public void joined(EntityJoinLevelEvent event){if(event.getEntity() instanceof Villager v&&event.getLevel() instanceof ServerLevel l){var s=EmploymentData.get(l).assigned(v.getUUID());if(s!=null&&!s.dimension.equals(l.dimension()))Employment.release(l,v.getUUID());else Employment.valid(l,v.getUUID());}}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void survival(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event){
        if(event.getEntity() instanceof Villager v&&v.level instanceof ServerLevel level){
            Employment.flee(level,v);
            if(!Employment.fleeing(v)&&br.com.kraftcity.industry.FarmerLogistics.leaveWater(level,v))return;
            WorkplaceVisits.tick(level,v);
            WorkerGates.openAlongPath(level,v);
        }
    }

    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event){
        var server=ServerLifecycleHooks.getCurrentServer();if(event.phase!=TickEvent.Phase.END||server==null)return;
        if(server.getTickCount()%5==0)for(var level:server.getAllLevels())WorkerGates.closeVacant(level);
        if(server.getTickCount()%10==0)for(var s:EmploymentData.get(server.overworld()).stations()){
            if(!s.active||s.worker==null||s.profession==null)continue;
            var level=server.getLevel(s.dimension);
            if(level==null||!(level.getEntity(s.worker) instanceof Villager worker))continue;
            boolean interrupted=worker.isSleeping()||Employment.fleeing(worker)||worker.getTarget()!=null||worker.isUsingItem();
            boolean working=s.sector==Profession.Sector.MILITARY
                ?br.com.kraftcity.military.Military.onDuty(level,s)
                :br.com.kraftcity.industry.IndustrialWork.day(level)&&!level.isThundering()
                    ||s.profession==Profession.FARMER&&br.com.kraftcity.industry.FarmerLogistics.ending(worker);
            ItemStack stowed=worker.isSleeping()||!interrupted&&!working?restingTool(s,worker):ItemStack.EMPTY;
            WorkNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(()->worker),
                new WorkNetwork.Activity(worker.getId(),worker.getUUID(),s.profession==Profession.FARMER,working&&!interrupted,stowed));
        }
        if(server.getTickCount()%20!=0)return;
        for(var s:EmploymentData.get(server.overworld()).stations())Employment.reconcile(server.overworld(),s);
    }
    private static ItemStack restingTool(EmploymentData.Station station,Villager worker){
        ItemStack stack=switch(station.sector){
            case INDUSTRIAL->station.getItem(EmploymentData.TOOL);
            case MILITARY->station.equipment.getItem(0);
            default->worker.getMainHandItem();
        };
        var item=stack.getItem();
        if(stack.isEmpty()||!(item instanceof TieredItem||item instanceof ShearsItem||item instanceof FishingRodItem
            ||item instanceof ProjectileWeaponItem||item instanceof TridentItem))return ItemStack.EMPTY;
        var view=stack.copy();
        view.setCount(1);
        return view;
    }

    @SubscribeEvent(priority=EventPriority.HIGH) public void interact(PlayerInteractEvent.EntityInteract event){
        if(!(event.getEntity() instanceof ServerPlayer player)||!(event.getTarget() instanceof Villager v)||event.getHand()!=InteractionHand.MAIN_HAND)return;
        var data=player.getPersistentData();if(data.getBoolean("residenciasaldeoes_register")||data.getBoolean("residenciasaldeoes_unlink"))return;
        if(Employment.valid(player.getLevel(),v.getUUID())){
            var s=EmploymentData.get(player.getLevel()).assigned(v.getUUID());
            WorkBlock.open(player,s,v.getUUID());event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
