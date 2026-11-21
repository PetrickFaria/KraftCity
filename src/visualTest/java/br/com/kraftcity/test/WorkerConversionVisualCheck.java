package br.com.kraftcity.test;

import br.com.kraftcity.employment.Employment;
import br.com.kraftcity.employment.EmploymentData;
import br.com.kraftcity.employment.Profession;
import br.com.kraftcity.employment.WorkBlockEntity;
import br.com.kraftcity.employment.WorkModule;
import br.com.kraftcity.industry.IndustrialRules;
import br.com.kraftcity.workforce.WorkforceData;
import br.com.residenciasaldeoes.ResidenciasDosAldeoes;
import br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity;
import br.com.residenciasaldeoes.data.ResidenceData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;

public final class WorkerConversionVisualCheck {
    private static final BlockPos POST=new BlockPos(184,-57,45);
    private static final BlockPos HOME=POST.east(4);
    private static final BlockPos SHELTER=HOME.south();

    private static void check(boolean valid,String message){
        if(!valid)throw new IllegalStateException(message);
    }

    static void prepare(ServerLevel level){
        level.setChunkForced(POST.getX()>>4,POST.getZ()>>4,true);
        level.setBlockAndUpdate(HOME,ResidenciasDosAldeoes.ANCHOR.get().defaultBlockState());
        ((ResidentialAnchorBlockEntity)level.getBlockEntity(HOME)).setSize(11);
        level.setBlockAndUpdate(SHELTER.below(),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(SHELTER.above(2),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(POST,WorkModule.INDUSTRIAL.get().defaultBlockState());
        var station=((WorkBlockEntity)level.getBlockEntity(POST)).station();
        var worker=EntityType.VILLAGER.create(level);
        worker.setPos(Vec3.atBottomCenterOf(SHELTER));
        worker.setNoAi(true);
        level.addFreshEntity(worker);
        var id=worker.getUUID();
        check("ok".equals(ResidenceData.get(level).link(level,HOME,worker)),"Farmer test residence");
        var actor=FakePlayerFactory.getMinecraft(level);
        actor.setPos(Vec3.atBottomCenterOf(POST.south()));
        station.setItem(31,new ItemStack(Items.EMERALD,16));
        check("ok".equals(Employment.hire(actor,station,id,Profession.FARMER)),"Farmer test hire");
        WorkforceData.get(level).setProgress(id,Profession.FARMER.id(),5,121);
        check(IndustrialRules.selectSize(level,station,13),"Farmer test area");
        var zombie=worker.convertTo(EntityType.ZOMBIE_VILLAGER,false);
        check(zombie!=null&&EmploymentData.get(level).awaitingCure(id),"Farmer infection before save");
        zombie.setNoAi(true);
        zombie.setInvulnerable(true);
        station.industry.putUUID("visual_zombie",zombie.getUUID());
        station.setChanged();
        System.out.println("FARMER_INFECTION_PREPARED_FOR_RESTART");
    }

    public static void verifyRestore(ServerLevel level){
        level.getChunkAt(POST);
        var station=((WorkBlockEntity)level.getBlockEntity(POST)).station();
        var id=station.worker;
        check(id!=null&&EmploymentData.get(level).awaitingCure(id)&&IndustrialRules.size(level,station)==13,
            "Saved farmer infection, assignment and 13×13");
        var entity=level.getEntity(station.industry.getUUID("visual_zombie"));
        check(entity instanceof ZombieVillager,"Reloaded infected farmer entity missing");
        var zombie=(ZombieVillager)entity;
        var saved=zombie.saveWithoutId(new CompoundTag());
        saved.putInt("ConversionTime",1);
        zombie.readAdditionalSaveData(saved);
        zombie.tick();
        var progress=WorkforceData.get(level).progress(id,Profession.FARMER.id());
        check(level.getEntity(id) instanceof Villager&&id.equals(station.worker)&&ResidenceData.get(level).owner(id)!=null
            &&progress.level()==5&&progress.xp()==121&&IndustrialRules.size(level,station)==13,
            "Cure after JVM restart preserves identity, home, assignment, area and XP");
        level.setChunkForced(POST.getX()>>4,POST.getZ()>>4,false);
        System.out.println("FARMER_CURE_AFTER_JVM_RESTART_PASSED");
    }
}
