package br.com.kraftcity.industry;

import br.com.kraftcity.employment.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.*;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

public final class IndustrialEvents {
    private final Map<UUID,Long> next=new HashMap<>();private int cursor;
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e){
        var server=ServerLifecycleHooks.getCurrentServer();if(e.phase!=TickEvent.Phase.END||server==null)return;
        var stations=EmploymentData.get(server.overworld()).stations().stream().filter(IndustrialWork::industrial).toList();
        long now=server.getTickCount();
        for(int i=0;i<Math.min(4,stations.size());i++){
            var s=stations.get(Math.floorMod(cursor++,stations.size()));var l=server.getLevel(s.dimension);
            if(l!=null&&now>=next.getOrDefault(s.id,0L)){next.put(s.id,now+EmploymentConfig.ACTION_TICKS.get());IndustrialWork.tick(l,s);}
        }
        if(now%20==0)for(ServerPlayer p:server.getPlayerList().getPlayers())IndustryNetwork.sendBounds(p);
    }
    @SubscribeEvent public void chest(PlayerInteractEvent.RightClickBlock e){
        if(!(e.getEntity() instanceof ServerPlayer p)||!p.getPersistentData().hasUUID("KCBindDiscard"))return;
        var id=p.getPersistentData().getUUID("KCBindDiscard");p.getPersistentData().remove("KCBindDiscard");
        var s=EmploymentData.get(p.getLevel()).station(id);BlockPos pos=e.getPos();
        if(s!=null&&s.active&&s.dimension.equals(p.level.dimension())&&p.distanceToSqr(Vec3.atCenterOf(s.pos))<4096
                &&br.com.kraftcity.logistics.DiscardChests.inRange(IndustrialWork.area(p.getLevel(),s),pos)
                &&(p.level.getBlockEntity(pos) instanceof ChestBlockEntity||p.level.getBlockEntity(pos) instanceof BarrelBlockEntity
                    ||s.profession==Profession.FARMER&&FarmerLogistics.supports(p.getLevel(),pos))){
            s.industry.putLong("discard",pos.asLong());s.industry.putString("discard_dimension",s.dimension.location().toString());br.com.kraftcity.logistics.DiscardChests.invalidate(p.getLevel());s.setChanged();p.sendSystemMessage(net.minecraft.network.chat.Component.literal("Baú de Descarte associado ao posto."));
        }else p.sendSystemMessage(net.minecraft.network.chat.Component.literal("Associação recusada: escolha baú ou barril a até cinco blocos do limite da área do posto."));
        e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);
    }
    @SubscribeEvent public void trample(net.minecraftforge.event.level.BlockEvent.FarmlandTrampleEvent e){
        if(e.getEntity() instanceof Villager v&&v.level instanceof ServerLevel l){
            var station=EmploymentData.get(l).assigned(v.getUUID());
            if(station!=null&&station.profession==Profession.FARMER)e.setCanceled(true);
        }
    }
    @SubscribeEvent public void animal(PlayerInteractEvent.EntityInteract e){
        if(!(e.getEntity() instanceof ServerPlayer p)||!(e.getTarget() instanceof Animal a)||!p.getPersistentData().hasUUID("KCRegisterAnimal"))return;
        var id=p.getPersistentData().getUUID("KCRegisterAnimal");var s=EmploymentData.get(p.getLevel()).station(id);
        if(s!=null&&s.active&&s.profession==Profession.BUTCHER&&s.dimension.equals(p.level.dimension())&&IndustrialWork.area(p.getLevel(),s).contains(a.position())
                &&(a instanceof Cow||a instanceof Sheep||a instanceof Pig||a instanceof Chicken||a instanceof Rabbit)
                &&(!a.getPersistentData().hasUUID("KCIndustryStation")||id.equals(a.getPersistentData().getUUID("KCIndustryStation")))){
            a.getPersistentData().putUUID("KCIndustryStation",id);a.getPersistentData().putLong("KCIndustryBorn",p.level.getGameTime()-a.tickCount-(a.isBaby()?24000+a.getAge():24000));
            p.sendSystemMessage(net.minecraft.network.chat.Component.literal("Animal registrado no posto. Agache e clique no posto para encerrar o cadastro."));
        }else p.sendSystemMessage(net.minecraft.network.chat.Component.literal("Animal indisponível ou fora da área."));
        e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);
    }
    @SubscribeEvent public void wolf(LivingEvent.LivingTickEvent e){
        if(!(e.getEntity() instanceof Wolf w)||!(w.level instanceof ServerLevel l)||w.tickCount%20!=0||!w.getPersistentData().hasUUID("KraftCityCaretaker")||w.getOwnerUUID()!=null)return;
        if(l.getEntity(w.getPersistentData().getUUID("KraftCityCaretaker")) instanceof Villager v&&v.isAlive()){
            // No player UUID is assigned. Vanilla tame flag disables wild prey goals; KC owns follow logic.
            if(w.distanceToSqr(v)>16&&!w.isOrderedToSit()&&!w.isAngry())w.getNavigation().moveTo(v,1);
        }
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public void joined(net.minecraftforge.event.entity.EntityJoinLevelEvent e){
        if(e.getEntity() instanceof Villager v&&e.getLevel() instanceof ServerLevel l&&!Employment.active(v))IndustrialWork.restoreHand(v);
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGH)
    public void died(net.minecraftforge.event.entity.living.LivingDeathEvent e){if(e.getEntity() instanceof Villager v)IndustrialWork.restoreHand(v);}
    @SubscribeEvent public void stopped(net.minecraftforge.event.server.ServerStoppedEvent e){next.clear();cursor=0;}
}
