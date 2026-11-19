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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;

public final class FarmerVisualCheck {
    private static final BlockPos POST=new BlockPos(79,-58,60);
    private static final BlockPos HOME=POST.offset(-7,1,-1);
    private static final BlockPos CHEST=POST.offset(4,1,0);
    private static final BlockPos CANE=POST.offset(2,1,1);
    private static final BlockPos STEM=POST.offset(1,1,-2);
    private static final BlockPos FRUIT=STEM.south();
    private static final List<BlockPos> ROW=List.of(POST.offset(-2,1,-2),POST.offset(-1,1,-2),POST.offset(0,1,-2));
    private static volatile int stage;
    private static UUID worker;
    private static int ticks;
    private static int capturedStage=-1,visibleStage=-1,stageTicks;
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
        for(int x=-9;x<=7;x++)for(int z=-5;z<=5;z++){
            l.setBlock(POST.offset(x,0,z),Blocks.STONE.defaultBlockState(),3);
            for(int y=1;y<=4;y++)l.setBlock(POST.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        l.setBlock(HOME,ResidenciasDosAldeoes.ANCHOR.get().defaultBlockState(),3);
        ((ResidentialAnchorBlockEntity)l.getBlockEntity(HOME)).setSize(11);
        var foot=HOME.east(2);
        var bed=Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST);
        l.setBlock(foot,bed.setValue(BedBlock.PART,BedPart.FOOT),3);
        l.setBlock(foot.east(),bed.setValue(BedBlock.PART,BedPart.HEAD),3);
        for(var at:ROW)l.setBlock(at.below(),Blocks.DIRT.defaultBlockState(),3);
        var v=EntityType.VILLAGER.create(l);
        v.setAge(0);v.setInvulnerable(true);
        v.setPos(POST.getX()+2.5,POST.getY()+1,POST.getZ()+2.5);
        l.addFreshEntity(v);
        check(ResidenceData.get(l).link(l,HOME,v).equals("ok"),"residential link");
        l.setBlock(POST,WorkModule.INDUSTRIAL.get().defaultBlockState(),3);
        var s=station(l);
        p.teleportTo(l,POST.getX()+.5,POST.getY()+1,POST.getZ()+3.5,180,18);
        s.setItem(31,new ItemStack(Items.EMERALD,10));
        check(Employment.hire(p,s,v.getUUID(),Profession.FARMER).equals("ok"),"hire");
        worker=v.getUUID();
        s.setItem(27,new ItemStack(Items.IRON_HOE));s.setItem(30,new ItemStack(Items.WATER_BUCKET));
        s.setItem(0,new ItemStack(Items.WHEAT_SEEDS,3));
        s.industry.putBoolean("preview",true);
        s.setChanged();
        IndustryNetwork.sendBounds(p);
        stage=1;
    }

    private static void progress(ServerPlayer p){
        var l=p.getLevel();var s=station(l);
        if(!(l.getEntity(worker) instanceof Villager v))return;
        if(stage==1){
            boolean previous=true;
            for(var at:ROW){
                boolean planted=l.getBlockState(at).is(Blocks.WHEAT);
                check(!planted||previous,"new crops skipped a reachable spiral position");
                previous=planted;
            }
            if(!previous)return;
            l.setBlock(CANE.below(2),Blocks.STONE.defaultBlockState(),3);
            l.setBlock(CANE.below(2).east(),Blocks.STONE.defaultBlockState(),3);
            l.setBlock(CANE.below(),Blocks.SAND.defaultBlockState(),3);
            l.setBlock(CANE.below().east(),Blocks.WATER.defaultBlockState(),3);
            for(int height=0;height<3;height++)l.setBlock(CANE.above(height),Blocks.SUGAR_CANE.defaultBlockState(),3);
            l.setBlock(STEM.below(),Blocks.FARMLAND.defaultBlockState(),3);
            l.setBlock(FRUIT.below(),Blocks.DIRT.defaultBlockState(),3);
            l.setBlock(FRUIT,Blocks.PUMPKIN.defaultBlockState(),3);
            l.setBlock(STEM,Blocks.ATTACHED_PUMPKIN_STEM.defaultBlockState().setValue(AttachedStemBlock.FACING,Direction.SOUTH),3);
            stage=2;
        }else if(stage==2){
            if(!l.getBlockState(CANE.above()).isAir()||!l.getBlockState(FRUIT).isAir())return;
            check(l.getBlockState(CANE).is(Blocks.SUGAR_CANE),"cane base was removed: "+l.getBlockState(CANE));
            check(l.getBlockState(STEM).is(Blocks.PUMPKIN_STEM),"pumpkin stem was removed: "+l.getBlockState(STEM));
            l.setBlock(CHEST,Blocks.BARREL.defaultBlockState(),3);
            s.industry.putLong("discard",CHEST.asLong());
            s.setItem(8,new ItemStack(Items.COBBLESTONE,6));
            l.setDayTime(13001);
            p.teleportTo(l,CHEST.getX()+2.5,CHEST.getY()+.5,CHEST.getZ()+4.5,150,20);
            stage=3;
        }else if(stage==3){
            var barrel=(BarrelBlockEntity)l.getBlockEntity(CHEST);
            int deposited=barrel.countItem(Items.COBBLESTONE);
            if(!v.isSleeping())return;
            check(deposited==6&&s.getItem(8).isEmpty(),"went to bed before discarding");
            s.industry.putBoolean("visual_farmer_night",true);
            s.setChanged();
            System.out.println("FARMER_SPIRAL_HARVEST_DISCARD_AND_SLEEP_PASSED");
            var bed=v.getSleepingPos().orElseThrow();
            p.teleportTo(l,bed.getX()+.5,bed.getY()+.5,bed.getZ()+3.5,180,20);
            stage=4;
        }
    }

    static boolean tick(Minecraft mc){
        var server=mc.getSingleplayerServer();
        var player=mc.player.getUUID();
        ticks++;
        if(ticks==1)server.execute(()->setup(server.getPlayerList().getPlayer(player)));
        if(ticks%5==0&&stage>0&&stage<4)server.execute(()->progress(server.getPlayerList().getPlayer(player)));
        if(stage!=visibleStage){visibleStage=stage;stageTicks=0;}
        stageTicks++;
        if(stage>=2&&stage!=capturedStage&&stageTicks>=8){
            capturedStage=stage;
            Screenshot.grab(new File("../build"),"visual-farmer-field-"+stage+".png",mc.getMainRenderTarget(),m->{});
        }
        check(ticks<1200,"field or sleep sequence timed out at stage "+stage);
        return stage==4&&capturedStage==4;
    }

    static void verifyRestore(ServerLevel l){
        var s=station(l);
        check(s.industry.getBoolean("visual_farmer_night")&&s.industry.getInt("spiral_size")==5,"saved spiral or night state missing");
        check(((BarrelBlockEntity)l.getBlockEntity(CHEST)).countItem(Items.COBBLESTONE)==6,"saved discard changed");
        for(var at:ROW)check(l.getBlockState(at).is(Blocks.WHEAT),"saved crop changed");
        System.out.println("FARMER_SAME_WORLD_RESTART_PASSED");
    }
}
