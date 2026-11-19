package br.com.kraftcity.employment;

import br.com.kraftcity.workforce.*;
import br.com.residenciasaldeoes.data.ResidenceData;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/** All mutations run on the server thread; no client price/identity is trusted. */
public final class Employment {
    static final ThreadLocal<Boolean> CONVERTING=ThreadLocal.withInitial(()->false);
    private Employment(){}
    public static boolean eligible(ServerLevel level,Villager v){
        var home=ResidenceData.get(level).owner(v.getUUID());
        return v.isAlive()&&!v.isBaby()&&v.level==level&&home!=null&&home.dimension.equals(level.dimension())
                &&(!level.hasChunkAt(home.pos)||level.getBlockEntity(home.pos) instanceof br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity)
                &&v.getVillagerData().getProfession()==VillagerProfession.NONE
                &&WorkforceData.get(level).worker(v.getUUID()).map(w->w.inventory().isEmpty()).orElse(true)
                &&EmploymentData.get(level).assigned(v.getUUID())==null;
    }
    public static List<Villager> candidates(ServerLevel level){
        var out=new ArrayList<Villager>();for(var home:ResidenceData.get(level).homes())
            if(home.dimension.equals(level.dimension()))for(UUID id:home.residents)
                if(level.getEntity(id) instanceof Villager v&&eligible(level,v))out.add(v);
        out.sort(Comparator.comparing(v->v.getUUID().toString()));return out;
    }
    public static boolean active(Villager v){
        return v.level instanceof ServerLevel level&&EmploymentData.get(level).assigned(v.getUUID())!=null;
    }
    private static final Map<Villager,Long> FLEE_UNTIL=new WeakHashMap<>();

    public static boolean fleeing(Villager v){
        return FLEE_UNTIL.getOrDefault(v,Long.MIN_VALUE)>v.level.getGameTime();
    }

