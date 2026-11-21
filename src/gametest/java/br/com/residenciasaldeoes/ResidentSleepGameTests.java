package br.com.residenciasaldeoes;

import br.com.kraftcity.employment.*;
import br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity;
import br.com.residenciasaldeoes.data.ResidenceData;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("residenciasaldeoes")
@PrefixGameTestTemplate(false)
public final class ResidentSleepGameTests {
    private static void check(boolean condition,String message){
        if(!condition)throw new GameTestAssertException(message);
    }

    @GameTest(template="empty",timeoutTicks=40,batch="resident_healing_contracts")
    public static void sleep_recovers_one_hp_every_six_hundred_ticks_for_all_resident_contracts(GameTestHelper helper){
        var level=helper.getLevel();
        var home=helper.absolutePos(new BlockPos(5,2,5));
        level.setBlockAndUpdate(home,ResidenciasDosAldeoes.ANCHOR.get().defaultBlockState());
        ((ResidentialAnchorBlockEntity)level.getBlockEntity(home)).setSize(11);
        var bed=home.east(2).above();
        var state=Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST);
        level.setBlockAndUpdate(bed.below(),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(bed.west().below(),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(bed.west(),state.setValue(BedBlock.PART,BedPart.FOOT));
        level.setBlockAndUpdate(bed,state.setValue(BedBlock.PART,BedPart.HEAD));
        var homes=ResidenceData.get(level);
        var villager=helper.spawn(EntityType.VILLAGER,new BlockPos(5,3,6));
        villager.setAge(0);
        villager.setNoAi(true);
        check(homes.link(level,home,villager).equals("ok"),"Resident link failed");
        var post=home.north(3).above();
        var employer=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"HealingTest"));
        employer.setPos(post.getX()+.5,post.getY(),post.getZ()+.5);

        for(var job:new Profession[]{null,Profession.FARMER,Profession.BAKER,Profession.SOLDIER,Profession.BUILDER}){
            if(job!=null){
                level.setBlockAndUpdate(post,WorkModule.generic(job.sector).defaultBlockState());
                var station=((WorkBlockEntity)level.getBlockEntity(post)).station();
                station.setItem(EmploymentData.PAYMENT,new ItemStack(Items.EMERALD,64));
                check(Employment.hire(employer,station,villager.getUUID(),job).equals("ok"),"Could not hire "+job);
            }
            float initial=Math.min(12,villager.getMaxHealth()-4);
            villager.setHealth(initial);
            villager.startSleeping(bed);
            if(job==Profession.SOLDIER){
                level.setDayTime(13000);
                br.com.kraftcity.military.Military.tick(level,EmploymentData.get(level).assigned(villager.getUUID()),villager);
                check(villager.getHealth()==initial,"Military rest added a second heal");
            }
            advance(helper,villager,599);
            check(villager.getHealth()==initial,"Heal occurred before 600 actual sleep ticks for "+job);
            advance(helper,villager,1);
            check(villager.getHealth()==initial+1,"Expected one HP at 600 ticks for "+job);
            advance(helper,villager,600);
            check(villager.getHealth()==initial+2,"Expected two HP at 1200 ticks for "+job);
            advance(helper,villager,1200);
            check(villager.getHealth()==initial+4,"Expected four HP at 2400 ticks for "+job);
            villager.stopSleeping();
            if(job!=null){Employment.release(level,villager.getUUID());level.removeBlock(post,false);}
        }

        villager.setHealth(villager.getMaxHealth()-.5F);
        villager.startSleeping(bed);
        advance(helper,villager,600);
        check(villager.getHealth()==villager.getMaxHealth(),"Heal exceeded health cap");
        villager.setHealth(10);
        advance(helper,villager,599);
        villager.stopSleeping();
        villager.startSleeping(bed);
        advance(helper,villager,1);
        check(villager.getHealth()==10,"A partial sleep session transferred to the next session");
        advance(helper,villager,598);
        new VillagerResidenceEvents().clearSleepProgress(new net.minecraftforge.event.entity.EntityLeaveLevelEvent(villager,level));
        advance(helper,villager,1);
        check(villager.getHealth()==10,"Unloaded sleep progress was carried into a new session");
        villager.stopSleeping();
        advance(helper,villager,600);
        check(villager.getHealth()==10,"Awake resident healed");
        villager.startSleeping(bed);
        level.setBlockAndUpdate(bed.west(),Blocks.AIR.defaultBlockState());
        advance(helper,villager,600);
        check(villager.getHealth()==10,"Invalid bed healed resident");
        villager.stopSleeping();
        level.setBlockAndUpdate(bed.west(),state.setValue(BedBlock.PART,BedPart.FOOT));
        level.setBlockAndUpdate(bed,state.setValue(BedBlock.PART,BedPart.HEAD));
        homes.unlink(level,villager.getUUID());
        villager.startSleeping(bed);
        advance(helper,villager,600);
        check(villager.getHealth()==10,"Unlinked villager healed");
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=680,batch="resident_healing_real_ticks")
    public static void injured_resident_heals_only_after_real_sleep_ticks(GameTestHelper helper){
        var level=helper.getLevel();
        var home=helper.absolutePos(new BlockPos(5,2,5));
        level.setBlockAndUpdate(home,ResidenciasDosAldeoes.ANCHOR.get().defaultBlockState());
        ((ResidentialAnchorBlockEntity)level.getBlockEntity(home)).setSize(7);
        var head=home.east(2).above();
        var state=Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST);
        level.setBlockAndUpdate(head.below(),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(head.west().below(),Blocks.COBBLESTONE.defaultBlockState());
        level.setBlockAndUpdate(head.west(),state.setValue(BedBlock.PART,BedPart.FOOT));
        level.setBlockAndUpdate(head,state.setValue(BedBlock.PART,BedPart.HEAD));
        var sleeper=helper.spawn(EntityType.VILLAGER,new BlockPos(5,3,6));
        sleeper.setNoAi(true);
        check(sleeper.hurt(net.minecraft.world.damagesource.DamageSource.GENERIC,8),"Test injury was rejected");
        check(sleeper.getHealth()==12,"Damage changed unexpectedly");
        check(ResidenceData.get(level).link(level,home,sleeper).equals("ok"),"Resident link failed");
        level.setDayTime(13000);
        sleeper.startSleeping(head);
        helper.startSequence().thenExecuteFor(590,()->{
            check(sleeper.isSleeping()&&sleeper.getHealth()==12,"Healing occurred before 30 seconds");
        }).thenWaitUntil(()->check(sleeper.getHealth()==13,"No healing after one period")).thenSucceed();
    }

    private static void advance(GameTestHelper helper,Villager villager,int ticks){
        for(int i=0;i<ticks;i++)VillagerResidenceEvents.healDuringSleep(helper.getLevel(),villager);
    }
}
