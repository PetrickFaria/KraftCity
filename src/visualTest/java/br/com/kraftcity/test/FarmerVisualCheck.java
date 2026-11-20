package br.com.kraftcity.test;

import br.com.kraftcity.employment.*;
import br.com.kraftcity.industry.*;
import br.com.residenciasaldeoes.ResidenciasDosAldeoes;
import br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity;
import br.com.residenciasaldeoes.data.ResidenceData;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="residenciasaldeoes",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class FarmerVisualCheck {
    private static int restoredSleepTicks;
    private static volatile boolean restoredSleeping;
    // This scenario has its own containers and neighbors, away from the GameTest grid.
    private static final BlockPos POST=new BlockPos(79,-61,-120);
    private static final BlockPos HOME=POST.offset(-7,0,-1);
    private static final BlockPos BED=HOME.east(3).above();
    private static final BlockPos CHEST=POST.offset(-5,1,2);
    private static final BlockPos CANE=POST.offset(2,1,1);
    private static final BlockPos STEM=POST.offset(-1,1,1);
    private static final BlockPos FRUIT=STEM.south();
    private static final BlockPos JUMP=POST.offset(-2,1,1);
    private static final BlockPos CHANNEL=POST.offset(4,0,2);
    private static final BlockPos FORGE_CHEST=POST.offset(4,1,-4);
    private static final BlockPos FORGE_ALIAS=FORGE_CHEST.north(2);
    private static final BlockPos COMPANION_CHEST=POST.offset(-6,1,-5);
    private static net.minecraftforge.items.ItemStackHandler forgeStorage;
    private static final BlockPos CARROTS=POST.offset(-2,1,-1);
    private static final BlockPos POTATOES=POST.offset(2,1,0);
    private static final List<BlockPos> ROW=List.of(POST.offset(-2,1,-2),POST.offset(-1,1,-2),POST.offset(0,1,-2),POST.offset(2,1,-2),POST.offset(2,1,-1),POST.offset(1,1,-1));
    private static volatile int stage;
    private static volatile String progressDetail="",serverFailure;
    private static UUID worker,visitor,pursuer,closingDrop;
    private static double escapeStart;
    private static long sleepObserved;
    private static final List<UUID> companions=new ArrayList<>();
    private static boolean canePlanted,sawLooseCane,sawJump,seenPhysicalDeposit,visitorLeft;
    private static int ticks;
    private static int capturedStage=-1,visibleStage=-1,stageTicks;
    private static long nightStarted;
    private static int recordedPlots;
    private FarmerVisualCheck(){}

    private static EmploymentData.Station station(ServerLevel l){
        return ((WorkBlockEntity)l.getBlockEntity(POST)).station();
    }

    private static void check(boolean value,String reason){
        if(!value)throw new IllegalStateException("Farmer visual: "+reason);
    }

    private static void setup(ServerPlayer p){
        var l=p.getLevel();
        l.setDayTime(1000);
        l.setWeatherParameters(10000,0,false,false);
        for(int x=-11;x<=9;x++)for(int z=-7;z<=7;z++){
            for(int y=-2;y<=0;y++)l.setBlock(POST.offset(x,y,z),Blocks.STONE.defaultBlockState(),3);
            for(int y=1;y<=4;y++)l.setBlock(POST.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        // Continuous terrain at the flat world's walking elevation. The farm
        // and home share a fenced compound with all work targets accessible.
        for(int x=-11;x<=9;x++)for(int z=-7;z<=7;z++){
            if(x==-11||x==9||z==-7||z==7)l.setBlock(POST.offset(x,1,z),Blocks.OAK_FENCE.defaultBlockState(),3);
        }
        ResidenceData.get(l).unlinkAll(l,l.dimension(),HOME);
        l.getEntitiesOfClass(Villager.class,new AABB(HOME).inflate(7)).forEach(Villager::discard);
        l.setBlock(HOME,ResidenciasDosAldeoes.ANCHOR.get().defaultBlockState(),3);
        check(((ResidentialAnchorBlockEntity)l.getBlockEntity(HOME)).setSize(11),"home size configuration");
        for(int i=0;i<ROW.size();i++){
            var soil=i%2==0?Blocks.DIRT.defaultBlockState():Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7);
            l.setBlock(ROW.get(i).below(),soil,3);
        }
        var v=EntityType.VILLAGER.create(l);
        v.setAge(0);v.setInvulnerable(true);
        v.setPos(POST.getX()+1.5,POST.getY()+1,POST.getZ()+2.5);
        l.addFreshEntity(v);
        check(ResidenceData.get(l).link(l,HOME,v).equals("ok"),"residential link");
        l.setBlock(POST,WorkModule.INDUSTRIAL.get().defaultBlockState(),3);
        var s=station(l);
        p.teleportTo(l,POST.getX()+.5,POST.getY()+1,POST.getZ()+3.5,180,18);
        s.setItem(31,new ItemStack(Items.EMERALD,10));
        check(Employment.hire(p,s,v.getUUID(),Profession.FARMER).equals("ok"),"hire");
        worker=v.getUUID();
        s.setItem(27,new ItemStack(Items.IRON_HOE));s.setItem(30,new ItemStack(Items.WATER_BUCKET));
        s.setItem(0,new ItemStack(Items.WHEAT_SEEDS,ROW.size()));
        s.setItem(1,new ItemStack(Items.SUGAR_CANE));
        l.setBlock(CANE.below(),Blocks.SAND.defaultBlockState(),3);
        l.setBlock(CANE.below().south(),Blocks.WATER.defaultBlockState(),3);
        var guest=EntityType.VILLAGER.create(l);
        guest.setPos(POST.getX()+.5,POST.getY()+1,POST.getZ()-1.5);
        l.addFreshEntity(guest);
        visitor=guest.getUUID();
        s.industry.putBoolean("preview",true);
        s.setChanged();
        IndustryNetwork.sendBounds(p);
        stage=1;
    }

    private static void progress(ServerPlayer p){
        var l=p.getLevel();var s=station(l);
        if(!(l.getEntity(worker) instanceof Villager v))return;
        if(stage==1){
            if(!canePlanted){
                if(!l.getBlockState(CANE).is(Blocks.SUGAR_CANE)){
                    for(var at:ROW)check(l.getBlockState(at).isAir(),"main zigzag started before irrigated cane");
                    return;
                }
                check(s.getItem(1).isEmpty(),"planting did not cost one cane");
                canePlanted=true;
            }
            if(l.getEntity(visitor) instanceof Villager guest){
                if(!IndustrialWork.area(l,s).contains(guest.position().add(0,.125,0))){
                    visitorLeft=true;
                    guest.discard();
                }
            }
            boolean previous=true;
            for(var at:ROW){
                boolean planted=l.getBlockState(at).is(Blocks.WHEAT);
                check(!planted||previous,"new crops skipped a reachable zigzag position");
                previous=planted;
            }
            while(recordedPlots<ROW.size()&&l.getBlockState(ROW.get(recordedPlots)).is(Blocks.WHEAT)){
                System.out.println("FARMER_ZIGZAG_PLOT "+(++recordedPlots)+" "+ROW.get(recordedPlots-1));
            }
            if(!previous||!visitorLeft)return;
            System.out.println("FARMER_MIXED_SOIL_ZIGZAG_PASSED");
            l.setBlock(ROW.get(0),Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),3);
            s.setItem(0,new ItemStack(Items.WHEAT_SEEDS));
            for(int height=1;height<3;height++)l.setBlock(CANE.above(height),Blocks.SUGAR_CANE.defaultBlockState(),3);
            l.setBlock(STEM.below(),Blocks.FARMLAND.defaultBlockState(),3);
            l.setBlock(FRUIT.below(),Blocks.DIRT.defaultBlockState(),3);
            l.setBlock(FRUIT,Blocks.PUMPKIN.defaultBlockState(),3);
            l.setBlock(STEM,Blocks.ATTACHED_PUMPKIN_STEM.defaultBlockState().setValue(AttachedStemBlock.FACING,Direction.SOUTH),3);
            stage=2;
        }else if(stage==2){
            sawLooseCane|=!l.getEntitiesOfClass(ItemEntity.class,new AABB(CANE).inflate(2,3,2),e->e.getItem().is(Items.SUGAR_CANE)).isEmpty();
            progressDetail="worker="+v.blockPosition()+" cane="+l.getBlockState(CANE.above()).getBlock()
                +" fruit="+l.getBlockState(FRUIT).getBlock()+" storedCane="+s.countItem(Items.SUGAR_CANE)
                +" loose="+sawLooseCane+" navigation="+v.getNavigation().getTargetPos()
                +" homeReturn="+v.getPersistentData().getBoolean("residenciasaldeoes_home_return_forced")
                +" delivery="+v.getPersistentData().contains("residenciasaldeoes_emerald_transfer")+" day="+l.getDayTime();
            if(!l.getBlockState(CANE.above()).isAir()||!l.getBlockState(FRUIT).isAir()||s.countItem(Items.SUGAR_CANE)!=2)return;
            if(l.getBlockState(ROW.get(0)).getValue(CropBlock.AGE)==7)return;
            for(var at:ROW)check(l.getBlockState(at).is(Blocks.WHEAT)&&l.getBlockState(at.below()).is(Blocks.FARMLAND),"crop or soil damaged beside the harvest");
            check(canePlanted&&sawLooseCane&&l.getBlockState(CANE).is(Blocks.SUGAR_CANE),"missing cane planting/world pickup/base");
            check(l.getBlockState(STEM).is(Blocks.PUMPKIN_STEM),"pumpkin stem was removed: "+l.getBlockState(STEM));
            l.setBlock(CARROTS.below(),Blocks.FARMLAND.defaultBlockState(),3);
            l.setBlock(POTATOES.below(),Blocks.FARMLAND.defaultBlockState(),3);
            l.setBlock(CARROTS,((CropBlock)Blocks.CARROTS).getStateForAge(7),3);
            l.setBlock(POTATOES,((CropBlock)Blocks.POTATOES).getStateForAge(7),3);
            s.setItem(2,new ItemStack(Items.CARROT));
            s.setItem(3,new ItemStack(Items.POTATO));
            // Block a standing cell without placing a solid block directly over the crop.
            l.setBlock(CARROTS.above(),Blocks.STONE.defaultBlockState(),3);
            p.teleportTo(l,POST.getX()-3.5,POST.getY()+2,POST.getZ()+.5,-90,25);
            stage=6;
        }else if(stage==6){
            progressDetail="worker="+v.blockPosition()+" carrots="+l.getBlockState(CARROTS)
                +" potatoes="+l.getBlockState(POTATOES)+" visitorLeft="+visitorLeft;
            if(l.getBlockState(POTATOES).getValue(CropBlock.AGE)==7)return;
            check(l.getBlockState(CARROTS).getValue(CropBlock.AGE)==7,"blocked carrot was harvested");
            l.setBlock(CARROTS.above(),Blocks.AIR.defaultBlockState(),3);
            stage=7;
        }else if(stage==7){
            if(l.getBlockState(CARROTS).getValue(CropBlock.AGE)==7)return;
            for(var at:ROW)check(l.getBlockState(at).is(Blocks.WHEAT)&&l.getBlockState(at.below()).is(Blocks.FARMLAND),
                "carrot or potato harvest damaged neighboring wheat");
            System.out.println("FARMER_VISITOR_EXIT_AND_BLOCKED_ROUTE_RECOVERY_PASSED");
            var mature=Blocks.PUMPKIN_STEM.defaultBlockState().setValue(StemBlock.AGE,7);
            l.setBlock(STEM,mature,3);
            l.setBlock(FRUIT.below(),Blocks.STONE.defaultBlockState(),3);
            l.setBlock(JUMP.below(),Blocks.FARMLAND.defaultBlockState(),3);
            l.setBlock(JUMP,Blocks.AIR.defaultBlockState(),3);
            stage=5;
        }else if(stage==5){
            progressDetail="worker="+v.blockPosition()+" fruit space="+l.getBlockState(JUMP)
                +" soil="+l.getBlockState(JUMP.below())+" jump="+sawJump;
            if(v.getY()>JUMP.getY()+.3&&Math.abs(v.getX()-(JUMP.getX()+.5))<.6&&Math.abs(v.getZ()-(JUMP.getZ()+.5))<.6)sawJump=true;
            if(!sawJump||!v.isOnGround()||!l.getBlockState(JUMP).isAir()||!l.getBlockState(JUMP.below()).is(Blocks.DIRT))return;
            check(l.getBlockState(STEM).is(Blocks.PUMPKIN_STEM)||l.getBlockState(STEM).is(Blocks.ATTACHED_PUMPKIN_STEM),"fruit-space work damaged stem");
            l.setBlock(CHEST,Blocks.BARREL.defaultBlockState(),3);
            l.setBlock(CHEST.east(),Blocks.STONE.defaultBlockState(),3);
            l.setBlock(CHEST.east().above(),Blocks.STONE.defaultBlockState(),3);
            for(int i=0;i<2;i++){
                var post=POST.offset(i==0?-3:3,0,-4);
                var companion=EntityType.VILLAGER.create(l);
                companion.setPos(post.getX()+.5,post.getY()+1,post.getZ()+.5);
                companion.setAge(0);
                l.addFreshEntity(companion);
                check(ResidenceData.get(l).link(l,HOME,companion).equals("ok"),"companion residence");
                l.setBlock(post,WorkModule.PROFESSIONAL.get(Profession.FARMER).get().defaultBlockState(),3);
                var job=((WorkBlockEntity)l.getBlockEntity(post)).station();
                var employer=net.minecraftforge.common.util.FakePlayerFactory.get(l,
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(),"FarmSleepTest"));
                employer.setPos(companion.getX(),companion.getY(),companion.getZ());
                job.setItem(31,new ItemStack(Items.EMERALD,10));
                check(Employment.hire(employer,job,companion.getUUID(),Profession.FARMER).equals("ok"),"companion hire");
                companions.add(companion.getUUID());
                if(i==1){
                    l.setBlock(FORGE_CHEST,Blocks.BEACON.defaultBlockState(),3);
                    l.setBlock(FORGE_ALIAS,Blocks.BEACON.defaultBlockState(),3);
                    check(forgeStorage!=null,"missing test capability");
                    forgeStorage.setStackInSlot(0,new ItemStack(Items.CARROT,62));
                    job.setItem(0,new ItemStack(Items.CARROT,15));
                    job.setItem(1,new ItemStack(Items.STICK,2));
                }else{
                    l.setBlock(COMPANION_CHEST,Blocks.ENDER_CHEST.defaultBlockState(),3);
                    var offered=new int[27];offered[0]=3;
                    job.setItem(0,new ItemStack(Items.COBBLESTONE,3));
                    check(!br.com.kraftcity.logistics.DiscardChests.accepts(l,job,companion,COMPANION_CHEST,offered),
                        "personal Ender chest accepted items before consent");
                    var held=p.getMainHandItem().copy();
                    boolean crouched=p.isShiftKeyDown();
                    p.setShiftKeyDown(true);
                    p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.ENDER_EYE));
                    var permission=net.minecraftforge.common.ForgeHooks.onRightClickBlock(p,net.minecraft.world.InteractionHand.MAIN_HAND,
                        COMPANION_CHEST,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(COMPANION_CHEST),
                            Direction.UP,COMPANION_CHEST,false));
                    p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,held);
                    p.setShiftKeyDown(crouched);
                    check(permission.isCanceled()&&br.com.kraftcity.logistics.DiscardChests.accepts(l,job,companion,COMPANION_CHEST,offered),
                        "explicit owner consent did not authorize Ender transfer");
                }
                var head=BED.south(i==0?-2:2);
                var state=Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST);
                l.setBlock(head.west(),state.setValue(BedBlock.PART,BedPart.FOOT),3);
                l.setBlock(head,state.setValue(BedBlock.PART,BedPart.HEAD),3);
            }
            s.industry.putLong("discard",CHEST.asLong());
            var foot=BED.west();
            var bed=Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST);
            l.setBlock(foot,bed.setValue(BedBlock.PART,BedPart.FOOT),3);
            l.setBlock(foot.east(),bed.setValue(BedBlock.PART,BedPart.HEAD),3);
            // Keep the top of the mattress out of the standing navigation graph.
            // The side aisle remains clear and is inside the residence.
            l.setBlock(foot.above(2),Blocks.STONE.defaultBlockState(),3);
            l.setBlock(foot.east().above(2),Blocks.STONE.defaultBlockState(),3);
            var homes=ResidenceData.get(l);
            check(homes.reserveBed(worker,l.dimension(),HOME,BED),"main worker bed reservation");
            for(int offset=0;offset<3;offset++)l.setBlock(CHANNEL.south(offset),Blocks.AIR.defaultBlockState(),3);
            l.setBlock(CHANNEL,Blocks.WATER.defaultBlockState(),3);
            p.teleportTo(l,CHANNEL.getX()+.5,CHANNEL.getY()+3,CHANNEL.getZ()+5,180,25);
            stage=8;
        }else if(stage==8){
            if(!l.getFluidState(CHANNEL.south(2)).is(net.minecraft.tags.FluidTags.WATER))return;
            var channel=CHANNEL.south();
            v.setPos(channel.getX()+.5,channel.getY()+.05,channel.getZ()+.5);
            var dropAt=POST.offset(-1,1,2);
            var drop=new ItemEntity(l,dropAt.getX()+.5,dropAt.getY()+.1,dropAt.getZ()+.5,new ItemStack(Items.COBBLESTONE,6));
            drop.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            drop.setDefaultPickUpDelay();
            l.addFreshEntity(drop);
            closingDrop=drop.getUUID();
            for(int slot=0;slot<EmploymentData.COMMON;slot++){
                if(s.getItem(slot).is(Items.CARROT))s.removeItemNoUpdate(slot);
            }
            check(IndustrialWork.insert(s,new ItemStack(Items.CARROT,15),true),"could not stage 15 carrots");
            l.getServer().getCommands().performPrefixedCommand(l.getServer().createCommandSourceStack(),"time set night");
            nightStarted=l.getGameTime();
            stage=9;
        }else if(stage==9){
            if(l.getGameTime()-nightStarted<10||v.getY()<CHANNEL.getY()+.95||v.isInWaterOrBubble())return;
            System.out.println("FARMER_FLOWING_CHANNEL_EXIT_PASSED");
            p.teleportTo(l,CHEST.getX()+2.5,CHEST.getY()+.5,CHEST.getZ()+4.5,150,20);
            stage=3;
        }else if(stage==3){
            var barrel=(BarrelBlockEntity)l.getBlockEntity(CHEST);
            int deposited=barrel.countItem(Items.COBBLESTONE);
            progressDetail="worker="+v.blockPosition()+" chest="+deposited+" stock="+s.countItem(Items.COBBLESTONE)
                +" groundDrop="+l.getEntity(closingDrop)
                +" phaseUntil="+s.industry.getLong("farmer_discard_until")+" chestTarget="+s.industry.contains("farmer_discard_pos")
                +" pickupUntil="+s.industry.getLong("farmer_pickup_until")+" navigation="+v.getNavigation().getTargetPos()
                +" now="+l.getGameTime()+" day="+l.getDayTime()+" rain="+l.isRaining()
                +" forced="+v.getPersistentData().getBoolean("residenciasaldeoes_home_return_forced")
                +" door="+v.getPersistentData().contains("residenciasaldeoes_night_door_task")
                +" delivery="+v.getPersistentData().contains("residenciasaldeoes_emerald_transfer")
                +" attacked="+(v.getLastHurtByMob()!=null)+" noAI="+v.isNoAi()
                +" bed="+l.getBlockState(BED)+" claimed="+(ResidenceData.get(l).bedClaim(worker)!=null)
                +" memory="+v.getBrain().getMemory(MemoryModuleType.HOME)+" tick="+v.tickCount;
            check(l.getGameTime()-nightStarted<480,"night sequence did not finish: "+progressDetail);
            if(!v.isSleeping()||companions.stream().anyMatch(id->!(l.getEntity(id) instanceof Villager resident)||!resident.isSleeping()))return;
            var forgeJob=EmploymentData.get(l).assigned(companions.get(1));
            check(forgeStorage.getStackInSlot(0).getCount()==64&&forgeJob.countItem(Items.CARROT)==13
                &&forgeJob.countItem(Items.STICK)==2,"Forge partial insert or filter failed: shared="+forgeStorage.getStackInSlot(0)+" carrots="+forgeJob.countItem(Items.CARROT)+" sticks="+forgeJob.countItem(Items.STICK));
            check(p.getEnderChestInventory().countItem(Items.COBBLESTONE)==3,"third farmer did not finish its authorized Ender discard");
            System.out.println("FARMER_AUTHORIZED_ENDER_DISCARD_PASSED");
            System.out.println("FARMER_FORGE_INVENTORY_PARTIAL_PASSED");
            System.out.println("FARMER_THREE_DISCARDS_AND_SHARED_FORGE_PASSED");
            var sleepingBeds=new HashSet<BlockPos>();
            sleepingBeds.add(v.getSleepingPos().orElseThrow());
            for(var id:companions){
                var resident=(Villager)l.getEntity(id);
                check(sleepingBeds.add(resident.getSleepingPos().orElseThrow()),"farmers used the same bed");
            }
            s.industry.putUUID("visual_farmer_companion_0",companions.get(0));
            s.industry.putUUID("visual_farmer_companion_1",companions.get(1));
            check(v.getSleepingPos().filter(BED::equals).isPresent(),"farmer slept outside its own bed");
            check(seenPhysicalDeposit&&deposited==6&&s.countItem(Items.COBBLESTONE)==0,"went to bed before discarding: "+progressDetail+" at designated barrel="+seenPhysicalDeposit);
            check(barrel.countItem(Items.CARROT)==5&&s.countItem(Items.CARROT)==10,"main carrot reserve: barrel="+barrel.countItem(Items.CARROT)+" stock="+s.countItem(Items.CARROT));
            s.industry.putBoolean("visual_farmer_night",true);
            s.industry.putInt("visual_saved_harvest_cursor",s.industry.getInt("zigzag_harvest"));
            s.industry.putInt("visual_saved_plant_cursor",s.industry.getInt("zigzag_plant"));
            s.setChanged();
            System.out.println("FARMER_THREE_DISTINCT_BEDS_PASSED");
            System.out.println("FARMER_ZIGZAG_HARVEST_JUMP_DISCARD_AND_SLEEP_PASSED");
            var bed=v.getSleepingPos().orElseThrow();
            p.teleportTo(l,bed.getX()+.5,bed.getY()+.5,bed.getZ()+5.5,180,23);
            sleepObserved=l.getGameTime();
            stage=4;
        }else if(stage==4){
            if(l.getGameTime()-sleepObserved<24)return;
            var companion=(Villager)l.getEntity(companions.get(1));
            var start=POST.offset(7,1,-4);
            companion.stopSleeping();
            companion.setPos(start.getX()+.5,start.getY(),start.getZ()+.5);
            escapeStart=companion.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(HOME));
            var zombie=EntityType.ZOMBIE.create(l);
            zombie.setInvulnerable(true);
            zombie.setPersistenceRequired();
            zombie.setPos(start.getX()+1.5,start.getY(),start.getZ()-1.5);
            zombie.setTarget(companion);
            l.addFreshEntity(zombie);
            pursuer=zombie.getUUID();
            p.teleportTo(l,start.getX()+3.5,start.getY()+2,start.getZ()+3.5,140,24);
            stage=10;
        }else if(stage==10){
            var companion=(Villager)l.getEntity(companions.get(1));
            if(!Employment.fleeing(companion)||companion.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(HOME))>.75*escapeStart)return;
            var zombie=l.getEntity(pursuer);
            if(zombie!=null)zombie.discard();
            stage=11;
        }else if(stage==11){
            var companion=(Villager)l.getEntity(companions.get(1));
            if(!companion.isSleeping())return;
            check(companion.getSleepingPos().filter(at->ResidenceData.get(l).ownsBed(l.dimension(),at,companion.getUUID())).isPresent(),
                "night escape lost the companion's residential bed");
            System.out.println("FARMER_NIGHT_PURSUIT_HOME_RETURN_PASSED");
            stage=12;
        }
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void depositProbe(WorkActionEvent event){
        if((stage!=3&&stage!=9)||!event.action.equals("deposit")||!event.position.equals(CHEST)||!event.worker.getUUID().equals(worker))return;
        seenPhysicalDeposit=event.worker.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(CHEST))<=3.5;
    }

    static boolean tick(Minecraft mc){
        var server=mc.getSingleplayerServer();
        var player=mc.player.getUUID();
        ticks++;
        if(serverFailure!=null)throw new IllegalStateException(serverFailure);
        if(ticks==1)server.execute(()->recordFailure(()->setup(server.getPlayerList().getPlayer(player))));
        if(ticks%5==0&&stage>0&&stage!=12)server.execute(()->recordFailure(()->progress(server.getPlayerList().getPlayer(player))));
        if(stage!=visibleStage){visibleStage=stage;stageTicks=0;}
        stageTicks++;
        if(stage>=2&&stage!=capturedStage&&stageTicks>=8){
            capturedStage=stage;
            Screenshot.grab(new File("../build"),"visual-farmer-field-"+stage+".png",mc.getMainRenderTarget(),m->{});
        }
        check(ticks<3600&&(stage==3||stage==4||stage>=8||ticks<2400),"field or sleep sequence timed out at stage "+stage+" "+progressDetail);
        return stage==12&&capturedStage==12;
    }

    private static void recordFailure(Runnable step){
        if(serverFailure!=null)return;
        try{step.run();}
        catch(RuntimeException failure){
            serverFailure=failure.toString();
            throw failure;
        }
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void testForgeInventory(net.minecraftforge.event.AttachCapabilitiesEvent<net.minecraft.world.level.block.entity.BlockEntity> event){
        if(stage!=5||!(event.getObject() instanceof net.minecraft.world.level.block.entity.BeaconBlockEntity beacon)
            ||!beacon.getBlockPos().equals(FORGE_CHEST)&&!beacon.getBlockPos().equals(FORGE_ALIAS))return;
        if(forgeStorage==null)forgeStorage=new net.minecraftforge.items.ItemStackHandler(1){
            @Override public boolean isItemValid(int slot,ItemStack stack){return stack.is(Items.CARROT);}
        };
        var capability=net.minecraftforge.common.util.LazyOptional.of(()->forgeStorage);
        event.addCapability(new net.minecraft.resources.ResourceLocation("residenciasaldeoes","visual_forge_chest"),
            new net.minecraftforge.common.capabilities.ICapabilityProvider(){
                @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
                    net.minecraftforge.common.capabilities.Capability<T> type,Direction face){
                    return type==net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER&&face==Direction.WEST
                        ?capability.cast():net.minecraftforge.common.util.LazyOptional.empty();
                }
            });
        event.addListener(capability::invalidate);
    }

    public static boolean verifyWorkersSleepAgain(Minecraft mc){
        var server=mc.getSingleplayerServer();
        int tick=++restoredSleepTicks;
        if(tick==1){
            mc.player.closeContainer();
            var player=mc.player.getUUID();
            server.execute(()->recordFailure(()->{
                var level=server.overworld();
                server.getPlayerList().getPlayer(player).teleportTo(level,BED.getX()+.5,BED.getY()+2,BED.getZ()+5.5,180,25);
                level.setDayTime(13001);
            }));
        }
        if(tick%10==0&&!restoredSleeping){
            var ownerId=mc.player.getUUID();
            server.execute(()->recordFailure(()->{
                var level=server.overworld();
                var station=station(level);
                var residents=List.of(station.worker,station.industry.getUUID("visual_farmer_companion_0"),
                    station.industry.getUUID("visual_farmer_companion_1"));
                var beds=new HashSet<BlockPos>();
                for(var id:residents){
                    if(!(level.getEntity(id) instanceof Villager villager)||!villager.isSleeping())return;
                    var bed=villager.getSleepingPos().orElseThrow();
                    check(beds.add(bed)&&ResidenceData.get(level).ownsBed(level.dimension(),bed,id),
                        "reopened farm has a conflicting bed");
                }
                var owner=server.getPlayerList().getPlayer(ownerId);
                check(owner.getEnderChestInventory().countItem(Items.COBBLESTONE)==3
                    &&br.com.kraftcity.logistics.DiscardChests.capability(level,COMPANION_CHEST)!=null,
                    "explicit Ender link or deposited items were lost on restart");
                System.out.println("FARMER_AUTHORIZED_ENDER_RESTART_PASSED");
                restoredSleeping=true;
            }));
        }
        if(serverFailure!=null)throw new IllegalStateException(serverFailure);
        check(tick<500,"three farmers did not return to sleep in the reopened world");
        if(!restoredSleeping)return false;
        Screenshot.grab(new File("../build"),"visual-farmers-restored.png",mc.getMainRenderTarget(),m->{});
        System.out.println("FARMER_THREE_SLEEP_AFTER_RESTART_PASSED");
        return true;
    }

    static void verifyRestore(ServerLevel l){
        var s=station(l);
        check(s.industry.getBoolean("visual_farmer_night")&&s.industry.getInt("zigzag_size")==5,"saved zigzag or night state missing");
        var claim=ResidenceData.get(l).bedClaim(s.worker);
        check(claim!=null&&claim.home().equals(HOME)&&claim.bed().equals(BED),"saved residential bed claim changed");
        var beds=new HashSet<BlockPos>();
        beds.add(claim.bed());
        for(int i=0;i<2;i++){
            var companion=ResidenceData.get(l).bedClaim(s.industry.getUUID("visual_farmer_companion_"+i));
            check(companion!=null&&companion.home().equals(HOME)&&beds.add(companion.bed()),"companion bed was lost or shared after restart");
        }
        check(s.industry.getInt("zigzag_harvest")==s.industry.getInt("visual_saved_harvest_cursor")
            &&s.industry.getInt("zigzag_plant")==s.industry.getInt("visual_saved_plant_cursor"),"saved zigzag cursors changed");
        check(((BarrelBlockEntity)l.getBlockEntity(CHEST)).countItem(Items.COBBLESTONE)==6,"saved discard changed");
        for(var at:ROW)check(l.getBlockState(at).is(Blocks.WHEAT)&&l.getBlockState(at.below()).is(Blocks.FARMLAND),
            "saved crop or soil changed at "+at);
        System.out.println("FARMER_SAME_WORLD_RESTART_PASSED");
    }
}
