package br.com.kraftcity.test;

import br.com.kraftcity.employment.*;
import br.com.kraftcity.industry.*;
import br.com.residenciasaldeoes.ResidenciasDosAldeoes;
import br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity;
import br.com.residenciasaldeoes.data.ResidenceData;
import java.io.File;
import java.util.List;
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
    private static final BlockPos POST=new BlockPos(79,-61,60);
    private static final BlockPos HOME=POST.offset(-7,0,-1);
    private static final BlockPos BED=HOME.east(3).above();
    private static final BlockPos CHEST=POST.offset(-5,1,2);
    private static final BlockPos CANE=POST.offset(2,1,1);
    private static final BlockPos STEM=POST.offset(-1,1,1);
    private static final BlockPos FRUIT=STEM.south();
    private static final BlockPos JUMP=POST.offset(-2,1,1);
    private static final BlockPos CARROTS=POST.offset(-2,1,-1);
    private static final BlockPos POTATOES=POST.offset(2,1,0);
    private static final List<BlockPos> ROW=List.of(POST.offset(-2,1,-2),POST.offset(-1,1,-2),POST.offset(0,1,-2),POST.offset(2,1,-2),POST.offset(2,1,-1),POST.offset(1,1,-1));
    private static volatile int stage;
    private static volatile String progressDetail="",serverFailure;
    private static UUID worker,visitor;
    private static boolean canePlanted,sawLooseCane,sawJump,seenPhysicalDeposit,visitorLeft;
    private static int ticks;
    private static int capturedStage=-1,visibleStage=-1,stageTicks;
    private static long nightStarted;
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
        for(var at:ROW)l.setBlock(at.below(),Blocks.DIRT.defaultBlockState(),3);
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
            if(!previous||!visitorLeft)return;
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
            s.industry.putLong("discard",CHEST.asLong());
            var drop=new ItemEntity(l,JUMP.getX()+.5,JUMP.getY()+.1,JUMP.getZ()+.5,new ItemStack(Items.COBBLESTONE,6));
            drop.setDefaultPickUpDelay();
            l.addFreshEntity(drop);
            var foot=BED.west();
            var bed=Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST);
            l.setBlock(foot,bed.setValue(BedBlock.PART,BedPart.FOOT),3);
            l.setBlock(foot.east(),bed.setValue(BedBlock.PART,BedPart.HEAD),3);
            // Keep the top of the mattress out of the standing navigation graph.
            // The side aisle remains clear and is inside the residence.
            l.setBlock(foot.above(2),Blocks.STONE.defaultBlockState(),3);
            l.setBlock(foot.east().above(2),Blocks.STONE.defaultBlockState(),3);
            l.setDayTime(13001);
            nightStarted=l.getGameTime();
            p.teleportTo(l,CHEST.getX()+2.5,CHEST.getY()+.5,CHEST.getZ()+4.5,150,20);
            stage=3;
        }else if(stage==3){
            var barrel=(BarrelBlockEntity)l.getBlockEntity(CHEST);
            int deposited=barrel.countItem(Items.COBBLESTONE);
            progressDetail="worker="+v.blockPosition()+" chest="+deposited+" stock="+s.countItem(Items.COBBLESTONE)
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
            if(!v.isSleeping())return;
            check(v.getSleepingPos().filter(BED::equals).isPresent(),"farmer slept outside its own bed");
            check(seenPhysicalDeposit&&deposited==6&&s.countItem(Items.COBBLESTONE)==0,"went to bed before discarding");
            s.industry.putBoolean("visual_farmer_night",true);
            s.industry.putInt("visual_saved_harvest_cursor",s.industry.getInt("zigzag_harvest"));
            s.industry.putInt("visual_saved_plant_cursor",s.industry.getInt("zigzag_plant"));
            s.setChanged();
            System.out.println("FARMER_ZIGZAG_HARVEST_JUMP_DISCARD_AND_SLEEP_PASSED");
            var bed=v.getSleepingPos().orElseThrow();
            p.teleportTo(l,bed.getX()+.5,bed.getY()+.5,bed.getZ()+3.5,180,20);
            stage=4;
        }
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void depositProbe(WorkActionEvent event){
        if(stage!=3||!event.action.equals("deposit")||!event.position.equals(CHEST)||!event.worker.getUUID().equals(worker))return;
        seenPhysicalDeposit=event.worker.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(CHEST))<=3.5;
    }

    static boolean tick(Minecraft mc){
        var server=mc.getSingleplayerServer();
        var player=mc.player.getUUID();
        ticks++;
        if(serverFailure!=null)throw new IllegalStateException(serverFailure);
        if(ticks==1)server.execute(()->recordFailure(()->setup(server.getPlayerList().getPlayer(player))));
        if(ticks%5==0&&stage>0&&stage!=4)server.execute(()->recordFailure(()->progress(server.getPlayerList().getPlayer(player))));
        if(stage!=visibleStage){visibleStage=stage;stageTicks=0;}
        stageTicks++;
        if(stage>=2&&stage!=capturedStage&&stageTicks>=8){
            capturedStage=stage;
            Screenshot.grab(new File("../build"),"visual-farmer-field-"+stage+".png",mc.getMainRenderTarget(),m->{});
        }
        check(ticks<3600&&(stage==3||stage==4||ticks<2400),"field or sleep sequence timed out at stage "+stage+" "+progressDetail);
        return stage==4&&capturedStage==4;
    }

    private static void recordFailure(Runnable step){
        if(serverFailure!=null)return;
        try{step.run();}
        catch(RuntimeException failure){
            serverFailure=failure.toString();
            throw failure;
        }
    }

    static void verifyRestore(ServerLevel l){
        var s=station(l);
        check(s.industry.getBoolean("visual_farmer_night")&&s.industry.getInt("zigzag_size")==5,"saved zigzag or night state missing");
        var claim=ResidenceData.get(l).bedClaim(s.worker);
        check(claim!=null&&claim.home().equals(HOME)&&claim.bed().equals(BED),"saved residential bed claim changed");
        check(s.industry.getInt("zigzag_harvest")==s.industry.getInt("visual_saved_harvest_cursor")
            &&s.industry.getInt("zigzag_plant")==s.industry.getInt("visual_saved_plant_cursor"),"saved zigzag cursors changed");
        check(((BarrelBlockEntity)l.getBlockEntity(CHEST)).countItem(Items.COBBLESTONE)==6,"saved discard changed");
        for(var at:ROW)check(l.getBlockState(at).is(Blocks.WHEAT),"saved crop changed");
        System.out.println("FARMER_SAME_WORLD_RESTART_PASSED");
    }
}
