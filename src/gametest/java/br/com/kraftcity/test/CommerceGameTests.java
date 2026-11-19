package br.com.kraftcity.test;

import br.com.kraftcity.commerce.*;
import br.com.kraftcity.employment.*;
import br.com.kraftcity.industry.*;
import br.com.kraftcity.logistics.DiscardChests;
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
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;

@GameTestHolder("residenciasaldeoes")
@PrefixGameTestTemplate(false)
public final class CommerceGameTests {
    private record F(ServerLevel l,EmploymentData.Station s,Villager v,ServerPlayer player,BlockPos home,BlockPos p){}
    private static void check(boolean value,String message){if(!value)throw new GameTestAssertException(message);}
    private static F fixture(GameTestHelper h,Profession prof){
        var l=h.getLevel();l.setDayTime(1000);l.setWeatherParameters(10000,0,false,false);
        var home=h.absolutePos(new BlockPos(2,2,2));var p=h.absolutePos(new BlockPos(8,2,8));
        l.setBlock(home,ResidenciasDosAldeoes.ANCHOR.get().defaultBlockState(),3);((br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity)l.getBlockEntity(home)).setSize(11);
        var v=h.spawn(EntityType.VILLAGER,new BlockPos(3,2,2));v.setAge(0);v.setNoAi(true);check(ResidenceData.get(l).link(l,home,v).equals("ok"),"home fixture");v.setPos(p.getX()+.5,p.getY(),p.getZ()+1.5);
        l.setBlock(p.below(),Blocks.STONE.defaultBlockState(),3);l.setBlock(p,WorkModule.COMMERCIAL.get().defaultBlockState(),3);var s=((WorkBlockEntity)l.getBlockEntity(p)).station();
        var player=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"CommerceTest"));player.setPos(p.getX()+.5,p.getY(),p.getZ()+1.5);s.setItem(31,new ItemStack(Items.EMERALD,30));
        String result=Employment.hire(player,s,v.getUUID(),prof);check(result.equals("ok"),"commercial hire "+prof+" result="+result+" cost="+EmploymentConfig.cost(Profession.Sector.COMMERCIAL)+" eligible="+Employment.eligible(l,v));return new F(l,s,v,player,home,p);
    }
    private static int count(F f,Item item){int n=0;for(int i=0;i<27;i++)if(f.s.getItem(i).is(item))n+=f.s.getItem(i).getCount();return n;}
    private static void work(F f,int ticks){while(ticks>0){int n=Math.min(ticks,20);CommercialWork.step(f.l,f.s,n);ticks-=n;}}
    private static void level(F f,int n,long xp){WorkforceData.get(f.l).setProgress(f.v.getUUID(),f.s.profession.id(),n,xp);}
    private static BarrelBlockEntity barrel(F f,BlockPos p){f.l.setBlock(p,Blocks.BARREL.defaultBlockState(),3);DiscardChests.invalidate(f.l);return (BarrelBlockEntity)f.l.getBlockEntity(p);}
    private static EmploymentData.Station source(F f,Profession prof,BlockPos p){f.l.setBlock(p,WorkModule.PROFESSIONAL.get(prof).get().defaultBlockState(),3);return ((WorkBlockEntity)f.l.getBlockEntity(p)).station();}
    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void approved_commercial_hiring_all_three_cost_ten_and_have_real_blocks(GameTestHelper h){
        check(EmploymentConfig.COMMERCIAL_ENABLED.get()&&EmploymentConfig.cost(Profession.Sector.COMMERCIAL)==10,"approved defaults absent");
        for(var p:List.of(Profession.BAKER,Profession.CONFECTIONER,Profession.GRILL_COOK)){
            var f=fixture(h,p);check(f.s.getItem(31).getCount()==20&&f.l.getBlockState(f.p).is(WorkModule.PROFESSIONAL.get(p).get()),"price/physical block "+p);
            check(!Employment.hire(f.player,f.s,f.v.getUUID(),p).equals("ok")&&f.s.getItem(31).getCount()==20,"double commercial hire");
            check(ResidenceData.get(f.l).owner(f.v.getUUID()).pos.equals(f.home),"housing lost");
        }h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void old_disabled_config_is_migrated_once_and_later_admin_pause_is_retained(GameTestHelper h){
        var old=new CommerceConfigMigration.Settings(false,-1,0);var migrated=CommerceConfigMigration.migrate(old);
        check(migrated.enabled()&&migrated.cost()==10&&migrated.rules()==1,"old defaults not migrated");
        var paused=new CommerceConfigMigration.Settings(false,10,1);check(CommerceConfigMigration.migrate(paused)==paused,"repeated migration overwrote admin");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void baker_requires_full_duration_and_three_wheat_per_recipe(GameTestHelper h){
        var f=fixture(h,Profession.BAKER);f.s.setItem(0,new ItemStack(Items.WHEAT,4));work(f,1180);
        check(count(f,Items.BREAD)==0&&count(f,Items.WHEAT)==4,"instant output or early debit");work(f,20);
        check(count(f,Items.BREAD)==1&&count(f,Items.WHEAT)==1&&WorkforceData.get(f.l).progress(f.v.getUUID(),Profession.BAKER.id()).xp()==1,"bread transaction");
        work(f,1200);check(count(f,Items.BREAD)==1&&count(f,Items.WHEAT)==1,"insufficient stock produced bread");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void confectioner_uses_existing_cane_recipe_without_new_recipe_or_free_batch(GameTestHelper h){
        var f=fixture(h,Profession.CONFECTIONER);var plan=CommercialRecipes.plans(f.l,Profession.CONFECTIONER).get(0);
        check(plan.input()==Items.SUGAR_CANE&&plan.count()==1&&plan.output().is(Items.SUGAR)&&plan.output().getCount()==1,"vanilla sugar conversion mismatch");
        f.s.setItem(0,new ItemStack(Items.SUGAR_CANE,2));work(f,1200);check(count(f,Items.SUGAR)==1&&count(f,Items.SUGAR_CANE)==1,"sugar batch or wrong input");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void grill_preserves_species_quantities_and_one_coal_cooks_eight_recipes(GameTestHelper h){
        var f=fixture(h,Profession.GRILL_COOK);f.s.setItem(30,new ItemStack(Items.COAL));f.s.setItem(0,new ItemStack(Items.BEEF,8));
        for(int i=0;i<8;i++)work(f,1200);
        check(count(f,Items.COOKED_BEEF)==8&&count(f,Items.BEEF)==0&&f.s.getItem(30).isEmpty()&&f.s.commerce.getLong("heat")==0,"coal recipe credit");
        for(var pair:List.of(new Item[]{Items.PORKCHOP,Items.COOKED_PORKCHOP},new Item[]{Items.CHICKEN,Items.COOKED_CHICKEN},new Item[]{Items.MUTTON,Items.COOKED_MUTTON},new Item[]{Items.RABBIT,Items.COOKED_RABBIT})){
            f.s.setItem(0,new ItemStack(pair[0]));f.s.setItem(30,new ItemStack(Items.COAL));work(f,1200);check(count(f,pair[1])==1,"wrong meat species "+pair[0]);
        }h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void fuel_shortage_pauses_without_consuming_input_or_resetting_progress(GameTestHelper h){
        var f=fixture(h,Profession.GRILL_COOK);f.s.setItem(0,new ItemStack(Items.BEEF));work(f,600);check(count(f,Items.BEEF)==1&&count(f,Items.COOKED_BEEF)==0&&f.s.commerce.getInt("elapsed")==0,"free cooking");
        f.s.setItem(30,new ItemStack(Items.COAL));work(f,600);check(f.s.commerce.getInt("elapsed")==600,"half operation");f.s.setItem(30,ItemStack.EMPTY);work(f,600);
        check(f.s.commerce.getInt("elapsed")==600&&count(f,Items.BEEF)==1,"missing fuel reset/debited");f.s.setItem(30,new ItemStack(Items.COAL));work(f,600);check(count(f,Items.COOKED_BEEF)==1,"fuel resume");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void small_fuels_are_combined_and_lava_bucket_container_is_preserved(GameTestHelper h){
        var f=fixture(h,Profession.GRILL_COOK);f.s.setItem(0,new ItemStack(Items.BEEF,2));f.s.setItem(30,new ItemStack(Items.STICK,2));work(f,1200);
        check(count(f,Items.COOKED_BEEF)==1&&f.s.getItem(30).isEmpty()&&f.s.commerce.getLong("heat")==0,"two sticks not combined");
        f.s.setItem(30,new ItemStack(Items.LAVA_BUCKET));work(f,1200);check(count(f,Items.COOKED_BEEF)==2&&count(f,Items.BUCKET)==1&&f.s.commerce.getLong("heat")==19800,"lava remaining bucket/heat");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void commercial_levels_derive_from_completed_recipes_at_25_50_75_100(GameTestHelper h){
        var f=fixture(h,Profession.BAKER);for(int i=1;i<=100;i++){
            f.s.setItem(0,new ItemStack(Items.WHEAT,3));int before=CommercialWork.level(f.l,f.s);work(f,CommercialWork.duration(before));
            var progress=WorkforceData.get(f.l).progress(f.v.getUUID(),Profession.BAKER.id());check(progress.xp()==i&&progress.level()==Math.min(5,1+i/25),"commercial threshold "+i);
        }
        int[] times={1200,1000,800,600,300};for(int i=1;i<=5;i++)check(CommercialWork.duration(i)==times[i-1],"commercial seconds Lv"+i);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void output_full_keeps_ready_operation_inputs_fuel_and_xp_until_space_exists(GameTestHelper h){
        var f=fixture(h,Profession.GRILL_COOK);f.s.setItem(28,new ItemStack(Items.BEEF));f.s.setItem(30,new ItemStack(Items.COAL));for(int i=0;i<27;i++)f.s.setItem(i,new ItemStack(Items.STONE,64));
        work(f,1200);check(f.s.getItem(28).is(Items.BEEF)&&f.s.getItem(30).getCount()==1&&f.s.commerce.getInt("elapsed")==1200&&WorkforceData.get(f.l).progress(f.v.getUUID(),Profession.GRILL_COOK.id()).xp()==0,"full output consumed transaction");
        f.s.setItem(0,ItemStack.EMPTY);CommercialWork.step(f.l,f.s,0);check(count(f,Items.COOKED_BEEF)==1&&f.s.getItem(28).isEmpty()&&f.s.getItem(30).isEmpty(),"ready transaction not resumed");
        CommercialWork.step(f.l,f.s,0);check(count(f,Items.COOKED_BEEF)==1,"double completion");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void night_thunder_and_missing_material_pause_without_free_elapsed_or_loss(GameTestHelper h){
        var f=fixture(h,Profession.BAKER);f.s.setItem(0,new ItemStack(Items.WHEAT,3));work(f,400);
        f.l.setDayTime(13000);work(f,1200);check(f.s.commerce.getInt("elapsed")==400&&count(f,Items.WHEAT)==3,"night work");
        f.l.setDayTime(1000);f.l.setWeatherParameters(0,1000,true,false);f.l.setRainLevel(1F);
        work(f,100);check(f.s.commerce.getInt("elapsed")==500,"ordinary rain paused commercial work");
        f.l.setThunderLevel(1F);work(f,200);check(f.s.commerce.getInt("elapsed")==500,"storm work");
        f.l.setWeatherParameters(10000,0,false,false);f.l.setRainLevel(0F);f.l.setThunderLevel(0F);
        f.s.setItem(0,ItemStack.EMPTY);work(f,200);check(f.s.commerce.getInt("elapsed")==500,"missing input reset progress");f.s.setItem(0,new ItemStack(Items.WHEAT,3));work(f,700);check(count(f,Items.BREAD)==1,"pause resume output");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void housing_loss_blocks_work_and_same_profession_rehire_retains_level_and_partial_recipe(GameTestHelper h){
        var f=fixture(h,Profession.BAKER);level(f,4,75);f.s.setItem(0,new ItemStack(Items.WHEAT,3));work(f,300);ResidenceData.get(f.l).unlink(f.l,f.v.getUUID());work(f,600);
        check(f.s.worker==null&&count(f,Items.BREAD)==0&&f.s.commerce.getInt("elapsed")==300,"homeless production/partial loss");
        check(ResidenceData.get(f.l).link(f.l,f.home,f.v).equals("ok"),"recovered home");check(f.s.worker==null,"automatic rehire");check(Employment.hire(f.player,f.s,f.v.getUUID(),Profession.BAKER).equals("ok"),"rehire");work(f,300);
        var p=WorkforceData.get(f.l).progress(f.v.getUUID(),Profession.BAKER.id());check(count(f,Items.BREAD)==1&&p.level()==4&&p.xp()==76&&f.s.getItem(31).getCount()==19,"rehire history/payment");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void schema_one_roundtrip_keeps_partial_recipe_heat_and_all_existing_inventory_slots(GameTestHelper h){
        var f=fixture(h,Profession.GRILL_COOK);f.s.setItem(28,new ItemStack(Items.BEEF,2));f.s.setItem(30,new ItemStack(Items.COAL));work(f,1200);work(f,400);
        var restored=EmploymentData.load(EmploymentData.get(f.l).save(new CompoundTag())).station(f.s.id);
        check(restored.commerce.getInt("elapsed")==400&&restored.commerce.getLong("heat")==1400&&restored.getItem(28).getCount()==1&&restored.worker.equals(f.v.getUUID()),"commercial state persistence");
        var legacy=EmploymentData.get(f.l).save(new CompoundTag());for(Tag raw:legacy.getList("stations",Tag.TAG_COMPOUND))((CompoundTag)raw).remove("commerce");
        check(EmploymentData.load(legacy).station(f.s.id).getItem(28).getCount()==1,"legacy schema inventory");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void cancelled_operation_changes_no_inventory_or_xp(GameTestHelper h){
        var f=fixture(h,Profession.BAKER);f.s.setItem(0,new ItemStack(Items.WHEAT,3));work(f,600);CommercialWork.cancel(f.s);check(f.s.commerce.getInt("elapsed")==0&&count(f,Items.WHEAT)==3&&count(f,Items.BREAD)==0,"cancel debit");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void discard_range_is_five_from_area_edge_with_stable_nearest_tie_break(GameTestHelper h){
        var f=fixture(h,Profession.BAKER);var near=f.p.west();barrel(f,near);barrel(f,f.p.east());barrel(f,f.p.east(7));barrel(f,f.p.east(8));
        var area=IndustrialWork.area(f.l,f.s);check(DiscardChests.inRange(area,f.p.east(7))&&!DiscardChests.inRange(area,f.p.east(8)),"five-block border range");
        var list=DiscardChests.around(f.l,f.s,area);check(list.get(0).pos().equals(near)&&list.stream().anyMatch(a->a.pos().equals(f.p.east(7)))&&list.stream().noneMatch(a->a.pos().equals(f.p.east(8))),"nearest deterministic/range");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void no_remote_extraction_before_worker_reaches_industrial_chest(GameTestHelper h){
        var f=fixture(h,Profession.BAKER);source(f,Profession.FARMER,f.p.east(6));var pos=f.p.east(5);var chest=barrel(f,pos);chest.setItem(0,new ItemStack(Items.WHEAT,10));
        work(f,20);check(count(f,Items.WHEAT)==0&&chest.getItem(0).getCount()==10,"teleported industrial input");f.v.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);work(f,20);
        check(count(f,Items.WHEAT)==3&&chest.getItem(0).getCount()==7,"withdrawal exceeded one needed recipe");f.v.setPos(f.p.getX()+.5,f.p.getY(),f.p.getZ()+1.5);work(f,1200);check(count(f,Items.BREAD)==1,"upstream chain");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void two_workers_share_scarce_chest_without_double_withdrawal(GameTestHelper h){
        var f=fixture(h,Profession.BAKER);var sourcePos=f.p.east(6);source(f,Profession.FARMER,sourcePos);var pos=f.p.east(5);var chest=barrel(f,pos);chest.setItem(0,new ItemStack(Items.WHEAT,3));
        var second=EntityType.VILLAGER.create(f.l);second.setAge(0);second.setNoAi(true);second.setPos(f.p.getX()+.5,f.p.getY(),f.p.getZ()+.5);f.l.addFreshEntity(second);check(ResidenceData.get(f.l).link(f.l,f.home,second).equals("ok"),"second resident");
        var p2=f.p.south(2);f.l.setBlock(p2,WorkModule.COMMERCIAL.get().defaultBlockState(),3);var station=((WorkBlockEntity)f.l.getBlockEntity(p2)).station();station.setItem(31,new ItemStack(Items.EMERALD,10));
        var employer=FakePlayerFactory.get(f.l,new GameProfile(UUID.randomUUID(),"SecondEmployer"));employer.setPos(p2.getX()+.5,p2.getY(),p2.getZ()+1.5);check(Employment.hire(employer,station,second.getUUID(),Profession.BAKER).equals("ok"),"second hire");
        f.v.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);second.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);CommercialWork.step(f.l,f.s,20);CommercialWork.step(f.l,station,20);
        check(CommercialWork.count(f.s,Items.WHEAT)+CommercialWork.count(station,Items.WHEAT)==3&&chest.getItem(0).isEmpty(),"double withdrawal");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void evening_deposits_finished_products_only_and_full_chest_never_loses_items(GameTestHelper h){
        var f=fixture(h,Profession.BAKER);var pos=f.p.west();var chest=barrel(f,pos);f.s.setItem(0,new ItemStack(Items.BREAD,3));f.s.setItem(1,new ItemStack(Items.WHEAT,2));f.l.setDayTime(12000);
        work(f,20);check(count(f,Items.BREAD)==0&&count(f,Items.WHEAT)==2&&chest.getItem(0).is(Items.BREAD),"evening product/input handling");
        for(int i=0;i<27;i++)chest.setItem(i,new ItemStack(Items.STONE,64));f.s.setItem(0,new ItemStack(Items.BREAD,3));f.s.commerce.remove("deposited_day");work(f,20);check(count(f,Items.BREAD)==3,"full discard lost items");f.l.setDayTime(1000);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void breaking_post_keeps_partial_operation_and_inventory_in_single_recovery_identity(GameTestHelper h){
        var f=fixture(h,Profession.BAKER);f.s.setItem(0,new ItemStack(Items.WHEAT,3));work(f,500);f.l.destroyBlock(f.p,true);
        check(!f.s.active&&f.s.worker==null&&f.s.commerce.getInt("elapsed")==500&&count(f,Items.WHEAT)==3,"break lost or completed operation");
        var tokens=f.l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(f.p).inflate(2),e->e.getItem().hasTag()&&e.getItem().getTag().hasUUID("KraftCityStation")&&e.getItem().getTag().getUUID("KraftCityStation").equals(f.s.id));check(tokens.size()==1,"duplicate recovery identity");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void protection_veto_prevents_withdrawal_and_recipe_debit(GameTestHelper h){
        var f=fixture(h,Profession.BAKER);f.s.setItem(0,new ItemStack(Items.WHEAT,3));var listener=new CommercialProtectionListener(f.s.id);
        MinecraftForge.EVENT_BUS.register(listener);try{work(f,1200);check(count(f,Items.WHEAT)==3&&count(f,Items.BREAD)==0&&f.s.commerce.getInt("elapsed")==0,"protection debit");}finally{MinecraftForge.EVENT_BUS.unregister(listener);}h.succeed();
    }
    public static final class CommercialProtectionListener {
        private final UUID station;public CommercialProtectionListener(UUID id){station=id;}
        @net.minecraftforge.eventbus.api.SubscribeEvent public void onCommercialAction(WorkActionEvent e){if(e.station.id.equals(station))e.setCanceled(true);}
    }
    private static F industrialSource(GameTestHelper h,F merchant,Profession profession){
        var l=merchant.l;var pos=merchant.p.east(6);l.setBlock(pos,WorkModule.INDUSTRIAL.get().defaultBlockState(),3);
        var v=EntityType.VILLAGER.create(l);v.setAge(0);v.setNoAi(true);v.setPos(merchant.home.getX()+1.5,merchant.home.getY(),merchant.home.getZ()+.5);l.addFreshEntity(v);
        check(ResidenceData.get(l).link(l,merchant.home,v).equals("ok"),"industrial chain housing");v.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+1.5);
        var s=((WorkBlockEntity)l.getBlockEntity(pos)).station();s.setItem(31,new ItemStack(Items.EMERALD,10));merchant.player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+1.5);
        check(Employment.hire(merchant.player,s,v.getUUID(),profession).equals("ok"),"industrial source hiring");merchant.player.setPos(merchant.p.getX()+.5,merchant.p.getY(),merchant.p.getZ()+1.5);
        return new F(l,s,v,merchant.player,merchant.home,pos);
    }
    private static void takeAndProduce(F merchant,BlockPos chest){
        merchant.v.setPos(chest.getX()+.5,chest.getY(),chest.getZ()+.5);work(merchant,20);
        merchant.v.setPos(merchant.p.getX()+.5,merchant.p.getY(),merchant.p.getZ()+1.5);work(merchant,1200);
    }
    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void complete_farmer_to_baker_chain_uses_real_mature_crop_and_discard(GameTestHelper h){
        var f=fixture(h,Profession.BAKER);var farm=industrialSource(h,f,Profession.FARMER);var chestPos=farm.p.west();var chest=barrel(f,chestPos);chest.clearContent();
        farm.s.setItem(27,new ItemStack(Items.NETHERITE_HOE));farm.s.setItem(0,new ItemStack(Items.WHEAT_SEEDS,25));var crop=farm.p.east().above();farm.l.setBlock(crop.below(),Blocks.FARMLAND.defaultBlockState(),3);
        for(int i=0;i<3&&count(farm,Items.WHEAT)<3;i++){farm.l.setBlock(crop,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),3);farm.v.setPos(crop.getX()+.5,crop.getY(),crop.getZ()+.5);IndustrialWork.tick(farm.l,farm.s);}
        int produced=count(farm,Items.WHEAT);check(produced>=3,"real farmer wheat production");IndustrialWork.deposit(farm.l,farm.s,farm.v);
        takeAndProduce(f,chestPos);check(count(f,Items.BREAD)==1,"real farmer-baker chain output");int left=0;for(int slot=0;slot<27;slot++)if(chest.getItem(slot).is(Items.WHEAT))left+=chest.getItem(slot).getCount();check(left+3==produced,"wheat conservation");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void complete_farmer_to_confectioner_chain_harvests_mature_cane_leaving_root(GameTestHelper h){
        var f=fixture(h,Profession.CONFECTIONER);var farm=industrialSource(h,f,Profession.FARMER);var chestPos=farm.p.west();var chest=barrel(f,chestPos);chest.clearContent();farm.s.setItem(27,new ItemStack(Items.IRON_HOE));farm.s.setItem(0,new ItemStack(Items.SUGAR_CANE,25));
        var root=farm.p.east().above();farm.v.setPos(root.getX()+.5,root.getY(),root.getZ()+.5);farm.l.setBlock(root.below(2),Blocks.STONE.defaultBlockState(),3);farm.l.setBlock(root.below(),Blocks.SAND.defaultBlockState(),3);farm.l.setBlock(root.below().south(),Blocks.WATER.defaultBlockState(),3);
        for(int n=0;n<3;n++)farm.l.setBlock(root.above(n),Blocks.SUGAR_CANE.defaultBlockState(),3);IndustrialWork.tick(farm.l,farm.s);
        check(farm.l.getBlockState(root).is(Blocks.SUGAR_CANE)&&farm.l.getBlockState(root.above()).isAir()&&count(farm,Items.SUGAR_CANE)==25,"cane must drop into world");
        h.succeedWhen(()->{
            IndustrialWork.tick(farm.l,farm.s);
            check(count(farm,Items.SUGAR_CANE)==27,"worker has not recovered both cane drops yet");
            IndustrialWork.deposit(farm.l,farm.s,farm.v);takeAndProduce(f,chestPos);
            check(count(f,Items.SUGAR)==1&&farm.s.getItem(0).getCount()==10,"real farmer-confectioner chain/reserve");
        });
    }
    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void complete_butcher_to_grill_chain_uses_real_adult_loot_and_vanilla_fuel(GameTestHelper h){
        var f=fixture(h,Profession.GRILL_COOK);var butcher=industrialSource(h,f,Profession.BUTCHER);var chestPos=butcher.p.west();var chest=barrel(f,chestPos);chest.clearContent();butcher.s.setItem(27,new ItemStack(Items.WOODEN_AXE));
        for(int i=0;i<3;i++){var cow=EntityType.COW.create(f.l);cow.setAge(0);cow.setNoAi(true);cow.setPos(butcher.p.getX()+1.5,butcher.p.getY(),butcher.p.getZ()+.5);cow.getPersistentData().putUUID("KCIndustryStation",butcher.s.id);cow.getPersistentData().putLong("KCIndustryBorn",-1000000+i);f.l.addFreshEntity(cow);}
        IndustrialWork.tick(f.l,butcher.s);check(count(butcher,Items.BEEF)==1,"real butcher production");IndustrialWork.deposit(f.l,butcher.s,butcher.v);f.s.setItem(30,new ItemStack(Items.COAL));takeAndProduce(f,chestPos);
        check(count(f,Items.COOKED_BEEF)==1&&f.s.commerce.getLong("heat")==1400,"real butcher-grill chain");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void commercial_two_menus_and_reconnection_point_to_exact_same_persistent_inventory(GameTestHelper h){
        var f=fixture(h,Profession.BAKER);f.s.setItem(0,new ItemStack(Items.WHEAT,3));work(f,600);
        var a=new WorkMenu(1,f.player.getInventory(),f.s,false,null);var b=new WorkMenu(2,f.player.getInventory(),f.s,false,f.v.getUUID());
        check(a.inventory==b.inventory&&a.slots.size()==68,"commercial shared menu");var reconnected=new WorkMenu(3,f.player.getInventory(),f.s,false,null);check(reconnected.inventory==a.inventory&&f.s.commerce.getInt("elapsed")==600,"reconnect lost partial/inventory");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void loaded_double_chest_is_one_deterministic_container(GameTestHelper h){
        var f=fixture(h,Profession.BAKER);var left=f.p.west();var right=left.south();
        var state=Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.EAST);
        f.l.setBlock(left,state.setValue(ChestBlock.TYPE,ChestType.LEFT),3);f.l.setBlock(right,state.setValue(ChestBlock.TYPE,ChestType.RIGHT),3);DiscardChests.invalidate(f.l);
        var found=DiscardChests.around(f.l,f.s,IndustrialWork.area(f.l,f.s));check(found.stream().filter(a->a.pos().equals(left)||a.pos().equals(right)).count()==1,"double chest duplicated candidate");
        check(found.stream().filter(a->a.pos().equals(left)||a.pos().equals(right)).findFirst().orElseThrow().inventory().getContainerSize()==54,"double chest inventory missing");h.succeed();
    }

    public static final class RemoveHousingOnAction {
        private final F fixture;public RemoveHousingOnAction(F f){fixture=f;}
        @net.minecraftforge.eventbus.api.SubscribeEvent public void onCommercialHousingRace(WorkActionEvent e){if(e.station.id.equals(fixture.s.id)&&e.action.equals("commercial_produce"))ResidenceData.get(fixture.l).unlink(fixture.l,fixture.v.getUUID());}
    }
    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void housing_removed_by_event_during_transaction_cannot_produce_or_credit_null_worker(GameTestHelper h){
        var f=fixture(h,Profession.BAKER);f.s.setItem(0,new ItemStack(Items.WHEAT,3));var listener=new RemoveHousingOnAction(f);MinecraftForge.EVENT_BUS.register(listener);
        try{work(f,1200);check(f.s.worker==null&&count(f,Items.WHEAT)==3&&count(f,Items.BREAD)==0,"housing event race produced items");WorkforceData.get(f.l).save(new CompoundTag());}
        finally{MinecraftForge.EVENT_BUS.unregister(listener);}h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="commerce")
    public static void farmer_cane_waits_for_natural_three_high_and_does_not_build_or_break_oversized_columns(GameTestHelper h){
        var f=fixture(h,Profession.CONFECTIONER);var farm=industrialSource(h,f,Profession.FARMER);farm.s.setItem(27,new ItemStack(Items.IRON_HOE));farm.s.setItem(0,new ItemStack(Items.SUGAR_CANE,25));var root=farm.p.east().above();farm.v.setPos(root.getX()+.5,root.getY(),root.getZ()+.5);
        farm.l.setBlock(root.below(2),Blocks.STONE.defaultBlockState(),3);farm.l.setBlock(root.below(),Blocks.SAND.defaultBlockState(),3);farm.l.setBlock(root.below().south(),Blocks.WATER.defaultBlockState(),3);
        farm.l.setBlock(root,Blocks.SUGAR_CANE.defaultBlockState(),3);farm.l.setBlock(root.above(),Blocks.SUGAR_CANE.defaultBlockState(),3);IndustrialWork.tick(farm.l,farm.s);
        check(farm.l.getBlockState(root.above()).is(Blocks.SUGAR_CANE)&&farm.l.getBlockState(root.above(2)).isAir()&&count(farm,Items.SUGAR_CANE)==25,"immature cane harvested or instant maturity planted");
        farm.l.setBlock(root.above(2),Blocks.SUGAR_CANE.defaultBlockState(),3);farm.l.setBlock(root.above(3),Blocks.SUGAR_CANE.defaultBlockState(),3);IndustrialWork.tick(farm.l,farm.s);
        check(farm.l.getBlockState(root.above(3)).is(Blocks.SUGAR_CANE)&&count(farm,Items.SUGAR_CANE)==25,"oversized column partially destroyed");
        farm.l.setBlock(root.above(3),Blocks.AIR.defaultBlockState(),3);IndustrialWork.tick(farm.l,farm.s);var drops=farm.l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(root).inflate(1,3,1),e->e.getItem().is(Items.SUGAR_CANE));
        check(count(farm,Items.SUGAR_CANE)==25&&drops.stream().mapToInt(e->e.getItem().getCount()).sum()==2
            &&farm.l.getBlockState(root).is(Blocks.SUGAR_CANE)&&farm.l.getBlockState(root.above()).isAir(),"mature cane harvest");h.succeed();
    }

}
