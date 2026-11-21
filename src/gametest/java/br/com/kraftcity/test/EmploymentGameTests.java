package br.com.kraftcity.test;

import br.com.kraftcity.employment.*;
import br.com.kraftcity.workforce.*;
import br.com.residenciasaldeoes.ResidenciasDosAldeoes;
import br.com.residenciasaldeoes.data.ResidenceData;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraft.core.Direction;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;

@GameTestHolder("residenciasaldeoes")
@PrefixGameTestTemplate(false)
public final class EmploymentGameTests {
    @GameTest(template="empty",timeoutTicks=40,batch="employment")
    public static void discarded_capture_resident_rebinds_history_without_automatic_employment(GameTestHelper h){
        var f=fixture(h,Profession.Sector.INDUSTRIAL);UUID old=f.resident.getUUID();
        check(attempt(f,Profession.FARMER).equals("ok"),"Capture fixture hire failed");
        var workforce=WorkforceData.get(f.level);workforce.setProgress(old,Profession.FARMER.id(),5,456);
        CompoundTag captured=f.resident.getPersistentData().copy();f.resident.discard();
        check(f.station.worker==null,"Discard left a ghost worker");
        var replacement=h.spawn(EntityType.VILLAGER,new BlockPos(3,2,3));replacement.setNoAi(true);replacement.setAge(0);replacement.getPersistentData().merge(captured);
        check(ResidenceData.get(f.level).reconcileCapturedResident(f.level,replacement),"Capture identity was not restored");
        check(workforce.progress(replacement.getUUID(),Profession.FARMER.id()).level()==5&&workforce.progress(replacement.getUUID(),Profession.FARMER.id()).xp()==456,"Capture lost profession history");
        check(f.station.worker==null&&workforce.worker(replacement.getUUID()).orElseThrow().workstation().isEmpty(),"Capture automatically restored employment");
        check(Employment.hire(f.player,f.station,replacement.getUUID(),Profession.FARMER).equals("ok"),"Captured resident could not be explicitly rehired");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="employment")
    public static void all_six_industrial_choices_create_distinct_registered_professional_blocks(GameTestHelper h){
        var f=fixture(h,Profession.Sector.INDUSTRIAL);int index=0;
        for(Profession profession:Profession.values())if(profession.sector==Profession.Sector.INDUSTRIAL){
            int price=index==0?10:1;BlockPos pos=f.pos.offset(0,0,index++*2);f.level.setBlock(pos,WorkModule.INDUSTRIAL.get().defaultBlockState(),3);
            var station=((WorkBlockEntity)f.level.getBlockEntity(pos)).station();station.setItem(31,new ItemStack(Items.EMERALD,10));
            f.player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+1.5);
            check(Employment.hire(f.player,station,f.resident.getUUID(),profession).equals("ok"),"Choice failed: "+profession);
            check(f.level.getBlockState(pos).is(WorkModule.PROFESSIONAL.get(profession).get())&&station.getItem(31).getCount()==10-price,"Choice did not transform/charge: "+profession);
            Employment.release(f.level,f.resident.getUUID());
        }
        check(index==6,"Industrial choice count changed");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="employment")
    public static void candidate_filter_excludes_babies_nitwits_vanilla_workers_and_nonresidents(GameTestHelper h){
        var f=fixture(h,Profession.Sector.INDUSTRIAL);
        var baby=h.spawn(EntityType.VILLAGER,new BlockPos(4,2,3));baby.setAge(-24000);baby.setNoAi(true);
        var nitwit=h.spawn(EntityType.VILLAGER,new BlockPos(4,2,4));nitwit.setAge(0);nitwit.setNoAi(true);nitwit.setVillagerData(nitwit.getVillagerData().setProfession(VillagerProfession.NITWIT));
        var farmer=h.spawn(EntityType.VILLAGER,new BlockPos(4,2,5));farmer.setAge(0);farmer.setNoAi(true);farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER));
        var outsider=h.spawn(EntityType.VILLAGER,new BlockPos(4,2,6));outsider.setAge(0);outsider.setNoAi(true);
        var homes=ResidenceData.get(f.level);for(Villager v:List.of(baby,nitwit,farmer))homes.link(f.level,f.home,v);
        var ids=Employment.candidates(f.level).stream().map(Villager::getUUID).toList();
        check(ids.contains(f.resident.getUUID()),"Eligible resident missing");
        for(Villager v:List.of(baby,nitwit,farmer,outsider))check(!ids.contains(v.getUUID()),"Ineligible candidate listed: "+v.getUUID());h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="employment")
    public static void loaded_orphan_post_reconciles_to_one_recoverable_inventory(GameTestHelper h){
        var f=fixture(h,Profession.Sector.INDUSTRIAL);check(attempt(f,Profession.FARMER).equals("ok"),"Orphan fixture hire failed");
        f.station.setItem(0,new ItemStack(Items.CARROT,11));
        // Simulate a persisted ledger whose chunk no longer contains that block entity.
        f.level.removeBlockEntity(f.pos);
        f.level.getChunkAt(f.pos).getSection(f.level.getSectionIndex(f.pos.getY())).setBlockState(f.pos.getX()&15,f.pos.getY()&15,f.pos.getZ()&15,Blocks.AIR.defaultBlockState(),false);
        Employment.reconcile(f.level,f.station);Employment.reconcile(f.level,f.station);
        check(!f.station.active&&f.station.worker==null,"Orphan assignment was retained");
        var drops=f.level.getEntitiesOfClass(ItemEntity.class,new AABB(f.pos).inflate(2),e->e.getItem().hasTag()&&e.getItem().getTag().hasUUID("KraftCityStation")&&e.getItem().getTag().getUUID("KraftCityStation").equals(f.station.id));
        check(drops.size()==1&&f.station.getItem(0).getCount()==11,"Reconciliation duplicated/lost recovery inventory");h.succeed();
    }
    private static String attempt(Fixture f,Profession profession){
        String result=Employment.hire(f.player,f.station,f.resident.getUUID(),profession);
        System.out.println("EMPLOYMENT-HIRE result="+result+" entity="+(f.level.getEntity(f.resident.getUUID())!=null)+" eligible="+Employment.eligible(f.level,f.resident)+" at="+f.station.at(f.level,f.pos)+" distance="+f.player.distanceToSqr(f.pos.getX()+.5,f.pos.getY()+.5,f.pos.getZ()+.5)+" profession="+f.resident.getVillagerData().getProfession()+" age="+f.resident.getAge());
        return result;
    }

    private static void check(boolean value,String message){if(!value)throw new GameTestAssertException(message);}
    private record Fixture(ServerLevel level,ServerPlayer player,Villager resident,BlockPos home,BlockPos pos,EmploymentData.Station station){}
    private static Fixture fixture(GameTestHelper h,Profession.Sector sector){
        BlockPos home=h.absolutePos(new BlockPos(2,2,2)),pos=h.absolutePos(new BlockPos(8,2,2));
        h.setBlock(new BlockPos(2,2,2),ResidenciasDosAldeoes.ANCHOR.get());
        ((br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity)h.getLevel().getBlockEntity(home)).setSize(11);
        h.setBlock(new BlockPos(8,1,2),Blocks.STONE);
        h.setBlock(new BlockPos(8,2,2),sector==Profession.Sector.INDUSTRIAL?WorkModule.INDUSTRIAL.get():WorkModule.COMMERCIAL.get());
        var resident=h.spawn(EntityType.VILLAGER,new BlockPos(3,2,2));resident.setNoAi(true);resident.setAge(0);
        check(ResidenceData.get(h.getLevel()).link(h.getLevel(),home,resident).equals("ok"),"Fixture resident link failed");
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"EmployerTest"));player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+1.5);
        check(player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<64,"Fake player was not positioned near the workstation: "+player.position());
        var s=((WorkBlockEntity)h.getLevel().getBlockEntity(pos)).station();s.setItem(EmploymentData.PAYMENT,new ItemStack(Items.EMERALD,30));
        return new Fixture(h.getLevel(),player,resident,home,pos,s);
    }
    @GameTest(template="empty",timeoutTicks=40,batch="employment")
    public static void industrial_hiring_is_atomic_transforms_real_block_and_keeps_home_and_bed(GameTestHelper h){
        var f=fixture(h,Profession.Sector.INDUSTRIAL);var homes=ResidenceData.get(f.level);
        BlockPos bed=f.home.offset(1,1,1);homes.reserveBed(f.resident.getUUID(),f.level.dimension(),f.home,bed);
        check(attempt(f,Profession.FARMER).equals("ok"),"Hire failed");
        check(f.level.getBlockState(f.pos).is(WorkModule.PROFESSIONAL.get(Profession.FARMER).get()),"Block did not physically transform");
        check(f.station.getItem(31).getCount()==20&&f.station.worker.equals(f.resident.getUUID()),"Wrong cost or worker");
        check(homes.owner(f.resident.getUUID()).pos.equals(f.home)&&homes.bedClaim(f.resident.getUUID()).bed().equals(bed),"Hiring changed housing/bed");
        check(f.resident.getVillagerData().getProfession()==VillagerProfession.NONE,"Assigned vanilla career");
        check(Employment.indicator(f.level,f.resident.getUUID(),false),"Assigned real workbench is not green");
        check(!attempt(f,Profession.FARMER).equals("ok")&&f.station.getItem(31).getCount()==20,"Double click charged twice");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="employment")
    public static void dismissal_is_guarded_and_rehire_preserves_profession_specific_history(GameTestHelper h){
        var f=fixture(h,Profession.Sector.INDUSTRIAL);
        var id=f.resident.getUUID();
        check(attempt(f,Profession.FARMER).equals("ok"),"initial farmer hire");
        var history=WorkforceData.get(f.level);
        history.setProgress(id,Profession.FARMER.id(),5,123);
        f.station.setItem(0,new ItemStack(Items.WHEAT,14));
        long epoch=Employment.epoch(f.station);
        f.player.getAbilities().mayBuild=false;
        check(!Employment.dismiss(f.player,f.station,epoch),"unpermitted dismissal");
        f.player.getAbilities().mayBuild=true;
        check(Employment.dismiss(f.player,f.station,epoch),"dismissal failed");
        check(!Employment.dismiss(f.player,f.station,epoch)&&f.station.worker==null,"duplicate dismissal");
        check(f.level.getEntity(id)==f.resident&&ResidenceData.get(f.level).owner(id).pos.equals(f.home)
            &&Employment.eligible(f.level,f.resident),"resident identity or housing lost");
        var saved=WorkforceData.load(history.save(new CompoundTag()));
        check(saved.hasEmploymentHistory(id)&&saved.progress(id,Profession.FARMER.id()).xp()==123
            &&saved.worker(id).orElseThrow().workstation().isEmpty(),"dismissed history not persisted");
        check(f.station.getItem(0).getCount()==14,"dismissal moved station inventory");
        var other=f.pos.south(3);
        f.level.setBlock(other,WorkModule.INDUSTRIAL.get().defaultBlockState(),3);
        var second=((WorkBlockEntity)f.level.getBlockEntity(other)).station();
        check(Employment.hire(f.player,second,id,Profession.BUTCHER).equals("payment"),"no emerald hire");
        second.setItem(31,new ItemStack(Items.EMERALD,2));
        check(Employment.hire(f.player,second,id,Profession.BUTCHER).equals("ok"),"cross-profession rehire");
        check(second.getItem(31).getCount()==1&&history.progress(id,Profession.BUTCHER.id()).xp()==0,"wrong rehire fee or transferred XP");
        history.setProgress(id,Profession.BUTCHER.id(),2,25);
        check(Employment.dismiss(f.player,second,Employment.epoch(second)),"butcher dismissal");
        f.station.setItem(31,new ItemStack(Items.EMERALD,1));
        check(attempt(f,Profession.FARMER).equals("ok")&&f.station.getItem(31).isEmpty(),"single emerald same job");
        check(!Employment.dismiss(f.player,f.station,epoch)&&f.station.worker.equals(id),"stale dismissal affected new assignment");
        check(history.progress(id,Profession.FARMER.id()).equals(new WorkforceData.Progress(5,123))
            &&history.progress(id,Profession.BUTCHER.id()).equals(new WorkforceData.Progress(2,25)),"profession histories mixed");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="employment")
    public static void invalid_selections_and_missing_funds_do_not_mutate_or_charge(GameTestHelper h){
        var f=fixture(h,Profession.Sector.INDUSTRIAL);f.station.setItem(31,new ItemStack(Items.EMERALD,9));
        check(attempt(f,Profession.FARMER).equals("payment"),"Insufficient payment accepted");
        check(!attempt(f,Profession.BAKER).equals("ok"),"Cross-sector selection accepted");
        check(!Employment.hire(f.player,f.station,UUID.randomUUID(),Profession.FARMER).equals("ok"),"Unknown UUID accepted");
        f.station.setItem(31,new ItemStack(Items.DIAMOND,30));
        check(!attempt(f,Profession.FARMER).equals("ok"),"Non-emerald payment accepted");
        check(f.station.worker==null&&f.station.profession==null&&f.level.getBlockState(f.pos).is(WorkModule.INDUSTRIAL.get()),"Invalid hire mutated block");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="employment")
    public static void commercial_explicit_pause_cannot_charge(GameTestHelper h){
        var original=net.minecraftforge.fml.config.ConfigTracker.INSTANCE.configSets().get(net.minecraftforge.fml.config.ModConfig.Type.SERVER).stream().filter(c->c.getSpec()==EmploymentConfig.SPEC).findFirst().orElseThrow().getConfigData();
        EmploymentConfig.SPEC.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
        try{
            EmploymentConfig.COMMERCIAL_ENABLED.set(false);EmploymentConfig.COMMERCIAL_COST.set(-1);
            var f=fixture(h,Profession.Sector.COMMERCIAL);
            check(EmploymentConfig.cost(Profession.Sector.COMMERCIAL)==-1,"Explicit pause ignored");
            check(attempt(f,Profession.BAKER).equals("pending"),"Paused commercial hire accepted");
            check(f.station.getItem(31).getCount()==30&&f.station.worker==null,"Paused commercial charge occurred");
        }finally{EmploymentConfig.SPEC.setConfig(original);}
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="employment")
    public static void losing_housing_releases_job_but_same_profession_rehire_restores_progress(GameTestHelper h){
        var f=fixture(h,Profession.Sector.INDUSTRIAL);UUID id=f.resident.getUUID();
        check(attempt(f,Profession.BEEKEEPER).equals("ok"),"Initial hire failed");
        var workers=WorkforceData.get(f.level);workers.setProgress(id,Profession.BEEKEEPER.id(),4,1234);
        ResidenceData.get(f.level).unlink(f.level,id);
        check(f.station.worker==null&&workers.worker(id).orElseThrow().workstation().isEmpty(),"Housing loss left employment ghost");
        check(!Employment.indicator(f.level,id,true),"Former worker stayed green from unrelated vanilla resources");
        check(ResidenceData.get(f.level).link(f.level,f.home,f.resident).equals("ok"),"Housing recovery failed");
        check(f.station.worker==null,"Housing recovery silently rehired worker");
        check(attempt(f,Profession.BEEKEEPER).equals("ok"),"Rehire failed");
        check(workers.worker(id).orElseThrow().level()==4&&workers.progress(id,Profession.BEEKEEPER.id()).xp()==1234,"Progress lost on rehire");
        check(WorkforceData.load(workers.save(new CompoundTag())).progress(id,Profession.BEEKEEPER.id()).xp()==1234,"Progress history lost in NBT");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="employment")
    public static void competing_players_and_posts_cannot_assign_a_second_worker(GameTestHelper h){
        var f=fixture(h,Profession.Sector.INDUSTRIAL);var second=h.spawn(EntityType.VILLAGER,new BlockPos(4,2,2));second.setNoAi(true);second.setAge(0);
        ResidenceData.get(f.level).link(f.level,f.home,second);
        check(attempt(f,Profession.FARMER).equals("ok"),"First hire failed");
        var competitor=FakePlayerFactory.get(f.level,new GameProfile(UUID.randomUUID(),"OtherEmployer"));competitor.setPos(f.pos.getX()+.5,f.pos.getY(),f.pos.getZ()+1.5);
        check(!Employment.hire(competitor,f.station,second.getUUID(),Profession.FARMER).equals("ok"),"Post accepted a second worker from another player");
        BlockPos other=f.pos.offset(0,0,3);f.level.setBlock(other,WorkModule.INDUSTRIAL.get().defaultBlockState(),3);
        var station=((WorkBlockEntity)f.level.getBlockEntity(other)).station();station.setItem(31,new ItemStack(Items.EMERALD,10));
        check(!Employment.hire(f.player,station,f.resident.getUUID(),Profession.FARMER).equals("ok"),"Worker accepted a second post");
        check(station.getItem(31).getCount()==10&&f.station.getItem(31).getCount()==20,"Competition consumed payment");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="employment")
    public static void block_break_and_replacement_preserve_one_inventory_and_reject_token_clones(GameTestHelper h){
        var f=fixture(h,Profession.Sector.INDUSTRIAL);
        check(attempt(f,Profession.BUTCHER).equals("ok"),"Hire failed");
        f.station.setItem(0,new ItemStack(Items.APPLE,13));f.station.setItem(27,new ItemStack(Items.IRON_AXE));
        f.level.destroyBlock(f.pos,true);
        check(!f.station.active&&f.station.worker==null,"Broken post remained active/employed");
        var items=f.level.getEntitiesOfClass(ItemEntity.class,new AABB(f.pos).inflate(2),e->e.getItem().hasTag()&&e.getItem().getTag().hasUUID("KraftCityStation")&&e.getItem().getTag().getUUID("KraftCityStation").equals(f.station.id));
        check(items.size()==1,"Break did not yield exactly one recovery token");ItemStack token=items.get(0).getItem().copy();items.get(0).discard();
        check(!token.getTag().contains("Items")&&!token.getTag().contains("BlockEntityTag"),"Token copied item contents");
        var stored=EmploymentData.load(EmploymentData.get(f.level).save(new CompoundTag())).station(f.station.id);
        check(stored.getItem(0).getCount()==13&&stored.getItem(27).is(Items.IRON_AXE)&&stored.getItem(31).getCount()==20,"Portable inventory persistence failed");
        f.player.setItemInHand(InteractionHand.MAIN_HAND,token.copy());
        ItemStack duplicate=token.copy();
        var hit=new BlockHitResult(Vec3.atCenterOf(f.pos.below()),Direction.UP,f.pos.below(),false);
        var context=new BlockPlaceContext(f.player,InteractionHand.MAIN_HAND,token,hit);
        check(((BlockItem)token.getItem()).place(context).consumesAction(),"Recovery token placement failed");
        var placed=(WorkBlockEntity)f.level.getBlockEntity(f.pos);check(placed!=null&&placed.station()==f.station,"Placement copied or replaced inventory identity");
        check(f.station.getItem(0).getCount()==13&&f.station.worker==null,"Replacement lost items or automatically rehired");
        BlockPos clone=f.pos.offset(0,0,2);f.level.setBlock(clone.below(),Blocks.STONE.defaultBlockState(),3);
        var secondContext=new BlockPlaceContext(f.player,InteractionHand.MAIN_HAND,duplicate,new BlockHitResult(Vec3.atCenterOf(clone.below()),Direction.UP,clone.below(),false));
        check(!((BlockItem)duplicate.getItem()).place(secondContext).consumesAction(),"Cloned token duplicated a live inventory");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="employment")
    public static void generic_industrial_survival_drop_stacks_and_creative_removes_without_drop(GameTestHelper h){
        var f=fixture(h,Profession.Sector.INDUSTRIAL);
        var block=WorkModule.INDUSTRIAL.get();
        f.station.clearContent();
        var bounds=new AABB(f.pos).inflate(1);
        f.player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        check(f.player.gameMode.destroyBlock(f.pos),"survival break failed");
        var dropped=f.level.getEntitiesOfClass(ItemEntity.class,bounds);
        check(dropped.size()==1,"survival did not drop exactly one generic block");
        var stack=dropped.get(0).getItem();
        var plain=new ItemStack(block,63);
        check(ItemStack.isSameItemSameTags(stack,plain)&&stack.getMaxStackSize()==64,
            "survival made a tagged or unstackable generic block");
        var chest=new net.minecraft.world.SimpleContainer(1);
        chest.setItem(0,plain);
        check(chest.addItem(stack.copy()).isEmpty()&&chest.getItem(0).getCount()==64,"generic stacks did not merge");
        check(EmploymentData.get(f.level).station(f.station.id)==null,"survival left an unused station identity");
        dropped.get(0).discard();
        f.level.setBlockAndUpdate(f.pos,block.defaultBlockState());
        var creative=((WorkBlockEntity)f.level.getBlockEntity(f.pos)).station();
        f.player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.CREATIVE);
        check(f.player.gameMode.destroyBlock(f.pos),"creative break failed");
        check(f.level.getEntitiesOfClass(ItemEntity.class,bounds).isEmpty(),"creative dropped a pristine post");
        check(EmploymentData.get(f.level).station(creative.id)==null,"creative left an unused identity");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40,batch="employment")
    public static void reopened_block_and_worker_menus_share_exact_inventory_and_currency_filter(GameTestHelper h){
        var f=fixture(h,Profession.Sector.INDUSTRIAL);
        check(attempt(f,Profession.FARMER).equals("ok"),"Menu fixture hire failed");
        var blockMenu=new WorkMenu(41,f.player.getInventory(),f.station,false,null);
        var workerMenu=new WorkMenu(42,f.player.getInventory(),f.station,false,f.resident.getUUID());
        check(blockMenu.inventory==workerMenu.inventory&&blockMenu.inventory==f.station,"Menus created separate inventory copies");
        blockMenu.slots.get(0).set(new ItemStack(Items.BREAD,7));
        check(workerMenu.slots.get(0).getItem().getCount()==7,"Worker menu did not see block mutation");
        check(blockMenu.slots.size()==67&&blockMenu.slots.stream().noneMatch(slot->slot.container==f.station
            &&slot.getContainerSlot()==EmploymentData.PAYMENT),"Farmer exposed hidden payment");
        for(int i=0;i<4;i++)check(blockMenu.getSlot(27+i).getContainerSlot()==27+i
            &&blockMenu.getSlot(27+i).y==WorkMenu.FARM_AUX_Y,"Auxiliary slots moved logically or misaligned");
        var reconnected=new WorkMenu(43,f.player.getInventory(),f.station,false,null);
        check(reconnected.slots.get(0).getItem().getCount()==7,"Reconnect lost block items");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="employment")
    public static void farmer_hiring_payment_caps_normal_and_quick_move_without_losing_existing_balance(GameTestHelper h){
        var f=fixture(h,Profession.Sector.INDUSTRIAL);
        f.station.setItem(EmploymentData.PAYMENT,ItemStack.EMPTY);
        var menu=new WorkMenu(30,f.player.getInventory(),f.station,true,null);
        var payment=menu.getSlot(0);
        check(payment.getMaxStackSize()==15&&payment.mayPlace(new ItemStack(Items.EMERALD))
            &&!payment.mayPlace(new ItemStack(Items.DIAMOND)),"Payment limit or currency filter");
        f.player.getInventory().setItem(9,new ItemStack(Items.EMERALD,64));
        menu.quickMoveStack(f.player,1);
        check(payment.getItem().getCount()==15&&f.player.getInventory().getItem(9).getCount()==49,"Quick move did not cap payment");
        var extra=new ItemStack(Items.EMERALD,6);
        payment.safeInsert(extra);
        check(extra.getCount()==6&&payment.getItem().getCount()==15,"Full payment accepted more emeralds");
        payment.set(new ItemStack(Items.EMERALD,35));
        payment.safeInsert(extra);
        check(payment.getItem().getCount()==35&&extra.getCount()==6,"Existing balance changed");
        check(Employment.hire(f.player,f.station,f.resident.getUUID(),Profession.FARMER).equals("ok"),"Direct fixture hire");
        menu.quickMoveStack(f.player,0);
        check(f.station.getItem(31).getCount()==25,"Stale hiring menu manipulated hired station");
        var hired=new WorkMenu(31,f.player.getInventory(),f.station,false,null);
        hired.quickMoveStack(f.player,31);
        check(f.station.getItem(31).getCount()==25&&f.station.countItem(Items.EMERALD)==74,"Hidden balance used by quick move");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="employment")
    public static void death_and_dimension_changes_clear_assignment_without_deleting_history(GameTestHelper h){
        var f=fixture(h,Profession.Sector.INDUSTRIAL);UUID id=f.resident.getUUID();check(attempt(f,Profession.FARMER).equals("ok"),"Death fixture hire failed");
        WorkforceData.get(f.level).setProgress(id,Profession.FARMER.id(),2,99);
        // A resident registered in another dimension cannot keep this workstation.
        f.station.dimension=net.minecraft.world.level.Level.NETHER;
        check(!Employment.valid(f.level,id)&&f.station.worker==null,"Dimension mismatch left employment ghost");
        check(WorkforceData.get(f.level).progress(id,Profession.FARMER.id()).xp()==99,"Dimension change erased history");
        f.station.dimension=f.level.dimension();check(attempt(f,Profession.FARMER).equals("ok"),"Death fixture hire failed");
        f.resident.hurt(net.minecraft.world.damagesource.DamageSource.GENERIC,Float.MAX_VALUE);
        check(f.station.worker==null&&WorkforceData.get(f.level).progress(id,Profession.FARMER.id()).level()==2,"Death left job or erased history");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40,batch="employment")
    public static void generic_work_blocks_stack_to_64_and_recovery_tokens_stay_individual(GameTestHelper h){
        var level=h.getLevel();
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"StackingTest"));
        int index=0;
        for(var sector:Profession.Sector.values()){
            var stack=new ItemStack(WorkModule.generic(sector),64);
            check(stack.getMaxStackSize()==64&&stack.isStackable(),"generic "+sector+" does not stack");
            var part=stack.split(12);
            check(stack.getCount()==52&&part.getCount()==12,"stack split lost blocks");
            var first=h.absolutePos(new BlockPos(3+index++,2,2));
            UUID previous=null;
            player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            for(int i=0;i<2;i++){
                var at=first.south(i);
                level.setBlock(at.below(),Blocks.STONE.defaultBlockState(),3);
                var hit=new BlockHitResult(Vec3.atCenterOf(at.below()),Direction.UP,at.below(),false);
                check(((BlockItem)stack.getItem()).place(new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,hit)).consumesAction(),"stacked generic block failed to place");
                var placed=((WorkBlockEntity)level.getBlockEntity(at)).station();
                check(!placed.id.equals(previous)&&placed.sector==sector&&placed.profession==null&&placed.worker==null,
                    "stacked placements shared a ledger identity");
                previous=placed.id;
            }
            check(stack.getCount()==50,"placing two generic blocks consumed the wrong count");
            part.getOrCreateTag().putUUID("KraftCityStation",UUID.randomUUID());
            part.setCount(1);
            check(part.getMaxStackSize()==1&&!part.isStackable(),"recovery token became stackable");
        }
        h.succeed();
    }

}
