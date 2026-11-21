package br.com.kraftcity.test;

import br.com.kraftcity.military.*;
import br.com.kraftcity.employment.*;
import br.com.kraftcity.workforce.WorkforceData;
import br.com.residenciasaldeoes.ResidenciasDosAldeoes;
import br.com.residenciasaldeoes.data.ResidenceData;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;

@GameTestHolder("residenciasaldeoes") @PrefixGameTestTemplate(false)
public final class MilitaryGameTests {
    private record F(ServerLevel l,EmploymentData.Station s,Villager v,ServerPlayer player,BlockPos home,BlockPos p){}
    private static void check(boolean v,String m){if(!v)throw new GameTestAssertException(m);}
    private static F fixture(GameTestHelper h){
        var l=h.getLevel();l.setDayTime(1000);l.setWeatherParameters(10000,0,false,false);l.setRainLevel(0);
        var home=h.absolutePos(new BlockPos(2,2,2));var p=h.absolutePos(new BlockPos(8,2,8));
        for(var at:BlockPos.betweenClosed(p.offset(-4,-1,-4),p.offset(4,-1,4)))l.setBlock(at,Blocks.STONE.defaultBlockState(),3);
        l.setBlock(home,ResidenciasDosAldeoes.ANCHOR.get().defaultBlockState(),3);((br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity)l.getBlockEntity(home)).setSize(11);
        var v=h.spawn(EntityType.VILLAGER,new BlockPos(3,2,2));v.setAge(0);v.setNoAi(true);check(ResidenceData.get(l).link(l,home,v).equals("ok"),"Soldier home");v.setPos(p.getX()+.5,p.getY()+1,p.getZ()+.5);
        l.setBlock(p,WorkModule.MILITARY.get().defaultBlockState(),3);var s=((WorkBlockEntity)l.getBlockEntity(p)).station();var player=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"MilitaryTest"));player.setPos(v.position());s.setItem(31,new ItemStack(Items.EMERALD,30));check(Employment.hire(player,s,v.getUUID(),Profession.SOLDIER).equals("ok"),"Military hire");return new F(l,s,v,player,home,p);
    }
    private static <T extends Mob>T mob(F f,EntityType<T> type,int dx){var v=type.create(f.l);v.setNoAi(true);v.setPos(f.p.getX()+dx+.5,f.p.getY()+1,f.p.getZ()+.5);f.l.addFreshEntity(v);return v;}
    private static void clearTestHostiles(F f){
        f.l.getEntitiesOfClass(Monster.class,f.v.getBoundingBox().inflate(40)).forEach(Entity::discard);
    }
    private static void mode(F f,Military.Mode mode){f.s.military.putString("mode",mode.name());}
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void contract_cost_fifteen_one_worker_physical_soldier_and_home(GameTestHelper h){var f=fixture(h);check(f.s.getItem(31).getCount()==15&&f.l.getBlockState(f.p).is(WorkModule.PROFESSIONAL.get(Profession.SOLDIER).get()),"Physical post/payment");check(!Employment.hire(f.player,f.s,f.v.getUUID(),Profession.SOLDIER).equals("ok")&&f.s.getItem(31).getCount()==15,"Double hire");check(ResidenceData.get(f.l).owner(f.v.getUUID())!=null&&f.v.getMaxHealth()==10,"Home and initial health");h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military")
    public static void dismissed_zombie_soldier_keeps_identity_cure_and_discount(GameTestHelper h){
        var f=fixture(h);
        var id=f.v.getUUID();
        var data=WorkforceData.get(f.l);
        data.setProgress(id,Profession.SOLDIER.id(),3,160000);
        f.s.equipment.setItem(0,new ItemStack(Items.IRON_SWORD));
        var zombie=Military.zombify(f.l,f.v);
        check(zombie!=null,"soldier did not transform");
        check(Employment.dismiss(f.player,f.s,Employment.epoch(f.s)),"cannot dismiss zombie soldier");
        check(f.l.getEntity(id)==zombie&&f.s.worker==null&&f.s.equipment.getItem(0).is(Items.IRON_SWORD)
            &&zombie.getMainHandItem().isEmpty()&&!f.s.military.getBoolean("zombie"),"dismissal replaced entity or retained assignment/gear");
        var persisted=EmploymentData.load(EmploymentData.get(f.l).save(new CompoundTag()));
        check(!persisted.detachedSoldier(id).isEmpty(),"detached soldier cure data was not saved");
        var villager=Military.cure(f.l,zombie);
        check(villager!=null&&villager.getUUID().equals(id)&&f.s.worker==null
            &&ResidenceData.get(f.l).owner(id)!=null&&data.progress(id,Profession.SOLDIER.id()).xp()==160000,"cure lost resident data");
        f.s.setItem(31,new ItemStack(Items.EMERALD,1));
        check(Employment.hire(f.player,f.s,id,Profession.SOLDIER).equals("ok")&&f.s.getItem(31).isEmpty(),"soldier rehire fee");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void additive_blacklist_and_natural_hostile_categories(GameTestHelper h){var f=fixture(h);check(!Military.selected(f.s,EntityType.CREEPER)&&!Military.selected(f.s,EntityType.COW)&&!Military.selected(f.s,EntityType.ZOMBIE),"Default additional list is empty");var cow=mob(f,EntityType.COW,2);check(!Military.authorized(f.l,f.s,f.v,cow),"Cow before selection");Military.select(f.s,EntityType.COW,true);check(Military.authorized(f.l,f.s,f.v,cow),"Selected peaceful cow");Military.select(f.s,EntityType.COW,false);check(!Military.authorized(f.l,f.s,f.v,cow),"Cow disabled");check(MilitaryNetwork.category(EntityType.WOLF,null)==1&&MilitaryNetwork.category(EntityType.COW,null)==0&&MilitaryNetwork.category(EntityType.ZOMBIE,null)==2,"Categories");h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void follow_defends_player_locally_without_needing_blacklist(GameTestHelper h){var f=fixture(h);mode(f,Military.Mode.FOLLOW);f.l.addNewPlayer(f.player);var z=mob(f,EntityType.ZOMBIE,2);check(Military.authorized(f.l,f.s,f.v,z),"Natural hostile near player");Military.select(f.s,EntityType.ZOMBIE,false);check(Military.authorized(f.l,f.s,f.v,z),"Blacklist acted as exclusion");z.setPos(f.p.getX()+14.5,f.p.getY()+1,f.p.getZ()+.5);check(!Military.authorized(f.l,f.s,f.v,z),"Hostile outside player proximity");f.player.setLastHurtByMob(z);check(Military.authorized(f.l,f.s,f.v,z),"Direct attack on owner lost priority");h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void free_and_guard_require_protected_city_or_self_defense(GameTestHelper h){var f=fixture(h);var z=mob(f,EntityType.ZOMBIE,2);for(var m:List.of(Military.Mode.FREE,Military.Mode.GUARD)){mode(f,m);check(Military.authorized(f.l,f.s,f.v,z),"City target "+m);}z.setPos(f.p.getX()+35,f.p.getY(),f.p.getZ()+35);check(!Military.authorized(f.l,f.s,f.v,z),"Nearby soldier alone authorizes nothing");f.v.setLastHurtByMob(z);check(Military.authorized(f.l,f.s,f.v,z),"Self-defense inside new bounds");z.setPos(f.p.getX()+101.5,f.p.getY()+1,f.p.getZ()+.5);check(!Military.authorized(f.l,f.s,f.v,z),"Chase exceeded area");h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military_guard_idle") public static void guard_faces_post_and_legacy_keep_maps_to_guard(GameTestHelper h){var f=fixture(h);clearTestHostiles(f);for(var type:net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES)Military.select(f.s,type,false);f.s.military.putString("mode","KEEP");check(Military.mode(f.s)==Military.Mode.GUARD,"Keep migration");f.l.setBlock(f.p,f.l.getBlockState(f.p).setValue(WorkBlock.FACING,Direction.EAST),3);Military.tick(f.l,f.s,f.v);check(f.v.getNavigation().isDone()&&f.v.getYRot()==Direction.EAST.toYRot(),"Guard stationary/front");h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void follow_damage_event_creates_order_only_after_actual_player_hit(GameTestHelper h){var f=fixture(h);mode(f,Military.Mode.FOLLOW);f.l.addNewPlayer(f.player);var z=mob(f,EntityType.COW,2);check(!Military.authorized(f.l,f.s,f.v,z),"Pre-hit follow");z.hurt(DamageSource.playerAttack(f.player),1);check(Military.authorized(f.l,f.s,f.v,z),"Actual player hit did not order");h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void melee_weapon_break_then_punch_and_new_sword(GameTestHelper h){var f=fixture(h);var cow=mob(f,EntityType.COW,1);Military.select(f.s,EntityType.COW,true);var sword=new ItemStack(Items.WOODEN_SWORD);sword.setDamageValue(sword.getMaxDamage()-1);f.s.equipment.setItem(0,sword);Military.bind(f.s,f.v);f.s.military.putLong("attackAt",0);Military.combat(f.l,f.s,f.v,cow);check(sword.isEmpty(),"Sword did not break");check(cow.getHealth()<cow.getMaxHealth(),"Sword hit failed");f.s.military.putLong("attackAt",0);f.s.military.putLong("retreatUntil",0);cow.invulnerableTime=0;Military.bind(f.s,f.v);Military.combat(f.l,f.s,f.v,cow);check(f.v.getMainHandItem().isEmpty(),"No punch fallback");f.s.equipment.setItem(0,new ItemStack(Items.IRON_SWORD));Military.bind(f.s,f.v);check(f.v.getMainHandItem().is(Items.IRON_SWORD),"Replacement weapon");h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void bow_ammo_durability_and_no_free_shot(GameTestHelper h){var f=fixture(h);var z=mob(f,EntityType.ZOMBIE,5);var bow=new ItemStack(Items.BOW);f.s.equipment.setItem(0,bow);f.s.setItem(0,new ItemStack(Items.ARROW,2));Military.bind(f.s,f.v);f.s.military.putLong("drawAt",f.l.getGameTime()-21);Military.combat(f.l,f.s,f.v,z);check(f.s.getItem(0).getCount()==1&&bow.getDamageValue()==1,"Bow ammo/wear");check(!f.l.getEntitiesOfClass(AbstractArrow.class,f.v.getBoundingBox().inflate(3)).isEmpty(),"No projectile");h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void crossbow_quick_charge_multishot_consumes_one_arrow(GameTestHelper h){var f=fixture(h);var z=mob(f,EntityType.ZOMBIE,5);var bow=new ItemStack(Items.CROSSBOW);bow.enchant(Enchantments.MULTISHOT,1);bow.enchant(Enchantments.QUICK_CHARGE,3);f.s.equipment.setItem(0,bow);f.s.setItem(0,new ItemStack(Items.ARROW,2));Military.bind(f.s,f.v);f.s.military.putLong("drawAt",f.l.getGameTime()-11);Military.combat(f.l,f.s,f.v,z);check(f.s.getItem(0).getCount()==1&&bow.getDamageValue()==3,"Crossbow ammo/wear");check(f.l.getEntitiesOfClass(AbstractArrow.class,f.v.getBoundingBox().inflate(3)).size()==3,"Multishot projectiles");h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void armor_loses_vanilla_durability_and_enchantments_remain(GameTestHelper h){var f=fixture(h);var armor=new ItemStack(Items.IRON_CHESTPLATE);armor.enchant(Enchantments.ALL_DAMAGE_PROTECTION,2);f.s.equipment.setItem(3,armor);Military.bind(f.s,f.v);f.v.hurt(DamageSource.mobAttack(mob(f,EntityType.ZOMBIE,2)),4);check(armor.getDamageValue()==1&&EnchantmentHelper.getItemEnchantmentLevel(Enchantments.ALL_DAMAGE_PROTECTION,armor)==2,"Armor wear/enchantment damage="+armor.getDamageValue());h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void cumulative_five_levels_health_and_fractional_xp(GameTestHelper h){var f=fixture(h);for(int i=0;i<5;i++){long xp=i*75000L;WorkforceData.get(f.l).setProgress(f.v.getUUID(),Profession.SOLDIER.id(),Military.level(xp),xp);Military.stats(f.l,f.v);check(Military.level(xp)==i+1&&f.v.getMaxHealth()==10+2*i,"Level health "+i);}check(Military.level(74999)==1&&Military.level(299999)==4&&Military.level(300000)==5,"Cumulative thresholds");h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void xp_final_soldier_full_other_ten_percent_player_final_half(GameTestHelper h){var f=fixture(h);var z=mob(f,EntityType.ZOMBIE,2);var other=UUID.randomUUID();Military.contribute(f.l,z,f.v.getUUID());Military.contribute(f.l,z,other);int reward=z.getExperienceReward();Military.award(f.l,z,f.v);check(WorkforceData.get(f.l).progress(f.v.getUUID(),Profession.SOLDIER.id()).xp()==reward*1000L,"Final soldier XP");check(WorkforceData.get(f.l).progress(other,Profession.SOLDIER.id()).xp()==reward*100L,"Other participant XP");Military.contribute(f.l,z,f.v.getUUID());Military.award(f.l,z,f.player);check(WorkforceData.get(f.l).progress(f.v.getUUID(),Profession.SOLDIER.id()).xp()==reward*1500L,"Player final half");h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void expired_contribution_and_environmental_kill_give_no_xp(GameTestHelper h){var f=fixture(h);var z=mob(f,EntityType.ZOMBIE,2);Military.contribute(f.l,z,f.v.getUUID());var c=z.getPersistentData().getCompound("KCMilitaryContributors");c.putLong(f.v.getUUID().toString(),f.l.getGameTime()-MilitaryModule.CONTRIBUTION_TICKS.get()-1);Military.award(f.l,z,f.player);check(WorkforceData.get(f.l).progress(f.v.getUUID(),Profession.SOLDIER.id()).xp()==0,"Expired XP");Military.contribute(f.l,z,f.v.getUUID());Military.award(f.l,z,null);check(WorkforceData.get(f.l).progress(f.v.getUUID(),Profession.SOLDIER.id()).xp()==0,"Environmental invented XP");h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void zombie_death_intercept_preserves_uuid_gear_home_and_progress(GameTestHelper h){var f=fixture(h);var zombie=mob(f,EntityType.ZOMBIE,1);f.s.equipment.setItem(0,new ItemStack(Items.DIAMOND_SWORD));WorkforceData.get(f.l).setProgress(f.v.getUUID(),Profession.SOLDIER.id(),3,150000);Military.bind(f.s,f.v);f.v.hurt(DamageSource.mobAttack(zombie),1000);var z=f.l.getEntity(f.v.getUUID());check(z instanceof SoldierZombie&&f.s.worker.equals(f.v.getUUID())&&f.s.military.getBoolean("zombie"),"Transformation identity/job");check(f.s.equipment.getItem(0).is(Items.DIAMOND_SWORD)&&ResidenceData.get(f.l).owner(f.v.getUUID())!=null&&((SoldierZombie)z).getMaxHealth()==14,"Transformation data");h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void vanilla_weakness_apple_countdown_and_cure_restore_same_soldier(GameTestHelper h){var f=fixture(h);f.s.equipment.setItem(0,new ItemStack(Items.IRON_SWORD));var z=Military.zombify(f.l,f.v);z.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,200));f.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.GOLDEN_APPLE,2));z.mobInteract(f.player,InteractionHand.MAIN_HAND);check(z.isConverting()&&f.player.getMainHandItem().getCount()==1,"Vanilla cure start/payment");var tag=z.saveWithoutId(new CompoundTag());tag.putInt("ConversionTime",1);z.readAdditionalSaveData(tag);z.tick();check(f.l.getEntity(f.v.getUUID()) instanceof Villager&&f.s.worker.equals(f.v.getUUID())&&!f.s.military.getBoolean("zombie"),"Vanilla cure completion");check(f.s.equipment.getItem(0).is(Items.IRON_SWORD),"Cure lost equipment");h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void zombie_final_death_drops_once_and_releases_home(GameTestHelper h){var f=fixture(h);f.s.equipment.setItem(0,new ItemStack(Items.IRON_SWORD));var z=Military.zombify(f.l,f.v);z.hurt(DamageSource.GENERIC,1000);check(f.s.worker==null&&f.s.equipment.isEmpty()&&ResidenceData.get(f.l).owner(f.v.getUUID())==null,"Final death links/items");long n=f.l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,z.getBoundingBox().inflate(3),e->e.getItem().is(Items.IRON_SWORD)).stream().mapToLong(e->e.getItem().getCount()).sum();check(n==1,"Final drop duplicate/loss "+n);h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void station_schema_one_preserves_gear_military_modes_xp_and_old_slots(GameTestHelper h){var f=fixture(h);f.s.equipment.setItem(2,new ItemStack(Items.DIAMOND_HELMET));f.s.setItem(0,new ItemStack(Items.BREAD,7));f.s.military.putString("mode","GUARD");Military.select(f.s,EntityType.COW,true);var tag=EmploymentData.get(f.l).save(new CompoundTag());var restored=EmploymentData.load(tag).station(f.s.id);check(tag.getInt("schema")==1&&restored.getContainerSize()==32&&restored.getItem(0).getCount()==7&&restored.equipment.getItem(2).is(Items.DIAMOND_HELMET)&&Military.mode(restored)==Military.Mode.GUARD&&Military.selected(restored,EntityType.COW),"Military additive save");h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void break_recoverable_token_retains_one_inventory_and_progress(GameTestHelper h){var f=fixture(h);f.s.equipment.setItem(0,new ItemStack(Items.IRON_SWORD));f.s.setItem(0,new ItemStack(Items.ARROW,12));WorkforceData.get(f.l).setProgress(f.v.getUUID(),Profession.SOLDIER.id(),2,75000);f.l.removeBlock(f.p,false);check(!f.s.active&&f.s.worker==null&&f.v.getMainHandItem().isEmpty()&&f.s.equipment.getItem(0).is(Items.IRON_SWORD)&&f.s.getItem(0).getCount()==12,"Break authority");check(WorkforceData.get(f.l).progress(f.v.getUUID(),Profession.SOLDIER.id()).xp()==75000,"Break XP");var tokens=f.l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(f.p).inflate(2),e->e.getItem().hasTag()&&e.getItem().getTag().hasUUID("KraftCityStation"));check(tokens.size()==1,"One recovery token");h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void shifts_sleeping_soldier_never_fights(GameTestHelper h){var f=fixture(h);var z=mob(f,EntityType.ZOMBIE,1);f.l.setDayTime(14000);check(!Military.onDuty(f.l,f.s)&&!Military.authorized(f.l,f.s,f.v,z),"Day soldier at night");f.s.military.putString("shift","NIGHT");check(Military.onDuty(f.l,f.s),"Night duty");f.l.setDayTime(1000);check(!Military.onDuty(f.l,f.s)&&!Military.authorized(f.l,f.s,f.v,z),"Night soldier daytime");h.succeed();}

    @GameTest(template="empty",timeoutTicks=80,batch="military_shield") public static void shield_blocks_and_wears_vanilla_amount(GameTestHelper h){var f=fixture(h);var z=mob(f,EntityType.ZOMBIE,5);var shield=new ItemStack(Items.SHIELD);f.s.equipment.setItem(1,shield);Military.bind(f.s,f.v);f.s.military.putUUID("target",z.getUUID());f.v.setYRot(Direction.EAST.toYRot());f.v.startUsingItem(InteractionHand.OFF_HAND);for(int i=0;i<6;i++)f.v.tick();float before=f.v.getHealth();f.v.setYRot(Direction.EAST.toYRot());f.v.hurt(DamageSource.mobAttack(z),4);check(f.v.getHealth()==before&&shield.getDamageValue()==5,"Shield block/wear hp="+f.v.getHealth()+" damage="+shield.getDamageValue()+" blocking="+f.v.isBlocking()+" usingTicks="+f.v.getTicksUsingItem());h.succeed();}
    @GameTest(template="empty",timeoutTicks=100,batch="military_sleep") public static void residential_bed_day_sleep_night_shift_no_threat_wake_and_double_healing(GameTestHelper h){var f=fixture(h);f.s.military.putString("shift","NIGHT");var bed=f.home.east();f.l.setBlock(bed,Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.HEAD).setValue(BedBlock.FACING,Direction.EAST),3);f.v.setPos(bed.getX()+.5,bed.getY(),bed.getZ()+.5);f.v.setHealth(5);f.v.tickCount=100;Military.tick(f.l,f.s,f.v);check(f.v.isSleeping()&&f.v.getHealth()==6,"Daytime bed sleep/heal twice rate");var z=mob(f,EntityType.ZOMBIE,1);check(!Military.authorized(f.l,f.s,f.v,z),"Sleeping soldier woke for target");check(ResidenceData.get(f.l).bedClaim(f.v.getUUID())!=null,"Bed claim lost");f.l.setDayTime(14000);Military.tick(f.l,f.s,f.v);check(!f.v.isSleeping(),"Night shift did not wake for duty");f.l.setDayTime(1000);h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void zombie_block_break_cannot_duplicate_or_rehire_until_cured(GameTestHelper h){var f=fixture(h);f.s.equipment.setItem(0,new ItemStack(Items.IRON_SWORD));var z=Military.zombify(f.l,f.v);f.l.removeBlock(f.p,false);check(f.s.worker==null&&!f.s.active&&f.s.equipment.getItem(0).is(Items.IRON_SWORD)&&z.getMainHandItem().isEmpty(),"Detached zombie retained equipment clone");check(Military.station(z)==f.s,"Suspended transformation lost identity");var cured=Military.cure(f.l,z);check(cured!=null&&cured.getMainHandItem().isEmpty()&&!f.s.military.getBoolean("zombie")&&f.s.worker==null,"Cure auto-employed detached soldier");h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void final_zombie_death_respects_vanishing(GameTestHelper h){var f=fixture(h);var sword=new ItemStack(Items.IRON_SWORD);sword.enchant(Enchantments.VANISHING_CURSE,1);f.s.equipment.setItem(0,sword);var z=Military.zombify(f.l,f.v);z.hurt(DamageSource.GENERIC,1000);check(f.s.equipment.isEmpty()&&f.l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,z.getBoundingBox().inflate(2),e->e.getItem().is(Items.IRON_SWORD)).isEmpty(),"Vanishing curse duplicated drop");h.succeed();}

    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void block_worker_and_reconnected_menus_share_one_gear_inventory(GameTestHelper h){var f=fixture(h);var a=new SoldierMenu(1,f.player.getInventory(),f.s,null);var b=new SoldierMenu(2,f.player.getInventory(),f.s,f.v.getUUID());a.equipment.setItem(0,new ItemStack(Items.IRON_SWORD));a.inventory.setItem(1,new ItemStack(Items.ARROW,9));var c=new SoldierMenu(3,f.player.getInventory(),f.s,null);check(a.inventory==b.inventory&&b.inventory==c.inventory&&a.equipment==b.equipment&&b.equipment==c.equipment&&c.equipment.getItem(0).is(Items.IRON_SWORD)&&c.inventory.getItem(1).getCount()==9,"Military menu duplication");check(a.slots.size()==74&&a.slots.get(31).mayPlace(new ItemStack(Items.EMERALD))&&!a.slots.get(31).mayPlace(new ItemStack(Items.DIAMOND)),"Military slots/payment");h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void housing_loss_rehire_retains_soldier_level_and_block_equipment(GameTestHelper h){var f=fixture(h);WorkforceData.get(f.l).setProgress(f.v.getUUID(),Profession.SOLDIER.id(),3,150000);f.s.equipment.setItem(0,new ItemStack(Items.IRON_SWORD));Military.bind(f.s,f.v);ResidenceData.get(f.l).unlink(f.l,f.v.getUUID());check(f.s.worker==null&&f.v.getMainHandItem().isEmpty()&&f.s.equipment.getItem(0).is(Items.IRON_SWORD),"Housing loss gear authority");check(ResidenceData.get(f.l).link(f.l,f.home,f.v).equals("ok")&&f.s.worker==null,"Automatic employment after rehouse");check(Employment.hire(f.player,f.s,f.v.getUUID(),Profession.SOLDIER).equals("ok")&&f.s.getItem(31).getCount()==14&&f.v.getMaxHealth()==14&&WorkforceData.get(f.l).progress(f.v.getUUID(),Profession.SOLDIER.id()).xp()==150000,"Rehire progression/payment");h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void follow_remembers_multiple_player_hits_but_ignores_unselected_peaceful_mobs(GameTestHelper h){var f=fixture(h);mode(f,Military.Mode.FOLLOW);f.l.addNewPlayer(f.player);var a=mob(f,EntityType.COW,2);var b=mob(f,EntityType.COW,3);var c=mob(f,EntityType.COW,4);a.hurt(DamageSource.playerAttack(f.player),1);b.hurt(DamageSource.playerAttack(f.player),1);check(Military.authorized(f.l,f.s,f.v,a)&&Military.authorized(f.l,f.s,f.v,b)&&!Military.authorized(f.l,f.s,f.v,c),"Follow target memory");h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void final_death_obeys_disabled_mob_loot_without_items_or_xp(GameTestHelper h){var f=fixture(h);boolean previous=f.l.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);try{f.l.getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(false,f.l.getServer());f.s.equipment.setItem(0,new ItemStack(Items.IRON_SWORD));var z=Military.zombify(f.l,f.v);z.hurt(DamageSource.GENERIC,1000);check(f.s.equipment.isEmpty()&&f.l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,z.getBoundingBox().inflate(2),e->e.getItem().is(Items.IRON_SWORD)).isEmpty(),"Mob loot rule ignored");}finally{f.l.getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(previous,f.l.getServer());}h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void bow_cannot_shoot_through_wall_or_consume_ammo_without_los(GameTestHelper h){var f=fixture(h);var z=mob(f,EntityType.ZOMBIE,5);f.s.equipment.setItem(0,new ItemStack(Items.BOW));f.s.setItem(0,new ItemStack(Items.ARROW,2));Military.bind(f.s,f.v);for(int y=0;y<4;y++)for(int dz=-1;dz<=1;dz++)f.l.setBlock(f.p.offset(2,y,dz),Blocks.STONE.defaultBlockState(),3);check(!f.v.hasLineOfSight(z),"Wall fixture did not block LOS");f.s.military.putLong("drawAt",f.l.getGameTime()-21);Military.combat(f.l,f.s,f.v,z);check(f.s.getItem(0).getCount()==2&&f.s.equipment.getItem(0).getDamageValue()==0&&!f.s.military.contains("drawAt"),"Arrow crossed wall or consumed ammo");h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void zombie_reload_rebinds_ledger_and_preserves_curing_countdown(GameTestHelper h){var f=fixture(h);f.s.equipment.setItem(0,new ItemStack(Items.IRON_SWORD));var z=Military.zombify(f.l,f.v);z.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,200));f.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.GOLDEN_APPLE));z.mobInteract(f.player,InteractionHand.MAIN_HAND);var saved=z.saveWithoutId(new CompoundTag());z.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);var loaded=MilitaryModule.ZOMBIE.get().create(f.l);loaded.load(saved);f.l.addFreshEntity(loaded);check(loaded.isConverting()&&loaded.getUUID().equals(f.v.getUUID())&&loaded.getMainHandItem()==f.s.equipment.getItem(0)&&f.s.worker.equals(loaded.getUUID()),"Reloaded zombie lost cure/ledger/identity");loaded.setNoAi(true);h.succeed();}

    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void charged_crossbow_fires_saved_projectile_once_without_new_ammo(GameTestHelper h){var f=fixture(h);var target=mob(f,EntityType.ZOMBIE,5);var crossbow=new ItemStack(Items.CROSSBOW);var arrows=new ListTag();arrows.add(new ItemStack(Items.ARROW).save(new CompoundTag()));crossbow.getOrCreateTag().put("ChargedProjectiles",arrows);CrossbowItem.setCharged(crossbow,true);f.s.equipment.setItem(0,crossbow);Military.bind(f.s,f.v);Military.combat(f.l,f.s,f.v,target);check(!CrossbowItem.isCharged(crossbow)&&crossbow.getTag().getList("ChargedProjectiles",Tag.TAG_COMPOUND).isEmpty()&&crossbow.getDamageValue()==1,"Loaded projectile was retained or not shot");long shots=f.l.getEntitiesOfClass(AbstractArrow.class,f.v.getBoundingBox().inflate(3),a->a.getOwner()==f.v).size();Military.combat(f.l,f.s,f.v,target);check(shots==1&&f.l.getEntitiesOfClass(AbstractArrow.class,f.v.getBoundingBox().inflate(3),a->a.getOwner()==f.v).size()==1,"Saved projectile fired twice");h.succeed();}
    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void exhausted_zombie_helmet_cannot_be_rebound_from_ledger(GameTestHelper h){var f=fixture(h);var helmet=new ItemStack(Items.IRON_HELMET);f.s.equipment.setItem(2,helmet);var z=Military.zombify(f.l,f.v);helmet.setDamageValue(helmet.getMaxDamage());z.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);Military.bind(f.s,z);check(f.s.equipment.getItem(2).isEmpty()&&z.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),"Broken helmet resurrected");h.succeed();}

    @GameTest(template="empty",timeoutTicks=80,batch="military") public static void isolated_soldier_does_not_create_civilian_protection_radius(GameTestHelper h){var f=fixture(h);var remote=new BlockPos(-4096,-50,-4096);f.l.getChunkAt(remote);f.l.setBlock(remote,WorkModule.PROFESSIONAL.get(Profession.SOLDIER).get().defaultBlockState(),3);((WorkBlockEntity)f.l.getBlockEntity(remote)).bind(f.s.id);f.s.pos=remote;f.s.setChanged();f.v.setPos(remote.getX()+.5,remote.getY()+1,remote.getZ()+.5);var z=EntityType.ZOMBIE.create(f.l);z.setNoAi(true);z.setPos(remote.getX()+2.5,remote.getY()+1,remote.getZ()+.5);f.l.addFreshEntity(z);check(!Military.protectedArea(f.l,z)&&Military.authorized(f.l,f.s,f.v,z),"Nearby hostile not authorized independently of residential protection");f.v.setLastHurtByMob(z);check(Military.authorized(f.l,f.s,f.v,z),"Selected immediate self-defense blocked");mode(f,Military.Mode.FOLLOW);check(Military.authorized(f.l,f.s,f.v,z),"Follow blocked self-defense");Military.select(f.s,EntityType.ZOMBIE,false);h.succeed();}

    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void explicit_mod_mob_selection_can_authorize_hostile_soldier_zombie(GameTestHelper h){var f=fixture(h);var other=EntityType.VILLAGER.create(f.l);other.setNoAi(true);other.setAge(0);other.setPos(f.p.getX()-2,f.p.getY()+1,f.p.getZ()+.5);f.l.addFreshEntity(other);check(ResidenceData.get(f.l).link(f.l,f.home,other).equals("ok"),"Second military resident");var post=f.p.west(3);f.l.setBlock(post,WorkModule.MILITARY.get().defaultBlockState(),3);var s=((WorkBlockEntity)f.l.getBlockEntity(post)).station();s.setItem(31,new ItemStack(Items.EMERALD,15));check(Employment.hire(f.player,s,other.getUUID(),Profession.SOLDIER).equals("ok"),"Second military hire");var z=Military.zombify(f.l,other);check(!Military.selected(f.s,z.getType())&&Military.authorized(f.l,f.s,f.v,z),"Naturally hostile mod creature required blacklist");Military.select(f.s,MilitaryModule.ZOMBIE.get(),true);check(Military.authorized(f.l,f.s,f.v,z),"Selected hostile mod creature remained immune");z.setNoAi(true);h.succeed();}

    @GameTest(template="empty",timeoutTicks=80,batch="military") public static void follow_and_worker_menu_do_not_force_load_distant_post_chunk(GameTestHelper h){var f=fixture(h);var cold=new BlockPos(16000000,60,16000000);check(!f.l.hasChunkAt(cold),"Cold chunk fixture already loaded");f.s.pos=cold;mode(f,Military.Mode.FOLLOW);f.l.addNewPlayer(f.player);f.player.setPos(f.v.getX()+4,f.v.getY(),f.v.getZ());var viaWorker=new SoldierMenu(1,f.player.getInventory(),f.s,f.v.getUUID());var viaBlock=new SoldierMenu(2,f.player.getInventory(),f.s,null);check(viaWorker.stillValid(f.player)&&!viaBlock.stillValid(f.player)&&!f.l.hasChunkAt(cold),"Remote menu forced chunk or denied worker");f.v.setOnGround(true);Military.tick(f.l,f.s,f.v);check(!f.l.hasChunkAt(cold)&&f.s.worker.equals(f.v.getUUID())&&f.v.getNavigation().isDone(),"Follow ignored distant post bounds or forced its chunk");h.succeed();}

    @GameTest(template="empty",timeoutTicks=40,batch="military") public static void removed_zombie_without_death_clears_ghost_contract_and_keeps_post_items(GameTestHelper h){var f=fixture(h);f.s.equipment.setItem(0,new ItemStack(Items.IRON_SWORD));var z=Military.zombify(f.l,f.v);z.discard();check(f.s.worker==null&&!f.s.military.getBoolean("zombie")&&f.s.equipment.getItem(0).is(Items.IRON_SWORD)&&ResidenceData.get(f.l).owner(f.v.getUUID())==null,"Discard left ghost or lost items");h.succeed();}

    @GameTest(template="empty",batch="military_inventory")
    public static void right_click_equips_by_slot_and_retains_item_data(GameTestHelper h){
        var f=fixture(h);
        var menu=new SoldierMenu(1,f.player.getInventory(),f.s,null);
        Item[] gear={br.com.kraftcity.industry.IndustrialTools.SWORD.get(),Items.SHIELD,Items.IRON_HELMET,
            Items.IRON_CHESTPLATE,Items.IRON_LEGGINGS,Items.IRON_BOOTS};
        for(int i=0;i<gear.length;i++){
            var item=new ItemStack(gear[i]);
            item.setDamageValue(3);
            item.getOrCreateTag().putInt("MilitaryTest",i);
            menu.getSlot(SoldierMenu.PLAYER_START).set(item);
            menu.clicked(SoldierMenu.PLAYER_START,1,net.minecraft.world.inventory.ClickType.PICKUP,f.player);
            check(!menu.getSlot(SoldierMenu.PLAYER_START).hasItem()&&f.s.equipment.getItem(i).is(gear[i])
                &&f.s.equipment.getItem(i).getDamageValue()==3&&f.s.equipment.getItem(i).getTag().getInt("MilitaryTest")==i,
                "Invalid quick equip or changed equipment data at "+i);
        }
        menu.getSlot(SoldierMenu.PLAYER_START).set(new ItemStack(Items.BOW));
        menu.clicked(SoldierMenu.PLAYER_START,1,net.minecraft.world.inventory.ClickType.PICKUP,f.player);
        check(menu.getSlot(SoldierMenu.PLAYER_START).getItem().is(Items.BOW)&&f.s.equipment.getItem(0).is(gear[0]),"Occupied destination overwritten");
        check(menu.slots.stream().filter(slot->slot.container==f.s&&slot.getContainerSlot()<31&&slot.isActive()).count()==9,
            "Military inventory must show nine storage slots");
        h.succeed();
    }

    @GameTest(template="empty",batch="military_inventory")
    public static void military_boundary_is_inclusive_and_stops_boundary_movement(GameTestHelper h){
        var f=fixture(h);
        var edge=Vec3.atBottomCenterOf(f.p.offset(100,10,100));
        check(Military.insideArea(f.s,edge)&&!Military.insideArea(f.s,edge.add(1,0,0))
            &&!Military.insideArea(f.s,edge.add(0,1,0))&&!Military.insideArea(f.s,edge.add(0,0,1)),"Military area bounds");
        f.v.setPos(edge);
        f.v.move(MoverType.SELF,new Vec3(1,1,1));
        check(Military.insideArea(f.s,f.v.position()),"Movement escaped military area");
        h.succeed();
    }

    @GameTest(template="empty",batch="military_inventory")
    public static void patrol_collects_partially_and_never_uses_other_slots(GameTestHelper h){
        var f=fixture(h);
        for(int i=0;i<9;i++)f.s.setItem(i,new ItemStack(Items.STONE,64));
        f.s.setItem(0,new ItemStack(Items.BREAD,63));
        var item=new net.minecraft.world.entity.item.ItemEntity(f.l,f.v.getX()+.5,f.v.getY(),f.v.getZ(),new ItemStack(Items.BREAD,5));
        f.l.addFreshEntity(item);
        Military.tick(f.l,f.s,f.v);
        check(f.s.getItem(0).getCount()==64&&item.isAlive()&&item.getItem().getCount()==4&&f.s.getItem(9).isEmpty(),
            "Pickup did not preserve the remainder in a full inventory");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=900,batch="military_live")
    public static void patrol_collects_walks_and_returns_to_guard_in_thunder(GameTestHelper h){
        var f=fixture(h);
        for(var pos:BlockPos.betweenClosed(f.p.offset(-8,0,-8),f.p.offset(12,0,8))){
            if(!pos.equals(f.p)&&!pos.equals(f.home))f.l.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
            f.l.setBlockAndUpdate(pos.above(7),Blocks.STONE.defaultBlockState());
        }
        clearTestHostiles(f);
        var resident=mob(f,EntityType.VILLAGER,-5);
        check("ok".equals(ResidenceData.get(f.l).link(f.l,f.home,resident)),"Resident for patrol scenario");
        var farmer=mob(f,EntityType.VILLAGER,4);
        farmer.setPos(Vec3.atBottomCenterOf(f.p.offset(4,1,2)));
        check("ok".equals(ResidenceData.get(f.l).link(f.l,f.home,farmer)),"Farmer residence for patrol scenario");
        var farmerPos=f.p.offset(6,0,3);
        f.l.setBlockAndUpdate(farmerPos,WorkModule.INDUSTRIAL.get().defaultBlockState());
        var farmerStation=((WorkBlockEntity)f.l.getBlockEntity(farmerPos)).station();
        farmerStation.setItem(31,new ItemStack(Items.EMERALD,32));
        check("ok".equals(Employment.hire(f.player,farmerStation,farmer.getUUID(),Profession.FARMER)),"Hire nearby farmer");
        boolean naturalSpawns=f.l.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
        f.l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,f.l.getServer());
        f.l.setWeatherParameters(0,10000,true,true);
        f.l.setRainLevel(1);
        f.l.setThunderLevel(1);
        f.s.equipment.setItem(0,new ItemStack(Items.DIAMOND_SWORD));
        f.v.setNoAi(false);
        var item=new net.minecraft.world.entity.item.ItemEntity(f.l,f.v.getX()+4,f.v.getY(),f.v.getZ(),new ItemStack(Items.BREAD,3));
        f.l.addFreshEntity(item);
        h.startSequence().thenWaitUntil(()->check(!item.isAlive()&&f.s.getItem(0).getCount()==3,
            "Patrol did not physically reach the item under thunder, soldier="+f.v.position()+" alive="+f.v.isAlive()
                +" path="+f.v.getNavigation().getTargetPos()+" item="+item.position()+" stored="+f.s.getItem(0)))
            .thenExecute(()->{
                mode(f,Military.Mode.GUARD);
                Military.clearTask(f.v,f.s);
            })
            .thenWaitUntil(()->check(f.v.position().distanceToSqr(Vec3.atBottomCenterOf(f.p.above()))<1e-8,
                "Returned soldier did not center on post"))
            .thenExecute(()->{
                var threat=mob(f,EntityType.ZOMBIE,4);
                threat.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,1000));
                threat.setHealth(2);
                check(threat.doHurtTarget(farmer),"Zombie did not actually attack the farmer");
                f.s.military.putUUID("visual_test_threat",threat.getUUID());
            })
            .thenWaitUntil(()->{
                var threat=f.l.getEntity(f.s.military.getUUID("visual_test_threat"));
                check((threat==null||!threat.isAlive())&&f.s.equipment.getItem(0).getDamageValue()>0,
                    "Guard did not kill the hostile during thunder: soldier="+f.v.position()+" path="+f.v.getNavigation().getTargetPos()
                        +" target="+threat+" selected="+f.s.military.getString("target")+" durability="+f.s.equipment.getItem(0).getDamageValue()
                        +" onGround="+f.v.isOnGround()+" duty="+Military.onDuty(f.l,f.s)
                        +" authorized="+(threat instanceof LivingEntity living&&Military.authorized(f.l,f.s,f.v,living))
                        +" visible="+(threat!=null&&f.v.hasLineOfSight(threat)));
            })
            .thenWaitUntil(()->check(f.v.position().distanceToSqr(Vec3.atBottomCenterOf(f.p.above()))<1e-8,
                "Guard did not return after combat"))
            .thenExecute(()->{
                mode(f,Military.Mode.FOLLOW);
                Military.clearTask(f.v,f.s);
                f.player.setPos(Vec3.atBottomCenterOf(f.p.east(11).above()));
                f.l.addNewPlayer(f.player);
            })
            .thenWaitUntil(()->check(f.v.distanceToSqr(f.player)<=49,"Follow did not close to seven blocks"))
            .thenExecute(()->{
                var target=mob(f,EntityType.COW,8);
                target.setHealth(2);
                Military.select(f.s,EntityType.COW,true);
                f.s.military.putUUID("visual_test_threat",target.getUUID());
            })
            .thenWaitUntil(()->{
                var target=f.l.getEntity(f.s.military.getUUID("visual_test_threat"));
                check(target==null||!target.isAlive(),"Follow did not defend player against an additional enemy");
            })
            .thenExecute(()->f.player.setPos(Vec3.atBottomCenterOf(f.p.east(101).above())))
            .thenExecuteFor(20,()->check(Military.insideArea(f.s,f.v.position()),"Follow left area for player"))
            .thenExecute(()->{
                f.l.setWeatherParameters(10000,0,false,false);
                f.l.setRainLevel(0);
                f.l.setThunderLevel(0);
                f.s.military.remove("visual_test_threat");
                f.l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(naturalSpawns,f.l.getServer());
                farmer.discard();
                resident.discard();
            })
            .thenSucceed();
    }

    @GameTest(template="empty",batch="military_guard_idle")
    public static void patrol_prioritizes_a_resident_attacker_over_self_defense(GameTestHelper h){
        var f=fixture(h);
        var resident=mob(f,EntityType.VILLAGER,4);
        resident.setPos(Vec3.atBottomCenterOf(f.home.east()));
        check(ResidenceData.get(f.l).link(f.l,f.home,resident).equals("ok"),"Link victim");
        resident.setPos(Vec3.atBottomCenterOf(f.p.east(4).above()));
        var soldierAttacker=mob(f,EntityType.COW,2);
        var residentAttacker=mob(f,EntityType.COW,4);
        f.l.setBlockAndUpdate(f.p.east(4),Blocks.STONE.defaultBlockState());
        f.v.setOnGround(true);
        f.v.setLastHurtByMob(soldierAttacker);
        resident.setLastHurtByMob(residentAttacker);
        check(Military.target(f.l,f.s,f.v)==residentAttacker,"Resident defense priority");
        mode(f,Military.Mode.FOLLOW);
        f.l.addNewPlayer(f.player);
        check(!Military.authorized(f.l,f.s,f.v,residentAttacker)&&Military.authorized(f.l,f.s,f.v,soldierAttacker),
            "Follow protected a resident or ignored self-defense");
        h.succeed();
    }

    @GameTest(template="empty",batch="military_inventory")
    public static void ranged_equipment_uses_main_hand_and_respects_charge_time(GameTestHelper h){
        var f=fixture(h);
        var target=mob(f,EntityType.ZOMBIE,4);
        for(var item:new Item[]{Items.BOW,Items.CROSSBOW}){
            f.s.equipment.setItem(0,new ItemStack(item));
            f.s.setItem(0,new ItemStack(Items.ARROW,3));
            Military.bind(f.s,f.v);
            Military.combat(f.l,f.s,f.v,target);
            check(f.v.isUsingItem()&&f.v.getUsedItemHand()==InteractionHand.MAIN_HAND,
                "Ranged soldier did not raise the main-hand weapon");
            for(int tick=0;tick<4;tick++)Military.combat(f.l,f.s,f.v,target);
            check(Math.abs(net.minecraft.util.Mth.wrapDegrees(f.v.yBodyRot+90))<1,"Ranged stance did not face its target");
            check(f.s.getItem(0).getCount()==3,"Ranged weapon fired before charging");
            Military.clearTask(f.v,f.s);
        }
        h.succeed();
    }
}
