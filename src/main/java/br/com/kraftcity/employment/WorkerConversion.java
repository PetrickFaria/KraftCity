package br.com.kraftcity.employment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;

/** Keeps the authoritative records under the original resident UUID during vanilla infection. */
public final class WorkerConversion {
    private static final ThreadLocal<Entity> CURING=new ThreadLocal<>();
    private record Death(MinecraftServer server,Runnable cleanup) {}
    private static final Map<Villager,Death> PENDING_DEATHS=new LinkedHashMap<>();

    public static boolean mayInfect(Villager worker,DamageSource source){
        return worker.level instanceof ServerLevel level&&Employment.active(worker)
            &&!br.com.kraftcity.military.Military.isSoldier(worker)
            &&(source.getEntity() instanceof Zombie||worker.getKillCredit() instanceof Zombie);
    }

    public static void deferDeath(Villager worker,ServerLevel level,Runnable cleanup){
        PENDING_DEATHS.put(worker,new Death(level.getServer(),cleanup));
    }

    static void finishDeaths(MinecraftServer server){
        var pending=new ArrayList<Runnable>();
        PENDING_DEATHS.entrySet().removeIf(entry->{
            if(entry.getValue().server!=server)return false;
            var worker=entry.getKey();
            if(!worker.isAlive())pending.add(entry.getValue().cleanup);
            return true;
        });
        pending.forEach(Runnable::run);
    }

    public static boolean infected(ServerLevel level,Villager worker,ZombieVillager zombie){
        var data=EmploymentData.get(level);
        if(data.assigned(worker.getUUID())==null||level.getEntity(zombie.getUUID())!=zombie
            ||(!worker.getUUID().equals(data.infectedWorker(zombie.getUUID()))
                &&!data.rememberInfection(zombie.getUUID(),worker.getUUID())))return false;
        PENDING_DEATHS.remove(worker);
        zombie.setPersistenceRequired();
        return true;
    }

    /** Called before the vanilla converted entity is indexed in the level. */
    public static boolean addOutcome(Level level,Mob source,Entity outcome){
        if(level instanceof ServerLevel server&&source instanceof Villager worker&&outcome instanceof ZombieVillager zombie){
            boolean added=level.addFreshEntity(outcome);
            if(added)infected(server,worker,zombie);
            return added;
        }
        if(!(level instanceof ServerLevel server)||!(source instanceof ZombieVillager)||!(outcome instanceof Villager))
            return level.addFreshEntity(outcome);
        var data=EmploymentData.get(server);
        UUID original=data.infectedWorker(source.getUUID());
        if(original==null)return level.addFreshEntity(outcome);
        for(var world:server.getServer().getAllLevels())if(world.getEntity(original)!=null)
            return level.addFreshEntity(outcome);
        outcome.setUUID(original);
        Entity prior=CURING.get();
        CURING.set(outcome);
        try{
            if(!level.addFreshEntity(outcome))return false;
            data.forgetInfection(source.getUUID());
            return true;
        }finally{
            if(prior==null)CURING.remove();
            else CURING.set(prior);
        }
    }

    public static boolean blockedIdentity(ServerLevel level,Villager villager){
        return CURING.get()!=villager&&EmploymentData.get(level).awaitingCure(villager.getUUID());
    }

    static void loseInfectedWorker(ServerLevel level,UUID zombie){
        var original=EmploymentData.get(level).forgetInfection(zombie);
        if(original==null)return;
        br.com.residenciasaldeoes.data.ResidenceData.get(level).unlink(level,original);
    }

    private WorkerConversion(){}
}
