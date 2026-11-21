package br.com.kraftcity.military;

import br.com.kraftcity.employment.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.server.ServerLifecycleHooks;

public final class MilitaryEvents {
    @SubscribeEvent public void joined(net.minecraftforge.event.entity.EntityJoinLevelEvent e){
        if(!(e.getLevel() instanceof ServerLevel l))return;
        if(e.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile shot
            &&shot.getOwner() instanceof Villager soldier&&Military.isSoldier(soldier))
            shot.getPersistentData().putUUID("KCSoldierShot",soldier.getUUID());
        if(e.getEntity() instanceof Villager v){var job=Military.station(v);if(Military.military(job))Military.bind(job,v);else if(v.getPersistentData().getBoolean("KCSoldier")){for(var slot:Military.GEAR)v.setItemSlot(slot,net.minecraft.world.item.ItemStack.EMPTY);v.getPersistentData().remove("KCSoldier");Military.residentHealth(l,v);}return;}if(!(e.getEntity() instanceof SoldierZombie z))return;var s=Military.station(z);
        if(s!=null&&(!s.dimension.equals(l.dimension())||!s.active))Employment.release(l,z.getUUID());
        if(s!=null&&s.active&&z.getUUID().equals(s.worker)&&s.dimension.equals(l.dimension())){Military.stats(l,z);Military.bind(s,z);}else for(var slot:Military.GEAR)z.setItemSlot(slot,net.minecraft.world.item.ItemStack.EMPTY);
    }
    @SubscribeEvent public void removed(net.minecraftforge.event.entity.EntityLeaveLevelEvent e){
        if(!(e.getLevel() instanceof ServerLevel l)||!(e.getEntity() instanceof SoldierZombie z)||z.getRemovalReason()==null||!z.getRemovalReason().shouldDestroy())return;
        var s=Military.station(z);
        if(s==null){
            var data=EmploymentData.get(l);
            if(!data.detachedSoldier(z.getUUID()).isEmpty()){
                data.forgetDetachedSoldier(z.getUUID());
                br.com.residenciasaldeoes.data.ResidenceData.get(l).unlink(l,z.getUUID());
            }
            return;
        }
        if(!s.military.getBoolean("zombie"))return;
        Employment.release(l,z.getUUID());s.military.remove("zombie");s.military.remove("villager");s.setChanged();br.com.residenciasaldeoes.data.ResidenceData.get(l).unlink(l,z.getUUID());
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e){
        var server=ServerLifecycleHooks.getCurrentServer();if(server==null||e.phase!=TickEvent.Phase.END)return;
        for(var s:EmploymentData.get(server.overworld()).stations())if(s.active&&s.worker!=null&&Military.military(s)){
            var l=server.getLevel(s.dimension);if(l==null)continue;
            if(l.getEntity(s.worker) instanceof Villager v){if(!v.isNoAi())Military.tick(l,s,v);else Military.bind(s,v);}
            else if(l.getEntity(s.worker) instanceof SoldierZombie z){Military.bind(s,z);Military.stats(l,z);s.setChanged();}
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void dying(LivingDeathEvent e){
        if(!(e.getEntity().level instanceof ServerLevel l))return;
        if(e.getEntity() instanceof Villager v&&Military.isSoldier(v)&&e.getSource().getEntity() instanceof Zombie){
            if(Military.zombify(l,v)!=null)e.setCanceled(true);return;
        }
        if(Military.isSoldier(e.getEntity()))Military.death(l,e.getEntity());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void died(LivingDeathEvent e){if(e.getEntity().level instanceof ServerLevel l)Military.award(l,e.getEntity(),e.getSource().getEntity()!=null?e.getSource().getEntity():e.getEntity().getKillCredit());}
    @SubscribeEvent(priority=EventPriority.LOWEST) public void damage(LivingDamageEvent e){
        if(e.getAmount()<=0||!(e.getEntity().level instanceof ServerLevel l))return;var source=e.getSource().getEntity();
        if(source instanceof Villager v&&Military.isSoldier(v)){
            var s=Military.station(v);if(!Military.authorized(l,s,v,e.getEntity())){e.setCanceled(true);return;}Military.contribute(l,e.getEntity(),v.getUUID());
        }
        if(source instanceof SoldierZombie z&&Military.isSoldier(z)){var gear=Military.station(z);var sword=gear.equipment.getItem(0);if(sword.getItem() instanceof net.minecraft.world.item.SwordItem)sword.hurtAndBreak(1,z,x->x.broadcastBreakEvent(EquipmentSlot.MAINHAND));gear.setChanged();}
        if(source instanceof Player p)for(var s:EmploymentData.get(l).stations())if(s.active&&Military.military(s)&&s.worker!=null&&s.military.hasUUID("player")&&p.getUUID().equals(s.military.getUUID("player"))){s.military.putUUID("orderedTarget",e.getEntity().getUUID());s.military.putLong("orderAt",l.getGameTime());var orders=s.military.getCompound("orders");for(String key:java.util.Set.copyOf(orders.getAllKeys()))if(l.getGameTime()-orders.getLong(key)>MilitaryModule.CONTRIBUTION_TICKS.get())orders.remove(key);orders.putLong(e.getEntity().getUUID().toString(),l.getGameTime());s.military.put("orders",orders);s.setChanged();}
    }
    @SubscribeEvent public void projectile(ProjectileImpactEvent event){
        var projectile=event.getProjectile();
        boolean tracked=projectile.getPersistentData().hasUUID("KCSoldierShot");
        if(!(projectile.level instanceof ServerLevel level))return;
        var owner=projectile.getOwner();
        if(!tracked&&!(owner instanceof Villager candidate&&Military.isSoldier(candidate)))return;
        if(!(owner instanceof Villager soldier)||!Military.isSoldier(soldier)
            ||!Military.insideArea(Military.station(soldier),event.getRayTraceResult().getLocation())
            ||event.getRayTraceResult() instanceof EntityHitResult hit&&hit.getEntity() instanceof LivingEntity target
                &&!Military.authorized(level,Military.station(soldier),soldier,target)){
            projectile.discard();
            event.setCanceled(true);
        }
    }
}
