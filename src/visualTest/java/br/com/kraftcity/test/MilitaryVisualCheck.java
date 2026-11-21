package br.com.kraftcity.test;

import br.com.kraftcity.military.*;
import br.com.kraftcity.military.client.SoldierScreen;
import br.com.kraftcity.employment.*;
import br.com.kraftcity.employment.client.WorkScreen;
import br.com.kraftcity.workforce.WorkforceData;
import br.com.residenciasaldeoes.ResidenciasDosAldeoes;
import br.com.residenciasaldeoes.data.ResidenceData;
import java.util.*;
import java.io.File;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

/** Real menus/packets, guard return, physical combat, vanilla cure and independent JVM restoration. */
public final class MilitaryVisualCheck {
    private static final BlockPos POST=new BlockPos(174,-57,40), HOME=POST.offset(-3,-1,0);
    private static volatile UUID worker,enemy;
    private static volatile boolean won,finished;
    private static volatile ItemStack savedWeapon,savedAmmo;
    private static boolean bowPose,crossbowPose;
    private static int wonAt=-1,openedAt=-1;
    private static EmploymentData.Station station(ServerLevel l){return ((WorkBlockEntity)l.getBlockEntity(POST)).station();}
    private static void check(boolean b,String m){if(!b)throw new IllegalStateException(m);}
    private static void capture(Minecraft mc,String name){Screenshot.grab(new File("../build"),name,mc.getMainRenderTarget(),message->{});}
    private static Button button(Minecraft mc,String text){return mc.screen.children().stream().filter(x->x instanceof Button).map(x->(Button)x).filter(x->x.getMessage().getString().contains(text)).min(Comparator.comparingInt(x->x.getMessage().getString().equals(text)?0:1)).orElseThrow(()->new IllegalStateException("Military button missing: "+text));}
    private static void click(Minecraft mc,String text){var b=button(mc,text);check(b.active,"Disabled military button "+text);b.onPress();}
    private static ServerPlayer player(Minecraft mc){return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());}
    private static void setup(ServerPlayer p){var l=p.getLevel();p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,6000,0,false,false));check(Math.floorMod(l.getDayTime(),24000)>=13000,"Commercial pause must remain night");
        p.teleportTo(l,POST.getX()+.5,POST.getY(),POST.getZ()+3.5,180,20);
        for(int x=-17;x<=17;x++)for(int z=-17;z<=17;z++){l.setBlock(POST.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);for(int y=0;y<=5;y++)l.setBlock(POST.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        l.setBlock(HOME.below(),Blocks.STONE.defaultBlockState(),3);ResidenceData.get(l).unlinkAll(l,l.dimension(),HOME);l.setBlock(HOME,ResidenciasDosAldeoes.ANCHOR.get().defaultBlockState(),3);((br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity)l.getBlockEntity(HOME)).setSize(11);
        var v=EntityType.VILLAGER.create(l);v.setAge(0);v.setNoAi(true);v.moveTo(POST.getX()+.5,POST.getY(),POST.getZ()+1.5,0,0);l.addFreshEntity(v);check(ResidenceData.get(l).link(l,HOME,v).equals("ok"),"Military visual residence");ResidenceData.get(l).renameResident(v.getUUID(),"Soldado de validação");worker=v.getUUID();
        l.setBlock(POST,WorkModule.MILITARY.get().defaultBlockState().setValue(WorkBlock.FACING,Direction.SOUTH),3);
        var s=station(l);
        // The copied GameTest world can contain hostiles from earlier fixtures in this post's range.
        var bounds=Military.areaBounds(POST);
        l.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,bounds,
            mob->mob instanceof net.minecraft.world.entity.monster.Enemy&&Military.insideArea(s,mob.position()))
            .forEach(net.minecraft.world.entity.Entity::discard);
        s.setItem(31,new ItemStack(Items.EMERALD,30));
        WorkBlock.open(p,s);
    }
    public static void tick(Minecraft mc,int elapsedTicks){
        if(elapsedTicks==0)mc.getSingleplayerServer().execute(()->setup(player(mc)));
        if(openedAt<0){
            if(mc.screen instanceof WorkScreen)openedAt=elapsedTicks;
            else{
                check(elapsedTicks<200,"Military hiring screen did not open");
                return;
            }
        }
        int t=elapsedTicks-openedAt;
        if(t==25){check(mc.screen instanceof WorkScreen,"Military hiring screen");String id=worker.toString().substring(0,6);for(int i=0;i<100&&mc.screen.children().stream().noneMatch(x->x instanceof Button b&&b.getMessage().getString().contains(id));i++)click(mc,">");click(mc,id);click(mc,"Soldado");}
        if(t==30)capture(mc,"visual-military-hiring.png");
        if(t==35)click(mc,"Treinar");
        if(t==60){check(mc.screen instanceof SoldierScreen&&mc.player.containerMenu instanceof SoldierMenu m&&m.slots.size()==74&&m.state!=null&&m.state.employed(),"Soldier menu not synchronized");mc.getSingleplayerServer().execute(()->{var p=player(mc);var s=station(p.getLevel());check(s.worker.equals(worker)&&s.getItem(31).getCount()==15,"Military contract/payment");p.getInventory().setItem(9,new ItemStack(Items.IRON_SWORD));p.getInventory().setItem(10,new ItemStack(Items.SHIELD));p.getInventory().setItem(11,new ItemStack(Items.IRON_CHESTPLATE));s.setItem(0,new ItemStack(Items.ARROW,16));});}
        if(t==72)for(int i=0;i<3;i++)mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,
            SoldierMenu.PLAYER_START+i,1,net.minecraft.world.inventory.ClickType.PICKUP,mc.player);
        if(t==80){
            var equipped=((SoldierMenu)mc.player.containerMenu).equipment;
            check(equipped.getItem(0).is(Items.IRON_SWORD)&&equipped.getItem(1).is(Items.SHIELD)
                &&equipped.getItem(3).is(Items.IRON_CHESTPLATE),"Quick equip did not synchronize through the game client");
            check(mc.screen.children().stream().anyMatch(widget->widget instanceof Button button&&button.getMessage().getString().equals("Demitir")),
                "Soldier dismissal button missing");
            capture(mc,"visual-military-soldier.png");
        }
        if(t==85)click(mc,"Blacklist");
        if(t==110){var m=(SoldierMenu)mc.player.containerMenu;check(m.catalog.stream().anyMatch(e->e.id().equals("minecraft:creeper")&&!e.selected()),"Creeper enabled by default");click(mc,"Pacíficos");click(mc,"Origem:");}
        if(t==120)click(mc,"minecraft:cow");
        if(t==140){capture(mc,"visual-military-blacklist.png");mc.getSingleplayerServer().execute(()->{var s=station(player(mc).getLevel());check(Military.selected(s,EntityType.COW),"Cow selection packet failed");Military.select(s,EntityType.COW,false);});}
        if(t==145)click(mc,"Voltar ao soldado");
        if(t==150){click(mc,"Guarnecer");click(mc,"Diurno");}
        if(t==170)mc.getSingleplayerServer().execute(()->{var l=player(mc).getLevel();var s=station(l);var v=(Villager)l.getEntity(worker);check(Military.mode(s)==Military.Mode.GUARD&&Military.shift(s)==Military.Shift.NIGHT,"Guard/night packets");v.setNoAi(false);v.setPos(POST.getX()+.5,POST.getY()+1,POST.getZ()+.5);});
        if(t==190){capture(mc,"visual-military-guard.png");mc.getSingleplayerServer().execute(()->{var l=player(mc).getLevel();var v=(Villager)l.getEntity(worker);check(v.distanceToSqr(Vec3.atBottomCenterOf(POST.above()))<.3&&v.getNavigation().isDone(),"Guard left post without target");});}
        if(t==195)mc.player.closeContainer();
        if(t==200)mc.getSingleplayerServer().execute(()->{var l=player(mc).getLevel();Military.select(station(l),EntityType.COW,true);var cow=EntityType.COW.create(l);cow.setNoAi(true);cow.setPos(POST.getX()+5.5,POST.getY(),POST.getZ()+.5);l.addFreshEntity(cow);enemy=cow.getUUID();});
        if(t==230)capture(mc,"visual-military-attack.png");
        if(t>210&&t%10==0&&!won)mc.getSingleplayerServer().execute(()->{var l=player(mc).getLevel();var cow=l.getEntity(enemy);if(cow==null||!cow.isAlive()){var s=station(l);check(s.equipment.getItem(0).getDamageValue()>0&&WorkforceData.get(l).progress(worker,Profession.SOLDIER.id()).xp()>0,"No real military hit/XP");Military.select(s,EntityType.COW,false);won=true;System.out.println("MILITARY_GRAPHICAL_COMBAT_AND_XP_PASSED");}});
        if(won&&wonAt<0)wonAt=t;
        if(wonAt>=0){int elapsed=t-wonAt;
            if(elapsed==70)mc.getSingleplayerServer().execute(()->{var l=player(mc).getLevel();var s=station(l);var v=(Villager)l.getEntity(worker);check(v.distanceToSqr(Vec3.atBottomCenterOf(POST.above()))<.4,"Guard did not physically return");Military.select(s,EntityType.COW,false);s.military.putString("mode","FREE");System.out.println("MILITARY_GUARD_RETURN_PASSED");});
            if(elapsed==160)mc.getSingleplayerServer().execute(()->{var l=player(mc).getLevel();var v=(Villager)l.getEntity(worker);check(Military.insideArea(station(l),v.position()),"Patrol left the military area");var s=station(l);s.military.putString("mode","FOLLOW");s.military.remove("orderedTarget");var cow=EntityType.COW.create(l);cow.setNoAi(true);cow.setPos(v.getX()+3,v.getY(),v.getZ());l.addFreshEntity(cow);enemy=cow.getUUID();Military.select(s,EntityType.COW,false);});
            if(elapsed==180)mc.getSingleplayerServer().execute(()->{var l=player(mc).getLevel();var s=station(l);check(!s.military.hasUUID("target"),"Follow attacked unprovoked");var cow=(LivingEntity)l.getEntity(enemy);cow.hurt(net.minecraft.world.damagesource.DamageSource.playerAttack(player(mc)),1);check(s.military.hasUUID("orderedTarget"),"Player hit did not authorize follow");Military.select(s,EntityType.COW,false);s.military.putString("mode","GUARD");cow.discard();var z=Military.zombify(l,(Villager)l.getEntity(worker));check(z!=null&&z.getUUID().equals(worker),"Zombie transformation");z.setNoAi(true);player(mc).teleportTo(l,z.getX()+.5,z.getY(),z.getZ()+4,180,15);System.out.println("MILITARY_FREE_FOLLOW_AND_TRANSFORM_PASSED");});
            if(elapsed==200)capture(mc,"visual-military-zombie.png");
            if(elapsed==205)mc.getSingleplayerServer().execute(()->{var l=player(mc).getLevel();var z=(SoldierZombie)l.getEntity(worker);z.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS,200));player(mc).setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.GOLDEN_APPLE,2));player(mc).setGameMode(net.minecraft.world.level.GameType.SURVIVAL);z.mobInteract(player(mc),InteractionHand.MAIN_HAND);player(mc).setGameMode(net.minecraft.world.level.GameType.CREATIVE);check(z.isConverting()&&player(mc).getMainHandItem().getCount()==1,"Vanilla weakness/apple cure");var tag=z.saveWithoutId(new net.minecraft.nbt.CompoundTag());tag.putInt("ConversionTime",1);z.readAdditionalSaveData(tag);z.setNoAi(false);});
            if(elapsed==225)mc.getSingleplayerServer().execute(()->{var l=player(mc).getLevel();var s=station(l);check(l.getEntity(worker) instanceof Villager&&s.worker.equals(worker)&&!s.military.getBoolean("zombie"),"Military cure recovery");s.military.putBoolean("visual_passed",true);s.military.putLong("visual_xp",WorkforceData.get(l).progress(worker,Profession.SOLDIER.id()).xp());s.military.putInt("visual_wear",s.equipment.getItem(0).getDamageValue());s.setChanged();if(!Boolean.getBoolean("kraftcity.militaryVisualOnly")&&!Boolean.getBoolean("kraftcity.commerceVisualOnly"))WorkerConversionVisualCheck.prepare(l);finished=true;System.out.println("MILITARY_VANILLA_CURE_PASSED");});
            if(elapsed>=245)verifyRanged(mc,elapsed-245);
        }
        check(t<1500,"Military visual combat/return timed out");
    }
    private static void verifyRanged(Minecraft mc,int tick){
        var server=mc.getSingleplayerServer();
        if(tick==0){
            check(finished,"Military finish marker missing");
            server.execute(()->{
                var player=player(mc);
                var level=player.getLevel();
                var station=station(level);
                if(!(level.getEntity(worker) instanceof Villager soldier))throw new IllegalStateException("Cured soldier missing");
                savedWeapon=station.equipment.getItem(0);
                savedAmmo=station.getItem(0);
                soldier.setPos(Vec3.atBottomCenterOf(POST.above()));
                player.teleportTo(level,POST.getX()+3.5,POST.getY()+1,POST.getZ()+6.5,180,15);
                var target=EntityType.COW.create(level);
                target.setNoAi(true);
                target.setPos(POST.getX()+5.5,POST.getY(),POST.getZ()+.5);
                target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(80);
                target.setHealth(80);
                level.addFreshEntity(target);
                enemy=target.getUUID();
                Military.select(station,EntityType.COW,true);
                station.equipment.setItem(0,new ItemStack(Items.BOW));
                station.setItem(0,new ItemStack(Items.ARROW,16));
                Military.clearTask(soldier,station);
            });
        }
        if(tick>=8&&tick<60&&!bowPose&&watchAim(mc,Items.BOW)){
            capture(mc,"visual-military-bow.png");
            bowPose=true;
        }
        if(tick==60){
            check(bowPose,"Bow aiming pose did not synchronize");
            server.execute(()->{
                var player=player(mc);
                var level=player.getLevel();
                var station=station(level);
                var soldier=(Villager)level.getEntity(worker);
                check(station.getItem(0).getCount()<16&&station.equipment.getItem(0).getDamageValue()>0,"No physical bow shot");
                station.equipment.setItem(0,new ItemStack(Items.CROSSBOW));
                station.setItem(0,new ItemStack(Items.ARROW,16));
                Military.clearTask(soldier,station);
            });
        }
        if(tick>=68&&tick<135&&!crossbowPose&&watchAim(mc,Items.CROSSBOW)){
            capture(mc,"visual-military-crossbow.png");
            crossbowPose=true;
        }
        if(tick==135){
            check(crossbowPose,"Crossbow loading pose did not synchronize");
            server.execute(()->{
                var player=player(mc);
                var level=player.getLevel();
                var station=station(level);
                var target=level.getEntity(enemy);
                check(station.getItem(0).getCount()<16&&station.equipment.getItem(0).getDamageValue()>0,"No physical crossbow shot");
                if(target!=null)target.discard();
                Military.select(station,EntityType.COW,false);
                station.equipment.setItem(0,savedWeapon);
                station.setItem(0,savedAmmo);
                Military.clearTask((Villager)level.getEntity(worker),station);
                System.out.println("MILITARY_RANGED_GRAPHICS_AND_AMMO_PASSED");
            });
        }
        if(tick==150)mc.stop();
    }

    private static boolean watchAim(Minecraft mc,Item weapon){
        return mc.level.getEntitiesOfClass(Villager.class,mc.player.getBoundingBox().inflate(24),villager->
            villager.getUUID().equals(worker)&&villager.getMainHandItem().is(weapon)&&villager.isUsingItem()
            &&villager.getUsedItemHand()==InteractionHand.MAIN_HAND&&villager.getTicksUsingItem()>6
            &&br.com.kraftcity.employment.client.WorkerPose.soldier(villager)).size()==1;
    }

    public static void verifyRestore(ServerLevel l){var s=station(l);check(s.active&&s.worker!=null&&s.profession==Profession.SOLDIER&&s.getItem(31).getCount()==15&&s.military.getBoolean("visual_passed"),"Military saved contract");check(l.getEntity(s.worker) instanceof Villager&&ResidenceData.get(l).owner(s.worker)!=null,"Military saved identity/home");check(s.equipment.getItem(0).is(Items.IRON_SWORD)&&s.equipment.getItem(0).getDamageValue()==s.military.getInt("visual_wear")&&s.equipment.getItem(1).is(Items.SHIELD)&&s.getItem(0).getCount()==16,"Military gear/inventory save");check(WorkforceData.get(l).progress(s.worker,Profession.SOLDIER.id()).xp()==s.military.getLong("visual_xp")&&Military.mode(s)==Military.Mode.GUARD&&Military.shift(s)==Military.Shift.NIGHT&&!Military.selected(s,EntityType.CREEPER),"Military progression/mode/shift save");System.out.println("MILITARY_WORLD_RESTART_PASSED");}
}
