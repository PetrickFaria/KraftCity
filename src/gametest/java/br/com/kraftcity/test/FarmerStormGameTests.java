package br.com.kraftcity.test;

import br.com.kraftcity.employment.Employment;
import br.com.kraftcity.employment.Profession;
import br.com.kraftcity.employment.WorkBlockEntity;
import br.com.kraftcity.employment.WorkModule;
import br.com.residenciasaldeoes.ResidenciasDosAldeoes;
import br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity;
import br.com.residenciasaldeoes.data.ResidenceData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("residenciasaldeoes")
@PrefixGameTestTemplate(false)
public final class FarmerStormGameTests {
    private static final String HARVEST = "Farmer did not harvest in ordinary rain or resume after thunder";

    private record Farm(Villager farmer,BlockPos home,BlockPos bed,BlockPos crop) {
        boolean inside(){
            return Math.abs(farmer.getBlockX()-home.getX())<=4 && Math.abs(farmer.getBlockZ()-home.getZ())<=4
                && farmer.getBlockY()>=home.getY()+1 && farmer.getBlockY()<=home.getY()+9;
        }
        boolean covered(){
            return inside()&&!farmer.level.canSeeSky(farmer.blockPosition().above());
        }
    }

    @GameTest(template="empty",batch="farmer_storm_lifecycle",timeoutTicks=1800)
    public static void three_farmers_walk_under_cover_sleep_and_resume_after_thunder(GameTestHelper h){
        var level=h.getLevel();
        level.setDayTime(1000);
        weather(level,true,false);
        Farm[] farms={farm(h,0),farm(h,12),farm(h,24)};
        Vec3[] starting=new Vec3[farms.length];
        h.startSequence()
            .thenWaitUntil(()->check(harvested(level,farms),HARVEST))
            .thenExecute(()->{
                weather(level,true,true);
                for(var farm:farms)ripen(level,farm.crop);
            })
            .thenWaitUntil(()->{
                check(ripe(level,farms),"Crop changed while farmers were returning during thunder");
                for(int i=0;i<farms.length;i++){
                    check(farms[i].covered(),"Farmer "+i+" did not reach the covered residence");
                    if(starting[i]==null)starting[i]=farms[i].farmer.position();
                }
            })
            .thenExecuteFor(70,()->assertSheltered(level,farms))
            .thenWaitUntil(()->check(walked(farms,starting),"A farmer stopped moving after reaching cover"))
            .thenExecute(()->level.setDayTime(13001))
            .thenWaitUntil(()->{
                for(var farm:farms)check(farm.farmer.getSleepingPos().filter(farm.bed::equals).isPresent(),
                    "Farmer did not walk to its assigned bed during a storm");
                check(ripe(level,farms),"Crop changed during the storm at night");
            })
            .thenExecute(()->{
                level.setDayTime(1000);
                for(int i=0;i<farms.length;i++)starting[i]=farms[i].farmer.position();
            })
            .thenExecuteFor(30,()->assertSheltered(level,farms))
            .thenWaitUntil(()->check(walked(farms,starting),"A farmer did not resume walking indoors at dawn"))
            .thenExecute(()->weather(level,true,false))
            .thenWaitUntil(()->check(harvested(level,farms),HARVEST))
            .thenExecute(()->weather(level,false,false))
            .thenSucceed();
    }

    private static void assertSheltered(ServerLevel level,Farm[] farms){
        for(var farm:farms)check(farm.inside(),"Farmer left the residential area during thunder");
        check(ripe(level,farms),"Crop changed while farmers were sheltered");
    }

    private static boolean walked(Farm[] farms,Vec3[] starting){
        for(int i=0;i<farms.length;i++){
            var farm=farms[i];
            if(!farm.covered()||farm.farmer.isSleeping()||farm.farmer.position().distanceToSqr(starting[i])<4)return false;
        }
        return true;
    }

    private static Farm farm(GameTestHelper h,int offset){
        var level=h.getLevel();
        var home=h.absolutePos(new BlockPos(5,2,5+offset));
        var post=home.east(10);
        for(var at:BlockPos.betweenClosed(home.offset(-4,0,-4),post.offset(3,0,4)))
            level.setBlockAndUpdate(at,Blocks.STONE.defaultBlockState());
        for(var at:BlockPos.betweenClosed(home.offset(-4,4,-4),home.offset(4,4,4)))
            level.setBlockAndUpdate(at,Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(home,ResidenciasDosAldeoes.ANCHOR.get().defaultBlockState());
        ((ResidentialAnchorBlockEntity)level.getBlockEntity(home)).setSize(9);
        var bed=home.west(3).above();
        var bedState=Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.WEST);
        level.setBlock(bed.east(),bedState,2);
        level.setBlock(bed,bedState.setValue(BedBlock.PART,BedPart.HEAD),2);
        var farmer=EntityType.VILLAGER.create(level);
        farmer.moveTo(Vec3.atBottomCenterOf(home.east().above()));
        level.addFreshEntity(farmer);
        check("ok".equals(ResidenceData.get(level).link(level,home,farmer)),"Link storm residence");
        level.setBlockAndUpdate(post,WorkModule.INDUSTRIAL.get().defaultBlockState());
        var station=((WorkBlockEntity)level.getBlockEntity(post)).station();
        var actor=FakePlayerFactory.getMinecraft(level);
        actor.setPos(Vec3.atBottomCenterOf(post.east().above()));
        station.setItem(31,new ItemStack(Items.EMERALD,16));
        check("ok".equals(Employment.hire(actor,station,farmer.getUUID(),Profession.FARMER)),"Hire storm farmer");
        station.setItem(27,new ItemStack(Items.IRON_HOE));
        station.setItem(0,new ItemStack(Items.WHEAT_SEEDS,32));
        var crop=post.east(2).above();
        level.setBlockAndUpdate(crop.below(),Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE,7));
        ripen(level,crop);
        farmer.setPos(Vec3.atBottomCenterOf(crop.west()));
        return new Farm(farmer,home,bed,crop);
    }

    private static void weather(ServerLevel level,boolean rain,boolean thunder){
        level.setWeatherParameters(rain?0:12000,rain?12000:0,rain,thunder);
        level.setRainLevel(rain?1:0);
        level.setThunderLevel(thunder?1:0);
    }

    private static void ripen(ServerLevel level,BlockPos crop){
        level.setBlockAndUpdate(crop,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7));
    }

    private static boolean ripe(ServerLevel level,Farm[] farms){
        for(var farm:farms)if(!level.getBlockState(farm.crop).is(Blocks.WHEAT)
            ||level.getBlockState(farm.crop).getValue(CropBlock.AGE)!=7)return false;
        return true;
    }

    private static boolean harvested(ServerLevel level,Farm[] farms){
        for(var farm:farms){
            var state=level.getBlockState(farm.crop);
            if(!state.is(Blocks.WHEAT)||state.getValue(CropBlock.AGE)==7)return false;
        }
        return true;
    }

    private static void check(boolean valid,String message){
        if(!valid)throw new GameTestAssertException(message);
    }
}
