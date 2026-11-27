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
import net.minecraft.world.level.ChunkPos;
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
        return fixture(h, profession, post -> {});
    }
    private static F fixture(GameTestHelper h, Profession profession, java.util.function.Consumer<BlockPos> beforeHire){
        return fixture(h,profession,0,beforeHire);
    }
    private static F fixture(GameTestHelper h, Profession profession, int elevation, java.util.function.Consumer<BlockPos> beforeHire){
        var l=h.getLevel();l.setDayTime(1000);l.setWeatherParameters(10000,0,false,false);
        BlockPos home=h.absolutePos(new BlockPos(2,2+elevation,2)),p=h.absolutePos(new BlockPos(8,2+elevation,8));
        l.setBlock(home,ResidenciasDosAldeoes.ANCHOR.get().defaultBlockState(),3);
        ((br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity)l.getBlockEntity(home)).setSize(11);
        l.setBlock(p.below(),Blocks.STONE.defaultBlockState(),3);l.setBlock(p,WorkModule.generic(profession.sector).defaultBlockState(),3);
        var v=h.spawn(EntityType.VILLAGER,new BlockPos(3,2+elevation,2));v.setNoAi(true);v.setAge(0);
        check(ResidenceData.get(l).link(l,home,v).equals("ok"),"residence link");v.setPos(p.getX()+.5,p.getY(),p.getZ()+1.5);
        var player=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"IndustryTest"));player.setPos(p.getX()+.5,p.getY(),p.getZ()+1.5);
        var s=((WorkBlockEntity)l.getBlockEntity(p)).station();s.setItem(31,new ItemStack(Items.EMERALD,EmploymentConfig.cost(profession.sector)));
        beforeHire.accept(p);
        String result=Employment.hire(player,s,v.getUUID(),profession);
        check(result.equals("ok"),"hire "+profession+": "+result+" at "+p+"; worker loaded="+(l.getEntity(v.getUUID())==v));
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
            step(f,3);
            if(i%25==0)check(progress.level()==Math.min(5,1+i/25),"wrong level threshold #"+i);
        }
        check(IndustrialRules.size(f.l,f.s)==5,"level progression changed the chosen area");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void farmer_automatic_inputs_irrigation_and_no_source_water(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);f.s.setItem(0,new ItemStack(Items.IRON_HOE));f.s.setItem(1,new ItemStack(Items.BONE_MEAL,8));f.s.setItem(2,new ItemStack(Items.WATER_BUCKET));
        var p=crop(f,Blocks.WHEAT.defaultBlockState());step(f,1);
        check(f.s.getItem(27).is(Items.IRON_HOE)&&f.s.getItem(28).is(Items.BONE_MEAL)&&f.s.getItem(30).is(Items.WATER_BUCKET),"input migration");
        check(f.s.getItem(28).getCount()==7,"bone meal not consumed exactly once");check(Irrigation.hydrates(f.l,p.below()),"hydration missing");
        level(f,5,100);
        for(int size:new int[]{5,13,7,11,9,5}){
            int edge=size/2;
            check(IndustrialRules.selectSize(f.l,f.s,size),"irrigation size "+size);
            check(Irrigation.hydrates(f.l,f.p.offset(edge,0,edge))
                &&Irrigation.hydrates(f.l,f.p.offset(-edge,2,-edge))
                &&!Irrigation.hydrates(f.l,f.p.offset(edge+1,0,0))
                &&!Irrigation.hydrates(f.l,f.p.offset(0,3,0)),"incorrect irrigation boundary for "+size);
        }
        check(f.l.getFluidState(f.p).isEmpty(),"created water source");
        f.s.setItem(30,ItemStack.EMPTY);check(!Irrigation.hydrates(f.l,p.below()),"ghost irrigation");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void farmer_sows_soil_but_leaves_dirt_path_in_zigzag(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        var pathBlocks=List.of(f.p.north(),f.p.south());
        var soils=List.of(f.p.east(),f.p.west(),f.p.south().east());
        for(var pos:pathBlocks)f.l.setBlockAndUpdate(pos,Blocks.DIRT_PATH.defaultBlockState());
        for(int i=0;i<soils.size();i++)f.l.setBlockAndUpdate(soils.get(i),
            List.of(Blocks.DIRT,Blocks.GRASS_BLOCK,Blocks.FARMLAND).get(i).defaultBlockState());
        f.s.setItem(0,new ItemStack(Items.WHEAT_SEEDS,12));
        f.s.setItem(27,new ItemStack(Items.IRON_HOE));
        f.s.setItem(30,new ItemStack(Items.WATER_BUCKET));
        for(var pos:soils){
            var feet=Vec3.atBottomCenterOf(pos.above());
            f.v.setPos(feet.x,feet.y,feet.z);
            step(f,8);
            check(f.l.getBlockState(pos).is(Blocks.FARMLAND)&&f.l.getBlockState(pos.above()).is(Blocks.WHEAT),
                "Valid plot remained unplanted: "+pos);
        }
        for(var pos:pathBlocks){
            var feet=Vec3.atBottomCenterOf(pos.above());
            f.v.setPos(feet.x,feet.y,feet.z);
            step(f,12);
            check(f.l.getBlockState(pos).is(Blocks.DIRT_PATH)&&f.l.isEmptyBlock(pos.above()),"Dirt path was cultivated");
        }
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void farmer_legacy_input_is_saved_until_stock_has_room(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        for(int i=0;i<EmploymentData.COMMON;i++)f.s.setItem(i,new ItemStack(Items.STONE,64));
        f.s.setItem(EmploymentData.INPUT_B,new ItemStack(Items.BEETROOT_SEEDS,7));
        IndustrialWork.restoreFarmerInput(f.s);
        var saved=EmploymentData.get(f.l).save(new net.minecraft.nbt.CompoundTag());
        var restored=EmploymentData.load(saved).station(f.s.id);
        check(restored.getItem(EmploymentData.INPUT_B).getCount()==7,"Full stock lost the legacy slot after save");
        restored.setItem(0,ItemStack.EMPTY);
        IndustrialWork.restoreFarmerInput(restored);
        IndustrialWork.restoreFarmerInput(restored);
        check(restored.getItem(0).is(Items.BEETROOT_SEEDS)&&restored.getItem(0).getCount()==7
            &&restored.getItem(EmploymentData.INPUT_B).isEmpty(),"Legacy stock was lost or duplicated");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=60,batch="farmer_yield_subproducts")
    public static void wooden_hoe_yield_keeps_potato_subproducts_and_replanting(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        var potatoes=(CropBlock)Blocks.POTATOES;
        var mature=potatoes.getStateForAge(potatoes.getMaxAge());
        var target=crop(f,mature);
        var neighbor=target.south();
        var immature=potatoes.getStateForAge(2);
        f.l.setBlockAndUpdate(neighbor.below(),Blocks.FARMLAND.defaultBlockState());
        f.l.setBlockAndUpdate(neighbor,immature);
        f.s.setItem(30,new ItemStack(Items.WATER_BUCKET));
        int trials=0;
        for(int i=0;i<800&&count(f,Items.POISONOUS_POTATO)==0;i++){
            f.s.setItem(27,new ItemStack(Items.WOODEN_HOE));
            f.l.setBlockAndUpdate(target,mature);
            step(f,4);
            check(f.l.getBlockState(target)==potatoes.getStateForAge(0),"a potato was not reserved for replanting");
            check(f.l.getBlockState(neighbor)==immature,"harvesting potatoes changed the adjacent crop");
            check(count(f,Items.POTATO)==++trials,"wooden hoe did not credit exactly one product");
        }
        check(count(f,Items.POISONOUS_POTATO)==1,"vanilla poisonous-potato subproduct was not retained");
        h.succeed();
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
    private static BlockPos hive(F f,boolean safe){
        var p=f.p.east().above();
        f.l.setBlock(p,Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL,5),3);
        if(safe)f.l.setBlock(p.below(),Blocks.CAMPFIRE.defaultBlockState(),3);
        var occupant=EntityType.BEE.create(f.l);
        ((net.minecraft.world.level.block.entity.BeehiveBlockEntity)f.l.getBlockEntity(p)).addOccupant(occupant,false);
        return p;
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void beekeeper_bottles_before_shears_no_lost_consumables(GameTestHelper h){
        var f=fixture(h,Profession.BEEKEEPER);
        f.s.setItem(0,new ItemStack(Items.GLASS_BOTTLE,2));
        f.s.setItem(1,new ItemStack(Items.SHEARS));
        var pos=hive(f,true);
        for(int i=0;i<2;i++){
            f.l.setBlock(pos,f.l.getBlockState(pos).setValue(BeehiveBlock.HONEY_LEVEL,5),3);
            step(f,1);
        }
        check(count(f,Items.HONEY_BOTTLE)==2&&count(f,Items.HONEYCOMB)==0&&f.s.getItem(28).isEmpty(),"bottle priority failed");
        f.l.setBlock(pos,f.l.getBlockState(pos).setValue(BeehiveBlock.HONEY_LEVEL,5),3);
        step(f,1);
        check(count(f,Items.HONEYCOMB)==3&&f.l.getBlockState(pos).is(Blocks.BEEHIVE)
            &&f.s.getItem(27).getDamageValue()==1,"honeycomb, shears or hive preservation");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="apiary_outputs")
    public static void beekeeper_full_inventory_preserves_bottle_and_honey_then_collects_without_smoke(GameTestHelper h){
        var f=fixture(h,Profession.BEEKEEPER);
        f.s.setItem(28,new ItemStack(Items.GLASS_BOTTLE));
        var pos=hive(f,false);
        for(int i=0;i<27;i++)f.s.setItem(i,new ItemStack(Items.STONE,64));
        step(f,1);
        check(f.l.getBlockState(pos).getValue(BeehiveBlock.HONEY_LEVEL)==5&&f.s.getItem(28).getCount()==1,"full inventory lost honey/bottle");
        f.s.setItem(0,ItemStack.EMPTY);
        step(f,1);
        check(count(f,Items.HONEY_BOTTLE)==1&&f.s.getItem(28).isEmpty()
            &&f.l.getBlockState(pos).getValue(BeehiveBlock.HONEY_LEVEL)==0,"smokeless collection failed");
        h.succeed();
    }

    private static Bee apiaryBee(F f,int remaining){
        var bee=animal(f,EntityType.BEE,false);
        bee.setPos(f.p.getX()+1.25,f.p.getY()+1.25,f.p.getZ()+1.25);
        var attempt=new CompoundTag();
        attempt.putUUID("station",f.s.id);
        attempt.putInt("remaining",remaining);
        bee.getPersistentData().put("KCApiaryAttempt",attempt);
        return bee;
    }

    @GameTest(template="empty",timeoutTicks=50,batch="apiary_tame")
    public static void beekeeper_bonds_bees_after_one_three_and_five_flowers(GameTestHelper h){
        var f=fixture(h,Profession.BEEKEEPER);
        for(int required:new int[]{1,3,5}){
            var bee=apiaryBee(f,required);
            f.s.setItem(29,new ItemStack(Items.DANDELION,required-1));
            step(f,Math.max(1,required-1));
            check(!bee.getPersistentData().hasUUID("KCApiary")&&f.s.getItem(29).isEmpty(),"Early bond or wrong flower consumption");
            var serialized=bee.saveWithoutId(new CompoundTag());
            bee.load(serialized);
            check(bee.getPersistentData().getCompound("KCApiaryAttempt").getInt("remaining")==1,"Incomplete attempt was lost");
            f.s.setItem(29,new ItemStack(Items.DANDELION));
            step(f,1);
            check(bee.getPersistentData().getUUID("KCApiary").equals(f.s.id)&&f.s.getItem(29).isEmpty(),"Final flower did not bond bee");
        }
        var excess=apiaryBee(f,1);
        f.s.setItem(29,new ItemStack(Items.DANDELION));
        step(f,1);
        check(!excess.getPersistentData().hasUUID("KCApiary")&&f.s.getItem(29).getCount()==1,"Bond overflow");
        var restored=EmploymentData.load(EmploymentData.get(f.l).save(new CompoundTag())).station(f.s.id);
        check(restored.industry.getCompound("apiary_bees").size()==3,"Bee roster did not survive saving");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=180,batch="apiary_breeding")
    public static void beekeeper_breeds_bees_without_destroying_hives(GameTestHelper h){
        var f=fixture(h,Profession.BEEKEEPER);
        var first=apiaryBee(f,1);
        var second=apiaryBee(f,1);
        f.s.setItem(29,new ItemStack(Items.DANDELION,4));
        step(f,3);
        check(first.isInLove()&&second.isInLove()&&f.s.getItem(29).isEmpty(),"Natural breeding was not requested");
        first.setNoAi(false);
        second.setNoAi(false);
        var bounds=IndustrialWork.area(f.l,f.s).inflate(4);
        h.succeedWhen(()->{
            var chicks=f.l.getEntitiesOfClass(Bee.class,bounds,Bee::isBaby);
            check(chicks.size()==1&&f.s.industry.getCompound("apiary_bees").size()==3
                &&chicks.get(0).getPersistentData().getUUID("KCApiary").equals(f.s.id),"Waiting for the natural linked offspring");
        });
    }

    @GameTest(template="empty",timeoutTicks=50,batch="apiary_area")
    public static void apiary_dimensions_and_hive_limits_follow_manual_selection(GameTestHelper h){
        var f=fixture(h,Profession.BEEKEEPER);
        check(IndustrialRules.size(f.l,f.s)==7&&!IndustrialRules.selectSize(f.l,f.s,5)
            &&!IndustrialRules.selectSize(f.l,f.s,9),"Wrong initial or locked apiary width");
        f.s.industry.putInt("size",5);
        check(IndustrialRules.size(f.l,f.s)==7&&f.s.industry.getInt("size")==7,"Old apiary width did not migrate");
        var near=hive(f,true);
        var extra=f.p.west().above();
        for(var alternative:List.of(extra,f.p.north().above(),f.p.south().above(),
                f.p.north(2).above(),f.p.south(2).above()))
            f.l.setBlock(alternative,Blocks.BEE_NEST.defaultBlockState(),3);
        step(f,1);
        check(f.s.industry.getList("apiary_hives",net.minecraft.nbt.Tag.TAG_LONG).size()==1,"Lv1 exceeded one hive");
        for(int lv=1;lv<=5;lv++){
            int selected=IndustrialRules.size(f.l,f.s);
            level(f,lv,25*(lv-1));
            check(IndustrialRules.size(f.l,f.s)==selected,"Level increased selected area");
            int next=IndustrialRules.maximumSize(Profession.BEEKEEPER,lv);
            check(IndustrialRules.selectSize(f.l,f.s,next),"Cannot select unlocked area");
            var area=IndustrialWork.area(f.l,f.s);
            check(area.getXsize()==next&&area.getZsize()==next&&area.getYsize()==5&&area.minY==f.p.getY()+1,"Incorrect apiary bounds");
            step(f,1);
            for(int tier=1;tier<=lv;tier++){
                check(IndustrialRules.selectSize(f.l,f.s,5+2*tier),"Cannot select legal width");
                step(f,1);
                check(f.s.industry.getList("apiary_hives",Tag.TAG_LONG).size()==tier,"Wrong hive cap at level "+lv+" / tier "+tier);
            }
        }
        f.l.removeBlock(near,false);
        step(f,1);
        var hives=f.s.industry.getList("apiary_hives",net.minecraft.nbt.Tag.TAG_LONG);
        check(hives.size()==5&&!hives.contains(net.minecraft.nbt.LongTag.valueOf(near.asLong()))
            &&f.l.getBlockState(extra).is(Blocks.BEE_NEST),"Destroyed hive was retained or nest ignored");
        f.s.industry.putInt("size",13);
        check(IndustrialRules.size(f.l,f.s)==13,"Migrating a width still available changed it");
        check(IndustrialRules.selectSize(f.l,f.s,7),"Larger apiary could not select a smaller width");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100,batch="apiary_nectar")
    public static void apiary_uses_vanilla_nectar_and_protects_only_its_bonded_bees(GameTestHelper h){
        var f=fixture(h,Profession.BEEKEEPER);
        f.l.setRainLevel(0);
        var bee=apiaryBee(f,1);
        var wild=apiaryBee(f,1);
        wild.getPersistentData().remove("KCApiaryAttempt");
        wild.getPersistentData().putUUID("KCApiary",UUID.randomUUID());
        f.s.setItem(29,new ItemStack(Items.DANDELION));
        step(f,1);
        check(bee.getPersistentData().getUUID("KCApiary").equals(f.s.id),"Bonded bee fixture");
        check(!wild.getPersistentData().getUUID("KCApiary").equals(f.s.id),"Foreign bee was adopted");
        var pos=f.p.east().above();
        f.l.setBlock(pos,Blocks.BEE_NEST.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL,4),3);
        var hive=(net.minecraft.world.level.block.entity.BeehiveBlockEntity)f.l.getBlockEntity(pos);
        var nectar=bee.saveWithoutId(new CompoundTag());
        nectar.putBoolean("HasNectar",true);
        bee.load(nectar);
        hive.addOccupantWithPresetTicks(bee,true,2401);
        h.startSequence().thenWaitUntil(()->{
            check(f.l.getBlockState(pos).getValue(BeehiveBlock.HONEY_LEVEL)==5,"Waiting for vanilla nectar deposit");
        }).thenExecute(()->{
            f.s.setItem(28,new ItemStack(Items.GLASS_BOTTLE));
            step(f,1);
            check(count(f,Items.HONEY_BOTTLE)==1,"Vanilla nectar did not produce the bottle");
            var linked=f.l.getEntitiesOfClass(Bee.class,IndustrialWork.area(f.l,f.s).inflate(8),
                b->b.getPersistentData().hasUUID("KCApiary")&&b.getPersistentData().getUUID("KCApiary").equals(f.s.id));
            check(linked.size()==1&&!linked.get(0).isAngry(),"Bonded bee duplication or attack");
            var bonded=linked.get(0);
            check(((br.com.kraftcity.industry.ApiaryBeeAppearance)bonded).kraftcity$isBonded(),"Stored bee lost its visual bond");
            bonded.setTarget(f.v);
            check(bonded.getTarget()!=f.v,"Bonded bee could reselect its own worker");
            var health=f.v.getHealth();
            bonded.doHurtTarget(f.v);
            check(f.v.getHealth()==health&&!bonded.hasStung(),"Bonded bee could sting during its own harvest");
            check(wild.isAngry()||wild.getTarget()==f.v,"Foreign bee lost its ability to retaliate");
        }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=360,batch="apiary_hive_path")
    public static void apiary_walks_around_a_wall_to_reach_a_hive(GameTestHelper h){
        var f=fixture(h,Profession.BEEKEEPER);
        buildWalkableFloor(f,ResidenceData.get(f.l).owner(f.v.getUUID()).pos);
        var near=Vec3.atBottomCenterOf(f.p.above());
        var pos=f.p.east(2).above();
        f.v.setPos(near);
        for(var at:BlockPos.betweenClosed(f.p.offset(1,1,-1),f.p.offset(1,3,1)))
            f.l.setBlock(at,Blocks.STONE.defaultBlockState(),3);
        f.l.setBlock(pos,Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL,5),3);
        ((net.minecraft.world.level.block.entity.BeehiveBlockEntity)f.l.getBlockEntity(pos))
            .addOccupant(EntityType.BEE.create(f.l),false);
        f.s.setItem(28,new ItemStack(Items.GLASS_BOTTLE));
        step(f,1);
        check(f.s.getItem(28).getCount()==1,"Worker harvested through a wall");
        f.v.setNoAi(false);
        h.succeedWhen(()->check(count(f,Items.HONEY_BOTTLE)==1&&f.v.position().distanceToSqr(near)>1,
            "Waiting for a physical approach around the wall"));
    }

    @GameTest(template="empty",timeoutTicks=60,batch="apiary_held_tools")
    public static void apiary_hand_prefers_flower_then_bottle_then_shears(GameTestHelper h){
        var f=fixture(h,Profession.BEEKEEPER);
        f.v.setPos(Vec3.atBottomCenterOf(f.p.above()));
        var shears=new ItemStack(Items.SHEARS);
        shears.setDamageValue(7);
        f.s.setItem(27,shears);
        step(f,1);
        check(f.v.getMainHandItem().is(Items.SHEARS)&&shears.getDamageValue()==7,"Idle shears not shown");
        f.s.setItem(28,new ItemStack(Items.GLASS_BOTTLE,3));
        step(f,1);
        check(f.v.getMainHandItem().is(Items.GLASS_BOTTLE)&&f.s.getItem(28).getCount()==3,"Bottle visual priority changed stock");
        var bee=apiaryBee(f,3);
        f.s.setItem(29,new ItemStack(Items.DANDELION));
        step(f,1);
        check(f.v.getMainHandItem().is(Items.DANDELION)&&f.s.getItem(29).isEmpty(),"Feeding did not show and consume flower");
        check(!((br.com.kraftcity.industry.ApiaryBeeAppearance)bee).kraftcity$isBonded(),"Unfinished bond changed appearance");
        var saved=f.v.saveWithoutId(new CompoundTag());
        check(ItemStack.of(saved.getList("HandItems",net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0)).isEmpty(),
            "Visual mirror became durable equipment in the saved villager");
        h.runAfterDelay(10,()->{
            step(f,1);
            check(f.v.getMainHandItem().is(Items.GLASS_BOTTLE),"Hand did not return to bottle after the swing");
            f.s.setItem(28,ItemStack.EMPTY);
            f.s.setItem(27,ItemStack.EMPTY);
            step(f,1);
            check(f.v.getMainHandItem().isEmpty(),"Missing supplies left a phantom display");
            h.succeed();
        });
    }

    @GameTest(template="empty",timeoutTicks=420,batch="apiary_lure")
    public static void apiary_lures_at_ten_blocks_and_bonds_only_inside(GameTestHelper h){
        var f=fixture(h,Profession.BEEKEEPER);
        buildWalkableFloor(f,ResidenceData.get(f.l).owner(f.v.getUUID()).pos);
        f.l.setRainLevel(0);
        var start=Vec3.atBottomCenterOf(f.p.above());
        f.v.setPos(start);
        var bee=apiaryBee(f,1);
        bee.setPos(start.add(11,0,0));
        f.s.setItem(29,new ItemStack(Items.DANDELION));
        step(f,1);
        check(f.v.getMainHandItem().isEmpty()&&f.s.getItem(29).getCount()==1,"Detected eleven blocks");
        bee.setPos(start.add(10,0,0));
        step(f,1);
        check(f.v.getMainHandItem().is(Items.DANDELION)&&f.s.getItem(29).getCount()==1,"Ten-block lure failed or fed outside");
        bee.setNoAi(false);
        var area=IndustrialWork.area(f.l,f.s);
        h.succeedWhen(()->check(((br.com.kraftcity.industry.ApiaryBeeAppearance)bee).kraftcity$isBonded()
            &&area.contains(bee.position())&&area.contains(f.v.position())&&f.s.getItem(29).isEmpty(),
            "Waiting for the bee to fly into the apiary for one flower: "+bee.position()));
    }

    @GameTest(template="empty",timeoutTicks=1000,batch="apiary_scheduled_no_hive")
    public static void apiary_scheduled_taming_without_hive_around_obstacle(GameTestHelper h){
        scheduledTaming(h,Items.POPPY,5,false,true);
    }

    @GameTest(template="empty",timeoutTicks=1000,batch="apiary_scheduled_hive")
    public static void apiary_scheduled_taming_with_hive(GameTestHelper h){
        scheduledTaming(h,Items.DANDELION,9,true,false);
    }

    @GameTest(template="empty",timeoutTicks=1000,batch="apiary_scheduled_close")
    public static void apiary_scheduled_taming_at_two_blocks(GameTestHelper h){
        scheduledTaming(h,Items.CORNFLOWER,2,false,false);
    }

    @GameTest(template="empty",timeoutTicks=160,batch="apiary_scheduled_protection")
    public static void apiary_taming_uses_flower_in_forge_interaction(GameTestHelper h){
        var f=fixture(h,Profession.BEEKEEPER);
        var start=Vec3.atBottomCenterOf(f.p.above());
        f.v.setPos(start);
        var bee=apiaryBee(f,1);
        f.s.setItem(27,new ItemStack(Items.SHEARS));
        f.s.setItem(29,new ItemStack(Items.POPPY));
        var actualItem=new boolean[1];
        java.util.function.Consumer<net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract> protection=event->{
            if(event.getTarget()!=bee)return;
            actualItem[0]=event.getItemStack().is(Items.POPPY);
            if(!actualItem[0])event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL,false,
            net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract.class,protection);
        try{
            step(f,1);
            check(actualItem[0]&&((ApiaryBeeAppearance)bee).kraftcity$isBonded(),"Forge did not see the offered flower");
        }finally{
            MinecraftForge.EVENT_BUS.unregister(protection);
        }
        h.succeed();
    }

    private static void scheduledTaming(GameTestHelper h,Item flower,int distance,boolean withHive,boolean obstacle){
        var f=fixture(h,Profession.BEEKEEPER);
        f.l.setRainLevel(0);
        f.l.setThunderLevel(0);
        check(IndustrialRules.selectSize(f.l,f.s,7),"Could not select the initial apiary");
        for(var floor:BlockPos.betweenClosed(f.p.offset(-4,-1,-5),f.p.offset(12,-1,5)))
            f.l.setBlock(floor,Blocks.STONE.defaultBlockState(),3);
        if(withHive)f.l.setBlock(f.p.north(2).above(),Blocks.BEEHIVE.defaultBlockState(),3);
        if(obstacle)for(int y=0;y<=2;y++)f.l.setBlock(f.p.offset(2,y,1),Blocks.STONE.defaultBlockState(),3);
        var start=Vec3.atBottomCenterOf(f.p.south());
        f.v.setPos(start);
        var bee=EntityType.BEE.create(f.l);
        bee.setPos(start.add(distance,.25,0));
        bee.setNoAi(true);
        f.l.addFreshEntity(bee);
        check(!obstacle||!f.v.hasLineOfSight(bee),"Wall failed to hide the bee");
        h.startSequence().thenIdle(45).thenExecute(()->{
            check(!f.v.getMainHandItem().is(flower)&&!bee.getPersistentData().contains("KCApiaryAttempt"),"Taming began with an empty flower slot");
            f.s.setItem(29,new ItemStack(flower,64));
        }).thenWaitUntil(()->check(f.v.getMainHandItem().is(flower),"Waiting for visible flower"))
            .thenExecute(()->{bee.setNoAi(false);f.v.setNoAi(false);})
            .thenWaitUntil(()->{
                check(((ApiaryBeeAppearance)bee).kraftcity$isBonded(),"Waiting for scheduled taming: "+bee.position()+" worker="+f.v.position());
                check(IndustrialWork.area(f.l,f.s).contains(bee.position()),"Bee bonded outside");
                int remaining=f.s.getItem(29).getCount();
                check(remaining>=59&&remaining<=63&&f.s.industry.getCompound("apiary_bees").size()==1,
                    "Expected 1–5 flowers and one recorded bee");
            }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=1200,batch="apiary_three_wild")
    public static void three_wild_bees_with_mob_griefing_disabled(GameTestHelper h){
        var f=fixture(h,Profession.BEEKEEPER);
        buildWalkableFloor(f,ResidenceData.get(f.l).owner(f.v.getUUID()).pos);
        f.l.setRainLevel(0);
        var start=Vec3.atBottomCenterOf(f.p.above());
        f.v.setPos(start);
        var rule=f.l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
        rule.set(false,f.l.getServer());
        h.runAfterDelay(1150,()->rule.set(true,f.l.getServer()));
        var bees=new ArrayList<Bee>();
        for(var offset:List.of(new Vec3(2,.1,0),new Vec3(0,.15,2),new Vec3(1,.25,-2))){
            var bee=EntityType.BEE.create(f.l);bee.setPos(start.add(offset));f.l.addFreshEntity(bee);bees.add(bee);
        }
        f.s.setItem(29,new ItemStack(Items.POPPY,64));
        h.startSequence().thenWaitUntil(()->{
            check(bees.stream().allMatch(bee->((ApiaryBeeAppearance)bee).kraftcity$isBonded()),"Waiting for three natural bonds");
            int consumed=64-f.s.getItem(29).getCount();
            check(consumed>=3&&consumed<=15&&f.s.industry.getCompound("apiary_bees").size()==3,"Incorrect slots or population");
        }).thenExecute(()->{
            var excess=EntityType.BEE.create(f.l);excess.setPos(start.add(-2,0,0));f.l.addFreshEntity(excess);bees.add(excess);
        }).thenIdle(75).thenExecute(()->{
            check(!((ApiaryBeeAppearance)bees.get(3)).kraftcity$isBonded()&&!bees.get(3).getPersistentData().contains("KCApiaryAttempt"),"Fourth bee interacted at capacity");
            rule.set(true,f.l.getServer());
            System.out.println("KC127_THREE_NORMAL_BONDS_AT_CAPACITY_WITHOUT_MOB_GRIEFING");
        }).thenSucceed();
    }


    @GameTest(template="empty",timeoutTicks=230,batch="apiary_roster_repair")
    public static void apiary_repairs_missing_records_without_touching_live_or_housed_bees(GameTestHelper h){
        var f=fixture(h,Profession.BEEKEEPER);
        f.l.setRainLevel(0);
        var worker=Vec3.atBottomCenterOf(f.p.above());
        f.v.setPos(worker);
        var live=apiaryBee(f,1);
        f.s.setItem(29,new ItemStack(Items.POPPY,2));
        step(f,1);
        var housed=apiaryBee(f,1);
        step(f,1);
        var liveId=live.getPersistentData().getUUID("KCApiaryBee");
        var housedId=housed.getPersistentData().getUUID("KCApiaryBee");
        var hivePos=f.p.east().above();
        f.l.setBlock(hivePos,Blocks.BEEHIVE.defaultBlockState(),3);
        ((BeehiveBlockEntity)f.l.getBlockEntity(hivePos)).addOccupant(housed,false);
        var orphan=UUID.randomUUID();
        var roster=f.s.industry.getCompound("apiary_bees");
        roster.putBoolean(orphan.toString(),true);
        var command="kraftcity apiario reparar "+f.p.getX()+" "+f.p.getY()+" "+f.p.getZ()+" ";
        var server=f.l.getServer();
        var source=server.createCommandSourceStack().withLevel(f.l).withSuppressedOutput();
        var area=IndustrialWork.area(f.l,f.s).inflate(8);
        var first=new ChunkPos(new BlockPos(area.minX,0,area.minZ));
        var last=new ChunkPos(new BlockPos(area.maxX,0,area.maxZ));
        h.startSequence().thenWaitUntil(()->{
            for(int x=first.x;x<=last.x;x++)for(int z=first.z;z<=last.z;z++)
                check(f.l.getChunkSource().getChunkNow(x,z)!=null&&f.l.areEntitiesLoaded(ChunkPos.asLong(x,z)),
                    "Waiting for apiary surroundings");
        }).thenExecute(()->{
            check(server.getCommands().performPrefixedCommand(source,command+orphan+" "+liveId)==0&&roster.size()==3,
                "Repair removed a loaded bee or partially applied");
            check(server.getCommands().performPrefixedCommand(source,command+housedId)==0&&roster.size()==3,
                "Repair removed a bee inside a hive");
            check(server.getCommands().performPrefixedCommand(source,command+orphan)==1&&roster.size()==2
                &&f.s.industry.getCompound("apiary_retired_bees").contains(orphan.toString()),"Missing bond was not archived");
            var arriving=apiaryBee(f,1);
            arriving.setPos(worker.add(0,.35,1.2));
            f.s.setItem(29,new ItemStack(Items.DANDELION));
            step(f,1);
            check(((ApiaryBeeAppearance)arriving).kraftcity$isBonded()&&f.s.getItem(29).isEmpty(),
                "Archived record still prevents taming");
        }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=350,batch="kc130_idle")
    public static void idle_industrial_worker_walks_and_stops_for_work(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        buildWalkableFloor(f,ResidenceData.get(f.l).owner(f.v.getUUID()).pos);
        var start=Vec3.atBottomCenterOf(f.p.above());
        f.v.setPos(start);
        f.v.setNoAi(false);
        f.s.setItem(27,new ItemStack(Items.IRON_HOE));
        var area=IndustrialWork.area(f.l,f.s);
        h.startSequence().thenWaitUntil(()->{
            check(area.contains(f.v.position()),"Idle walk escaped professional area");
            check(f.v.distanceToSqr(start)>1,"Waiting for a spontaneous industrial walk");
        }).thenExecute(()->{
            var crop=f.p.east(2).above();
            f.l.setBlock(crop.below(),Blocks.FARMLAND.defaultBlockState(),3);
            f.l.setBlock(crop,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),3);
        }).thenWaitUntil(()->check(count(f,Items.WHEAT)>0||
            !f.l.getBlockState(f.p.east(2).above()).is(Blocks.WHEAT),"Idle path blocked a ripe crop"))
          .thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=90,batch="kc130_bond")
    public static void apiary_worker_death_releases_colony_and_replacement_tames_same_bees(GameTestHelper h){
        var f=fixture(h,Profession.BEEKEEPER);
        var start=Vec3.atBottomCenterOf(f.p.above());
        f.v.setPos(start);
        var colony=new ArrayList<Bee>();
        for(int i=0;i<3;i++){
            var bee=apiaryBee(f,1);
            f.s.setItem(29,new ItemStack(Items.POPPY));
            step(f,1);
            check(((ApiaryBeeAppearance)bee).kraftcity$isBonded(),"Initial colony failed");
            colony.add(bee);
        }
        var hivePos=f.p.north().above();
        f.l.setBlock(hivePos,Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.FACING,Direction.SOUTH),3);
        var hive=(BeehiveBlockEntity)f.l.getBlockEntity(hivePos);
        hive.addOccupant(colony.get(2),false);
        var home=ResidenceData.get(f.l).owner(f.v.getUUID()).pos;
        f.v.hurt(net.minecraft.world.damagesource.DamageSource.OUT_OF_WORLD,200);
        check(f.s.worker==null&&f.s.industry.getCompound("apiary_bees").isEmpty(),"Death retained a colony");
        for(int i=0;i<2;i++)check(colony.get(i).isAlive()&&!((ApiaryBeeAppearance)colony.get(i)).kraftcity$isBonded(),
            "Living bee was lost or kept its owner's bond");
        check(hive.getOccupantCount()==1,"Death expelled a resident bee");
        var replacement=EntityType.VILLAGER.create(f.l);
        replacement.setNoAi(true);replacement.setPos(start);f.l.addFreshEntity(replacement);
        check(ResidenceData.get(f.l).link(f.l,home,replacement).equals("ok"),"Replacement home");
        var player=FakePlayerFactory.get(f.l,new GameProfile(UUID.randomUUID(),"ApiaryReplacement"));
        player.setPos(start);f.s.setItem(31,new ItemStack(Items.EMERALD,64));
        check(Employment.hire(player,f.s,replacement.getUUID(),Profession.BEEKEEPER).equals("ok"),"Replacement hire");
        hive.emptyAllLivingFromHive(null,hive.getBlockState(),BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
        var released=f.l.getEntitiesOfClass(Bee.class,new AABB(hivePos).inflate(2),bee->!colony.contains(bee));
        check(released.size()==1&&!((ApiaryBeeAppearance)released.get(0)).kraftcity$isBonded(),"Stored bee did not return wild");
        f.s.setItem(29,new ItemStack(Items.POPPY,16));
        for(int i=0;i<15;i++)IndustrialWork.tick(f.l,f.s);
        check(f.s.industry.getCompound("apiary_bees").size()==3&&released.get(0).getPersistentData()
            .getUUID("KCApiaryWorker").equals(replacement.getUUID()),"Replacement failed to tame the former colony");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=270,batch="kc130_hive_stock")
    public static void apiary_saves_occupied_nest_and_replaces_it_on_an_isolated_fence(GameTestHelper h){
        var f=fixture(h,Profession.BEEKEEPER);
        buildWalkableFloor(f,ResidenceData.get(f.l).owner(f.v.getUUID()).pos);
        var start=Vec3.atBottomCenterOf(f.p.above());
        f.v.setPos(start);
        var active=f.p.east().above();
        var extra=f.p.east(2).above();
        var fence=f.p.south(2).above();
        f.l.setBlock(active,Blocks.BEEHIVE.defaultBlockState(),3);
        f.l.setBlock(extra,Blocks.BEE_NEST.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL,3),3);
        var honey=(BeehiveBlockEntity)f.l.getBlockEntity(extra);
        honey.addOccupant(EntityType.BEE.create(f.l),false);
        step(f,1);
        f.v.setNoAi(false);
        h.startSequence().thenWaitUntil(()->{
            check(f.l.isEmptyBlock(extra)&&count(f,Items.BEE_NEST)==1,"Waiting for occupied nest to reach stock");
            check(f.l.getBlockState(active).is(Blocks.BEEHIVE),"The active hive was removed");
        }).thenExecute(()->{
            var saved=f.s.getItem(0);
            check(saved.getTagElement("BlockEntityTag").getList("Bees",Tag.TAG_COMPOUND).size()==1,"Nest occupant lost");
            check(saved.getTagElement("BlockStateTag").getString("honey_level").equals("3"),"Nest honey lost");
            level(f,2,25);
            f.l.setBlock(fence,Blocks.OAK_FENCE.defaultBlockState(),3);
        }).thenIdle(30).thenExecute(()->{
            check(f.l.isEmptyBlock(fence.above()),"Promotion alone placed a second hive");
            check(IndustrialRules.selectSize(f.l,f.s,9),"Lv2 could not select 9x9");
            f.l.setBlock(fence.east(),Blocks.OAK_FENCE.defaultBlockState(),3);
        }).thenIdle(30).thenExecute(()->{
            check(f.l.isEmptyBlock(fence.above())&&f.l.isEmptyBlock(fence.east().above()),"A connected fence was used");
            f.l.removeBlock(fence.east(),false);
        }).thenWaitUntil(()->{
            check(f.l.getBlockState(fence.above()).is(Blocks.BEE_NEST),"Waiting for an isolated fence placement");
            check(count(f,Items.BEE_NEST)==0&&f.l.getBlockState(fence.above()).getValue(BeehiveBlock.HONEY_LEVEL)==3
                &&((BeehiveBlockEntity)f.l.getBlockEntity(fence.above())).getOccupantCount()==1,"Placement lost data or duplicated stock");
        }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=450,batch="kc131_hive_reduction")
    public static void apiary_shrinks_waits_for_its_bee_and_recognizes_existing_hives_on_expansion(GameTestHelper h){
        var f=fixture(h,Profession.BEEKEEPER);
        buildWalkableFloor(f,ResidenceData.get(f.l).owner(f.v.getUUID()).pos);
        f.v.setPos(Vec3.atBottomCenterOf(f.p.above()));
        level(f,3,50);
        check(IndustrialRules.selectSize(f.l,f.s,11),"Select Lv3 area");
        var near=f.p.west().above();
        var inner=f.p.east(2).above();
        var outer=f.p.north(5).above();
        for(var pos:List.of(near,inner,outer))f.l.setBlock(pos,Blocks.BEE_NEST.defaultBlockState(),3);
        f.l.setBlock(inner,f.l.getBlockState(inner).setValue(BeehiveBlock.HONEY_LEVEL,3),3);
        var bee=apiaryBee(f,1);
        f.s.setItem(29,new ItemStack(Items.POPPY));
        step(f,1);
        var bond=bee.getPersistentData().getUUID("KCApiaryBee");
        ApiaryWork.recordHive(f.l,bee.getPersistentData(),inner);
        check(IndustrialRules.selectSize(f.l,f.s,7),"Shrink apiary");
        f.v.setNoAi(false);
        h.startSequence().thenIdle(90).thenExecute(()->{
            var registered=f.s.industry.getList("apiary_hives",Tag.TAG_LONG);
            check(registered.size()==1&&registered.get(0).equals(net.minecraft.nbt.LongTag.valueOf(near.asLong())),
                "Shrink did not preserve nearest nest");
            check(f.l.getBlockState(inner).is(Blocks.BEE_NEST)&&bee.isAlive()&&count(f,Items.BEE_NEST)==0,
                "A nest was taken while its bee was outdoors");
            check(ApiaryWork.canDiscoverHive(f.l,bee,inner)&&!ApiaryWork.canDiscoverHive(f.l,bee,outer),
                "The habitual bee could not rediscover its nest or acquired a nest outside its area");
            ((BeehiveBlockEntity)f.l.getBlockEntity(inner)).addOccupant(bee,false);
        }).thenWaitUntil(()->{
            check(f.l.isEmptyBlock(inner)&&count(f,Items.BEE_NEST)==1,"Waiting for the returned bee and nest to reach stock");
            check(f.l.getBlockState(outer).is(Blocks.BEE_NEST),"Worker collected outside its current selection");
            var item=f.s.getItem(0);
            var occupant=item.getTagElement("BlockEntityTag").getList("Bees",Tag.TAG_COMPOUND).getCompound(0);
            check(occupant.getCompound("EntityData").getCompound("ForgeData").getUUID("KCApiaryBee").equals(bond)
                &&item.getTagElement("BlockStateTag").getString("honey_level").equals("3"),"Stored identity or honey lost");
        }).thenExecute(()->{
            f.l.setBlock(f.p.south(2).above(),Blocks.OAK_FENCE.defaultBlockState(),3);
            check(IndustrialRules.selectSize(f.l,f.s,11),"Expand without another promotion");
        }).thenWaitUntil(()->{
            var placed=f.p.south(2).above(2);
            check(f.l.getBlockState(placed).is(Blocks.BEE_NEST)&&count(f,Items.BEE_NEST)==0,"Waiting for stocked nest");
            check(f.s.industry.getList("apiary_hives",Tag.TAG_LONG).size()==3
                &&f.l.getBlockState(outer).is(Blocks.BEE_NEST),"Existing outer nest was not recognized");
            var hive=(BeehiveBlockEntity)f.l.getBlockEntity(placed);
            check(hive.getOccupantCount()==1&&f.l.getBlockState(placed).getValue(BeehiveBlock.HONEY_LEVEL)==3,"Replaced nest lost content");
        }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=700,batch="kc131_hive_craft")
    public static void apiary_crafts_only_for_open_capacity_and_consumes_exact_split_ingredients(GameTestHelper h){
        var f=fixture(h,Profession.BEEKEEPER);
        buildWalkableFloor(f,ResidenceData.get(f.l).owner(f.v.getUUID()).pos);
        f.v.setPos(Vec3.atBottomCenterOf(f.p.above()));
        f.l.setBlock(f.p.east().above(),Blocks.BEEHIVE.defaultBlockState(),3);
        f.s.setItem(0,new ItemStack(Items.HONEYCOMB,6));
        f.s.setItem(1,new ItemStack(Items.OAK_PLANKS,2));
        f.s.setItem(2,new ItemStack(Items.BIRCH_PLANKS));
        level(f,2,25);
        f.v.setNoAi(false);
        h.startSequence().thenIdle(90).thenExecute(()->{
            check(count(f,Items.BEEHIVE)==0&&count(f,Items.HONEYCOMB)==6,"Promotion alone authorized crafting");
            check(IndustrialRules.selectSize(f.l,f.s,9),"Expand to crafting capacity");
            f.s.setItem(2,ItemStack.EMPTY);
        }).thenIdle(45).thenExecute(()->{
            check(count(f,Items.BEEHIVE)==0&&count(f,Items.HONEYCOMB)==6,"Two planks authorized crafting");
            f.s.setItem(0,new ItemStack(Items.HONEYCOMB,5));
            f.s.setItem(2,new ItemStack(Items.BIRCH_PLANKS));
        }).thenIdle(45).thenExecute(()->{
            check(count(f,Items.BEEHIVE)==0&&count(f,Items.HONEYCOMB)==5,"Five combs authorized crafting");
            f.s.setItem(0,new ItemStack(Items.HONEYCOMB,10));
            f.s.setItem(3,new ItemStack(Items.BEE_NEST));
        }).thenIdle(90).thenExecute(()->{
            check(count(f,Items.HONEYCOMB)==10&&count(f,Items.BEEHIVE)==0,"Existing stored nest did not suppress crafting");
            f.s.setItem(3,ItemStack.EMPTY);
            f.s.setItem(1,new ItemStack(Items.OAK_PLANKS,4));
            for(int slot=2;slot<27;slot++)f.s.setItem(slot,new ItemStack(Items.COBBLESTONE,64));
        }).thenIdle(90).thenExecute(()->{
            check(count(f,Items.HONEYCOMB)==10&&count(f,Items.OAK_PLANKS)==4&&count(f,Items.BEEHIVE)==0,"Full storage lost resources");
            f.s.setItem(1,new ItemStack(Items.OAK_PLANKS,2));
            f.s.setItem(2,new ItemStack(Items.BIRCH_PLANKS));
        }).thenWaitUntil(()->{
            check(count(f,Items.BEEHIVE)==1&&count(f,Items.HONEYCOMB)==4,"Waiting for exactly one hive from mixed planks");
            check(count(f,Items.OAK_PLANKS)+count(f,Items.BIRCH_PLANKS)==0,"Recipe used the wrong number of planks");
        }).thenExecute(()->{
            // The player takes the first craft and supplies materials for another.
            for(int slot=0;slot<27;slot++)f.s.setItem(slot,ItemStack.EMPTY);
            f.s.setItem(0,new ItemStack(Items.HONEYCOMB,6));
            f.s.setItem(1,new ItemStack(Items.OAK_PLANKS,3));
        }).thenWaitUntil(()->{
            check(count(f,Items.BEEHIVE)==1&&count(f,Items.HONEYCOMB)==0,"Six reserved combs were not sufficient");
        }).thenExecute(()->{
            f.s.setItem(27,new ItemStack(Items.SHEARS));
            hive(f,true);
        }).thenWaitUntil(()->{
            check(count(f,Items.HONEYCOMB)==3,"First real harvest has not replenished the reserve");
        }).thenExecute(()->{
            var pos=f.p.east().above();
            f.l.setBlock(pos,f.l.getBlockState(pos).setValue(BeehiveBlock.HONEY_LEVEL,5),3);
        }).thenWaitUntil(()->{
            check(count(f,Items.HONEYCOMB)==6&&count(f,Items.BEEHIVE)==1,"Second real harvest or anti-duplication failed");
        }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=80,batch="kc131_comb_reserve")
    public static void apiary_discards_only_combs_above_the_shared_reserve(GameTestHelper h){
        var f=fixture(h,Profession.BEEKEEPER);
        var pos=f.p.west().above();
        f.l.setBlock(pos,Blocks.BARREL.defaultBlockState(),3);
        var barrel=(BarrelBlockEntity)f.l.getBlockEntity(pos);
        f.s.industry.putLong("discard",pos.asLong());
        f.v.setPos(Vec3.atBottomCenterOf(f.p.above()));
        for(int total:new int[]{0,4,6,10,20}){
            barrel.clearContent();
            f.s.setItem(0,new ItemStack(Items.HONEYCOMB,Math.min(4,total)));
            f.s.setItem(1,new ItemStack(Items.HONEYCOMB,Math.max(0,total-4)));
            f.s.setItem(2,new ItemStack(Items.BEE_NEST));
            IndustrialWork.deposit(f.l,f.s,f.v);
            check(count(f,Items.HONEYCOMB)==Math.min(total,6)&&barrel.countItem(Items.HONEYCOMB)==Math.max(0,total-6)
                &&count(f,Items.BEE_NEST)==1,"Reserve or conservation at "+total);
        }
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=200,batch="kc130_hive_cycle")
    public static void apiary_nectar_visit_shortens_only_for_a_bound_bee(GameTestHelper h){
        var f=fixture(h,Profession.BEEKEEPER);
        f.l.setRainLevel(0);f.l.setThunderLevel(0);f.l.setDayTime(1000);
        f.v.setPos(Vec3.atBottomCenterOf(f.p.above()));
        var tame=apiaryBee(f,1);
        f.s.setItem(29,new ItemStack(Items.POPPY));
        step(f,1);
        var id=tame.getPersistentData().getUUID("KCApiaryBee");
        var hivePos=f.p.east().above();
        var control=hivePos.east(4);
        for(var pos:List.of(hivePos,control))f.l.setBlock(pos,Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.FACING,Direction.SOUTH),3);
        var hive=(BeehiveBlockEntity)f.l.getBlockEntity(hivePos);
        var vanilla=(BeehiveBlockEntity)f.l.getBlockEntity(control);
        var tagged=tame.saveWithoutId(new CompoundTag());tagged.putString("id","minecraft:bee");tagged.putBoolean("HasNectar",true);
        hive.storeBee(tagged,0,true);tame.discard();
        var wild=EntityType.BEE.create(f.l).saveWithoutId(new CompoundTag());wild.putString("id","minecraft:bee");wild.putBoolean("HasNectar",true);
        vanilla.storeBee(wild,0,true);
        h.runAfterDelay(90,()->check(hive.getOccupantCount()==1,"Bonded bee left too early"));
        h.succeedWhen(()->{
            check(hive.isEmpty()&&vanilla.getOccupantCount()==1,"Normal visit or wild control");
            check(f.l.getBlockState(hivePos).getValue(BeehiveBlock.HONEY_LEVEL)>0
                &&f.l.getBlockState(control).getValue(BeehiveBlock.HONEY_LEVEL)==0,"Vanilla nectar release not preserved");
            var bee=f.l.getEntitiesOfClass(Bee.class,new AABB(hivePos).inflate(4),b->b.getPersistentData().hasUUID("KCApiaryBee")
                &&b.getPersistentData().getUUID("KCApiaryBee").equals(id)).stream().findFirst().orElseThrow();
            long outdoors=bee.getPersistentData().getLong(ApiaryWork.POLLINATE_AFTER)-f.l.getGameTime();
            check(outdoors>=2290&&outdoors<=2300,"Missing outdoor balance: "+outdoors);
        });
    }

    @GameTest(template="empty",timeoutTicks=80,batch="apiary_boundaries")
    public static void apiary_bonded_movement_is_bounded_on_all_six_faces(GameTestHelper h){
        var f=fixture(h,Profession.BEEKEEPER);
        var bee=apiaryBee(f,1);
        f.s.setItem(29,new ItemStack(Items.DANDELION));
        step(f,1);
        var area=IndustrialWork.area(f.l,f.s);
        check(((br.com.kraftcity.industry.ApiaryBeeAppearance)bee).kraftcity$isBonded(),"No test bond");
        for(var side:Direction.values()){
            bee.setPos(area.getCenter());
            bee.move(net.minecraft.world.entity.MoverType.SELF,new Vec3(side.getStepX()*12,side.getStepY()*12,side.getStepZ()*12));
            var bounds=bee.getBoundingBox();
            check(bounds.minX>=area.minX&&bounds.maxX<=area.maxX&&bounds.minY>=area.minY
                &&bounds.maxY<=area.maxY&&bounds.minZ>=area.minZ&&bounds.maxZ<=area.maxZ,"Bee flew past "+side);
        }
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void all_jobs_keep_shared_inventory_and_additive_save_data(GameTestHelper h){
        for(Profession p:Profession.values())if(p.sector==Profession.Sector.INDUSTRIAL){
            var f=fixture(h,p);f.s.setItem(26,new ItemStack(Items.DIAMOND,7));f.s.industry.putLong("fish_elapsed",42);f.s.industry.putBoolean("preview",true);
            var restored=EmploymentData.load(EmploymentData.get(f.l).save(new CompoundTag())).station(f.s.id);
            check(restored.getItem(26).getCount()==7&&restored.industry.getLong("fish_elapsed")==42&&restored.worker.equals(f.v.getUUID()),"persist "+p);
            var player=FakePlayerFactory.get(f.l,new GameProfile(UUID.randomUUID(),"SharedJob"));var a=new WorkMenu(1,player.getInventory(),f.s,false,null);var b=new WorkMenu(2,player.getInventory(),f.s,false,f.v.getUUID());
            int expectedSlots=switch(p){
                case FARMER,BEEKEEPER->66;
                case LUMBERJACK->65;
                default->68;
            };
            check(a.inventory==b.inventory&&a.slots.size()==expectedSlots&&b.slots.size()==expectedSlots,"dual menu "+p);
        }h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void farmer_xp_bar_tracks_cumulative_thresholds_and_maximum(GameTestHelper h){
        check(IndustrialRules.farmerXpBarWidth(1,0,182)==0,"empty experience");
        check(IndustrialRules.farmerXpBarWidth(2,38,182)==94,"proportional experience");
        check(IndustrialRules.farmerXpBarWidth(3,50,182)==0,"experience reset at level boundary");
        check(IndustrialRules.farmerXpBarWidth(5,100,182)==182
            &&IndustrialRules.farmerXpBarWidth(5,Long.MAX_VALUE,182)==182,"full maximum level bar");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void legacy_employment_schema_loads_without_reset_or_inventory_migration(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);f.s.setItem(0,new ItemStack(Items.APPLE,13));var tag=EmploymentData.get(f.l).save(new CompoundTag());
        for(Tag raw:tag.getList("stations",Tag.TAG_COMPOUND))((CompoundTag)raw).remove("industry");
        var old=EmploymentData.load(tag).station(f.s.id);check(old.getItem(0).getCount()==13&&old.industry.isEmpty()&&old.worker.equals(f.v.getUUID()),"schema1 compatibility");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void area_selection_respects_level_and_lv5_smaller_options(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        for(int lv=1;lv<=5;lv++){
            level(f,lv,0);
            check(IndustrialRules.size(f.l,f.s)==5,"level expanded the selected area");
            check(!IndustrialRules.selectSize(f.l,f.s,5+lv*2),"locked area accepted");
            check(IndustrialRules.selectSize(f.l,f.s,3+lv*2),"unlocked area rejected");
            check(IndustrialRules.size(f.l,f.s)==3+lv*2,"manual size did not take effect");
            check(IndustrialRules.selectSize(f.l,f.s,5),"cannot reselect the smallest area");
        }
        f.s.industry.remove("size");
        f.s.industry.putInt("zigzag_size",11);
        check(IndustrialRules.size(f.l,f.s)==11,"legacy active area lost during migration");
        h.succeed();
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
    public static void farmer_hoe_yield_uses_each_integer_in_its_material_range(GameTestHelper h){
        var hoes=List.of(Items.WOODEN_HOE,Items.STONE_HOE,Items.IRON_HOE,Items.GOLDEN_HOE,
            IndustrialTools.HOE.get(),Items.DIAMOND_HOE,Items.NETHERITE_HOE);
        for(int tier=0;tier<hoes.size();tier++){
            var tool=new ItemStack(hoes.get(tier));
            int maximum=tier+1;
            int draws=maximum*4000;
            int[] frequencies=new int[maximum+1];
            var random=net.minecraft.util.RandomSource.create(91+tier);
            for(int i=0;i<draws;i++){
                int amount=IndustrialTools.farmerYield(tool,random);
                check(amount>=1&&amount<=maximum,"out-of-range yield from "+tool);
                frequencies[amount]++;
            }
            for(int amount=1;amount<=maximum;amount++){
                check(Math.abs(frequencies[amount]-4000)<300,"skewed yield from "+tool+" for "+amount);
            }
        }
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void custom_tool_recipes_tiers_and_variants_are_registered(GameTestHelper h){
        check(IndustrialTools.tier(new ItemStack(IndustrialTools.HOE.get()))==3,"emerald tier");
        for(String id:List.of("emerald_hoe","emerald_axe","emerald_sword","wood_shears","gold_shears","emerald_shears","diamond_shears","netherite_shears","iron_fishing_rod","gold_fishing_rod","emerald_fishing_rod","diamond_fishing_rod","netherite_fishing_rod","stone_shears","stone_fishing_rod"))
            check(h.getLevel().getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation("residenciasaldeoes",id)).isPresent(),"missing recipe "+id);
        check(Items.SHEARS.getMaxDamage()==238&&Items.FISHING_ROD.getMaxDamage()==64,"vanilla overwritten");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void seven_materials_and_stone_stack_data(GameTestHelper h){
        var rows=IndustrialTools.matrix();
        var unique=new HashSet<Item>();
        for(var row:rows){
            check(row.size()==7,"Incomplete material coverage");
            check(row.stream().allMatch(unique::add),"Duplicate item in tool matrix");
        }
        check(rows.size()==5&&unique.size()==35&&rows.get(3).get(2)==Items.SHEARS
            &&rows.get(4).get(0)==Items.FISHING_ROD,"Missing tool or changed vanilla identity");
        var shears=new ItemStack(rows.get(3).get(1));
        var rod=new ItemStack(rows.get(4).get(1));
        for(var tool:List.of(shears,rod)){
            check(tool.getMaxDamage()==Tiers.STONE.getUses()&&tool.getItem().isValidRepairItem(tool,new ItemStack(Items.COBBLESTONE)),
                "Stone durability or repair material");
            tool.setDamageValue(10);
            tool.getOrCreateTag().putString("GameTest","stone");
            check(ItemStack.matches(tool,ItemStack.of(tool.save(new CompoundTag()))),"Stone tool lost NBT");
        }
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"StoneToolCrafting"));
        var menu=new net.minecraft.world.inventory.CraftingMenu(0,player.getInventory());
        var grid=new net.minecraft.world.inventory.CraftingContainer(menu,3,3);
        grid.setItem(1,new ItemStack(Items.COBBLESTONE));
        grid.setItem(3,new ItemStack(Items.COBBLESTONE));
        checkStoneRecipe(h,grid,"stone_shears",shears.getItem());
        grid.clearContent();
        for(int index:new int[]{2,4,6})grid.setItem(index,new ItemStack(Items.COBBLESTONE));
        for(int index:new int[]{5,8})grid.setItem(index,new ItemStack(Items.STRING));
        checkStoneRecipe(h,grid,"stone_fishing_rod",rod.getItem());
        h.succeed();
    }

    private static void checkStoneRecipe(GameTestHelper h,net.minecraft.world.inventory.CraftingContainer grid,String id,Item item){
        var recipe=h.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,grid,h.getLevel()).orElseThrow();
        check(recipe.getId().equals(new net.minecraft.resources.ResourceLocation("residenciasaldeoes",id))
            &&recipe.assemble(grid).is(item),"Stone recipe did not craft "+id);
    }

    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void shepherd_works_with_stone_shears(GameTestHelper h){
        var shears=new ItemStack(IndustrialTools.matrix().get(3).get(1));
        var shepherd=fixture(h,Profession.SHEPHERD);
        shepherd.s.setItem(27,shears);
        var sheep=animal(shepherd,EntityType.SHEEP,false);
        step(shepherd,1);
        check(sheep.isSheared()&&sheep.isAlive()&&shears.getDamageValue()>0,"Stone shears failed professional work");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void fisherman_works_with_stone_rod(GameTestHelper h){
        var rod=new ItemStack(IndustrialTools.matrix().get(4).get(1));
        var fisherman=fixture(h,Profession.FISHERMAN);
        fisherman.s.setItem(27,rod);
        water(fisherman);
        step(fisherman,IndustrialRules.fishingTicks(EmploymentConfig.FISH_TICKS.get(),1)/EmploymentConfig.ACTION_TICKS.get()+1);
        check(count(fisherman,Items.COD)+count(fisherman,Items.SALMON)+count(fisherman,Items.PUFFERFISH)
            +count(fisherman,Items.TROPICAL_FISH)==1&&rod.getDamageValue()>0,"Stone rod failed professional work");
        h.succeed();
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
                case LUMBERJACK->{
                    lumberTree(f,f.p.east(2),Blocks.OAK_LOG,Blocks.OAK_LEAVES,5);
                    f.v.setNoAi(false);
                }
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
        check(f.s.industry.getInt("zigzag_size")==5,"level reset the selected zigzag");
        check(IndustrialRules.selectSize(f.l,f.s,13),"level-five size still locked");
        step(f,1);
        check(f.s.industry.getInt("zigzag_size")==13,"zigzag not invalidated by manual resize");
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
        step(f,3);
        check(count(f,Items.WHEAT)>=1&&count(f,Items.WHEAT)<=3,"incorrect iron hoe yield");
        check(f.l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(center).inflate(1)).isEmpty(),"duplicate world drops");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void farmer_collects_mod_agnostic_ground_items_at_border(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        var at=f.p.east(3).above();
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
    public static void farmer_rejects_virtual_cane_and_fruit_keeps_roots_and_neighbor(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        f.s.setItem(27,new ItemStack(Items.IRON_HOE));f.s.setItem(30,new ItemStack(Items.WATER_BUCKET));
        var root=f.p.east().above();
        f.l.setBlock(root.below(),Blocks.SAND.defaultBlockState(),3);
        check(!Blocks.SUGAR_CANE.defaultBlockState().canSurvive(f.l,root),"bucket acted as adjoining water");
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
        step(f,2);
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
            step(f,3);
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
        boolean regular=kind instanceof CropBlock;
        int height=regular?1:0;
        var target=regular?f.p.above(2):f.p.offset(-2,1,-2);
        var start=f.p.offset(2,1+height,2);
        for(var floor:BlockPos.betweenClosed(f.p.offset(-4,-1,-4),f.p.offset(4,-1,4))){
            f.l.setBlock(floor,Blocks.STONE.defaultBlockState(),3);
        }
        var soil=Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7);
        var crop=kind instanceof CropBlock selected?selected:(CropBlock)Blocks.WHEAT;
        var stem=target.east();
        for(var base:FarmerWork.zigzag(f.p,5)){
            var pos=base.above(height);
            if(pos.below().equals(f.p))continue;
            if(regular&&!base.below().equals(f.p))f.l.setBlock(base.below(),Blocks.STONE.defaultBlockState(),3);
            f.l.setBlock(pos.below(),soil,3);
            if(pos.equals(target)||pos.equals(stem)&&!regular)continue;
            f.l.setBlock(pos,crop.getStateForAge(0),3);
        }
        if(kind instanceof CropBlock){
            // This raised layer is above the water bucket's irrigation height.
            // Feed both edges with actual water so moisture is stable during the audit.
            for(int z:new int[]{-3,3}){
                var water=f.p.offset(0,1,z);
                f.l.setBlock(water.below(),Blocks.STONE.defaultBlockState(),3);
                f.l.setBlock(water,Blocks.WATER.defaultBlockState(),3);
            }
            f.l.setBlock(target,crop.getStateForAge(crop.getMaxAge()),3);
        }else{
            f.l.setBlock(target.below(),Blocks.DIRT.defaultBlockState(),3);
            f.l.setBlock(target,kind.defaultBlockState(),3);
            var attached=kind==Blocks.MELON?Blocks.ATTACHED_MELON_STEM:Blocks.ATTACHED_PUMPKIN_STEM;
            f.l.setBlock(stem,attached.defaultBlockState().setValue(AttachedStemBlock.FACING,Direction.WEST),3);
        }
        var before=new HashMap<BlockPos,BlockState>();
        for(var base:FarmerWork.zigzag(f.p,5)){
            var pos=base.above(height);
            before.put(pos,f.l.getBlockState(pos));
            before.put(pos.below(),f.l.getBlockState(pos.below()));
        }
        f.s.setItem(27,new ItemStack(Items.IRON_HOE));
        for(int i=0;i<4;i++)f.s.setItem(i,new ItemStack(List.of(Items.WHEAT_SEEDS,Items.CARROT,Items.POTATO,Items.BEETROOT_SEEDS).get(i),4));
        f.s.setItem(30,new ItemStack(Items.WATER_BUCKET));
        var from=Vec3.atBottomCenterOf(start);
        f.v.setPos(from.x,from.y,from.z);
        f.v.setNoAi(false);
        Runnable audit=()->{
            for(var entry:before.entrySet()){
                var pos=entry.getKey();
                if(pos.equals(target))continue;
                var actual=f.l.getBlockState(pos);
                if(!(kind instanceof CropBlock)&&pos.equals(stem)){
                    check(actual.is(kind==Blocks.MELON?Blocks.MELON_STEM:Blocks.PUMPKIN_STEM)
                        &&actual.getValue(StemBlock.AGE)==7,"fruit harvest damaged the mature stem");
                }else{
                    var expected=entry.getValue();
                    boolean naturalGrowth=expected.getBlock() instanceof CropBlock adjacent
                        &&actual.getBlock()==adjacent&&actual.getValue(adjacent.getAgeProperty())>=expected.getValue(adjacent.getAgeProperty());
                    if(actual!=expected&&!naturalGrowth)com.mojang.logging.LogUtils.getLogger().error(
                        "After harvest at {} by worker at {}, neighbor {} changed from {} to {}",
                        target,f.v.position(),pos,entry.getValue(),actual);
                    check(actual==expected||naturalGrowth,"harvest changed neighbor "+pos+" from "+entry.getValue()+" to "+actual);
                }
            }
        };
        var produce=kind==Blocks.WHEAT?Items.WHEAT:kind==Blocks.CARROTS?Items.CARROT:
            kind==Blocks.POTATOES?Items.POTATO:kind==Blocks.BEETROOTS?Items.BEETROOT:
            kind==Blocks.MELON?Items.MELON_SLICE:Items.PUMPKIN;
        var stock=new int[1];
        var actualDrops=new java.util.ArrayList<net.minecraft.world.entity.item.ItemEntity>();
        h.startSequence().thenWaitUntil(()->{
            check(WorkforceData.get(f.l).progress(f.v.getUUID(),Profession.FARMER.id()).xp()>0,"Farm worker has not harvested at "+f.v.position());
            check(f.v.distanceToSqr(from)>4,"harvest did not require walking");
            check(f.v.distanceToSqr(Vec3.atCenterOf(target))<3,"worker was not at the harvest target: "+f.v.position());
            if(regular)check(f.l.getBlockState(target).is(kind)&&!crop.isMaxAge(f.l.getBlockState(target)),
                "Selected crop was not harvested at "+target);
            com.mojang.logging.LogUtils.getLogger().info("Harvest target {} by worker at {}: {} -> {} ({} plot/soil states checked)",
                target,f.v.position(),before.get(target),f.l.getBlockState(target),before.size());
            audit.run();
            stock[0]=count(f,produce);
            actualDrops.addAll(f.l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(target).inflate(1),e->e.getItem().is(produce)));
            check(!actualDrops.isEmpty(),"no actual harvest drops at "+target);
        }).thenWaitUntil(()->{
            check(actualDrops.stream().noneMatch(net.minecraft.world.entity.item.ItemEntity::isAlive)
                &&count(f,produce)>stock[0],"worker did not collect the drops produced by harvest");
            audit.run();
        }).thenExecuteFor(15,audit).thenSucceed();
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
    public static void farmer_flees_within_field_and_returns_to_work(GameTestHelper h){
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
            check(IndustrialWork.area(f.l,f.s).contains(f.v.position().add(0,.125,0)),"worker fled beyond the field");
            check(f.v.distanceToSqr(start)>.9,"worker has not moved away from the zombie");
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

        final net.minecraftforge.common.util.LazyOptional<net.minecraftforge.items.IItemHandler> rejectingFace=
            net.minecraftforge.common.util.LazyOptional.of(()->new net.minecraftforge.items.ItemStackHandler(1){
                @Override public boolean isItemValid(int slot,ItemStack stack){return false;}
            });

        @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
            net.minecraftforge.common.capabilities.Capability<T> cap,Direction side){
            if(cap!=net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER)
                return net.minecraftforge.common.util.LazyOptional.empty();
            if(side==Direction.EAST)return capability.cast();
            if(side==Direction.WEST)return rejectingFace.cast();
            return net.minecraftforge.common.util.LazyOptional.empty();
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
    public static void farmer_plants_one_cane_only_with_real_water(GameTestHelper h){
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
            check(f.l.getBlockState(root).is(Blocks.SUGAR_CANE)==!virtual&&count(f,Items.SUGAR_CANE)==(virtual?2:1),"cane planting failed, virtual="+virtual);
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
    @GameTest(template="empty",timeoutTicks=500,batch="farmer_night_lifecycle")
    public static void farmer_discards_carrots_then_sleeps_until_dawn(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        var home=ResidenceData.get(f.l).owner(f.v.getUUID()).pos;
        buildWalkableFloor(f, home);
        var bed=home.east(2).above();
        var state=Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST);
        f.l.setBlock(bed.west(),state.setValue(BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.FOOT),3);
        f.l.setBlock(bed,state.setValue(BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.HEAD),3);
        var chestPos=f.p.east(4).above();
        f.l.setBlock(chestPos,Blocks.CHEST.defaultBlockState(),3);
        var chest=(ChestBlockEntity)f.l.getBlockEntity(chestPos);
        f.s.setItem(0,new ItemStack(Items.CARROT,15));
        var ripe=crop(f,((CropBlock)Blocks.WHEAT).getStateForAge(7));
        var start=Vec3.atBottomCenterOf(f.p.south().above());
        f.v.setPos(start.x,start.y,start.z);
        f.s.setItem(27,new ItemStack(Items.IRON_HOE));
        f.v.setNoAi(false);
        f.l.getServer().getCommands().performPrefixedCommand(f.l.getServer().createCommandSourceStack(),"time set night");
        h.startSequence().thenWaitUntil(()->{
            check(f.v.getSleepingPos().filter(bed::equals).isPresent(),"waiting for actual bed sleep, worker at "+f.v.position()
                +" target "+f.v.getNavigation().getTargetPos()+" claim "+ResidenceData.get(f.l).bedClaim(f.v.getUUID()));
            check(chest.countItem(Items.CARROT)==5&&count(f,Items.CARROT)==10,"farmer slept before discarding carrots: chest="+chest.countItem(Items.CARROT)+" stock="+count(f,Items.CARROT)
                +" checked day="+f.s.industry.getLong("farmer_discard_day"));
        }).thenExecuteFor(35,()->{
            check(f.v.isSleeping(),"farmer woke during the night");
            check(f.l.getBlockState(ripe).getValue(CropBlock.AGE)==7,"harvest during rest");
        }).thenExecute(()->f.l.setDayTime(23000)).thenWaitUntil(()->{
            check(!f.v.isSleeping(),"farmer still sleeping at dawn");
            check(f.l.getBlockState(ripe).getValue(CropBlock.AGE)<7,"farmer did not resume work");
        }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=550,batch="farmer_blocked_home_recovery")
    public static void stranded_farmer_uses_safe_home_recovery_only_after_timeout(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        var homes=ResidenceData.get(f.l);
        var home=homes.owner(f.v.getUUID()).pos;
        buildWalkableFloor(f,home);
        var bed=placeBed(f.l,home.east(2).above());
        var feet=f.p.above();
        for(var dir:Direction.Plane.HORIZONTAL){
            f.l.setBlockAndUpdate(feet.relative(dir),Blocks.STONE.defaultBlockState());
            f.l.setBlockAndUpdate(feet.relative(dir).above(),Blocks.STONE.defaultBlockState());
        }
        f.l.setBlockAndUpdate(feet.above(2),Blocks.STONE.defaultBlockState());
        var start=Vec3.atBottomCenterOf(feet);
        f.v.setPos(start.x,start.y,start.z);
        f.v.setNoAi(false);
        f.s.setItem(EmploymentData.TOOL,new ItemStack(Items.IRON_HOE));
        f.l.setDayTime(13000);
        h.startSequence().thenExecuteFor(285,()->{
            check(f.v.position().distanceToSqr(start)<4,"Worker recovered before the residential timeout");
        }).thenWaitUntil(()->{
            check(f.v.getSleepingPos().filter(bed::equals).isPresent(),"Waiting for safe residential recovery from closed cage");
        }).thenExecute(()->check(EmploymentData.get(f.l).assigned(f.v.getUUID())==f.s
            &&f.s.getItem(EmploymentData.TOOL).is(Items.IRON_HOE)&&homes.owner(f.v.getUUID()).pos.equals(home),
            "Emergency recovery changed profession, home or tools")).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=850,batch="farmer_long_home_return")
    public static void farmer_discards_and_walks_twenty_blocks_home(GameTestHelper h){verifyLongHomeReturn(h,20,32);}

    @GameTest(template="empty",timeoutTicks=850,batch="farmer_long_home_return")
    public static void farmer_discards_and_walks_forty_five_blocks_home(GameTestHelper h){verifyLongHomeReturn(h,45,40);}

    @GameTest(template="empty",timeoutTicks=850,batch="farmer_long_home_return")
    public static void farmer_discards_and_walks_sixty_four_blocks_home(GameTestHelper h){verifyLongHomeReturn(h,64,48);}

    @GameTest(template="empty",timeoutTicks=850,batch="farmer_long_home_return")
    public static void farmer_discards_and_walks_ninety_blocks_home(GameTestHelper h){verifyLongHomeReturn(h,90,56);}

    @GameTest(template="empty",timeoutTicks=850,batch="farmer_long_home_return")
    public static void farmer_discards_and_walks_to_linked_home_a_hundred_blocks_away(GameTestHelper h){verifyLongHomeReturn(h,100,64);}

    private static void verifyLongHomeReturn(GameTestHelper h,int distance,int height){
        verifyLongHomeReturn(h,distance,height,Profession.FARMER);
    }

    private static void verifyLongHomeReturn(GameTestHelper h,int distance,int height,Profession profession){
        var level=h.getLevel();
        var post=h.absolutePos(new BlockPos(8,2,8)).above(height);
        var home=post.west(distance);
        for(var pos:BlockPos.betweenClosed(home.offset(-4,0,-4),post.offset(3,0,4))){
            level.setBlockAndUpdate(pos,Blocks.SEA_LANTERN.defaultBlockState());
            for(int y=1;y<=3;y++)level.setBlockAndUpdate(pos.above(y),Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(home,ResidenciasDosAldeoes.ANCHOR.get().defaultBlockState());
        ((br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity)level.getBlockEntity(home)).setSize(9);
        var bed=placeBed(level,home.east(2).above());
        level.setBlockAndUpdate(post,WorkModule.INDUSTRIAL.get().defaultBlockState());
        var station=((WorkBlockEntity)level.getBlockEntity(post)).station();
        var worker=EntityType.VILLAGER.create(level);
        var start=Vec3.atBottomCenterOf(post.above());
        worker.setPos(start.x,start.y,start.z);
        level.addFreshEntity(worker);
        worker.setAge(0);
        check(ResidenceData.get(level).link(level,home,worker).equals("ok"),"Long-range home link failed");
        var employer=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"LongFarmRoute"));
        employer.setPos(worker.getX(),worker.getY(),worker.getZ());
        station.setItem(31,new ItemStack(Items.EMERALD,EmploymentConfig.cost(Profession.Sector.INDUSTRIAL)));
        check(Employment.hire(employer,station,worker.getUUID(),profession).equals("ok"),"Long-range hire failed");
        var target=post.north().above();
        level.setBlockAndUpdate(target,Blocks.BARREL.defaultBlockState());
        var barrel=(BarrelBlockEntity)level.getBlockEntity(target);
        station.setItem(0,new ItemStack(Items.COBBLESTONE,3));
        level.setDayTime(13000);
        var previous=new Vec3[]{worker.position()};
        h.startSequence().thenWaitUntil(()->check(barrel.countItem(Items.COBBLESTONE)==3,"No twilight discard"))
            .thenWaitUntil(()->{
                var current=worker.position();
                if(!worker.isSleeping()&&current.distanceToSqr(previous[0])>300
                    &&Math.abs(current.x-previous[0].x)>30)check(false,"Worker skipped the long path");
                if(current.distanceToSqr(previous[0])>1)previous[0]=current;
                check(worker.getSleepingPos().filter(bed::equals).isPresent(),"Long walk still in progress at "+worker.position()+" bed="+bed+" state="+level.getBlockState(bed)
                    +" claim="+ResidenceData.get(level).bedClaim(worker.getUUID())+" target="+worker.getNavigation().getTargetPos()
                    +" done="+worker.getNavigation().isDone()+" homeSize="+ResidenceData.get(level).owner(worker.getUUID()).size);
            }).thenExecute(()->check(station.getItem(0).isEmpty()&&ResidenceData.get(level).owner(worker.getUUID()).pos.equals(home),
                "Long return altered the link or inventory")).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=500,batch="farmer_natural_twilight")
    public static void blocked_and_full_discard_do_not_delay_bed_or_break_next_route(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        var home=ResidenceData.get(f.l).owner(f.v.getUUID()).pos;
        buildWalkableFloor(f,home);
        var bed=home.east(2).above();
        var state=Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST);
        f.l.setBlock(bed.west(),state.setValue(BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.FOOT),3);
        f.l.setBlock(bed,state.setValue(BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.HEAD),3);
        var blocked=f.p.east(4).above();
        f.l.setBlock(blocked,Blocks.BARREL.defaultBlockState(),3);
        for(var side:Direction.Plane.HORIZONTAL){
            f.l.setBlock(blocked.relative(side),Blocks.STONE.defaultBlockState(),3);
            f.l.setBlock(blocked.relative(side).above(),Blocks.STONE.defaultBlockState(),3);
        }
        var fullPos=f.p.south(4).above();
        f.l.setBlock(fullPos,Blocks.BARREL.defaultBlockState(),3);
        var full=(BarrelBlockEntity)f.l.getBlockEntity(fullPos);
        for(int i=0;i<full.getContainerSize();i++)full.setItem(i,new ItemStack(Items.COBBLESTONE,64));
        f.s.setItem(0,new ItemStack(Items.CARROT,15));
        f.s.setItem(27,new ItemStack(Items.IRON_HOE));
        var ripe=crop(f,((CropBlock)Blocks.WHEAT).getStateForAge(7));
        var start=Vec3.atBottomCenterOf(blocked.east(2));
        f.v.setPos(start.x,start.y,start.z);
        f.l.setDayTime(11999);
        f.v.setNoAi(false);
        h.startSequence().thenWaitUntil(()->check(f.s.industry.getLong("farmer_discard_day")>0,"Twilight did not start closing"))
            .thenExecute(()->f.l.setDayTime(13001))
            .thenWaitUntil(()->{
                check(f.v.getSleepingPos().filter(bed::equals).isPresent(),"worker never reached bed after inaccessible discard");
                check(count(f,Items.CARROT)==15,"discard lost carrots");
            }).thenExecute(()->f.l.setDayTime(23000)).thenWaitUntil(()->{
                check(!f.v.isSleeping()&&f.l.getBlockState(ripe).getValue(CropBlock.AGE)<7,
                    "worker did not resume farming after failed discard");
            }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=300,batch="worker_bed_builder")
    public static void builder_walks_to_registered_residential_bed(GameTestHelper h){
        workerSleeps(h,Profession.BUILDER);
    }

    @GameTest(template="empty",timeoutTicks=300,batch="worker_bed_commerce")
    public static void baker_walks_to_registered_residential_bed(GameTestHelper h){
        workerSleeps(h,Profession.BAKER);
    }

    @GameTest(template="empty",timeoutTicks=300,batch="worker_bed_military")
    public static void off_duty_soldier_walks_to_registered_residential_bed(GameTestHelper h){
        workerSleeps(h,Profession.SOLDIER);
    }

    private static void workerSleeps(GameTestHelper h,Profession profession){
        var f=fixture(h,profession);
        var home=ResidenceData.get(f.l).owner(f.v.getUUID()).pos;
        buildWalkableFloor(f,home);
        var head=home.east(2).above();
        var bed=Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST);
        f.l.setBlock(head.west(),bed.setValue(BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.FOOT),3);
        f.l.setBlock(head,bed.setValue(BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.HEAD),3);
        var start=Vec3.atBottomCenterOf(f.p.south().above());
        f.v.setPos(start.x,start.y,start.z);
        f.v.setNoAi(false);
        f.l.setDayTime(13001);
        h.startSequence().thenWaitUntil(()->{
            check(f.v.getSleepingPos().filter(head::equals).isPresent(),profession+" has not reached the bed from "+f.v.position());
            check(f.l.getBlockState(head).getValue(BedBlock.OCCUPIED),"registered bed not occupied");
        }).thenExecuteFor(20,()->check(f.v.isSleeping(),profession+" is not staying asleep"))
            .thenExecute(()->f.l.setDayTime(23000))
            .thenWaitUntil(()->check(!f.v.isSleeping(),profession+" did not wake at dawn")).thenSucceed();
    }

    private static void buildWalkableFloor(F f,BlockPos home){
        for(var floor:BlockPos.betweenClosed(home.offset(-4,0,-4),f.p.offset(6,0,6))){
            if(floor.equals(home)||floor.equals(f.p))continue;
            // Lighting gives the navigation and rest scenarios a deterministic field
            // while the separate threat test continues to exercise real zombies.
            boolean lamp=Math.floorMod(floor.getX(),4)==0&&Math.floorMod(floor.getZ(),4)==0;
            f.l.setBlock(floor,(lamp?Blocks.SEA_LANTERN:Blocks.STONE).defaultBlockState(),3);
        }
    }

    @GameTest(template="empty",timeoutTicks=220,batch="industry")
    public static void farmer_jumps_physically_on_only_one_empty_cell_beside_mature_stem(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        buildWalkableFloor(f,ResidenceData.get(f.l).owner(f.v.getUUID()).pos);
        var stem=f.p.offset(-2,1,0);
        var empty=stem.north();
        var neighbor=stem.south();
        var soil=Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7);
        for(var pos:List.of(stem,empty,neighbor))f.l.setBlock(pos.below(),soil,3);
        var mature=Blocks.MELON_STEM.defaultBlockState().setValue(StemBlock.AGE,7);
        var crop=Blocks.CARROTS.defaultBlockState();
        f.l.setBlock(stem,mature,3);
        f.l.setBlock(neighbor,crop,3);
        var start=Vec3.atBottomCenterOf(f.p.east(2).north(2).above());
        f.v.setPos(start.x,start.y,start.z);
        f.s.setItem(27,new ItemStack(Items.IRON_HOE));
        f.s.setItem(30,new ItemStack(Items.WATER_BUCKET));
        f.v.setNoAi(false);
        h.startSequence().thenWaitUntil(()->{
            check(!f.v.isOnGround()&&f.v.getY()>empty.getY()+.3&&f.v.getX()>=empty.getX()
                &&f.v.getX()<empty.getX()+1&&f.v.getZ()>=empty.getZ()&&f.v.getZ()<empty.getZ()+1,"waiting for physical jump");
        }).thenWaitUntil(()->{
            check(f.l.getBlockState(empty.below()).is(Blocks.DIRT),"jump did not convert the selected cell");
            check(f.l.getBlockState(neighbor.below())==soil&&f.l.getBlockState(neighbor)==crop,"jump damaged adjacent crop/soil");
            check(f.l.getBlockState(stem).is(Blocks.MELON_STEM),"jump damaged the stem");
        }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=160,batch="industry")
    public static void idle_visitor_leaves_occupied_field_while_farmer_stays(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        buildWalkableFloor(f,ResidenceData.get(f.l).owner(f.v.getUUID()).pos);
        var visitor=EntityType.VILLAGER.create(f.l);
        var start=Vec3.atBottomCenterOf(f.p.west().above());
        visitor.setPos(start.x,start.y,start.z);
        f.l.addFreshEntity(visitor);
        var bounds=IndustrialWork.area(f.l,f.s);
        check(bounds.contains(visitor.position()),"visitor did not start inside");
        var ownerStart=Vec3.atBottomCenterOf(f.p.south().above());
        f.v.setPos(ownerStart.x,ownerStart.y,ownerStart.z);
        f.v.setNoAi(false);
        h.succeedWhen(()->{
            check(!bounds.contains(visitor.position().add(0,.125,0)),"visitor has not left at "+visitor.position());
            check(bounds.contains(f.v.position().add(0,.125,0)),"worker was displaced from the field");
        });
    }

    @GameTest(template="empty",timeoutTicks=160,batch="workplace_overlap")
    public static void another_worker_leaves_foreign_field_but_can_idle_in_its_overlap(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        var home=ResidenceData.get(f.l).owner(f.v.getUUID()).pos;
        buildWalkableFloor(f,home);
        var workplace=f.p.east(3);
        f.l.setBlock(workplace,WorkModule.COMMERCIAL.get().defaultBlockState(),3);
        var baker=((WorkBlockEntity)f.l.getBlockEntity(workplace)).station();
        var guest=EntityType.VILLAGER.create(f.l);
        var start=Vec3.atBottomCenterOf(f.p.west(2).above());
        guest.setPos(start.x,start.y,start.z);
        f.l.addFreshEntity(guest);
        check(ResidenceData.get(f.l).link(f.l,home,guest).equals("ok"),"link guest to existing home");
        var player=FakePlayerFactory.get(f.l,new GameProfile(UUID.randomUUID(),"WorkplaceVisitTest"));
        player.setPos(guest.getX(),guest.getY(),guest.getZ());
        baker.setItem(31,new ItemStack(Items.EMERALD,EmploymentConfig.cost(Profession.Sector.COMMERCIAL)));
        check(Employment.hire(player,baker,guest.getUUID(),Profession.BAKER).equals("ok"),"hire visiting baker");
        // A removed recipe pauses production and supply runs, so the test
        // observes the idle worker even with other test batches' stock loaded.
        baker.commerce.putString("recipe","residenciasaldeoes:removed_fixture_recipe");
        baker.setChanged();
        var farmArea=IndustrialWork.area(f.l,f.s);
        var bakeryArea=IndustrialWork.area(f.l,baker);
        var overlap=Vec3.atBottomCenterOf(f.p.east(2).above());
        check(farmArea.contains(start)&&!bakeryArea.contains(start)&&farmArea.contains(overlap)&&bakeryArea.contains(overlap),
            "fixture did not establish exclusive and overlapping areas");
        h.startSequence().thenWaitUntil(()->check(!farmArea.contains(guest.position()),"baker did not vacate the field"))
            .thenExecute(()->{
                guest.getNavigation().stop();
                guest.setPos(overlap.x,overlap.y,overlap.z);
                guest.setDeltaMovement(Vec3.ZERO);
                guest.getMoveControl().setWantedPosition(overlap.x,overlap.y,overlap.z,0);
            }).thenExecuteFor(30,()->{
                var feet=guest.position().add(0,.125,0);
                check(farmArea.contains(feet)&&bakeryArea.contains(feet)&&!WorkplaceVisits.leaving(guest),
                    "authorized worker left overlap at "+guest.position()+" navigation "+guest.getNavigation().getTargetPos()
                    +" commercial state "+baker.commerce.getString("status"));
            }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=280,batch="farmer_forge_closing")
    public static void farmer_walks_to_sided_forge_inventory_to_discard_at_twilight(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        buildWalkableFloor(f,ResidenceData.get(f.l).owner(f.v.getUUID()).pos);
        var pos=f.p.east(4).above();
        var mod=new ModInventory();
        MinecraftForge.EVENT_BUS.register(mod);
        try{
            f.l.setBlock(pos,Blocks.BEACON.defaultBlockState(),3);
        }finally{
            MinecraftForge.EVENT_BUS.unregister(mod);
        }
        f.s.setItem(0,new ItemStack(Items.CARROT,15));
        var start=Vec3.atBottomCenterOf(f.p.south().above());
        f.v.setPos(start.x,start.y,start.z);
        f.v.setNoAi(false);
        f.l.setDayTime(11999);
        h.startSequence().thenWaitUntil(()->{
            check(mod.storage.getStackInSlot(0).getCount()==5&&count(f,Items.CARROT)==10,"waiting for Forge discard");
            check(f.v.getX()>pos.getX()+1&&f.v.distanceToSqr(start)>9,"discard without reaching the accessible side");
        }).thenExecute(()->f.l.setDayTime(1000)).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=300,batch="forge_partial_after_obstacle")
    public static void farmer_walks_around_rejecting_face_and_preserves_unaccepted_excess(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        buildWalkableFloor(f,ResidenceData.get(f.l).owner(f.v.getUUID()).pos);
        var pos=f.p.east(4).above();
        var mod=new ModInventory();
        mod.storage.setStackInSlot(0,new ItemStack(Items.CARROT,62));
        MinecraftForge.EVENT_BUS.register(mod);
        try{
            f.l.setBlock(pos,Blocks.BEACON.defaultBlockState(),3);
        }finally{
            MinecraftForge.EVENT_BUS.unregister(mod);
        }
        f.l.setBlock(pos.west(),Blocks.STONE.defaultBlockState(),3);
        f.l.setBlock(pos.west().above(),Blocks.STONE.defaultBlockState(),3);
        f.s.setItem(0,new ItemStack(Items.CARROT,15));
        f.s.setItem(1,new ItemStack(Items.STICK,3));
        var start=Vec3.atBottomCenterOf(f.p.south().above());
        f.v.setPos(start.x,start.y,start.z);
        f.v.setNoAi(false);
        f.l.setDayTime(13001);
        h.startSequence().thenWaitUntil(()->{
            check(mod.storage.getStackInSlot(0).getCount()==64,"waiting for partial insertion from east");
            check(count(f,Items.CARROT)==13&&count(f,Items.STICK)==3,"partial acceptance lost or duplicated items");
            check(f.v.getX()>pos.getX()+1,"farmer did not walk around the blocked side");
        }).thenExecute(()->f.l.setDayTime(1000)).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=100,batch="industry")
    public static void falling_visitor_cannot_destroy_a_crop_in_an_occupied_field(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        var crop=crop(f,Blocks.CARROTS.defaultBlockState());
        var control=crop.south(4);
        f.v.setPos(f.p.getX()+.5,f.p.getY()+1,f.p.getZ()-1.5);
        f.l.setBlock(crop.below(2),Blocks.STONE.defaultBlockState(),3);
        f.l.setBlock(control.below(2),Blocks.STONE.defaultBlockState(),3);
        f.l.setBlock(control.below(),Blocks.FARMLAND.defaultBlockState(),3);
        f.l.setBlock(control,Blocks.CARROTS.defaultBlockState(),3);
        var visitors=new ArrayList<Villager>();
        for(var pos:List.of(crop,control)){
            var center=Vec3.atBottomCenterOf(pos);
            var villager=EntityType.VILLAGER.create(f.l);
            villager.setPos(center.x,center.y+5,center.z);
            villager.setDeltaMovement(0,-.5,0);
            f.l.addFreshEntity(villager);
            visitors.add(villager);
        }
        h.startSequence().thenWaitUntil(()->check(visitors.stream().allMatch(v->v.getY()<=crop.getY()+.1),
                "visitors have not landed: "+visitors.stream().map(Villager::position).toList()))
            .thenExecute(()->{
                var soil=f.l.getBlockState(crop.below());
                var result=f.l.getBlockState(crop);
                com.mojang.logging.LogUtils.getLogger().info("Villager landings at {} and {}, farmer at {}, protected plot {}/{} and control {}/{}",
                    visitors.get(0).position(),visitors.get(1).position(),f.v.position(),soil,result,
                    f.l.getBlockState(control.below()),f.l.getBlockState(control));
                check(soil.is(Blocks.FARMLAND)&&result.is(Blocks.CARROTS),"physical visitor fall damaged the plot");
                check(f.l.getBlockState(control.below()).is(Blocks.DIRT)&&f.l.getBlockState(control).isAir(),
                    "Control fall did not reproduce soil conversion and loss of the crop above");
            }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=420,batch="farmer_water_at_night")
    public static void farmer_climbs_out_of_flowing_channel_and_sleeps(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        var home=ResidenceData.get(f.l).owner(f.v.getUUID()).pos;
        buildWalkableFloor(f,home);
        var bed=placeBed(f.l,home.east(2).above());
        var source=f.p.east().south();
        for(int n=0;n<3;n++){
            var channel=source.south(n);
            f.l.setBlock(channel.below(),Blocks.STONE.defaultBlockState(),3);
            f.l.setBlock(channel,Blocks.AIR.defaultBlockState(),3);
        }
        f.l.setBlock(source,Blocks.WATER.defaultBlockState(),3);
        h.startSequence().thenWaitUntil(()->check(f.l.getFluidState(source.south(2)).is(net.minecraft.tags.FluidTags.WATER),
            "water source did not feed both lower cells")).thenExecute(()->{
            var center=Vec3.atBottomCenterOf(source.south());
            f.v.setPos(center.x,center.y+.05,center.z);
            f.v.setNoAi(false);
            f.l.setDayTime(13001);
        }).thenWaitUntil(()->{
            check(f.v.getSleepingPos().filter(bed::equals).isPresent(),"worker failed to climb and reach bed from "+f.v.position()
                +" water "+f.v.isInWaterOrBubble()+" target "+f.v.getNavigation().getTargetPos());
        }).thenExecute(()->f.l.setDayTime(1000)).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=380,batch="farmer_three_sleepers")
    public static void three_farmers_share_one_residence_without_sharing_beds(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        var home=ResidenceData.get(f.l).owner(f.v.getUUID()).pos;
        buildWalkableFloor(f,home);
        var beds=new HashSet<BlockPos>();
        for(int z=-2;z<=2;z+=2)beds.add(placeBed(f.l,home.east(2).above().south(z)));
        var workers=new ArrayList<Villager>();
        workers.add(f.v);
        for(int i=1;i<=2;i++){
            var post=f.p.east(i*2);
            f.l.setBlock(post,WorkModule.PROFESSIONAL.get(Profession.FARMER).get().defaultBlockState(),3);
            var station=((WorkBlockEntity)f.l.getBlockEntity(post)).station();
            var worker=EntityType.VILLAGER.create(f.l);
            worker.setPos(post.getX()+.5,post.getY()+1,post.getZ()+1.5);
            f.l.addFreshEntity(worker);
            check(ResidenceData.get(f.l).link(f.l,home,worker).equals("ok"),"shared residence rejected farmer");
            var player=FakePlayerFactory.get(f.l,new GameProfile(UUID.randomUUID(),"ThreeFarmers"));
            player.setPos(worker.getX(),worker.getY(),worker.getZ());
            station.setItem(31,new ItemStack(Items.EMERALD,10));
            check(Employment.hire(player,station,worker.getUUID(),Profession.FARMER).equals("ok"),"farmer hire in shared home");
            workers.add(worker);
        }
        f.v.setPos(f.p.getX()+.5,f.p.getY()+1,f.p.getZ()+1.5);
        f.v.setNoAi(false);
        f.l.setDayTime(13001);
        h.startSequence().thenWaitUntil(()->{
            var actual=new HashSet<BlockPos>();
            for(var worker:workers){
                var bed=worker.getSleepingPos().orElse(null);
                check(worker.isSleeping()&&bed!=null,"awake farmer at "+worker.position());
                check(actual.add(bed)&&ResidenceData.get(f.l).ownsBed(f.l.dimension(),bed,worker.getUUID()),"bed conflict");
            }
            check(actual.equals(beds),"workers did not occupy the three assigned beds");
        }).thenExecute(()->f.l.setDayTime(1000)).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=350,batch="farmer_homeward_escape")
    public static void farmer_heads_home_when_pursued_at_night_and_sleeps_after_safety(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        var home=ResidenceData.get(f.l).owner(f.v.getUUID()).pos;
        buildWalkableFloor(f,home);
        var bed=placeBed(f.l,home.east(2).above());
        var start=Vec3.atBottomCenterOf(f.p.south().above());
        f.v.setPos(start.x,start.y,start.z);
        f.v.setNoAi(false);
        var pursuer=EntityType.ZOMBIE.create(f.l);
        pursuer.setNoAi(true);
        pursuer.setInvulnerable(true);
        pursuer.setPersistenceRequired();
        pursuer.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));
        pursuer.setPos(start.x+2,start.y,start.z+1);
        f.l.addFreshEntity(pursuer);
        pursuer.setTarget(f.v);
        f.l.setDayTime(13001);
        var center=Vec3.atCenterOf(home);
        h.startSequence().thenWaitUntil(()->check(Employment.fleeing(f.v),"worker has not reacted to the zombie at "
            +pursuer.position()+" alive="+pursuer.isAlive()+" target="+pursuer.getTarget()+" worker="+f.v.position()))
            .thenWaitUntil(()->check(f.v.position().distanceToSqr(center)<start.distanceToSqr(center)*.7,
                "night escape has not approached the residence: "+f.v.position()))
            .thenExecute(pursuer::discard).thenWaitUntil(()->{
            check(f.v.getSleepingPos().filter(bed::equals).isPresent(),"farmer did not sleep after fleeing");
            check(f.l.getEntity(f.v.getUUID())==f.v,"residential return replaced the entity");
        }).thenExecute(()->f.l.setDayTime(1000)).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=320,batch="farmer_ground_drop_before_bed")
    public static void closing_worker_physically_collects_item_below_farmland_grid_height(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        var home=ResidenceData.get(f.l).owner(f.v.getUUID()).pos;
        buildWalkableFloor(f,home);
        var bed=placeBed(f.l,home.east(2).above());
        var soil=f.p.east(2).north();
        f.l.setBlock(soil,Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7),3);
        var item=new net.minecraft.world.entity.item.ItemEntity(f.l,soil.getX()+.5,soil.getY()+.9375,
            soil.getZ()+.5,new ItemStack(Items.COBBLESTONE,6));
        item.setDeltaMovement(Vec3.ZERO);
        f.l.addFreshEntity(item);
        var chest=f.p.west(4).above();
        f.l.setBlock(chest,Blocks.BARREL.defaultBlockState(),3);
        var barrel=(BarrelBlockEntity)f.l.getBlockEntity(chest);
        var start=Vec3.atBottomCenterOf(f.p.south(2).above());
        f.v.setPos(start.x,start.y,start.z);
        f.v.setNoAi(false);
        f.l.setDayTime(13001);
        h.startSequence().thenWaitUntil(()->{
            check(f.v.getSleepingPos().filter(bed::equals).isPresent(),"worker did not get to bed");
            check(!item.isAlive()&&barrel.countItem(Items.COBBLESTONE)==6,"drop on farmland did not reach the barrel");
        }).thenExecute(()->f.l.setDayTime(1000)).thenSucceed();
    }

    private static BlockPos placeBed(ServerLevel level,BlockPos head){
        var bed=Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST);
        level.setBlock(head.west(),bed.setValue(BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.FOOT),3);
        level.setBlock(head,bed.setValue(BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.HEAD),3);
        return head;
    }

    @GameTest(template="empty",timeoutTicks=400,batch="farmer_day_linked_discard")
    public static void closing_shared_inventory_accepts_only_authorized_excess_and_worker_returns_to_harvest(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        buildWalkableFloor(f,ResidenceData.get(f.l).owner(f.v.getUUID()).pos);
        var wheat=crop(f,((CropBlock)Blocks.WHEAT).getStateForAge(7));
        f.s.setItem(27,new ItemStack(Items.IRON_HOE));
        f.s.setItem(0,new ItemStack(Items.CARROT,15));
        f.s.setItem(1,new ItemStack(Items.STICK,2));
        f.s.setItem(2,new ItemStack(Items.WHEAT_SEEDS,4));
        var shared=new net.minecraftforge.items.ItemStackHandler(1){
            @Override public boolean isItemValid(int slot,ItemStack item){return item.is(Items.CARROT);}
        };
        shared.setStackInSlot(0,new ItemStack(Items.CARROT,62));
        var denied=f.p.east(2).south().above();
        var open=f.p.east(4).above();
        var alias=f.p.east(4).south(2).above();
        putLinked(f.l,denied,shared,UUID.randomUUID());
        putLinked(f.l,open,shared,f.v.getUUID());
        putLinked(f.l,alias,shared,f.v.getUUID());
        var offers=new int[27];
        offers[0]=5;
        var unloaded=open.offset(1600,0,1600);
        check(!f.l.hasChunkAt(unloaded),"unloaded recipient fixture already loaded");
        check(!br.com.kraftcity.logistics.DiscardChests.accepts(f.l,f.s,f.v,unloaded,offers)
            &&!f.l.hasChunkAt(unloaded),"discard forced an unloaded chunk");
        check(!br.com.kraftcity.logistics.DiscardChests.accepts(f.l,f.s,f.v,denied,offers),"private channel offered automation");
        check(br.com.kraftcity.logistics.DiscardChests.accepts(f.l,f.s,f.v,alias,offers),"second linked endpoint did not expose shared storage");
        var start=Vec3.atBottomCenterOf(f.p.south().above());
        f.v.setPos(start.x,start.y,start.z);
        f.v.setNoAi(false);
        h.startSequence().thenExecuteFor(35,()->{
            check(shared.getStackInSlot(0).getCount()==62&&count(f,Items.CARROT)==15,"a farmer discarded during the day");
        }).thenExecute(()->f.l.setDayTime(12000)).thenWaitUntil(()->{
            check(shared.getStackInSlot(0).getCount()==64,"waiting for physical partial transfer into linked channel");
            check(count(f,Items.CARROT)==13&&count(f,Items.STICK)==2,"linked endpoints consumed the rejected items");
        }).thenExecute(()->{
            f.l.setBlock(wheat,((CropBlock)Blocks.WHEAT).getStateForAge(7),3);
            f.l.setDayTime(23000);
        }).thenWaitUntil(()->{
            check(f.l.getBlockState(wheat).getValue(CropBlock.AGE)==0,"worker did not leave linked chest to resume harvest");
            check(IndustrialWork.area(f.l,f.s).contains(f.v.position().add(0,.125,0)),"worker stayed outside the field");
            check(count(f,Items.CARROT)==13&&shared.getStackInSlot(0).getCount()==64,"shared aliases duplicated transfer");
        }).thenSucceed();
    }

    private static final class LinkedBeacon extends BeaconBlockEntity implements br.com.kraftcity.logistics.WorkerDiscardAccess {
        private final net.minecraftforge.items.IItemHandler shared;
        private final UUID allowedWorker;

        LinkedBeacon(BlockPos pos,net.minecraftforge.items.IItemHandler shared,UUID allowedWorker){
            super(pos,Blocks.BEACON.defaultBlockState());
            this.shared=shared;
            this.allowedWorker=allowedWorker;
        }

        @Override public net.minecraftforge.items.IItemHandler discardInventory(Villager worker,Direction face){
            return face==Direction.EAST&&allowedWorker.equals(worker.getUUID())?shared:null;
        }
    }

    private static void putLinked(ServerLevel level,BlockPos pos,net.minecraftforge.items.IItemHandler shared,UUID allowed){
        level.setBlock(pos,Blocks.BEACON.defaultBlockState(),3);
        level.removeBlockEntity(pos);
        level.setBlockEntity(new LinkedBeacon(pos,shared,allowed));
    }

    @GameTest(template="empty",timeoutTicks=320,batch="farmer_escapes_after_discard")
    public static void farmer_flees_without_damage_after_leaving_discard_container(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        buildWalkableFloor(f,ResidenceData.get(f.l).owner(f.v.getUUID()).pos);
        var chest=f.p.east(4).above();
        f.l.setBlock(chest,Blocks.BARREL.defaultBlockState(),3);
        var barrel=(BarrelBlockEntity)f.l.getBlockEntity(chest);
        f.s.setItem(0,new ItemStack(Items.COBBLESTONE,3));
        var start=Vec3.atBottomCenterOf(f.p.south().above());
        f.v.setPos(start.x,start.y,start.z);
        f.v.setNoAi(false);
        var zombie=EntityType.ZOMBIE.create(f.l);
        zombie.setInvulnerable(true);
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        zombie.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));
        var atTransfer=new Vec3[1];
        float health=f.v.getHealth();
        f.l.setDayTime(13000);
        h.startSequence().thenWaitUntil(()->check(barrel.countItem(Items.COBBLESTONE)==3,"closing discard did not reach the barrel"))
            .thenExecute(()->{
                atTransfer[0]=f.v.position();
                zombie.setPos(f.v.getX()+1.5,f.v.getY(),f.v.getZ()+1.5);
                f.l.addFreshEntity(zombie);
                zombie.setTarget(f.v);
            }).thenWaitUntil(()->{
                check(Employment.fleeing(f.v)&&f.v.distanceToSqr(atTransfer[0])>4,
                    "farmer remained stopped at the barrel: start="+atTransfer[0]+", position="+f.v.position()
                    +", fleeing="+Employment.fleeing(f.v)+", path="+f.v.getNavigation().getTargetPos()
                    +", path finished="+f.v.getNavigation().isDone());
                check(f.v.getHealth()==health&&f.v.getLastHurtByMob()==null,"escape depended on damage");
            }).thenExecute(()->{zombie.discard();f.l.setDayTime(1000);}).thenSucceed();
    }

    public static final class PlacementObserver {
        final F fixture;
        final BlockPos target,neighbor;
        final BlockState mature;
        boolean observed;

        PlacementObserver(F fixture,BlockPos target,BlockPos neighbor,BlockState mature){
            this.fixture=fixture;
            this.target=target;
            this.neighbor=neighbor;
            this.mature=mature;
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void onProposedCrop(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent event){
            if(event.getLevel()!=fixture.l||!event.getPos().equals(target)||event.getEntity()==null
                ||!event.getEntity().getUUID().equals(fixture.v.getUUID()))return;
            observed=true;
            var during=fixture.l.getBlockState(target);
            com.mojang.logging.LogUtils.getLogger().info("Replant preflight worker={} target={} live={} proposed={} neighbor={} state={}",
                fixture.v.position(),target,during,event.getPlacedBlock(),neighbor,fixture.l.getBlockState(neighbor));
            // Model a listener reacting to a replacement that is already visible
            // in the world before it has decided whether to allow the operation.
            if(during!=mature)fixture.l.removeBlock(neighbor,false);
            event.setCanceled(true);
        }
    }

    @GameTest(template="empty",timeoutTicks=240,batch="farmer_replant_preflight")
    public static void real_harvest_route_never_exposes_a_temporary_replant_to_forge(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        buildWalkableFloor(f,ResidenceData.get(f.l).owner(f.v.getUUID()).pos);
        var mature=((CropBlock)Blocks.WHEAT).getStateForAge(7);
        var target=crop(f,mature);
        var neighbor=target.north();
        var soil=Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7);
        var immature=Blocks.CARROTS.defaultBlockState();
        f.l.setBlock(neighbor.below(),soil,3);
        f.l.setBlock(neighbor,immature,3);
        f.s.setItem(27,new ItemStack(Items.IRON_HOE));
        f.s.setItem(30,new ItemStack(Items.WATER_BUCKET));
        var start=Vec3.atBottomCenterOf(f.p.south(2).above());
        f.v.setPos(start.x,start.y,start.z);
        f.v.setNoAi(false);
        var observer=new PlacementObserver(f,target,neighbor,mature);
        MinecraftForge.EVENT_BUS.register(observer);
        h.runAfterDelay(210,()->MinecraftForge.EVENT_BUS.unregister(observer));
        h.succeedWhen(()->{
            check(observer.observed&&f.v.distanceToSqr(start)>4,"worker did not physically reach the crop preflight");
            check(f.l.getBlockState(target)==mature&&f.l.getBlockState(neighbor)==immature
                &&f.l.getBlockState(neighbor.below())==soil,"cancelled replant temporarily affected the adjoining plot");
            check(count(f,Items.WHEAT)==0,"cancelled replant credited an uncommitted harvest");
            MinecraftForge.EVENT_BUS.unregister(observer);
        });
    }

    @GameTest(template="empty",timeoutTicks=220,batch="farmer_cane_column")
    public static void farmer_harvests_only_the_center_cane_and_collects_both_segments(GameTestHelper h){
        caneColumns(h,new int[]{2,3,2});
    }

    @GameTest(template="empty",timeoutTicks=220,batch="farmer_cane_column")
    public static void farmer_preserves_other_adjacent_cane_of_multiple_heights(GameTestHelper h){
        caneColumns(h,new int[]{1,2,3,2,1});
    }

    private static void caneColumns(GameTestHelper h,int[] heights){
        var f=fixture(h,Profession.FARMER);
        buildWalkableFloor(f,ResidenceData.get(f.l).owner(f.v.getUUID()).pos);
        var middle=f.p.north(2).above();
        var cane=Blocks.SUGAR_CANE.defaultBlockState();
        for(int i=0;i<heights.length;i++){
            var root=middle.east(i-heights.length/2);
            f.l.setBlock(root.below(2),Blocks.STONE.defaultBlockState(),3);
            f.l.setBlock(root.below(2).north(),Blocks.STONE.defaultBlockState(),3);
            f.l.setBlock(root.below(),Blocks.SAND.defaultBlockState(),3);
            f.l.setBlock(root.below().north(),Blocks.WATER.defaultBlockState(),3);
            check(cane.canSurvive(f.l,root),"invalid cane fixture at "+root);
            for(int y=0;y<heights[i];y++)f.l.setBlock(root.above(y),cane,3);
        }
        f.s.setItem(27,new ItemStack(Items.IRON_HOE));
        var start=Vec3.atBottomCenterOf(f.p.south(2).above());
        f.v.setPos(start.x,start.y,start.z);
        f.v.setNoAi(false);
        Runnable audit=()->{
            for(int i=0;i<heights.length;i++){
                var root=middle.east(i-heights.length/2);
                int expected=i==heights.length/2?1:heights[i];
                for(int y=0;y<3;y++)check(f.l.getBlockState(root.above(y)).is(Blocks.SUGAR_CANE)==(y<expected),
                    "cane at "+root.above(y)+" is "+f.l.getBlockState(root.above(y))+" with support "+f.l.getBlockState(root.below())
                    +" and water "+f.l.getFluidState(root.below().north())+" differs from expected height "+expected);
                check(f.l.getBlockState(root.below()).is(Blocks.SAND),"cane support changed");
            }
        };
        h.startSequence().thenWaitUntil(()->{
            check(f.l.getBlockState(middle.above()).isAir(),"selected cane has not been harvested");
            check(f.v.distanceToSqr(start)>4,"worker did not walk to the cane");
            audit.run();
        }).thenWaitUntil(()->{
            check(count(f,Items.SUGAR_CANE)==2,"worker has not picked up both actual cane drops");
            audit.run();
        }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void farmer_post_drops_generic_block_and_contents_and_keeps_history(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        level(f,2,36);
        f.s.setItem(0,new ItemStack(Items.CARROT,15));
        f.s.setItem(27,new ItemStack(Items.IRON_HOE));
        check(f.l.destroyBlock(f.p,true),"farmer post could not be removed");
        var drops=f.l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(f.p).inflate(2));
        check(drops.stream().filter(e->e.getItem().is(WorkModule.INDUSTRIAL.get().asItem())).mapToInt(e->e.getItem().getCount()).sum()==1,
            "missing or duplicated industrial block");
        check(drops.stream().filter(e->e.getItem().is(Items.CARROT)).mapToInt(e->e.getItem().getCount()).sum()==15,"lost or duplicated carrots");
        check(drops.stream().filter(e->e.getItem().is(Items.IRON_HOE)).count()==1,"lost or duplicated tool");
        check(drops.stream().noneMatch(e->e.getItem().is(WorkModule.PROFESSIONAL.get(Profession.FARMER).get().asItem())),"professional token still dropped");
        check(EmploymentData.get(f.l).station(f.s.id)==null&&!Employment.active(f.v),"stale station identity");
        check(WorkforceData.get(f.l).progress(f.v.getUUID(),Profession.FARMER.id()).xp()==36,"employment history was lost");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=230,batch="farmer_water_day_deep")
    public static void farmer_surfaces_from_two_blocks_of_water_and_resumes_work(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        var home=ResidenceData.get(f.l).owner(f.v.getUUID()).pos;
        buildWalkableFloor(f,home);
        var ripe=crop(f,((CropBlock)Blocks.WHEAT).getStateForAge(7));
        f.s.setItem(27,new ItemStack(Items.IRON_HOE));
        f.s.setItem(0,new ItemStack(Items.WHEAT_SEEDS,3));
        var bottom=f.p.south(2).below();
        for(var at:BlockPos.betweenClosed(bottom.offset(-1,0,-1),bottom.offset(1,1,1))){
            var wall=!at.equals(bottom)&&!at.equals(bottom.above());
            f.l.setBlock(at,wall?Blocks.STONE.defaultBlockState():Blocks.WATER.defaultBlockState(),3);
        }
        f.l.setBlock(bottom.below(),Blocks.STONE.defaultBlockState(),3);
        var start=Vec3.atBottomCenterOf(bottom);
        f.v.setPos(start.x,start.y+.05,start.z);
        f.v.setNoAi(false);
        h.startSequence().thenWaitUntil(()->{
            check(!f.v.isInWaterOrBubble()&&f.v.getY()>start.y+1.8,"farmer has not surfaced from the deep channel at "+f.v.position());
        }).thenWaitUntil(()->check(f.l.getBlockState(ripe).getValue(CropBlock.AGE)==0,"farmer did not return to work after surfacing"))
            .thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=200,batch="farmer_pickup_by_fence")
    public static void farmer_collects_a_moving_item_at_a_fence_corner_without_collecting_through_wall(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        buildWalkableFloor(f,ResidenceData.get(f.l).owner(f.v.getUUID()).pos);
        var fence=f.p.east(2).north().above();
        f.l.setBlock(fence,Blocks.OAK_FENCE.defaultBlockState(),3);
        f.l.setBlock(fence.north(),Blocks.OAK_FENCE.defaultBlockState(),3);
        var stone=fence.east(2);
        f.l.setBlock(stone,Blocks.STONE.defaultBlockState(),3);
        f.l.setBlock(stone.above(),Blocks.STONE.defaultBlockState(),3);
        var start=Vec3.atBottomCenterOf(f.p.west().south().above());
        f.v.setPos(start.x,start.y,start.z);
        f.v.setNoAi(false);
        var moved=new net.minecraft.world.entity.item.ItemEntity(f.l,fence.getX()-.8,fence.getY()+.1,fence.getZ()+.12,new ItemStack(Items.STICK));
        moved.setDeltaMovement(.17,0,0);
        f.l.addFreshEntity(moved);
        var buried=new net.minecraft.world.entity.item.ItemEntity(f.l,stone.getX()+.5,stone.getY()+.5,stone.getZ()+.5,new ItemStack(Items.DIAMOND));
        buried.setNoGravity(true);
        buried.setDeltaMovement(Vec3.ZERO);
        f.l.addFreshEntity(buried);
        h.startSequence().thenWaitUntil(()->{
            check(!moved.isAlive()&&count(f,Items.STICK)==1,"moving item near fence was not reached");
            check(f.v.distanceToSqr(start)>2,"worker collected from its starting position");
            check(count(f,Items.DIAMOND)==0,"worker collected through the solid barrier");
        }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=260,batch="farmer_residential_escape")
    public static void farmer_evades_zombie_inside_residence_and_then_sleeps(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        var home=ResidenceData.get(f.l).owner(f.v.getUUID());
        buildWalkableFloor(f,home.pos);
        var bed=placeBed(f.l,home.pos.east(2).above());
        var start=Vec3.atBottomCenterOf(home.pos.south(3).above());
        f.v.setPos(start.x,start.y,start.z);
        f.l.setDayTime(13000);
        f.v.setNoAi(false);
        var zombie=EntityType.ZOMBIE.create(f.l);
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        zombie.setInvulnerable(true);
        zombie.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));
        zombie.setPos(start.x,start.y,start.z-1.5);
        f.l.addFreshEntity(zombie);
        zombie.setTarget(f.v);
        Runnable bounds=()->{
            double limit=home.size/2+.5;
            check(Math.abs(f.v.getX()-home.pos.getX()-.5)<limit&&Math.abs(f.v.getZ()-home.pos.getZ()-.5)<limit,
                "worker left the residential boundary at "+f.v.position());
        };
        h.startSequence().thenWaitUntil(()->{
            bounds.run();
            check(Employment.fleeing(f.v)&&f.v.distanceToSqr(start)>1,"night worker did not evade the zombie, farmer="+f.v.position()
                +" threatAlive="+zombie.isAlive()+" target="+zombie.getTarget()+" day="+f.l.getDayTime());
            check(f.v.getLastHurtByMob()==null,"escape required damage");
        }).thenExecuteFor(15,bounds).thenExecute(zombie::discard).thenWaitUntil(()->{
            bounds.run();
            check(f.v.getSleepingPos().filter(bed::equals).isPresent(),"farmer did not return to its bed");
        }).thenExecute(()->f.l.setDayTime(1000)).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=240,batch="farmer_collect_in_current")
    public static void farmer_reaches_item_carried_by_a_current_from_the_bank(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        buildWalkableFloor(f,ResidenceData.get(f.l).owner(f.v.getUUID()).pos);
        var source=f.p.east(2).north(2);
        for(int i=0;i<4;i++){
            var water=source.south(i);
            f.l.setBlock(water,Blocks.AIR.defaultBlockState(),3);
            f.l.setBlock(water.below(),Blocks.STONE.defaultBlockState(),3);
        }
        f.l.setBlock(source,Blocks.WATER.defaultBlockState(),3);
        var start=Vec3.atBottomCenterOf(f.p.west(2).above());
        f.v.setPos(start.x,start.y,start.z);
        var drop=new net.minecraft.world.entity.item.ItemEntity(f.l,source.getX()+.5,source.getY()+.65,source.getZ()+1.3,
            new ItemStack(Items.FLINT));
        drop.setDeltaMovement(0,0,0);
        h.startSequence().thenWaitUntil(()->check(f.l.getFluidState(source.south(2)).is(net.minecraft.tags.FluidTags.WATER),"no flowing water"))
            .thenExecute(()->f.l.addFreshEntity(drop)).thenWaitUntil(()->{
                check(drop.getZ()>source.getZ()+1.38,"item has not moved along the current: "+drop.position());
            }).thenExecute(()->f.v.setNoAi(false)).thenWaitUntil(()->{
                check(!drop.isAlive()&&count(f,Items.FLINT)==1,"worker at "+f.v.position()+" did not reach drifting item at "+drop.position());
                check(f.v.distanceToSqr(start)>2,"distant water pickup bypassed walking");
            }).thenSucceed();
    }

    /** Models a Forge subscriber that expands a player hoe break to adjoining crops. */
    public static final class AreaHoeSubscriber {
        private final BlockPos target;
        private final ServerLevel level;
        private boolean veto=true;
        private boolean vetoed;
        private boolean observed;
        private boolean sawHoe;

        AreaHoeSubscriber(ServerLevel level,BlockPos target){
            this.target=target;
            this.level=level;
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGH)
        public void vetoBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent event){
            if(veto&&event.getLevel()==level&&event.getPos().equals(target)){
                vetoed=true;
                event.setCanceled(true);
            }
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void onBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent event){
            if(event.getLevel()!=level||!event.getPos().equals(target))return;
            observed=true;
            sawHoe=event.getPlayer().getMainHandItem().canPerformAction(net.minecraftforge.common.ToolActions.HOE_DIG);
            if(!sawHoe||net.minecraftforge.fml.ModList.get().isLoaded("quark"))return;
            for(var pos:BlockPos.betweenClosed(target.offset(-1,0,-1),target.offset(1,0,1))){
                if(!pos.equals(target)&&level.getBlockState(pos).getBlock() instanceof CropBlock)level.destroyBlock(pos,true,event.getPlayer());
            }
        }
    }

    @GameTest(template="empty",timeoutTicks=240,batch="farmer_area_hoe")
    public static void a_player_hoe_subscriber_cannot_expand_a_real_farmer_harvest(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        buildWalkableFloor(f,ResidenceData.get(f.l).owner(f.v.getUUID()).pos);
        var wheat=(CropBlock)Blocks.WHEAT;
        var target=f.p.east().north().above();
        var soil=Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7);
        var neighbors=new java.util.LinkedHashMap<BlockPos,BlockState>();
        for(var cell:BlockPos.betweenClosed(target.offset(-1,0,-1),target.offset(1,0,1))){
            if(cell.below().equals(f.p))continue;
            f.l.setBlock(cell.below(),soil,3);
            var state=cell.equals(target)?wheat.getStateForAge(7):wheat.getStateForAge(1);
            f.l.setBlock(cell,state,3);
            if(!cell.equals(target))neighbors.put(cell.immutable(),state);
        }
        f.s.setItem(0,new ItemStack(Items.WHEAT_SEEDS,4));
        f.s.setItem(27,new ItemStack(IndustrialTools.HOE.get()));
        f.s.setItem(30,new ItemStack(Items.WATER_BUCKET));
        var start=Vec3.atBottomCenterOf(f.p.south(2).above());
        f.v.setPos(start.x,start.y,start.z);
        var observer=new AreaHoeSubscriber(f.l,target);
        MinecraftForge.EVENT_BUS.register(observer);
        h.runAfterDelay(210,()->MinecraftForge.EVENT_BUS.unregister(observer));
        f.v.setNoAi(false);
        h.startSequence().thenWaitUntil(()->{
            check(observer.vetoed,"waiting for the Forge veto");
            check(f.l.getBlockState(target)==wheat.getStateForAge(7)&&count(f,Items.WHEAT)==0,
                "Forge veto was ignored");
            for(var plot:neighbors.entrySet())check(f.l.getBlockState(plot.getKey())==plot.getValue(),"veto did not protect a neighboring crop");
        }).thenExecute(()->observer.veto=false).thenWaitUntil(()->{
            check(observer.observed&&f.v.distanceToSqr(start)>4,"waiting for real farmer arrival and Forge event");
            for(var plot:neighbors.entrySet()){
                var cell=plot.getKey();
                check(f.l.getBlockState(cell)==plot.getValue()&&f.l.getBlockState(cell.below())==soil,
                    "Forge area harvest changed neighboring crop or support at "+cell+" with hoe="+observer.sawHoe);
            }
            check(f.l.getBlockState(target)==wheat.getStateForAge(0),"target harvest not committed");
        }).thenWaitUntil(()->check(count(f,Items.WHEAT)>0,"farmer did not collect wheat"))
            .thenExecute(()->MinecraftForge.EVENT_BUS.unregister(observer)).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=120,batch="industry")
    public static void contracted_farmer_never_breeds_and_neighbor_cannot_pocket_tagged_food(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        var normal=EntityType.VILLAGER.create(f.l);
        normal.setAge(0);
        normal.setCanPickUpLoot(true);
        var at=Vec3.atBottomCenterOf(f.p.east().above());
        normal.setPos(at.x,at.y,at.z);
        f.l.setBlock(f.p.east(),Blocks.STONE.defaultBlockState(),3);
        f.l.addFreshEntity(normal);
        f.v.getInventory().setItem(0,new ItemStack(Items.CARROT,24));
        for(int slot=0;slot<27;slot++)f.s.setItem(slot,new ItemStack(Items.STONE,64));
        normal.getInventory().setItem(0,new ItemStack(Items.CARROT,24));
        check(!f.v.canBreed()&&normal.canBreed(),"employment changed ordinary villager breeding or allowed hired villager");
        var drop=new net.minecraft.world.entity.item.ItemEntity(f.l,at.x,at.y+.05,at.z,new ItemStack(Items.CARROT,3));
        drop.getPersistentData().putUUID(br.com.kraftcity.industry.FarmerLogistics.HARVEST_STATION,f.s.id);
        drop.setDeltaMovement(Vec3.ZERO);
        f.l.addFreshEntity(drop);
        h.startSequence().thenExecuteFor(4,()->check(drop.isAlive()&&normal.getInventory().countItem(Items.CARROT)==24,
            "nearby villager took the tagged professional drop")).thenExecute(()->{
                f.v.setPos(at.x,at.y,at.z);
                f.v.setCanPickUpLoot(true);
                f.s.setItem(0,new ItemStack(Items.CARROT,63));
            }).thenWaitUntil(()->{
                check(count(f,Items.CARROT)==64&&drop.getItem().getCount()==2&&drop.isAlive(),
                    "partial pickup stock="+count(f,Items.CARROT)+" drop="+drop.getItem()+" alive="+drop.isAlive());
                check(f.v.getInventory().countItem(Items.CARROT)==24,"professional pickup used private inventory");
            }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="industry")
    public static void bordering_farmer_picks_from_own_field_and_leaves_neighbor_items(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        var otherPos=f.p.east(5);
        f.l.setBlock(otherPos,WorkModule.INDUSTRIAL.get().defaultBlockState(),3);
        var other=((WorkBlockEntity)f.l.getBlockEntity(otherPos)).station();
        other.profession=Profession.FARMER;
        other.worker=UUID.randomUUID();
        other.industry.putInt("size",5);
        var border=f.p.east(2).above();
        f.v.setPos(border.getX()+.9,border.getY(),border.getZ()+.5);
        var own=new net.minecraft.world.entity.item.ItemEntity(f.l,border.getX()+.7,border.getY(),border.getZ()+.5,new ItemStack(Items.WHEAT));
        var foreign=new net.minecraft.world.entity.item.ItemEntity(f.l,border.getX()+1.1,border.getY(),border.getZ()+.5,new ItemStack(Items.CARROT));
        f.l.addFreshEntity(own);
        f.l.addFreshEntity(foreign);
        step(f,2);
        check(!own.isAlive()&&count(f,Items.WHEAT)==1&&foreign.isAlive()&&count(f,Items.CARROT)==0,"farmer stole the neighbor's core drop");
        h.succeed();
    }

    public static final class FarmYieldAudit {
        final F farm;
        final Set<BlockPos> targets;
        final Map<Item,Integer> drops=new HashMap<>();
        int sounds;

        FarmYieldAudit(F farm,Set<BlockPos> targets){this.farm=farm;this.targets=targets;}

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void drop(net.minecraftforge.event.entity.EntityJoinLevelEvent event){
            if(event.getLevel()!=farm.l||!(event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity entity))return;
            var data=entity.getPersistentData();
            var key=br.com.kraftcity.industry.FarmerLogistics.HARVEST_STATION;
            if(data.hasUUID(key)&&data.getUUID(key).equals(farm.s.id))drops.merge(entity.getItem().getItem(),entity.getItem().getCount(),Integer::sum);
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void sound(net.minecraftforge.event.PlayLevelSoundEvent.AtPosition event){
            if(event.getLevel()==farm.l&&targets.contains(new BlockPos(event.getPosition()))
                &&event.getSource()==net.minecraft.sounds.SoundSource.BLOCKS)sounds++;
        }
    }

    @GameTest(template="empty",timeoutTicks=1500,batch="farmer_yield_conservation")
    public static void physical_harvest_conserves_ten_carrots_ten_potatoes_and_wheat_separately(GameTestHelper h){
        var f=fixture(h,Profession.FARMER);
        buildWalkableFloor(f,ResidenceData.get(f.l).owner(f.v.getUUID()).pos);
        var soil=Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7);
        var plots=new LinkedHashMap<BlockPos,Block>();
        for(var pos:FarmerWork.zigzag(f.p,5)){
            if(pos.below().equals(f.p))continue;
            int index=plots.size();
            if(index==21)break;
            var crop=(CropBlock)(index<10?Blocks.CARROTS:index<20?Blocks.POTATOES:Blocks.WHEAT);
            plots.put(pos,crop);
            f.l.setBlock(pos.below(),soil,3);
            f.l.setBlock(pos,crop.getStateForAge(crop.getMaxAge()),3);
        }
        f.s.setItem(27,new ItemStack(Items.IRON_HOE));
        f.s.setItem(30,new ItemStack(Items.WATER_BUCKET));
        level(f,1,4);
        var secondPos=f.p.east(5);
        f.l.setBlock(secondPos,WorkModule.INDUSTRIAL.get().defaultBlockState(),3);
        var second=((WorkBlockEntity)f.l.getBlockEntity(secondPos)).station();
        var caneWorker=EntityType.VILLAGER.create(f.l);
        var caneStart=Vec3.atBottomCenterOf(secondPos.south(2).above());
        caneWorker.setPos(caneStart.x,caneStart.y,caneStart.z);
        f.l.addFreshEntity(caneWorker);
        check(ResidenceData.get(f.l).link(f.l,ResidenceData.get(f.l).owner(f.v.getUUID()).pos,caneWorker).equals("ok"),"second farmer has no home");
        var player=FakePlayerFactory.get(f.l,new GameProfile(UUID.randomUUID(),"HarvestAudit"));
        player.setPos(caneStart.x,caneStart.y,caneStart.z);
        second.setItem(31,new ItemStack(Items.EMERALD,10));
        check(Employment.hire(player,second,caneWorker.getUUID(),Profession.FARMER).equals("ok"),"second farmer was not hired");
        second.setItem(27,new ItemStack(Items.IRON_HOE));
        var cane=secondPos.east().north().above();
        f.l.setBlock(cane.below(2),Blocks.STONE.defaultBlockState(),3);
        f.l.setBlock(cane.below().north().below(),Blocks.STONE.defaultBlockState(),3);
        f.l.setBlock(cane.below().north(),Blocks.WATER.defaultBlockState(),3);
        f.l.setBlock(cane.below(),Blocks.SAND.defaultBlockState(),3);
        for(int y=0;y<3;y++)f.l.setBlock(cane.above(y),Blocks.SUGAR_CANE.defaultBlockState(),3);
        var caneFarm=new F(f.l,second,caneWorker,secondPos);
        var caneAudit=new FarmYieldAudit(caneFarm,Set.of(cane.above()));
        MinecraftForge.EVENT_BUS.register(caneAudit);
        var audit=new FarmYieldAudit(f,plots.keySet());
        MinecraftForge.EVENT_BUS.register(audit);
        var start=Vec3.atBottomCenterOf(f.p.south(3).above());
        f.v.setPos(start.x,start.y,start.z);
        f.v.setNoAi(false);
        h.runAfterDelay(1490,()->{
            MinecraftForge.EVENT_BUS.unregister(audit);
            MinecraftForge.EVENT_BUS.unregister(caneAudit);
        });
        h.startSequence().thenWaitUntil(()->{
            check(caneAudit.sounds==1&&count(caneFarm,Items.SUGAR_CANE)==2&&f.l.getBlockState(cane).is(Blocks.SUGAR_CANE),
                "second farmer did not complete a physical cane harvest with one sound and two drops");
            check(count(f,Items.SUGAR_CANE)==0&&count(caneFarm,Items.CARROT)==0&&!f.v.canBreed()&&!caneWorker.canBreed(),
                "workers crossed harvest ownership or could breed");
            check(audit.sounds==21,"expected one break sound per committed harvest, heard "+audit.sounds);
            check(plots.keySet().stream().allMatch(pos->f.l.getBlockState(pos.below())==soil),"field supports were changed");
            for(var item:List.of(Items.CARROT,Items.POTATO,Items.WHEAT,Items.WHEAT_SEEDS)){
                check(count(f,item)==audit.drops.getOrDefault(item,0),"stock differs from physical drops for "+item);
            }
            check(count(f,Items.CARROT)>=10&&count(f,Items.POTATO)>=10&&count(f,Items.WHEAT)>0,
                "some of the mature crops did not produce items");
            check(IndustrialRules.level(f.l,f.s)==2&&IndustrialRules.size(f.l,f.s)==5,"area changed when the worker reached Lv2");
            check(IndustrialRules.selectSize(f.l,f.s,7)&&IndustrialRules.size(f.l,f.s)==7,"manual resize to 7 failed");
        }).thenExecute(()->{
            MinecraftForge.EVENT_BUS.unregister(audit);
            MinecraftForge.EVENT_BUS.unregister(caneAudit);
        }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=850,batch="apiary_route")
    public static void apiary_discards_and_walks_to_bed(GameTestHelper h){
        verifyLongHomeReturn(h,20,32,Profession.BEEKEEPER);
    }

    @GameTest(template="empty",timeoutTicks=480,batch="apiary_storm")
    public static void apiary_returns_during_thunder_and_works_again_when_clear(GameTestHelper h){
        var f=fixture(h,Profession.BEEKEEPER);
        var home=ResidenceData.get(f.l).owner(f.v.getUUID()).pos;
        buildWalkableFloor(f,home);
        var start=Vec3.atBottomCenterOf(f.p.south().above());
        f.v.setPos(start);
        f.v.setNoAi(false);
        var pos=hive(f,true);
        f.s.setItem(28,ItemStack.EMPTY);
        f.l.setWeatherParameters(0,10000,true,true);
        f.l.setRainLevel(1);
        f.l.setThunderLevel(1);
        h.startSequence().thenWaitUntil(()->{
            check(f.l.isThundering(),"Storm not active");
            check(f.v.position().distanceToSqr(Vec3.atCenterOf(home))<22,"Apiarist did not return during storm");
        }).thenExecute(()->{
            check(f.l.getBlockState(pos).getValue(BeehiveBlock.HONEY_LEVEL)==5&&!f.v.isSleeping(),"Storm consumed a hive or caused daytime sleep");
            f.l.setWeatherParameters(10000,0,false,false);
            f.l.setRainLevel(0);
            f.l.setThunderLevel(0);
            f.s.setItem(28,new ItemStack(Items.GLASS_BOTTLE));
        }).thenWaitUntil(()->check(count(f,Items.HONEY_BOTTLE)==1,"Apiarist did not return to harvest"))
        .thenSucceed();
    }

    private static BlockPos lumberTree(F f, BlockPos root, Block log, Block leaf, int height) {
        f.l.setBlockAndUpdate(root.below(),Blocks.DIRT.defaultBlockState());
        for (int y=0;y<height;y++) f.l.setBlockAndUpdate(root.above(y),log.defaultBlockState());
        var foliage=leaf.defaultBlockState().setValue(LeavesBlock.DISTANCE,1);
        var crown=root.above(height-1);
        for (var direction:Direction.Plane.HORIZONTAL) f.l.setBlockAndUpdate(crown.relative(direction),foliage);
        f.l.setBlockAndUpdate(crown.above(),foliage);
        return root;
    }

    @GameTest(template="empty",timeoutTicks=40,batch="lumberjack_axe_slot")
    public static void lumberjack_menu_accepts_all_seven_axe_tiers(GameTestHelper h){
        var f=fixture(h,Profession.LUMBERJACK);
        var player=FakePlayerFactory.get(f.l,new GameProfile(UUID.randomUUID(),"LumberjackMenu"));
        player.setPos(Vec3.atBottomCenterOf(f.p.south()));
        var menu=new WorkMenu(1,player.getInventory(),f.s,false,null);
        for(var item:List.of(Items.WOODEN_AXE,Items.STONE_AXE,Items.IRON_AXE,Items.GOLDEN_AXE,
                IndustrialTools.AXE.get(),Items.DIAMOND_AXE,Items.NETHERITE_AXE)){
            var axe=new ItemStack(item);
            axe.setDamageValue(7);
            menu.setCarried(axe.copy());
            menu.clicked(27,0,net.minecraft.world.inventory.ClickType.PICKUP,player);
            check(menu.getCarried().isEmpty()&&ItemStack.matches(f.s.getItem(27),axe),"axe did not reach the tool slot: "+item);
            menu.clicked(27,0,net.minecraft.world.inventory.ClickType.PICKUP,player);
            check(f.s.getItem(27).isEmpty()&&ItemStack.matches(menu.getCarried(),axe),"axe was lost, duplicated or changed");
        }
        menu.setCarried(new ItemStack(Items.OAK_SAPLING));
        menu.clicked(27,0,net.minecraft.world.inventory.ClickType.PICKUP,player);
        check(f.s.getItem(27).isEmpty()&&menu.getCarried().is(Items.OAK_SAPLING),"sapling accepted by tool slot");
        f.s.setItem(0,new ItemStack(Items.IRON_AXE));
        IndustrialWork.replenish(f.s);
        check(f.s.getItem(0).isEmpty()&&f.s.getItem(27).is(Items.IRON_AXE),"shared inventory no longer supplies the tool slot");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=650,batch="lumberjack_vanilla_growth")
    public static void lumberjack_finishes_an_oak_grown_after_hiring_beside_its_post(GameTestHelper h){
        assertLumberjackFellsVanillaOak(h, false);
    }

    @GameTest(template="empty",timeoutTicks=650,batch="lumberjack_vanilla_growth")
    public static void lumberjack_finishes_bonemealed_oak_at_area_edge(GameTestHelper h){
        assertLumberjackFellsVanillaOak(h, true);
    }

    @GameTest(template="empty",timeoutTicks=1300,batch="lumberjack_vanilla_branched")
    public static void lumberjack_waits_for_shift_and_axe_then_cuts_preexisting_branched_oak(GameTestHelper h){
        var level = h.getLevel();
        var logs = new ArrayList<BlockPos>();
        var f = fixture(h, Profession.LUMBERJACK, post -> {
            for (var floor : BlockPos.betweenClosed(post.offset(-2,-1,-2),post.offset(2,-1,2))) {
                level.setBlockAndUpdate(floor, Blocks.DIRT.defaultBlockState());
            }
            var root = post.east(2);
            var feature = net.minecraft.data.worldgen.features.TreeFeatures.FANCY_OAK.value();
            check(feature.place(level, level.getChunkSource().getGenerator(), net.minecraft.util.RandomSource.create(1), root),
                    "Could not generate a vanilla fancy oak");
            for (var pos : BlockPos.betweenClosed(root.offset(-4,0,-4),root.offset(4,20,4))) {
                if (level.getBlockState(pos).is(Blocks.OAK_LOG)) logs.add(pos.immutable());
            }
            level.setBlockAndUpdate(root.east(),Blocks.STONE.defaultBlockState());
            check(logs.stream().anyMatch(pos -> pos.getY() > post.getY() + 4
                    && (pos.getX() != root.getX() || pos.getZ() != root.getZ())), "Generated oak did not have a high branch");
        });
        f.v.setPos(Vec3.atBottomCenterOf(f.p.west(2).north(2)));
        f.s.setItem(EmploymentData.TOOL, new ItemStack(Items.NETHERITE_AXE));
        level.setDayTime(13000);
        f.v.setNoAi(false);
        h.startSequence().thenExecuteAfter(90, () -> {
            check(logs.stream().allMatch(pos -> level.getBlockState(pos).is(Blocks.OAK_LOG)), "Cut started at night");
            f.s.setItem(EmploymentData.TOOL, ItemStack.EMPTY);
            level.setDayTime(1000);
        }).thenExecuteAfter(90, () -> {
            check(logs.stream().allMatch(pos -> level.getBlockState(pos).is(Blocks.OAK_LOG)), "Cut started without an axe");
            f.s.setItem(EmploymentData.TOOL, new ItemStack(Items.NETHERITE_AXE));
        }).thenWaitUntil(() -> {
            check(logs.stream().noneMatch(pos -> level.getBlockState(pos).is(Blocks.OAK_LOG)), "Vanilla fancy oak left uncut logs");
            check(count(f, Items.OAK_LOG) == logs.size(), "Logs missing from a mature tree");
        }).thenExecute(() -> f.v.setNoAi(true)).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=650,batch="lumberjack_vanilla_short_oak")
    public static void lumberjack_reaches_down_from_its_own_post_when_oak_grows_beside_it(GameTestHelper h){
        var f = fixture(h, Profession.LUMBERJACK);
        var root = f.p.east();
        f.l.setBlockAndUpdate(root.below(), Blocks.DIRT.defaultBlockState());
        f.v.setPos(Vec3.atBottomCenterOf(f.p.above()).add(0, 0, .1));
        f.s.setItem(EmploymentData.TOOL, new ItemStack(Items.NETHERITE_AXE));
        var feature = net.minecraft.data.worldgen.features.TreeFeatures.OAK.value();
        check(feature.place(f.l, f.l.getChunkSource().getGenerator(), net.minecraft.util.RandomSource.create(0), root),
                "Could not generate short vanilla oak");
        var logs = new ArrayList<BlockPos>();
        for (var pos : BlockPos.betweenClosed(root, root.above(8))) {
            if (f.l.getBlockState(pos).is(Blocks.OAK_LOG)) logs.add(pos.immutable());
        }
        check(logs.size() == 4, "Expected a four-log oak");
        f.v.setNoAi(false);
        h.succeedWhen(() -> {
            check(logs.stream().noneMatch(pos -> f.l.getBlockState(pos).is(Blocks.OAK_LOG)), "Short oak had uncut logs");
            check(count(f, Items.OAK_LOG) == logs.size(), "Missing short oak logs");
            check(f.l.getBlockState(f.p).is(WorkModule.PROFESSIONAL.get(Profession.LUMBERJACK).get()), "Worker cut its own post");
            f.v.setNoAi(true);
        });
    }

    private static void assertLumberjackFellsVanillaOak(GameTestHelper h, boolean boneMeal) {
        var f = fixture(h, Profession.LUMBERJACK);
        // The full suite reuses space for multiple batches. Keep this approach and canopy clear.
        for (var cell : BlockPos.betweenClosed(f.p.offset(-4,-1,-4),f.p.offset(4,12,4))) {
            if (cell.getY() == f.p.getY() - 1) {
                f.l.setBlockAndUpdate(cell, Blocks.DIRT.defaultBlockState());
            } else if (!cell.equals(f.p) && !f.l.isEmptyBlock(cell)) {
                f.l.removeBlock(cell, false);
            }
        }
        var root = f.p.east(boneMeal ? 2 : 1);
        var start = boneMeal
                ? Vec3.atBottomCenterOf(f.p.west(2).north(2)).add(-.4, 0, -.4)
                : Vec3.atBottomCenterOf(f.p.west(2)).add(0, 0, .17);
        f.v.setPos(start);
        f.s.setItem(EmploymentData.TOOL, new ItemStack(Items.NETHERITE_AXE));
        f.v.setNoAi(false);
        f.l.setBlockAndUpdate(root, Blocks.OAK_SAPLING.defaultBlockState());
        var sapling = (SaplingBlock) Blocks.OAK_SAPLING;
        var random = net.minecraft.util.RandomSource.create(9);
        var logs = new ArrayList<BlockPos>();
        h.startSequence().thenExecuteAfter(100, () -> {
            // Exercise detection during the shift from a known approach rather than a random idle position.
            f.v.getNavigation().stop();
            f.v.setPos(start);
            for (int attempt = 0; attempt < 100 && f.l.getBlockState(root).is(sapling); attempt++) {
                var state = f.l.getBlockState(root);
                if (boneMeal) {
                    var actor=FakePlayerFactory.get(f.l,new GameProfile(f.v.getUUID(),"LumberjackGrower"));
                    actor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.BONE_MEAL,64));
                    var hit=new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(root),Direction.UP,root,false);
                    net.minecraftforge.common.ForgeHooks.onPlaceItemIntoWorld(
                        new net.minecraft.world.item.context.UseOnContext(actor,net.minecraft.world.InteractionHand.MAIN_HAND,hit));
                } else sapling.randomTick(state, f.l, root, random);
            }
            check(f.l.getBlockState(root).is(Blocks.OAK_LOG), "Vanilla sapling did not grow");
            for (var pos : BlockPos.betweenClosed(root.offset(-4,0,-4),root.offset(4,16,4))) {
                if (f.l.getBlockState(pos).is(Blocks.OAK_LOG)) logs.add(pos.immutable());
            }
            check(logs.size() >= 4, "Vanilla tree had fewer than four logs");
            f.l.setBlockAndUpdate(root.east(),Blocks.STONE.defaultBlockState());
        }).thenWaitUntil(() -> {
            var remaining = logs.stream().filter(pos -> f.l.getBlockState(pos).is(Blocks.OAK_LOG)).toList();
            check(remaining.isEmpty(), "Vanilla oak left uncut logs " + remaining + " at post " + f.p
                    + " with worker at " + f.v.position() + " and axe damage " + f.s.getItem(EmploymentData.TOOL).getDamageValue());
            check(f.v.position().distanceToSqr(start) > 1, "Worker never approached the trunk");
            check(count(f, Items.OAK_LOG) == logs.size(), "Some generated logs were not collected");
            check(f.s.getItem(EmploymentData.TOOL).getDamageValue() == logs.size(), "Durability did not match actual cuts");
            check(f.l.getBlockState(f.p).is(WorkModule.PROFESSIONAL.get(Profession.LUMBERJACK).get()), "Tree cut affected the work post");
        }).thenExecute(() -> f.v.setNoAi(true)).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=60,batch="lumberjack_bounds")
    public static void lumberjack_area_is_exact_manual_and_survives_save(GameTestHelper h){
        var f=fixture(h,Profession.LUMBERJACK);
        check(f.l.getBlockState(f.p).is(WorkModule.PROFESSIONAL.get(Profession.LUMBERJACK).get()),"station conversion");
        check(f.s.getItem(EmploymentData.PAYMENT).isEmpty(),"ten emeralds");
        check(!IndustrialRules.selectSize(f.l,f.s,25),"level one unlocked the second option");
        for(int level=1;level<=5;level++){
            level(f,level,(level-1)*25L);
            check(IndustrialRules.size(f.l,f.s)==15+10*Math.max(0,level-2),"level expanded area");
            int width=15+10*(level-1);
            var legacy=EmploymentData.load(EmploymentData.get(f.l).save(new CompoundTag())).station(f.s.id);
            legacy.industry.remove("lumberjack_width");
            legacy.industry.putInt("size",level*5);
            check(IndustrialRules.size(f.l,legacy)==width && IndustrialRules.size(f.l,legacy)==width,"legacy option migrated incorrectly");
            check(IndustrialRules.selectSize(f.l,f.s,width),"could not select "+width);
            var bounds=IndustrialWork.area(f.l,f.s);
            check(bounds.getXsize()==width&&bounds.getYsize()==15&&bounds.getZsize()==width,"wrong dimensions "+bounds);
            check(bounds.minX==f.p.getX()-width/2&&bounds.maxX==f.p.getX()+width/2+1
                    &&bounds.minZ==f.p.getZ()-width/2&&bounds.maxZ==f.p.getZ()+width/2+1
                    &&bounds.minY==f.p.getY()-7&&bounds.maxY==f.p.getY()+8,"incorrect centering");
            for(int dx:new int[]{-width/2,0,width/2})for(int dz:new int[]{-width/2,0,width/2}){
                check(bounds.contains(Vec3.atBottomCenterOf(f.p.offset(dx,-7,dz))),"bottom boundary missing");
                check(bounds.contains(Vec3.atBottomCenterOf(f.p.offset(dx,7,dz))),"top boundary missing");
            }
            check(!bounds.contains(Vec3.atBottomCenterOf(f.p.east(width/2+1))),"outside horizontal boundary");
            check(!bounds.contains(Vec3.atBottomCenterOf(f.p.above(8))),"outside vertical boundary");
        }
        var restored=EmploymentData.load(EmploymentData.get(f.l).save(new CompoundTag())).station(f.s.id);
        check(restored!=null&&restored.worker.equals(f.v.getUUID())&&restored.profession==Profession.LUMBERJACK
                &&restored.industry.getInt("lumberjack_width")==55,"saved lumberjack state");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=400,batch="lumberjack_upper_corner")
    public static void lumberjack_cuts_tree_starting_at_upper_corner(GameTestHelper h){
        assertLumberjackCutsAtCorner(h,7);
    }

    @GameTest(template="empty",timeoutTicks=400,batch="lumberjack_lower_corner")
    public static void lumberjack_cuts_tree_starting_at_lower_corner(GameTestHelper h){
        assertLumberjackCutsAtCorner(h,-7);
    }

    private static void assertLumberjackCutsAtCorner(GameTestHelper h,int height){
        var f=fixture(h,Profession.LUMBERJACK,12,post->{});
        var edge=f.p.offset(7,height,7);
        var outside=f.p.offset(-8,height,7);
        for(var floor:BlockPos.betweenClosed(f.p.offset(-8,height-1,3),edge.below()))
            f.l.setBlockAndUpdate(floor,Blocks.STONE.defaultBlockState());
        var root=lumberTree(f,edge,Blocks.OAK_LOG,Blocks.OAK_LEAVES,4);
        lumberTree(f,outside,Blocks.OAK_LOG,Blocks.OAK_LEAVES,4);
        var start=Vec3.atBottomCenterOf(root.west(5).north(2));
        f.v.setPos(start);
        f.s.setItem(EmploymentData.TOOL,new ItemStack(Items.NETHERITE_AXE));
        f.v.setNoAi(false);
        h.succeedWhen(()->{
            check(count(f,Items.OAK_LOG)==4,"worker did not finish the boundary tree");
            check(f.v.distanceToSqr(start)>1,"worker did not approach the boundary tree");
            check(f.l.isEmptyBlock(root)&&f.l.isEmptyBlock(root.above(3)),"boundary tree left logs");
            check(f.l.getBlockState(outside).is(Blocks.OAK_LOG),"cut an independent tree outside the area");
            f.v.setNoAi(true);
        });
    }

    private static void lumberTime(GameTestHelper h,Item axe,int ticks){
        var f=fixture(h,Profession.LUMBERJACK);
        for(var p:BlockPos.betweenClosed(f.p.offset(-2,-1,-2),f.p.offset(2,-1,2))) f.l.setBlockAndUpdate(p,Blocks.DIRT.defaultBlockState());
        var root=lumberTree(f,f.p.east(2),Blocks.OAK_LOG,Blocks.OAK_LEAVES,6);
        f.v.setPos(f.p.getX()+1.5,f.p.getY(),f.p.getZ()+1.5);
        f.s.setItem(EmploymentData.TOOL,new ItemStack(axe));
        f.v.setNoAi(false);
        long[] first={-1}, second={-1};
        h.succeedWhen(()->{
            if(first[0]<0){
                check(f.l.isEmptyBlock(root),"waiting for first cut");
                first[0]=f.l.getGameTime();
            }
            check(f.l.isEmptyBlock(root.above()),"waiting for second cut");
            if(second[0]<0)second[0]=f.l.getGameTime();
            long since=second[0]-first[0];
            check(since>=ticks&&since<=ticks+2,"second cut took "+since+" ticks instead of "+ticks);
            check(count(f,Items.OAK_LOG)==2&&f.s.getItem(27).getDamageValue()==2,"loot or durability did not commit twice");
            f.v.setNoAi(true);
        });
    }

    @GameTest(template="empty",timeoutTicks=770,batch="lumberjack_timing")
    public static void lumberjack_wood_300_ticks(GameTestHelper h){lumberTime(h,Items.WOODEN_AXE,300);}
    @GameTest(template="empty",timeoutTicks=660,batch="lumberjack_timing")
    public static void lumberjack_stone_250_ticks(GameTestHelper h){lumberTime(h,Items.STONE_AXE,250);}
    @GameTest(template="empty",timeoutTicks=550,batch="lumberjack_timing")
    public static void lumberjack_iron_200_ticks(GameTestHelper h){lumberTime(h,Items.IRON_AXE,200);}
    @GameTest(template="empty",timeoutTicks=450,batch="lumberjack_timing")
    public static void lumberjack_gold_150_ticks(GameTestHelper h){lumberTime(h,Items.GOLDEN_AXE,150);}
    @GameTest(template="empty",timeoutTicks=350,batch="lumberjack_timing")
    public static void lumberjack_emerald_100_ticks(GameTestHelper h){lumberTime(h,IndustrialTools.AXE.get(),100);}
    @GameTest(template="empty",timeoutTicks=260,batch="lumberjack_timing")
    public static void lumberjack_diamond_50_ticks(GameTestHelper h){lumberTime(h,Items.DIAMOND_AXE,50);}

    @GameTest(template="empty",timeoutTicks=720,batch="lumberjack_tall")
    public static void lumberjack_netherite_and_tall_tree_leave_outside_and_decorative_logs(GameTestHelper h){
        var f=fixture(h,Profession.LUMBERJACK);
        for(var p:BlockPos.betweenClosed(f.p.offset(-2,-1,-2),f.p.offset(2,-1,2))) f.l.setBlockAndUpdate(p,Blocks.DIRT.defaultBlockState());
        f.v.setPos(f.p.getX()+1.5,f.p.getY(),f.p.getZ()+1.5);
        var root=lumberTree(f,f.p.east(2),Blocks.SPRUCE_LOG,Blocks.SPRUCE_LEAVES,8);
        var outside=lumberTree(f,f.p.west(3),Blocks.BIRCH_LOG,Blocks.BIRCH_LEAVES,5);
        var decorated=f.p.south(2).west();
        for(int y=0;y<3;y++) f.l.setBlockAndUpdate(decorated.above(y),Blocks.OAK_LOG.defaultBlockState());
        f.s.setItem(EmploymentData.TOOL,new ItemStack(Items.NETHERITE_AXE));
        f.v.setNoAi(false);
        long[] first={-1};
        h.succeedWhen(()->{
            check(f.l.isEmptyBlock(root),"waiting for first cut");
            if(first[0]<0)first[0]=f.l.getGameTime();
            if(f.l.getGameTime()-first[0]<40&&f.l.isEmptyBlock(root.above()))h.fail("netherite cut before its full duration");
            check(f.l.isEmptyBlock(root.above(7)),"waiting for the top log");
            check(count(f,Items.SPRUCE_LOG)==8&&f.s.getItem(27).getDamageValue()==8,"tall tree count or wear");
            check(f.l.getBlockState(outside).is(Blocks.BIRCH_LOG)&&f.l.getBlockState(decorated).is(Blocks.OAK_LOG),"outside tree or decor changed");
            check(f.l.getBlockState(root.east().above(7)).is(Blocks.SPRUCE_LEAVES),"canopy destroyed immediately");
            f.v.setNoAi(true);
        });
    }

    @GameTest(template="empty",timeoutTicks=360,batch="lumberjack_replant")
    public static void lumberjack_replants_a_single_sapling_after_respecting_the_clearance(GameTestHelper h){
        var f=fixture(h,Profession.LUMBERJACK);
        level(f,5,100);
        check(IndustrialRules.selectSize(f.l,f.s,25),"select replant area");
        var seed=f.p.east(8);
        for(var soil:BlockPos.betweenClosed(f.p.offset(-12,-1,-12),f.p.offset(12,-1,12))) f.l.setBlockAndUpdate(soil,Blocks.STONE.defaultBlockState());
        lumberClearance(f,seed);
        f.l.setBlockAndUpdate(seed.below(),Blocks.DIRT.defaultBlockState());
        f.l.setBlockAndUpdate(seed,Blocks.DANDELION.defaultBlockState());
        f.v.setPos(seed.getX()+.5,seed.getY(),seed.getZ()+1.5);
        f.s.setItem(EmploymentData.INPUT_A,new ItemStack(Items.BIRCH_SAPLING));
        var obstacle=seed.north(6);
        f.l.setBlockAndUpdate(obstacle,Blocks.STONE.defaultBlockState());
        f.v.setNoAi(false);
        h.runAfterDelay(70,()->{
            check(!f.l.getBlockState(seed).is(Blocks.BIRCH_SAPLING)&&f.s.getItem(28).getCount()==1,"planted too near an obstacle");
            f.l.removeBlock(obstacle,false);
        });
        h.succeedWhen(()->{
            check(f.l.getBlockState(seed).is(Blocks.BIRCH_SAPLING),"waiting for a legal sapling");
            check(f.s.getItem(28).isEmpty(),"seed was not consumed");
            f.v.setNoAi(true);
        });
    }

    @GameTest(template="empty",timeoutTicks=620,batch="lumberjack_protection")
    public static void lumberjack_rejects_placed_pillars_and_resumes_after_axe_break(GameTestHelper h){
        var f=fixture(h,Profession.LUMBERJACK);
        for(var cell:BlockPos.betweenClosed(f.p.offset(-2,-1,-2),f.p.offset(2,-1,2)))
            f.l.setBlockAndUpdate(cell,Blocks.DIRT.defaultBlockState());
        var good=lumberTree(f,f.p.east(2),Blocks.BIRCH_LOG,Blocks.BIRCH_LEAVES,5);
        var placed=lumberTree(f,f.p.west(2),Blocks.OAK_LOG,Blocks.OAK_LEAVES,5);
        var actor=FakePlayerFactory.get(f.l,new GameProfile(UUID.randomUUID(),"LumberjackBuilder"));
        var snapshot=net.minecraftforge.common.util.BlockSnapshot.create(f.l.dimension(),f.l,placed.above(2));
        MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent(
                snapshot,f.l.getBlockState(placed.below()),actor));
        var lastUse=new ItemStack(Items.NETHERITE_AXE);
        lastUse.setDamageValue(lastUse.getMaxDamage()-1);
        f.s.setItem(27,lastUse);
        f.v.setPos(f.p.getX()+1.5,f.p.getY(),f.p.getZ()+1.5);
        f.v.setNoAi(false);
        h.startSequence().thenWaitUntil(()->{
            check(f.l.isEmptyBlock(good)&&f.s.getItem(27).isEmpty(),"first cut did not break axe");
            check(count(f,Items.BIRCH_LOG)==1,"first cut output");
        }).thenIdle(65).thenExecute(()->{
            check(f.l.getBlockState(good.above()).is(Blocks.BIRCH_LOG),"cut continued without axe");
            check(f.l.getBlockState(placed).is(Blocks.OAK_LOG),"constructed trunk changed");
            f.s.setItem(4,new ItemStack(Items.NETHERITE_AXE));
        }).thenWaitUntil(()->{
            check(f.l.isEmptyBlock(good.above(4)),"waiting for resumed tree");
            check(count(f,Items.BIRCH_LOG)==5&&f.s.getItem(27).getDamageValue()==4,"resumed tree duplicated output or wear");
            check(f.l.getBlockState(placed).is(Blocks.OAK_LOG),"constructed trunk changed");
            f.v.setNoAi(true);
        }).thenSucceed();
    }

    private static void lumberClearance(F f,BlockPos root){
        for(var pos:BlockPos.betweenClosed(root.offset(-6,0,-6),root.offset(7,8,7))){
            if(pos.distSqr(f.p)<1)continue;
            if(!f.l.isEmptyBlock(pos))f.l.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        }
    }

    @GameTest(template="empty",timeoutTicks=420,batch="lumberjack_dark_oak")
    public static void lumberjack_dark_oak_waits_for_four_saplings(GameTestHelper h){
        var f=fixture(h,Profession.LUMBERJACK);
        level(f,5,100);
        check(IndustrialRules.selectSize(f.l,f.s,25),"select dark oak area");
        var root=f.p.east(8);
        for(var cell:BlockPos.betweenClosed(f.p.offset(-12,-1,-12),f.p.offset(12,-1,12)))
            f.l.setBlockAndUpdate(cell,Blocks.STONE.defaultBlockState());
        lumberClearance(f,root);
        var square=List.of(root,root.east(),root.south(),root.south().east());
        for(var cell:square)f.l.setBlockAndUpdate(cell.below(),Blocks.DIRT.defaultBlockState());
        f.s.setItem(28,new ItemStack(Items.DARK_OAK_SAPLING));
        var firstPlotOnly = Vec3.atBottomCenterOf(root.west(2)).add(-.5, 0, 0);
        f.v.setPos(firstPlotOnly);
        f.v.setNoAi(false);
        h.runAfterDelay(80,()->{
            check(square.stream().noneMatch(cell->f.l.getBlockState(cell).is(Blocks.DARK_OAK_SAPLING))
                &&f.s.getItem(28).getCount()==1,"dark oak used only one sapling");
            f.v.getNavigation().stop();
            f.v.setPos(firstPlotOnly);
            f.s.setItem(28,new ItemStack(Items.DARK_OAK_SAPLING,4));
        });
        h.succeedWhen(()->{
            check(square.stream().allMatch(cell->f.l.getBlockState(cell).is(Blocks.DARK_OAK_SAPLING)),"waiting for dark oak square");
            check(f.s.getItem(28).isEmpty(),"did not consume the four saplings");
            check(f.v.position().distanceToSqr(firstPlotOnly) > .25, "Did not approach the whole planting square");
            f.v.setNoAi(true);
        });
    }

    @GameTest(template="empty",timeoutTicks=360,batch="lumberjack_reduce")
    public static void lumberjack_resize_cancels_cutting_outside_the_new_area(GameTestHelper h){
        var f=fixture(h,Profession.LUMBERJACK);
        for(var cell:BlockPos.betweenClosed(f.p.offset(-5,-1,-5),f.p.offset(9,-1,4)))
            f.l.setBlockAndUpdate(cell,Blocks.DIRT.defaultBlockState());
        level(f,2,25);
        check(IndustrialRules.selectSize(f.l,f.s,25),"select initial larger area");
        var root=lumberTree(f,f.p.east(8),Blocks.OAK_LOG,Blocks.OAK_LEAVES,5);
        f.s.setItem(27,new ItemStack(Items.NETHERITE_AXE));
        f.v.setPos(f.p.getX()+7.5,f.p.getY(),f.p.getZ()+1.5);
        f.v.setNoAi(false);
        h.startSequence().thenWaitUntil(()->check(f.l.isEmptyBlock(root),"waiting for first cut"))
            .thenExecute(()->check(IndustrialRules.selectSize(f.l,f.s,15),"shrink area"))
            .thenIdle(85).thenExecute(()->{
                check(f.l.getBlockState(root.above()).is(Blocks.OAK_LOG)&&count(f,Items.OAK_LOG)==1,"cut outside new area");
                f.v.setNoAi(true);
            }).thenSucceed();
    }
}