    static void flee(ServerLevel level,Villager v){
        if(v.tickCount%5!=0||v.isNoAi()||!active(v)||br.com.kraftcity.military.Military.isSoldier(v))return;
        var zombie=level.getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class,
            v.getBoundingBox().inflate(8),z->z.isAlive()&&!br.com.kraftcity.military.Military.isSoldier(z)
                &&(z.getTarget()==v||v.hasLineOfSight(z))).stream()
            .min(Comparator.comparingDouble(v::distanceToSqr)).orElse(null);
        if(zombie==null){
            if(!fleeing(v)&&FLEE_UNTIL.remove(v)!=null){
                v.getNavigation().stop();
                v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                v.getBrain().eraseMemory(MemoryModuleType.PATH);
            }
            return;
        }
        boolean starting=!fleeing(v);
        if(starting)com.mojang.logging.LogUtils.getLogger().debug("Worker {} at {} fleeing zombie at {}",
            v.getUUID(),v.position(),zombie.position());
        FLEE_UNTIL.put(v,level.getGameTime()+40);
        if(starting||v.getNavigation().isDone()||v.tickCount%15==0){
            if(v.isSleeping())v.stopSleeping();
            v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            v.getBrain().eraseMemory(MemoryModuleType.PATH);
            var away=net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(v,12,5,zombie.position());
            if(away!=null)v.getNavigation().moveTo(away.x,away.y,away.z,1.25);
            else if(starting)v.getNavigation().stop();
        }
    }

    public static void makePortable(ServerLevel level,EmploymentData.Station s){
        if(!s.active)return;
        if(s.worker!=null)release(level,s.worker);
        s.active=false;s.setChanged();
        var block=s.profession!=null?WorkModule.PROFESSIONAL.get(s.profession).get():WorkModule.generic(s.sector);
        ItemStack token=new ItemStack(block);token.getOrCreateTag().putUUID("KraftCityStation",s.id);
        ItemEntity item=new ItemEntity(level,s.pos.getX()+.5,s.pos.getY()+.5,s.pos.getZ()+.5,token);
        item.setInvulnerable(true);item.setUnlimitedLifetime();level.addFreshEntity(item);
    }
    public static void reconcile(ServerLevel origin,EmploymentData.Station s){
        if(!s.active)return;
        var level=origin.getServer().getLevel(s.dimension);if(level==null||!level.hasChunkAt(s.pos))return;
        if(level.getBlockEntity(s.pos) instanceof WorkBlockEntity existing&&existing.identity()==null
                &&existing.getBlockState().getBlock() instanceof WorkBlock block&&block.sector==s.sector&&block.profession==s.profession)existing.bind(s.id);
        if(!(level.getBlockEntity(s.pos) instanceof WorkBlockEntity be)||!s.id.equals(be.identity()))makePortable(level,s);
        else if(s.worker!=null)valid(level,s.worker);
    }
    public static void release(ServerLevel level,UUID worker){
        br.com.kraftcity.builder.BuilderWork.release(level,worker);
        br.com.kraftcity.military.Military.release(level,worker);
        var data=EmploymentData.get(level);
        for(ServerLevel world:level.getServer().getAllLevels())if(world.getEntity(worker) instanceof Villager v)br.com.kraftcity.industry.IndustrialWork.restoreHand(v);
        for(var s:data.stations())if(worker.equals(s.worker)){s.worker=null;if(s.sector==Profession.Sector.COMMERCIAL)s.commerce.putLong("epoch",s.commerce.getLong("epoch")+1);s.setChanged();}
        var workforce=WorkforceData.get(level);var old=workforce.worker(worker).orElse(null);
        if(old!=null&&old.workstation().isPresent()){
            old.profession().ifPresent(p->{var progress=workforce.progress(worker,p);workforce.setProgress(worker,p,old.level(),progress.xp());});
            workforce.put(new WorkerRecord(worker,old.profession(),old.level(),Optional.empty(),old.area(),old.inventory()));
        }
    }
    public static boolean valid(ServerLevel level,UUID worker){
        var station=EmploymentData.get(level).assigned(worker);if(station==null)return false;
        var home=ResidenceData.get(level).owner(worker);
        if(home==null||!home.dimension.equals(station.dimension)){release(level,worker);return false;}
        var housing=level.getServer().getLevel(home.dimension);
        if(housing==null||(housing.hasChunkAt(home.pos)&&!(housing.getBlockEntity(home.pos) instanceof br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity))){release(level,worker);return false;}
        ServerLevel target=level.getServer().getLevel(station.dimension);if(target==null){release(level,worker);return false;}
        if(target.hasChunkAt(station.pos)){
            if(!(target.getBlockEntity(station.pos) instanceof WorkBlockEntity be)||!station.id.equals(be.identity())){release(level,worker);return false;}
        }
        for(ServerLevel world:level.getServer().getAllLevels())if(world.getEntity(worker) instanceof Villager v){
            if(!v.isAlive()||!world.dimension().equals(station.dimension)){release(level,worker);return false;}
        }
        return true;
    }
    public static boolean indicator(ServerLevel level,UUID worker,boolean legacy){
        // Preserve every legacy indicator for villagers never participating in the new system.
        return WorkforceData.get(level).worker(worker).filter(w->w.profession().isPresent()).isPresent()?valid(level,worker):legacy;
    }
    public static String hire(ServerPlayer player,EmploymentData.Station s,UUID worker,Profession profession){
        ServerLevel level=player.getLevel();
        if(!level.getServer().isSameThread())throw new IllegalStateException("Hiring off server thread");
        if(s.military.getBoolean("zombie"))return "ineligible";
        if(!s.at(level,s.pos)||s.worker!=null||(s.profession!=null&&s.profession!=profession)||profession==null||profession.sector!=s.sector)return "invalid";
        if(!(level.getBlockEntity(s.pos) instanceof WorkBlockEntity be)||!s.id.equals(be.identity())
                ||player.distanceToSqr(s.pos.getX()+.5,s.pos.getY()+.5,s.pos.getZ()+.5)>64)return "invalid";
        if(!(level.getEntity(worker) instanceof Villager villager)||!eligible(level,villager))return "ineligible";
        int price=EmploymentConfig.cost(s.sector);if(price<0)return "pending";
        var payment=s.getItem(EmploymentData.PAYMENT);
        if(price>0&&(!payment.is(Items.EMERALD)||payment.getCount()<price))return "payment";
        // Physical replacement first. A failed block operation never consumes payment.
        if(s.profession==null){
            CONVERTING.set(true);
            try { if(!level.setBlock(s.pos,WorkModule.PROFESSIONAL.get(profession).get().defaultBlockState().setValue(WorkBlock.FACING,level.getBlockState(s.pos).getValue(WorkBlock.FACING)),3))return "invalid"; }
            finally { CONVERTING.remove(); }
        }
        if(!(level.getBlockEntity(s.pos) instanceof WorkBlockEntity converted))throw new IllegalStateException("Missing professional block entity");
        converted.bind(s.id);s.profession=profession;s.worker=worker;s.setChanged();
        var workforce=WorkforceData.get(level);var progress=workforce.progress(worker,profession.id());
        var previous=workforce.worker(worker).orElse(null);
        workforce.setProgress(worker,profession.id(),progress.level(),progress.xp());
        workforce.put(new WorkerRecord(worker,Optional.of(profession.id()),progress.level(),
                Optional.of(new Workstation(profession.id(),level.dimension(),s.pos)),previous==null?Optional.empty():previous.area(),List.of()));
        // The custom block is the workstation. Never assign a conflicting vanilla career/POI.
        villager.releasePoi(MemoryModuleType.JOB_SITE);villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
        villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
        if(profession==Profession.BUILDER)workforce.setProgress(worker,profession.id(),br.com.kraftcity.builder.BuilderWork.level(progress.xp()),progress.xp());
        payment.shrink(price);s.setChanged();if(profession==Profession.SOLDIER)br.com.kraftcity.military.Military.trained(level,s,villager,player);return "ok";
    }
}
