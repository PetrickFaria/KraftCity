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
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("residenciasaldeoes")
@PrefixGameTestTemplate(false)
public final class WorkerConversionGameTests {
    private record Fixture(ServerLevel level, BlockPos home, EmploymentData.Station station, Villager worker, ServerPlayer player) {}
    private static void check(boolean valid,String message){if(!valid)throw new net.minecraft.gametest.framework.GameTestAssertException(message);}

    private static Fixture fixture(GameTestHelper h,Profession profession,int offset){
        var level=h.getLevel();
        BlockPos home=h.absolutePos(new BlockPos(2+offset,2,2));
        BlockPos post=home.east(4);
        level.setBlockAndUpdate(home,ResidenciasDosAldeoes.ANCHOR.get().defaultBlockState());
        ((ResidentialAnchorBlockEntity)level.getBlockEntity(home)).setSize(11);
        level.setBlockAndUpdate(post,WorkModule.generic(profession.sector).defaultBlockState());
        var station=((WorkBlockEntity)level.getBlockEntity(post)).station();
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"ConversionTest"));
        player.setPos(Vec3.atBottomCenterOf(post.south()));
        station.setItem(31,new ItemStack(Items.EMERALD,64));
        var worker=resident(level,home);
        check("ok".equals(Employment.hire(player,station,worker.getUUID(),profession)),"Hire "+profession);
        return new Fixture(level,home,station,worker,player);
    }

    private static Villager resident(ServerLevel level,BlockPos home){
        var worker=EntityType.VILLAGER.create(level);
        worker.setPos(Vec3.atBottomCenterOf(home.east()));
        worker.setNoAi(true);
        level.addFreshEntity(worker);
        check("ok".equals(ResidenceData.get(level).link(level,home,worker)),"Worker residence");
        return worker;
    }

    private static ZombieVillager infect(Fixture f){
        var zombie=EntityType.ZOMBIE.create(f.level);
        zombie.setPos(f.worker.position());
        zombie.setNoAi(true);
        f.level.addFreshEntity(zombie);
        var prior=f.level.getDifficulty();
        try{
            f.level.getServer().setDifficulty(Difficulty.HARD,true);
            f.worker.hurt(DamageSource.mobAttack(zombie),1000);
        }finally{
            f.level.getServer().setDifficulty(prior,true);
            zombie.discard();
        }
        List<ZombieVillager> converted=f.level.getEntitiesOfClass(ZombieVillager.class,f.worker.getBoundingBox().inflate(1));
        check(converted.size()==1,"Expected exactly one infected worker, found "+converted.size());
        return converted.get(0);
    }

    private static void cure(Fixture f,ZombieVillager zombie){
        zombie.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,200));
        f.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.GOLDEN_APPLE,2));
        zombie.mobInteract(f.player,InteractionHand.MAIN_HAND);
        check(zombie.isConverting()&&f.player.getMainHandItem().getCount()==1,"Cure accepted exactly one golden apple");
        var tag=zombie.saveWithoutId(new CompoundTag());
        tag.putInt("ConversionTime",1);
        zombie.readAdditionalSaveData(tag);
        zombie.tick();
    }

    @GameTest(template="empty",batch="worker_conversion")
    public static void vanilla_conversion_preserves_every_civilian_profession(GameTestHelper h){
        for(var profession:Profession.values()){
            if(profession==Profession.SOLDIER)continue; // The specialized soldier conversion has its own GameTests.
            var f=fixture(h,profession,0);
            var id=f.worker.getUUID();
            var workforce=WorkforceData.get(f.level);
            workforce.setProgress(id,profession.id(),4,89);
            if(profession==Profession.FARMER)
                check(IndustrialRules.selectSize(f.level,f.station,11),"Level 4 selects 11×11");
            f.station.setItem(0,new ItemStack(Items.BREAD,7));
            var zombie=infect(f);
            check(id.equals(f.station.worker)&&f.level.getEntity(id)==null,"Infection preserves assignment for "+profession);
            var saved=EmploymentData.load(EmploymentData.get(f.level).save(new CompoundTag()));
            check(saved.awaitingCure(id),"Infection maps to original identity in SavedData");
            var duplicate=EntityType.VILLAGER.create(f.level);
            duplicate.setUUID(id);
            duplicate.setPos(f.worker.position());
            check(!f.level.addFreshEntity(duplicate),"Duplicate resident identity was accepted");
            var zombieSave=zombie.saveWithoutId(new CompoundTag());
            zombie.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            var loaded=EntityType.ZOMBIE_VILLAGER.create(f.level);
            loaded.load(zombieSave);
            check(f.level.addFreshEntity(loaded),"Zombie chunk reload");
            cure(f,loaded);
            check(f.level.getEntity(id) instanceof Villager&&f.station.worker.equals(id),"Cure restores original worker for "+profession);
            check(ResidenceData.get(f.level).owner(id)!=null&&f.station.getItem(0).getCount()==7,"Cure preserved residence and inventory");
            var progress=workforce.progress(id,profession.id());
            check(progress.level()==4&&progress.xp()==89,"Cure preserved level and partial XP for "+profession);
            check(!EmploymentData.get(f.level).awaitingCure(id),"Cure consumed the reservation");
            if(profession==Profession.FARMER)
                check(IndustrialRules.size(f.level,f.station)==11,"Cure preserved farmer area");
            f.level.getEntity(id).discard();
            f.level.removeBlock(f.station.pos,false);
            f.level.removeBlock(f.home,false);
        }
        h.succeed();
    }

    @GameTest(template="empty",batch="worker_conversion")
    public static void cure_does_not_displace_farmer_replacement(GameTestHelper h){
        var f=fixture(h,Profession.FARMER,0);
        var id=f.worker.getUUID();
        var workforce=WorkforceData.get(f.level);
        workforce.setProgress(id,Profession.FARMER.id(),5,103);
        check(IndustrialRules.selectSize(f.level,f.station,13),"Previous farmer has 13×13");
        var zombie=infect(f);
        check(Employment.dismiss(f.player,f.station,Employment.epoch(f.station)),"Dismiss infected worker");
        var replacement=resident(f.level,f.home);
        check("ok".equals(Employment.hire(f.player,f.station,replacement.getUUID(),Profession.FARMER)),"Hire replacement");
        check(IndustrialRules.size(f.level,f.station)==5&&!IndustrialRules.selectSize(f.level,f.station,7),"Level 1 replacement starts at 5×5");
        cure(f,zombie);
        check(f.level.getEntity(id) instanceof Villager&&ResidenceData.get(f.level).owner(id)!=null,"Cure restores residence");
        check(replacement.getUUID().equals(f.station.worker)&&EmploymentData.get(f.level).assigned(id)==null,"Cure must not displace replacement");
        check(workforce.progress(id,Profession.FARMER.id()).xp()==103,"Detached cure preserved XP");
        h.succeed();
    }

    @GameTest(template="empty",batch="worker_conversion")
    public static void dead_farmer_replacement_starts_small_and_progress_only_unlocks(GameTestHelper h){
        var f=fixture(h,Profession.FARMER,0);
        var workforce=WorkforceData.get(f.level);
        workforce.setProgress(f.worker.getUUID(),Profession.FARMER.id(),3,59);
        check(IndustrialRules.selectSize(f.level,f.station,9),"Previous farmer uses 9×9");
        f.worker.hurt(DamageSource.GENERIC,1000);
        var replacement=resident(f.level,f.home);
        check("ok".equals(Employment.hire(f.player,f.station,replacement.getUUID(),Profession.FARMER)),"Hire after death");
        check(IndustrialRules.size(f.level,f.station)==5,"Replacement area starts at 5×5");
        workforce.setProgress(replacement.getUUID(),Profession.FARMER.id(),1,24);
        IndustrialRules.progress(f.level,f.station);
        check(IndustrialRules.level(f.level,f.station)==2&&IndustrialRules.size(f.level,f.station)==5,"Leveling does not increase selected area");
        check(IndustrialRules.selectSize(f.level,f.station,7),"Level 2 unlocks 7×7");
        workforce.setProgress(replacement.getUUID(),Profession.FARMER.id(),5,100);
        check(IndustrialRules.selectSize(f.level,f.station,5)&&IndustrialRules.size(f.level,f.station)==5,"Level 5 can keep 5×5");
        h.succeed();
    }

    @GameTest(template="empty",batch="worker_conversion")
    public static void destroyed_post_leaves_cured_farmer_history_available_for_hiring(GameTestHelper h){
        var f=fixture(h,Profession.FARMER,0);
        var id=f.worker.getUUID();
        var workforce=WorkforceData.get(f.level);
        workforce.setProgress(id,Profession.FARMER.id(),5,122);
        check(IndustrialRules.selectSize(f.level,f.station,13),"Previous farmer area");
        var zombie=infect(f);
        f.level.removeBlock(f.station.pos,false);
        cure(f,zombie);
        check(EmploymentData.get(f.level).assigned(id)==null&&f.level.getEntity(id) instanceof Villager,
            "Destroyed post must not regain a contract");
        check(ResidenceData.get(f.level).owner(id)!=null&&workforce.progress(id,Profession.FARMER.id()).xp()==122,
            "Cure preserves history and existing home");
        var post=f.station.pos;
        f.level.setBlockAndUpdate(post,WorkModule.INDUSTRIAL.get().defaultBlockState());
        var newStation=((WorkBlockEntity)f.level.getBlockEntity(post)).station();
        newStation.setItem(31,new ItemStack(Items.EMERALD,64));
        check("ok".equals(Employment.hire(f.player,newStation,id,Profession.FARMER)),"Rehire worker at a new station");
        check(IndustrialRules.size(f.level,newStation)==5&&IndustrialRules.level(f.level,newStation)==5,
            "A newly hired experienced worker starts with 5×5");
        h.succeed();
    }

    @GameTest(template="empty",batch="worker_conversion")
    public static void permanent_zombie_loss_does_not_transfer_identity_or_inventory(GameTestHelper h){
        var f=fixture(h,Profession.FARMER,0);
        var id=f.worker.getUUID();
        var zombie=infect(f);
        f.station.setItem(0,new ItemStack(Items.BREAD,9));
        var unrelated=EntityType.ZOMBIE_VILLAGER.create(f.level);
        unrelated.setPos(zombie.position());
        unrelated.setNoAi(true);
        f.level.addFreshEntity(unrelated);
        cure(f,unrelated);
        check(f.level.getEntity(id)==null&&id.equals(f.station.worker),"Another zombie cannot claim the reserved worker");
        zombie.discard();
        check(!EmploymentData.get(f.level).awaitingCure(id)&&f.station.worker==null
            &&ResidenceData.get(f.level).owner(id)==null&&f.station.getItem(0).getCount()==9,
            "Losing the zombie releases links and keeps the station inventory");
        h.succeed();
    }
}
