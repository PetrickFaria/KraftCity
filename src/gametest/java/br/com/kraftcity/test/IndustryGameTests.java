package br.com.kraftcity.test;

import br.com.kraftcity.employment.*;
import br.com.kraftcity.industry.*;
import br.com.kraftcity.workforce.*;
import br.com.residenciasaldeoes.ResidenciasDosAldeoes;
import br.com.residenciasaldeoes.data.ResidenceData;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder("residenciasaldeoes")
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class IndustryGameTests {
    private record F(ServerLevel l,EmploymentData.Station s,Villager v,BlockPos p){}
    private static void check(boolean v,String m){if(!v)throw new GameTestAssertException(m);}
    private static F fixture(GameTestHelper h,Profession profession){
        var l=h.getLevel();l.setDayTime(1000);l.setWeatherParameters(10000,0,false,false);
        BlockPos home=h.absolutePos(new BlockPos(2,2,2)),p=h.absolutePos(new BlockPos(8,2,8));
        l.setBlock(home,ResidenciasDosAldeoes.ANCHOR.get().defaultBlockState(),3);
        ((br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity)l.getBlockEntity(home)).setSize(11);
        l.setBlock(p.below(),Blocks.STONE.defaultBlockState(),3);l.setBlock(p,WorkModule.INDUSTRIAL.get().defaultBlockState(),3);
        var v=h.spawn(EntityType.VILLAGER,new BlockPos(3,2,2));v.setNoAi(true);v.setAge(0);
        check(ResidenceData.get(l).link(l,home,v).equals("ok"),"residence link");v.setPos(p.getX()+.5,p.getY(),p.getZ()+1.5);
        var player=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"IndustryTest"));player.setPos(p.getX()+.5,p.getY(),p.getZ()+1.5);
        var s=((WorkBlockEntity)l.getBlockEntity(p)).station();s.setItem(31,new ItemStack(Items.EMERALD,10));
        check(Employment.hire(player,s,v.getUUID(),profession).equals("ok"),"hire "+profession);
        return new F(l,s,v,p);
    }
    private static void step(F f,int n){for(int i=0;i<n;i++)IndustrialWork.tick(f.l,f.s);}
    private static int count(F f,Item item){int n=0;for(int i=0;i<27;i++)if(f.s.getItem(i).is(item))n+=f.s.getItem(i).getCount();return n;}
    private static int index(F f,BlockPos p){int size=IndustrialRules.size(f.l,f.s),r=size/2;return ((p.getZ()-f.p.getZ()+r)*size+p.getX()-f.p.getX()+r)*5+p.getY()-f.p.getY()+(f.s.profession==Profession.FARMER?-1:2);}
    private static BlockPos crop(F f,BlockState state){BlockPos p=f.p.east().above();f.l.setBlock(p.below(),Blocks.FARMLAND.defaultBlockState(),3);f.l.setBlock(p,state,3);f.v.setPos(p.getX()+.5,p.getY(),p.getZ()+.5);return p;}
    private static void level(F f,int level,long xp){WorkforceData.get(f.l).setProgress(f.v.getUUID(),f.s.profession.id(),level,xp);}
    private static <T extends Animal>T animal(F f,EntityType<T> type,boolean baby){var a=type.create(f.l);a.setPos(f.p.getX()+1.5,f.p.getY(),f.p.getZ()+.5);a.setNoAi(true);a.setAge(baby?-24000:0);a.getPersistentData().putUUID("KCIndustryStation",f.s.id);f.l.addFreshEntity(a);return a;}
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void farmer_mature_only_replants_same_species_and_progresses_at_25_50_75_100(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);f.s.setItem(27,new ItemStack(Items.NETHERITE_HOE));f.s.setItem(0,new ItemStack(Items.WHEAT_SEEDS,64));
        BlockPos p=crop(f,Blocks.WHEAT.defaultBlockState());step(f,1);check(count(f,Items.WHEAT)==0,"immature crop harvested");
        for(int i=1;i<=100;i++){
            f.l.setBlock(p,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),3);f.s.industry.putInt("cursor",index(f,p));step(f,1);
            check(f.l.getBlockState(p).is(Blocks.WHEAT)&&f.l.getBlockState(p).getValue(CropBlock.AGE)==0,"crop not replanted #"+i);
            var progress=WorkforceData.get(f.l).progress(f.v.getUUID(),Profession.FARMER.id());check(progress.xp()==i,"harvest xp #"+i);
            if(i%25==0)check(progress.level()==Math.min(5,1+i/25),"wrong level threshold #"+i);
        }
        check(IndustrialRules.size(f.l,f.s)==13,"Lv5 area");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void farmer_automatic_inputs_irrigation_and_no_source_water(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);f.s.setItem(0,new ItemStack(Items.IRON_HOE));f.s.setItem(1,new ItemStack(Items.BONE_MEAL,8));f.s.setItem(2,new ItemStack(Items.WATER_BUCKET));
        var p=crop(f,Blocks.WHEAT.defaultBlockState());step(f,1);
        check(f.s.getItem(27).is(Items.IRON_HOE)&&f.s.getItem(28).is(Items.BONE_MEAL)&&f.s.getItem(30).is(Items.WATER_BUCKET),"input migration");
        check(f.s.getItem(28).getCount()==7,"bone meal not consumed exactly once");check(Irrigation.hydrates(f.l,p.below()),"hydration missing");
        level(f,5,100);check(Irrigation.hydrates(f.l,f.p.offset(4,0,4))&&!Irrigation.hydrates(f.l,f.p.offset(5,0,0)),"vanilla four-block water range");
        check(f.l.getFluidState(f.p).isEmpty(),"created water source");
        f.s.setItem(30,ItemStack.EMPTY);check(!Irrigation.hydrates(f.l,p.below()),"ghost irrigation");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void farmer_never_tills_without_seed_and_preserves_full_inventory_harvest(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);f.s.setItem(27,new ItemStack(Items.IRON_HOE));BlockPos p=f.p.east().above();f.l.setBlock(p.below(),Blocks.DIRT.defaultBlockState(),3);step(f,30);
        check(f.l.getBlockState(p.below()).is(Blocks.DIRT),"tilled without plantables");
        p=crop(f,Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE,7));for(int i=0;i<27;i++)f.s.setItem(i,new ItemStack(Items.STONE,64));
        step(f,1);check(f.l.getBlockState(p).getValue(CropBlock.AGE)==7,"lost crop when full");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void butcher_slaughters_oldest_over_capacity_preserving_pair_and_babies(GameTestHelper h){
        var f=fixture(h,Profession.BUTCHER);f.s.setItem(27,new ItemStack(Items.IRON_AXE));
        var oldest=animal(f,EntityType.COW,false);oldest.getPersistentData().putLong("KCIndustryBorn",-1000000);
        animal(f,EntityType.COW,false);animal(f,EntityType.COW,false);var baby=animal(f,EntityType.COW,true);
        step(f,1);check(!oldest.isAlive()&&baby.isAlive(),"did not slaughter oldest adult");
        check(f.l.getEntitiesOfClass(Cow.class,IndustrialWork.area(f.l,f.s),a->!a.isBaby()&&a.isAlive()).size()==2,"lost breeding pair");
        check(count(f,Items.BEEF)>=1&&count(f,Items.BEEF)<=2,"axe tier meat yield");
        step(f,1);check(baby.isAlive(),"baby slaughtered");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void butcher_ignores_unregistered_animals_and_species_without_pair(GameTestHelper h){
        var f=fixture(h,Profession.BUTCHER);f.s.setItem(27,new ItemStack(Items.IRON_AXE));
        var cow=animal(f,EntityType.COW,false);var pig=animal(f,EntityType.PIG,false);var chicken=animal(f,EntityType.CHICKEN,false);
        var wild=animal(f,EntityType.COW,false);wild.getPersistentData().remove("KCIndustryStation");
        step(f,2);check(cow.isAlive()&&pig.isAlive()&&chicken.isAlive()&&wild.isAlive(),"unpaired or unregistered animal killed");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void butcher_breeds_pair_with_food_and_milk_respects_one_minute(GameTestHelper h){
        var f=fixture(h,Profession.BUTCHER);f.s.setItem(27,new ItemStack(Items.IRON_AXE));f.s.setItem(28,new ItemStack(Items.WHEAT,2));
        var a=animal(f,EntityType.COW,false);animal(f,EntityType.COW,false);step(f,1);
        check(f.l.getEntitiesOfClass(Cow.class,IndustrialWork.area(f.l,f.s),Cow::isBaby).size()==1&&f.s.getItem(28).isEmpty(),"breeding transaction");
        f.s.setItem(0,new ItemStack(Items.BUCKET,2));step(f,1);check(count(f,Items.MILK_BUCKET)==1,"milk collection");
        // Other cow is also viable; mark it recently milked so the same cow cannot be reused.
        for(var c:f.l.getEntitiesOfClass(Cow.class,IndustrialWork.area(f.l,f.s)))if(!c.isBaby())c.getPersistentData().putLong("KCIndustryMilk",f.l.getGameTime());
        step(f,1);check(count(f,Items.MILK_BUCKET)==1,"milk cooldown bypass");a.getPersistentData().putLong("KCIndustryMilk",f.l.getGameTime()-1200);f.s.industry.putLong("milk",f.l.getGameTime()-1200);step(f,1);check(count(f,Items.MILK_BUCKET)==2,"milk cooldown never ends");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void shepherd_shears_never_kills_and_expansion_only_breeding(GameTestHelper h){
        var f=fixture(h,Profession.SHEPHERD);f.s.setItem(27,new ItemStack(Items.SHEARS));f.s.setItem(28,new ItemStack(Items.WHEAT,4));
        var a=animal(f,EntityType.SHEEP,false);var b=animal(f,EntityType.SHEEP,false);step(f,2);
        check(a.isSheared()&&b.isSheared()&&a.isAlive()&&b.isAlive(),"shepherd killed or failed to shear");step(f,1);
        check(f.s.getItem(28).getCount()==4,"bred at adult capacity");
        level(f,2,25);step(f,1);check(f.l.getEntitiesOfClass(Sheep.class,IndustrialWork.area(f.l,f.s),Sheep::isBaby).size()==1,"expansion failed breeding");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void shepherd_progress_thresholds_and_broken_tool_stop(GameTestHelper h){
        var f=fixture(h,Profession.SHEPHERD);var sheep=animal(f,EntityType.SHEEP,false);f.s.setItem(27,new ItemStack(Items.SHEARS));
        for(int i=1;i<=100;i++){sheep.setSheared(false);step(f,1);var p=WorkforceData.get(f.l).progress(f.v.getUUID(),Profession.SHEPHERD.id());check(p.xp()==i&&p.level()==Math.min(5,1+i/25),"shearing progression #"+i);}
        var tool=new ItemStack(Items.SHEARS);tool.setDamageValue(tool.getMaxDamage()-1);f.s.setItem(27,tool);sheep.setSheared(false);
        step(f,1);sheep.setSheared(false);step(f,1);check(f.s.getItem(27).isEmpty(),"tool did not break");sheep.setSheared(false);step(f,1);check(!sheep.isSheared(),"worked with broken tool");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void fractional_wear_is_exact_for_every_level_and_survives_nbt(GameTestHelper h){
        var f=fixture(h,Profession.SHEPHERD);
        for(int level=1;level<=5;level++){
            level(f,level,0);var tool=new ItemStack(Items.SHEARS);
            for(int i=0;i<3;i++)IndustrialRules.wear(f.l,f.s,tool);
            tool=ItemStack.of(tool.save(new CompoundTag()));IndustrialRules.wear(f.l,f.s,tool);
            check(tool.getDamageValue()==7-level&&tool.getOrCreateTag().getInt("KraftCityWearQuarters")==0,"fractional wear Lv"+level);
        }h.succeed();
    }
    private static BlockPos water(F f){BlockPos c=f.p.offset(0,-2,0);for(BlockPos p:BlockPos.betweenClosed(c.offset(-1,-1,-1),c.offset(1,1,1)))f.l.setBlock(p,Blocks.WATER.defaultBlockState(),3);f.s.industry.putLong("water",c.asLong());return c;}
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void fisher_requires_complete_water_and_completes_real_fish_table(GameTestHelper h){
        var f=fixture(h,Profession.FISHERMAN);f.s.setItem(27,new ItemStack(Items.FISHING_ROD));var c=water(f);
        check(IndustrialWork.validWater(f.l,c),"water 3x3x3 rejected");
        step(f,IndustrialRules.fishingTicks(EmploymentConfig.FISH_TICKS.get(),1)/EmploymentConfig.ACTION_TICKS.get()+1);
        int fish=count(f,Items.COD)+count(f,Items.SALMON)+count(f,Items.PUFFERFISH)+count(f,Items.TROPICAL_FISH);
        check(fish==1,"no actual fish output");f.l.setBlock(c,Blocks.STONE.defaultBlockState(),3);check(!IndustrialWork.validWater(f.l,c),"insufficient water accepted");step(f,1);check(f.s.industry.getInt("fish_elapsed")==0,"phantom fishing progress");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void fisher_relative_durations_are_mathematically_explicit(GameTestHelper h){
        int[] expected={900,750,600,450,300};for(int i=1;i<=5;i++)check(IndustrialRules.fishingTicks(600,i)==expected[i-1],"fish duration Lv"+i);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void breeder_logical_caretaker_no_player_owner_and_no_theft(GameTestHelper h){
        var f=fixture(h,Profession.BREEDER);f.s.setItem(28,new ItemStack(Items.BONE,64));var wild=animal(f,EntityType.WOLF,false);var owned=animal(f,EntityType.WOLF,false);var owner=UUID.randomUUID();owned.setTame(true);owned.setOwnerUUID(owner);
        step(f,50);check(wild.getPersistentData().hasUUID("KraftCityCaretaker")&&wild.getPersistentData().getUUID("KraftCityCaretaker").equals(f.v.getUUID()),"logical taming failed");
        check(wild.isTame()&&wild.getOwnerUUID()==null&&owner.equals(owned.getOwnerUUID()),"invalid or stolen player ownership");
        var saved=wild.saveWithoutId(new CompoundTag());var restored=EntityType.WOLF.create(f.l);restored.load(saved);check(restored.getPersistentData().getUUID("KraftCityCaretaker").equals(f.v.getUUID()),"caretaker not persistent");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void breeder_fills_capacity_with_real_wolf_offspring(GameTestHelper h){
        var f=fixture(h,Profession.BREEDER);level(f,2,25);f.s.setItem(29,new ItemStack(Items.BEEF,4));
        var a=animal(f,EntityType.WOLF,false);var b=animal(f,EntityType.WOLF,false);for(var w:List.of(a,b)){w.setTame(true);w.getPersistentData().putUUID("KraftCityCaretaker",f.v.getUUID());}
        step(f,1);check(f.l.getEntitiesOfClass(Wolf.class,IndustrialWork.area(f.l,f.s),Wolf::isBaby).size()==1,"no real wolf child");
        check(f.s.getItem(29).getCount()==2,"wolf food consumed incorrectly");h.succeed();
    }
    private static BlockPos hive(F f,boolean safe){var p=f.p.east();f.l.setBlock(p,Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL,5),3);if(safe)f.l.setBlock(p.below(),Blocks.CAMPFIRE.defaultBlockState(),3);f.s.industry.putInt("cursor",index(f,p));return p;}
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void beekeeper_bottles_before_shears_no_lost_consumables(GameTestHelper h){
        var f=fixture(h,Profession.BEEKEEPER);f.s.setItem(0,new ItemStack(Items.GLASS_BOTTLE,2));f.s.setItem(1,new ItemStack(Items.SHEARS));var p=hive(f,true);
        for(int i=0;i<2;i++){f.l.setBlock(p,Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL,5),3);f.s.industry.putInt("cursor",index(f,p));step(f,1);}
        check(count(f,Items.HONEY_BOTTLE)==2&&count(f,Items.HONEYCOMB)==0,"bottle priority failed");
        f.l.setBlock(p,Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL,5),3);f.s.industry.putInt("cursor",index(f,p));step(f,1);
        check(count(f,Items.HONEYCOMB)==3&&f.l.getBlockState(p).is(Blocks.BEEHIVE),"honeycomb or hive preservation");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void beekeeper_smoke_and_output_capacity_are_required(GameTestHelper h){
        var f=fixture(h,Profession.BEEKEEPER);f.s.setItem(27,new ItemStack(Items.GLASS_BOTTLE));var p=hive(f,false);step(f,1);
        check(f.l.getBlockState(p).getValue(BeehiveBlock.HONEY_LEVEL)==5&&f.s.getItem(27).getCount()==1,"unsafe hive consumed");
        f.l.setBlock(p.below(),Blocks.CAMPFIRE.defaultBlockState(),3);for(int i=0;i<27;i++)f.s.setItem(i,new ItemStack(Items.STONE,64));f.s.industry.putInt("cursor",index(f,p));step(f,1);
        check(f.l.getBlockState(p).getValue(BeehiveBlock.HONEY_LEVEL)==5&&f.s.getItem(27).getCount()==1,"full inventory lost honey/bottle");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void beekeeper_breeds_bees_without_destroying_hives(GameTestHelper h){
        var f=fixture(h,Profession.BEEKEEPER);level(f,2,25);f.s.setItem(28,new ItemStack(Items.DANDELION,2));animal(f,EntityType.BEE,false);animal(f,EntityType.BEE,false);step(f,1);
        check(f.l.getEntitiesOfClass(Bee.class,IndustrialWork.area(f.l,f.s),Bee::isBaby).size()==1,"bee reproduction failed");check(f.s.getItem(28).isEmpty(),"flower consumption");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void all_jobs_keep_shared_inventory_and_additive_save_data(GameTestHelper h){
        for(Profession p:Profession.values())if(p.sector==Profession.Sector.INDUSTRIAL){
            var f=fixture(h,p);f.s.setItem(26,new ItemStack(Items.DIAMOND,7));f.s.industry.putLong("fish_elapsed",42);f.s.industry.putBoolean("preview",true);
            var restored=EmploymentData.load(EmploymentData.get(f.l).save(new CompoundTag())).station(f.s.id);
            check(restored.getItem(26).getCount()==7&&restored.industry.getLong("fish_elapsed")==42&&restored.worker.equals(f.v.getUUID()),"persist "+p);
            var player=FakePlayerFactory.get(f.l,new GameProfile(UUID.randomUUID(),"SharedJob"));var a=new WorkMenu(1,player.getInventory(),f.s,false,null);var b=new WorkMenu(2,player.getInventory(),f.s,false,f.v.getUUID());
            check(a.inventory==b.inventory&&a.slots.size()==68,"dual menu "+p);
        }h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void legacy_employment_schema_loads_without_reset_or_inventory_migration(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);f.s.setItem(0,new ItemStack(Items.APPLE,13));var tag=EmploymentData.get(f.l).save(new CompoundTag());
        for(Tag raw:tag.getList("stations",Tag.TAG_COMPOUND))((CompoundTag)raw).remove("industry");
        var old=EmploymentData.load(tag).station(f.s.id);check(old.getItem(0).getCount()==13&&old.industry.isEmpty()&&old.worker.equals(f.v.getUUID()),"schema1 compatibility");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void area_selection_respects_level_and_lv5_smaller_options(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);for(int lv=1;lv<=5;lv++){level(f,lv,0);check(IndustrialRules.size(f.l,f.s)==3+lv*2,"level size");check(!IndustrialRules.selectSize(f.l,f.s,15),"oversize accepted");if(lv<5)check(!IndustrialRules.selectSize(f.l,f.s,3+lv*2-2),"smaller early area accepted");}
        check(IndustrialRules.selectSize(f.l,f.s,5)&&IndustrialRules.size(f.l,f.s)==5,"Lv5 smaller area");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void discard_keeps_plantable_reserve_and_transfers_excess_exactly(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);BlockPos p=f.p.west();f.l.setBlock(p,Blocks.BARREL.defaultBlockState(),3);f.s.industry.putLong("discard",p.asLong());
        f.s.setItem(0,new ItemStack(Items.WHEAT_SEEDS,40));f.s.setItem(1,new ItemStack(Items.WHEAT,20));
        IndustrialWork.deposit(f.l,f.s,f.v);var chest=(BarrelBlockEntity)f.l.getBlockEntity(p);
        check(f.s.getItem(0).getCount()==10&&count(f,Items.WHEAT)==0,"reserve/excess");int seeds=0,wheat=0;for(int i=0;i<27;i++){if(chest.getItem(i).is(Items.WHEAT_SEEDS))seeds+=chest.getItem(i).getCount();if(chest.getItem(i).is(Items.WHEAT))wheat+=chest.getItem(i).getCount();}
        check(seeds==30&&wheat==20,"discard duplication/loss");IndustrialWork.deposit(f.l,f.s,f.v);check(f.s.getItem(0).getCount()==10,"reserve deposited twice");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void automatic_discard_prioritizes_boundary_left_and_partial_fallback(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        var nearLeft=f.p.west(3);
        var nearRight=f.p.east(3);
        var far=f.p.west(5);
        var protectedPos=f.p.west(3).north();
        var sealed=f.p.north(3);
        for(var pos:List.of(nearLeft,nearRight,far,protectedPos,sealed))f.l.setBlock(pos,Blocks.BARREL.defaultBlockState(),3);
        for(var side:Direction.values())f.l.setBlock(sealed.relative(side),Blocks.STONE.defaultBlockState(),3);
        var left=(BarrelBlockEntity)f.l.getBlockEntity(nearLeft);
        var right=(BarrelBlockEntity)f.l.getBlockEntity(nearRight);
        var distant=(BarrelBlockEntity)f.l.getBlockEntity(far);
        var blocked=(BarrelBlockEntity)f.l.getBlockEntity(protectedPos);
        var area=IndustrialWork.area(f.l,f.s);
        var order=br.com.kraftcity.logistics.DiscardChests.destinations(f.l,f.s,area);
        check(order.equals(List.of(protectedPos,nearLeft,nearRight,far)),"unexpected destination order: "+order);
        var platform=f.l.getBlockState(f.p);
        f.l.setBlock(f.p,platform.setValue(WorkBlock.FACING,Direction.SOUTH),3);
        check(br.com.kraftcity.logistics.DiscardChests.destinations(f.l,f.s,area).get(0).equals(nearRight),"left did not follow post rotation");
        f.l.setBlock(f.p,platform,3);
        for(int slot=0;slot<27;slot++)left.setItem(slot,new ItemStack(Items.STONE,64));
        left.setItem(0,new ItemStack(Items.CARROT,62));
        f.s.industry.putLong("discard",far.asLong());
        f.s.setItem(0,new ItemStack(Items.CARROT,15));
        var protection=new Object(){
            @net.minecraftforge.eventbus.api.SubscribeEvent
            public void protect(WorkActionEvent event){if(event.position.equals(protectedPos))event.setCanceled(true);}
        };
        MinecraftForge.EVENT_BUS.register(protection);
        try{IndustrialWork.deposit(f.l,f.s,f.v);}
        finally{MinecraftForge.EVENT_BUS.unregister(protection);}
        check(blocked.isEmpty()&&distant.isEmpty()&&left.countItem(Items.CARROT)==64
            &&right.countItem(Items.CARROT)==3&&count(f,Items.CARROT)==10,"priority/remainder or conservation failed");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void protection_cancels_without_consuming_and_alert_is_throttled(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);f.s.setItem(27,new ItemStack(Items.IRON_HOE));f.s.setItem(0,new ItemStack(Items.WHEAT_SEEDS,10));var p=crop(f,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7));
        var listener=new Object(){@net.minecraftforge.eventbus.api.SubscribeEvent public void deny(WorkActionEvent e){if(e.station.id.equals(f.s.id))e.setCanceled(true);}};
        MinecraftForge.EVENT_BUS.register(listener);try{step(f,2);check(f.l.getBlockState(p).getValue(CropBlock.AGE)==7&&f.s.getItem(27).getDamageValue()==0,"protection bypass");long at=f.s.industry.getCompound("alerts").getLong("protection");step(f,1);check(f.s.industry.getCompound("alerts").getLong("protection")==at,"alert spam");}finally{MinecraftForge.EVENT_BUS.unregister(listener);}h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void custom_tool_recipes_tiers_and_variants_are_registered(GameTestHelper h){
        check(IndustrialTools.tier(new ItemStack(IndustrialTools.HOE.get()))==3,"emerald tier");
        for(String id:List.of("emerald_hoe","emerald_axe","emerald_sword","wood_shears","gold_shears","emerald_shears","diamond_shears","netherite_shears","iron_fishing_rod","gold_fishing_rod","emerald_fishing_rod","diamond_fishing_rod","netherite_fishing_rod"))
            check(h.getLevel().getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation("residenciasaldeoes",id)).isPresent(),"missing recipe "+id);
        check(Items.SHEARS.getMaxDamage()==238&&Items.FISHING_ROD.getMaxDamage()==64,"vanilla overwritten");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void dawn_takes_at_most_five_useful_farmer_items_once_per_day(GameTestHelper h){
        var f=fixture(h,Profession.SHEPHERD);var farmer=f.p.offset(0,0,4);var barrel=f.p.south();
        f.l.setBlock(farmer,WorkModule.PROFESSIONAL.get(Profession.FARMER).get().defaultBlockState(),3);
        var source=((WorkBlockEntity)f.l.getBlockEntity(farmer)).station();
        // Lv5 permits a smaller 5x5? Source is unemployed: place its barrel inside its 5x5 area.
        barrel=farmer.north();f.l.setBlock(barrel,Blocks.BARREL.defaultBlockState(),3);source.industry.putLong("discard",barrel.asLong());
        var chest=(BarrelBlockEntity)f.l.getBlockEntity(barrel);chest.setItem(0,new ItemStack(Items.WHEAT,20));chest.setItem(1,new ItemStack(Items.CARROT,20));
        step(f,1);check(f.s.getItem(28).is(Items.WHEAT)&&f.s.getItem(28).getCount()==5&&chest.getItem(0).getCount()==15,"dawn transfer not exactly five: slot="+f.s.getItem(28)+" source="+chest.getItem(0));
        step(f,1);check(f.s.getItem(28).getCount()==5&&chest.getItem(0).getCount()==15&&chest.getItem(1).getCount()==20,"repeat dawn or wrong item");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void night_stops_production_deposits_once_and_keeps_home(GameTestHelper h){
        var f=fixture(h,Profession.SHEPHERD);f.s.setItem(27,new ItemStack(Items.SHEARS));var sheep=animal(f,EntityType.SHEEP,false);
        var pos=f.p.west();f.l.setBlock(pos,Blocks.BARREL.defaultBlockState(),3);f.s.industry.putLong("discard",pos.asLong());f.s.setItem(0,new ItemStack(Items.WHITE_WOOL,9));
        f.l.setDayTime(12000);step(f,1);check(!sheep.isSheared()&&count(f,Items.WHITE_WOOL)==0,"night production/deposit");
        check(ResidenceData.get(f.l).owner(f.v.getUUID())!=null&&f.s.worker.equals(f.v.getUUID()),"night lost home/job");f.l.setDayTime(1000);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void breeder_care_consumes_bone_and_progresses_without_free_passive_xp(GameTestHelper h){
        var f=fixture(h,Profession.BREEDER);var wolf=animal(f,EntityType.WOLF,false);wolf.setTame(true);wolf.getPersistentData().putUUID("KraftCityCaretaker",f.v.getUUID());f.s.setItem(28,new ItemStack(Items.BONE,3));
        step(f,1);check(f.s.getItem(28).getCount()==2&&WorkforceData.get(f.l).progress(f.v.getUUID(),Profession.BREEDER.id()).xp()==1,"care progression");
        step(f,5);check(f.s.getItem(28).getCount()==2,"care cooldown bypass");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void two_posts_cannot_shear_or_harvest_same_target_twice(GameTestHelper h){
        var f=fixture(h,Profession.SHEPHERD);f.s.setItem(27,new ItemStack(Items.SHEARS));var sheep=animal(f,EntityType.SHEEP,false);step(f,1);int n=count(f,Items.WHITE_WOOL);
        var pos=f.p.east(2);f.l.setBlock(pos,WorkModule.INDUSTRIAL.get().defaultBlockState(),3);
        var second=EntityType.VILLAGER.create(f.l);second.setAge(0);second.setNoAi(true);second.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);f.l.addFreshEntity(second);
        check(ResidenceData.get(f.l).link(f.l,ResidenceData.get(f.l).owner(f.v.getUUID()).pos,second).equals("ok"),"second home");
        var post=((WorkBlockEntity)f.l.getBlockEntity(pos)).station();post.setItem(31,new ItemStack(Items.EMERALD,10));
        var player=FakePlayerFactory.get(f.l,new GameProfile(UUID.randomUUID(),"SecondJob"));player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        check(Employment.hire(player,post,second.getUUID(),Profession.SHEPHERD).equals("ok"),"second job");post.setItem(27,new ItemStack(Items.SHEARS));IndustrialWork.tick(f.l,post);
        int extra=0;for(int i=0;i<27;i++)if(post.getItem(i).is(Items.WHITE_WOOL))extra+=post.getItem(i).getCount();
        check(n>0&&count(f,Items.WHITE_WOOL)==n&&extra==0,"duplicate shared target action");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void milk_exchange_can_use_slot_freed_by_last_empty_bucket(GameTestHelper h){
        var f=fixture(h,Profession.BUTCHER);f.s.setItem(27,new ItemStack(Items.IRON_AXE));animal(f,EntityType.COW,false);
        for(int slot=0;slot<27;slot++)f.s.setItem(slot,new ItemStack(Items.STONE,64));f.s.setItem(0,new ItemStack(Items.BUCKET));
        step(f,1);check(count(f,Items.MILK_BUCKET)==1&&count(f,Items.BUCKET)==0,"full-inventory bucket exchange");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void breeding_accepts_two_food_items_split_between_slots(GameTestHelper h){
        var f=fixture(h,Profession.BUTCHER);f.s.setItem(27,new ItemStack(Items.IRON_AXE));animal(f,EntityType.COW,false);animal(f,EntityType.COW,false);
        f.s.setItem(28,new ItemStack(Items.WHEAT));f.s.setItem(29,new ItemStack(Items.WHEAT));step(f,1);
        check(f.s.getItem(28).isEmpty()&&f.s.getItem(29).isEmpty()&&f.l.getEntitiesOfClass(Cow.class,IndustrialWork.area(f.l,f.s),Cow::isBaby).size()==1,"split food transaction");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void every_job_stops_without_its_indispensable_consumables(GameTestHelper h){
        for(Profession p:Profession.values())if(p.sector==Profession.Sector.INDUSTRIAL){
            var f=fixture(h,p);
            switch(p){
                case FARMER->crop(f,Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE,7));
                case BUTCHER->{animal(f,EntityType.COW,false);animal(f,EntityType.COW,false);animal(f,EntityType.COW,false);}
                case SHEPHERD->animal(f,EntityType.SHEEP,false);
                case FISHERMAN->water(f);
                case BREEDER->animal(f,EntityType.WOLF,false);
                case BEEKEEPER->hive(f,true);
                default->{}
            }
            step(f,1);check(WorkforceData.get(f.l).progress(f.v.getUUID(),p.id()).xp()==0,"free progress without requirement "+p);
            check(!f.s.industry.getCompound("alerts").isEmpty(),"missing indispensable warning "+p);
        }h.succeed();
    }


    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void farmer_zigzag_starts_northwest_and_crosses_each_row(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        var firstRow=FarmerWork.zigzag(f.p,5);
        check(firstRow.get(0).equals(f.p.offset(-2,1,-2))&&firstRow.get(4).equals(f.p.offset(2,1,-2))
            &&firstRow.get(5).equals(f.p.offset(2,1,-1)),"route must start northwest and alternate east/west");
        for(int width:new int[]{5,13}){
            var path=FarmerWork.zigzag(f.p,width);
            check(path.size()==width*width&&new java.util.HashSet<>(path).size()==path.size(),"repeated or missing zigzag cell");
            check(path.get(path.size()-1).equals(f.p.offset(f.p.getX()-path.get(0).getX(),1,f.p.getZ()-path.get(0).getZ())),"zigzag did not finish at opposite corner");
            for(int n=1;n<path.size();n++)check(path.get(n).distManhattan(path.get(n-1))==1,"zigzag jumped a cell");
        }
        f.s.setItem(27,new ItemStack(Items.IRON_HOE));f.s.setItem(0,new ItemStack(Items.WHEAT_SEEDS,1));
        step(f,1);check(f.s.industry.getInt("zigzag_size")==5,"missing saved size");
        level(f,5,100);step(f,1);
        check(f.s.industry.getInt("zigzag_size")==13,"zigzag not invalidated by level growth");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void farmer_height_and_single_mature_crop_preserve_manual_neighbors(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        var area=IndustrialWork.area(f.l,f.s);
        check(area.minY==f.p.getY()+1&&area.maxY==f.p.getY()+4,"incorrect farmer height");
        check(!area.contains(net.minecraft.world.phys.Vec3.atCenterOf(f.p)),"farmer block included");
        f.s.setItem(27,new ItemStack(Items.IRON_HOE));
        f.s.setItem(0,new ItemStack(Items.WHEAT_SEEDS,4));
        var center=crop(f,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7));
        var near=center.south();
        f.l.setBlock(near.below(),Blocks.FARMLAND.defaultBlockState(),3);
        var carrots=Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE,2);
        f.l.setBlock(near,carrots,3);
        step(f,1);
        check(f.l.getBlockState(center).is(Blocks.WHEAT)&&f.l.getBlockState(center).getValue(CropBlock.AGE)==0,"iron hoe did not replant wheat");
        check(f.l.getBlockState(near)==carrots,"manual adjacent crop damaged");
        check(count(f,Items.WHEAT)>=1&&count(f,Items.WHEAT)<=2,"incorrect iron hoe yield");
        check(f.l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(center).inflate(1)).isEmpty(),"duplicate world drops");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void farmer_collects_mod_agnostic_ground_items_at_border(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        var at=f.p.east(5).above();
        f.v.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);
        var inside=new net.minecraft.world.entity.item.ItemEntity(f.l,at.getX()+.5,at.getY(),at.getZ()+.5,new ItemStack(Items.COBBLESTONE,3));
        var outside=new net.minecraft.world.entity.item.ItemEntity(f.l,at.getX()+1.5,at.getY(),at.getZ()+.5,new ItemStack(Items.GOLD_INGOT));
        f.l.addFreshEntity(inside);f.l.addFreshEntity(outside);
        step(f,1);
        check(!inside.isAlive()&&count(f,Items.COBBLESTONE)==3&&outside.isAlive(),"wrong pickup boundary or conservation");
        outside.discard();
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void farmer_virtual_cane_and_fruit_keeps_roots_and_neighbor(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        f.s.setItem(27,new ItemStack(Items.IRON_HOE));f.s.setItem(30,new ItemStack(Items.WATER_BUCKET));
        var root=f.p.east().above();
        f.l.setBlock(root.below(),Blocks.SAND.defaultBlockState(),3);
        check(Blocks.SUGAR_CANE.defaultBlockState().canSurvive(f.l,root),"bucket did not support adjoining cane");
        check(f.l.getFluidState(f.p).isEmpty(),"virtual irrigation placed water");
        var fruit=f.p.west().above();
        var stem=fruit.north();
        f.l.setBlock(stem.below(),Blocks.FARMLAND.defaultBlockState(),3);
        f.l.setBlock(fruit.below(),Blocks.DIRT.defaultBlockState(),3);
        f.l.setBlock(fruit,Blocks.PUMPKIN.defaultBlockState(),3);
        f.l.setBlock(stem,Blocks.ATTACHED_PUMPKIN_STEM.defaultBlockState().setValue(AttachedStemBlock.FACING,Direction.SOUTH),3);
        f.v.setPos(fruit.getX()+.5,fruit.getY(),fruit.getZ()+1.5);
        f.s.industry.putInt("cursor",index(f,fruit));
        step(f,1);
        check(f.l.getBlockState(fruit).isAir()&&count(f,Items.PUMPKIN)==1,"fruit did not go to shared inventory");
        check(f.l.getBlockState(stem).is(Blocks.PUMPKIN_STEM),"fruit harvest destroyed its stem");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void farmer_night_jump_attempts_one_discard_and_keeps_each_seed_type(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        var at=f.p.south().west();
        f.l.setBlock(at,Blocks.BARREL.defaultBlockState(),3);
        var barrel=(BarrelBlockEntity)f.l.getBlockEntity(at);
        f.s.industry.putLong("discard",at.asLong());
        f.s.setItem(0,new ItemStack(Items.WHEAT_SEEDS,40));
        f.s.setItem(1,new ItemStack(Items.MELON_SEEDS,2));
        f.s.setItem(2,new ItemStack(Items.STICK,7));
        f.l.setDayTime(13001);f.v.setNoAi(false);
        FarmerLogistics.beforeHome(f.l,f.v);
        check(count(f,Items.WHEAT_SEEDS)==10&&count(f,Items.MELON_SEEDS)==2&&count(f,Items.STICK)==0,"night transfer/reserve");
        f.s.setItem(2,new ItemStack(Items.STICK,3));
        f.l.setDayTime(1000);f.l.setDayTime(13001);
        FarmerLogistics.beforeHome(f.l,f.v);
        check(count(f,Items.STICK)==3&&!barrel.isEmpty(),"night jump repeated the same discard");
        f.v.setNoAi(true);f.l.setDayTime(1000);
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void farmer_discards_to_forge_inventory_and_keeps_rejected_items(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        var at=f.p.south().west();
        f.l.setBlock(at,Blocks.HOPPER.defaultBlockState(),3);
        var hopper=(HopperBlockEntity)f.l.getBlockEntity(at);
        check(FarmerLogistics.supports(f.l,at),"missing Forge item capability");
        f.s.industry.putLong("discard",at.asLong());
        f.s.setItem(0,new ItemStack(Items.APPLE,5));
        IndustrialWork.deposit(f.l,f.s,f.v);
        check(hopper.countItem(Items.APPLE)==5&&count(f,Items.APPLE)==0,"Forge transfer failed");
        for(int i=0;i<hopper.getContainerSize();i++)hopper.setItem(i,new ItemStack(Items.STONE,64));
        f.s.setItem(0,new ItemStack(Items.APPLE,3));
        IndustrialWork.deposit(f.l,f.s,f.v);
        check(count(f,Items.APPLE)==3&&hopper.countItem(Items.STONE)==320,"full Forge inventory lost items");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void farmer_reserves_the_only_lateral_fruit_cell(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        var stem=f.p.offset(2,1,1);
        var fruit=stem.west();
        f.l.setBlock(stem.below(),Blocks.FARMLAND.defaultBlockState(),3);
        f.l.setBlock(fruit.below(),Blocks.DIRT.defaultBlockState(),3);
        f.s.setItem(27,new ItemStack(Items.IRON_HOE));
        f.s.setItem(0,new ItemStack(Items.MELON_SEEDS,1));
        // Seed an initial orientation/cursor at the stem cell. Position the
        // worker adjacent so the assertions exercise the real placement step.
        var route=FarmerWork.zigzag(f.p,5);
        f.s.industry.putInt("zigzag_size",5);
        f.s.industry.putInt("zigzag_plant",route.indexOf(stem)*FarmerWork.HEIGHT);
        f.v.setPos(stem.getX()+.5,stem.getY(),stem.getZ()+.5);
        step(f,1);
        check(f.l.getBlockState(stem).is(Blocks.MELON_STEM),"failed to plant melon seed");
        f.s.setItem(0,new ItemStack(Items.WHEAT_SEEDS,5));
        f.s.industry.putInt("zigzag_plant",route.indexOf(fruit)*FarmerWork.HEIGHT);
        f.v.setPos(fruit.getX()+.5,fruit.getY(),fruit.getZ()+.5);
        step(f,1);
        check(f.l.getBlockState(fruit).isAir()&&f.l.getBlockState(fruit.below()).is(Blocks.DIRT),"tilled the reserved fruit cell");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void farmer_five_by_five_reaps_one_and_preserves_twenty_four_neighbors(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        // Place the 5x5 crop layer above the post and preserve the entire support layer.
        var center=f.p.above(2);
        f.s.setItem(27,new ItemStack(Items.IRON_HOE));
        for(var species:List.of(Blocks.WHEAT,Blocks.CARROTS,Blocks.POTATOES,Blocks.BEETROOTS)){
            var crop=(CropBlock)species;
            for(var at:BlockPos.betweenClosed(center.offset(-2,0,-2),center.offset(2,0,2))){
                f.l.setBlock(at.below(),Blocks.FARMLAND.defaultBlockState(),3);
                f.l.setBlock(at,crop.getStateForAge(at.equals(center)?crop.getMaxAge():0),3);
            }
            f.s.setItem(0,new ItemStack(species==Blocks.WHEAT?Items.WHEAT_SEEDS:species==Blocks.CARROTS?Items.CARROT:species==Blocks.POTATOES?Items.POTATO:Items.BEETROOT_SEEDS,4));
            f.v.setPos(center.getX()+2.5,center.getY(),center.getZ()+2.5);
            long xp=WorkforceData.get(f.l).progress(f.v.getUUID(),Profession.FARMER.id()).xp();
            step(f,1);
            check(crop.isMaxAge(f.l.getBlockState(center)),"worked a distant block");
            f.v.setPos(center.getX()+.5,center.getY(),center.getZ()+.5);
            step(f,1);
            check(WorkforceData.get(f.l).progress(f.v.getUUID(),Profession.FARMER.id()).xp()==xp+1,"more or fewer than one harvest");
            for(var at:BlockPos.betweenClosed(center.offset(-2,0,-2),center.offset(2,0,2))){
                check(f.l.getBlockState(at)==crop.getStateForAge(0),"neighbor crop changed: "+at);
                check(f.l.getBlockState(at.below()).is(Blocks.FARMLAND),"neighbor soil changed: "+at);
            }
        }
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=220,batch="industry")
    public static void walking_farmer_harvests_only_target_wheat(GameTestHelper h){walkingHarvest(h,Blocks.WHEAT);}
    @GameTest(template="empty",timeoutTicks=220,batch="industry")
    public static void walking_farmer_harvests_only_target_carrots(GameTestHelper h){walkingHarvest(h,Blocks.CARROTS);}
    @GameTest(template="empty",timeoutTicks=220,batch="industry")
    public static void walking_farmer_harvests_only_target_potatoes(GameTestHelper h){walkingHarvest(h,Blocks.POTATOES);}
    @GameTest(template="empty",timeoutTicks=220,batch="industry")
    public static void walking_farmer_harvests_only_target_beetroot(GameTestHelper h){walkingHarvest(h,Blocks.BEETROOTS);}
    @GameTest(template="empty",timeoutTicks=220,batch="industry")
    public static void walking_farmer_harvests_only_target_melon(GameTestHelper h){walkingHarvest(h,Blocks.MELON);}
    @GameTest(template="empty",timeoutTicks=220,batch="industry")
    public static void walking_farmer_harvests_only_target_pumpkin(GameTestHelper h){walkingHarvest(h,Blocks.PUMPKIN);}

    private static void walkingHarvest(GameTestHelper h,Block kind){
        var f=fixture(h,Profession.FARMER);
        var target=f.p.offset(-2,1,-2);
        var start=f.p.offset(2,1,2);
        for(var floor:BlockPos.betweenClosed(f.p.offset(-4,-1,-4),f.p.offset(4,-1,4))){
            f.l.setBlock(floor,Blocks.STONE.defaultBlockState(),3);
        }
        var soil=Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7);
        var crop=kind instanceof CropBlock selected?selected:(CropBlock)Blocks.WHEAT;
        var stem=target.east();
        for(var pos:FarmerWork.zigzag(f.p,5)){
            if(!pos.below().equals(f.p))f.l.setBlock(pos.below(),soil,3);
            if(pos.equals(target)||pos.equals(stem)&&!(kind instanceof CropBlock)||pos.below().equals(f.p))continue;
            var stage=pos.getZ()==target.getZ()+1?crop.getMaxAge():0;
            f.l.setBlock(pos,crop.getStateForAge(stage),3);
        }
        if(kind instanceof CropBlock){
            f.l.setBlock(target,crop.getStateForAge(crop.getMaxAge()),3);
        }else{
            f.l.setBlock(target.below(),Blocks.DIRT.defaultBlockState(),3);
            f.l.setBlock(target,kind.defaultBlockState(),3);
            var attached=kind==Blocks.MELON?Blocks.ATTACHED_MELON_STEM:Blocks.ATTACHED_PUMPKIN_STEM;
            f.l.setBlock(stem,attached.defaultBlockState().setValue(AttachedStemBlock.FACING,Direction.WEST),3);
        }
        var before=new HashMap<BlockPos,BlockState>();
        for(var pos:FarmerWork.zigzag(f.p,5)){
            before.put(pos,f.l.getBlockState(pos));
            before.put(pos.below(),f.l.getBlockState(pos.below()));
        }
        f.s.setItem(27,new ItemStack(Items.IRON_HOE));
        for(int i=0;i<4;i++)f.s.setItem(i,new ItemStack(List.of(Items.WHEAT_SEEDS,Items.CARROT,Items.POTATO,Items.BEETROOT_SEEDS).get(i),4));
        f.s.setItem(30,new ItemStack(Items.WATER_BUCKET));
        var from=Vec3.atBottomCenterOf(start);
        f.v.setPos(from.x,from.y,from.z);
        f.v.setNoAi(false);
        h.succeedWhen(()->{
            check(WorkforceData.get(f.l).progress(f.v.getUUID(),Profession.FARMER.id()).xp()>0,"Farm worker has not harvested at "+f.v.position());
            check(f.v.distanceToSqr(from)>4,"harvest did not require walking");
            check(f.v.distanceToSqr(Vec3.atCenterOf(target))<3,"worker was not at the harvest target: "+f.v.position());
            for(var entry:before.entrySet()){
                var pos=entry.getKey();
                if(pos.equals(target))continue;
                var actual=f.l.getBlockState(pos);
                if(!(kind instanceof CropBlock)&&pos.equals(stem)){
                    check(actual.is(kind==Blocks.MELON?Blocks.MELON_STEM:Blocks.PUMPKIN_STEM)
                        &&actual.getValue(StemBlock.AGE)==7,"fruit harvest damaged the mature stem");
                }else check(actual==entry.getValue(),"harvest changed neighbor "+pos+" from "+entry.getValue()+" to "+actual);
            }
        });
    }

    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void farmer_works_in_rain_and_pauses_in_thunder(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        f.s.setItem(27,new ItemStack(Items.IRON_HOE));
        f.s.setItem(0,new ItemStack(Items.WHEAT_SEEDS,4));
        var ripe=Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7);
        var at=crop(f,ripe);
        float rain=f.l.getRainLevel(1),thunder=f.l.getThunderLevel(1);
        try{
            f.l.setRainLevel(1);f.l.setThunderLevel(1);
            step(f,1);check(f.l.getBlockState(at)==ripe,"worked during thunder");
            f.l.setThunderLevel(0);
            step(f,1);check(f.l.getBlockState(at).is(Blocks.WHEAT)&&f.l.getBlockState(at).getValue(CropBlock.AGE)==0,"did not resume in ordinary rain");
        }finally{
            f.l.setRainLevel(rain);f.l.setThunderLevel(thunder);
        }
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=180,batch="industry")
    public static void worker_walks_through_fence_gate_and_shuts_it(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        for(var floor:BlockPos.betweenClosed(f.p.offset(-4,0,-4),f.p.offset(4,0,4))){
            if(!floor.equals(f.p))f.l.setBlock(floor,Blocks.STONE.defaultBlockState(),3);
        }
        var gate=f.p.offset(-2,1,0);
        for(int x=-4;x<=4;x++){
            if(x!=-2)f.l.setBlock(f.p.offset(x,1,0),Blocks.OAK_FENCE.defaultBlockState(),3);
        }
        f.l.setBlock(gate,Blocks.OAK_FENCE_GATE.defaultBlockState(),3);
        var start=Vec3.atBottomCenterOf(gate.south(2));
        f.v.setPos(start.x,start.y,start.z);
        f.v.setNoAi(false);
        f.s.setItem(27,new ItemStack(Items.IRON_HOE));
        var crop=gate.north(2);
        f.l.setBlock(crop.below(),Blocks.FARMLAND.defaultBlockState(),3);
        f.l.setBlock(crop,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),3);
        h.startSequence().thenWaitUntil(()->check(f.l.getBlockState(gate).getValue(FenceGateBlock.OPEN),
            "worker has not opened the gate: position="+f.v.position()+" target="+f.v.getNavigation().getTargetPos()))
        .thenWaitUntil(()->{
            check(f.l.getBlockState(crop).getValue(CropBlock.AGE)==0,"farmer did not reach the crop behind the gate");
            check(f.v.getZ()<gate.getZ(),"farmer did not cross the gate");
            check(!f.l.getBlockState(gate).getValue(FenceGateBlock.OPEN),"gate did not close after passage");
        }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=200,batch="industry")
    public static void farmer_flees_beyond_field_and_returns_to_work(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        var home=ResidenceData.get(f.l).owner(f.v.getUUID()).pos;
        for(var pos:BlockPos.betweenClosed(f.p.offset(-9,0,-9),f.p.offset(9,0,9))){
            if(pos.equals(f.p)||pos.equals(home))continue;
            int distance=Math.max(Math.abs(pos.getX()-f.p.getX()),Math.abs(pos.getZ()-f.p.getZ()));
            f.l.setBlock(pos.below(Math.max(0,distance-7)),Blocks.STONE.defaultBlockState(),3);
        }
        var wheat=crop(f,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7));
        var start=Vec3.atBottomCenterOf(f.p.offset(1,1,2));
        f.v.setPos(start.x,start.y,start.z);
        f.s.setItem(27,new ItemStack(Items.IRON_HOE));f.s.setItem(0,new ItemStack(Items.WHEAT_SEEDS,4));
        var zombie=EntityType.ZOMBIE.create(f.l);
        zombie.setNoAi(true);zombie.setInvulnerable(true);zombie.setPersistenceRequired();
        zombie.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));
        zombie.setPos(start.x,start.y,start.z-1.5);zombie.setTarget(f.v);
        f.l.addFreshEntity(zombie);
        f.v.setNoAi(false);
        h.startSequence().thenWaitUntil(()->check(Employment.fleeing(f.v),
            "worker did not detect zombie: worker="+f.v.position()+" zombie="+zombie.position()
            +" alive="+zombie.isAlive()+" target="+zombie.getTarget()))
        .thenWaitUntil(()->{
            check(!IndustrialWork.area(f.l,f.s).contains(f.v.position().add(0,.125,0)),"worker has not fled beyond the field");
            check(f.l.getBlockState(wheat).getValue(CropBlock.AGE)==7,"harvest continued during danger");
        }).thenExecute(zombie::discard).thenWaitUntil(()->{
            check(!Employment.fleeing(f.v),"worker resumed before the danger expired");
            check(f.l.getBlockState(wheat).getValue(CropBlock.AGE)==0,"worker did not return to harvest");
        }).thenSucceed();
    }

    private static final class ModInventory implements net.minecraftforge.common.capabilities.ICapabilityProvider {
        final net.minecraftforge.items.ItemStackHandler storage=new net.minecraftforge.items.ItemStackHandler(1){
            @Override public boolean isItemValid(int slot,ItemStack stack){return stack.is(Items.CARROT);}
        };
        final net.minecraftforge.common.util.LazyOptional<net.minecraftforge.items.IItemHandler> capability=
            net.minecraftforge.common.util.LazyOptional.of(()->storage);

        @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
            net.minecraftforge.common.capabilities.Capability<T> cap,Direction side){
            return cap==net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER&&side==Direction.EAST
                ?capability.cast():net.minecraftforge.common.util.LazyOptional.empty();
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void attach(net.minecraftforge.event.AttachCapabilitiesEvent<BlockEntity> event){
            if(event.getObject() instanceof BeaconBlockEntity){
                event.addCapability(new net.minecraft.resources.ResourceLocation("residenciasaldeoes","test_inventory"),this);
                event.addListener(capability::invalidate);
            }
        }
    }

    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void farmer_discovers_sided_mod_inventory_and_reserves_ten_across_stacks(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        var pos=f.p.east().south();
        var mod=new ModInventory();
        MinecraftForge.EVENT_BUS.register(mod);
        try{
            f.l.setBlock(pos,Blocks.BEACON.defaultBlockState(),3);
        }finally{
            MinecraftForge.EVENT_BUS.unregister(mod);
        }
        f.s.setItem(0,new ItemStack(Items.CARROT,8));
        f.s.setItem(1,new ItemStack(Items.CARROT,7));
        f.s.setItem(2,new ItemStack(Items.STICK,3));
        f.s.setItem(3,new ItemStack(Items.BONE_MEAL,4));
        f.v.setPos(pos.getX()+1.5,pos.getY(),pos.getZ()+.5);
        IndustrialWork.deposit(f.l,f.s,f.v);
        check(mod.storage.getStackInSlot(0).getCount()==5&&count(f,Items.CARROT)==10,"sided storage or combined reserve failed");
        check(count(f,Items.STICK)==3&&count(f,Items.BONE_MEAL)==4,"filter or functional input was ignored");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void farmer_plants_one_cane_with_real_and_virtual_water(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        f.s.setItem(27,new ItemStack(Items.IRON_HOE));
        var root=f.p.east().above();
        for(boolean virtual:new boolean[]{false,true}){
            f.l.setBlock(root,Blocks.AIR.defaultBlockState(),3);
            f.l.setBlock(root.below(2),Blocks.STONE.defaultBlockState(),3);
            f.l.setBlock(root.below(),Blocks.SAND.defaultBlockState(),3);
            f.l.setBlock(root.below().south(),virtual?Blocks.STONE.defaultBlockState():Blocks.WATER.defaultBlockState(),3);
            f.s.setItem(30,virtual?new ItemStack(Items.WATER_BUCKET):ItemStack.EMPTY);
            f.s.setItem(0,new ItemStack(Items.SUGAR_CANE,2));
            f.v.setPos(root.getX()+.5,root.getY(),root.getZ()+.5);
            step(f,1);
            check(f.l.getBlockState(root).is(Blocks.SUGAR_CANE)&&count(f,Items.SUGAR_CANE)==1,"cane planting failed, virtual="+virtual);
        }
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void farmer_collects_before_harvest_and_harvests_before_sowing(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        f.s.setItem(27,new ItemStack(Items.IRON_HOE));f.s.setItem(0,new ItemStack(Items.WHEAT_SEEDS,4));
        var wheat=crop(f,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7));
        var empty=wheat.south();
        f.l.setBlock(empty.below(),Blocks.FARMLAND.defaultBlockState(),3);
        var item=new net.minecraft.world.entity.item.ItemEntity(f.l,f.v.getX(),f.v.getY(),f.v.getZ(),new ItemStack(Items.STICK));
        f.l.addFreshEntity(item);
        step(f,1);
        check(!item.isAlive()&&count(f,Items.STICK)==1&&f.l.getBlockState(wheat).getValue(CropBlock.AGE)==7,"harvest outranked pickup");
        step(f,1);
        check(f.l.getBlockState(wheat).getValue(CropBlock.AGE)==0&&f.l.getBlockState(empty).isAir(),"sowing outranked harvest");
        h.succeed();
    }
}
