package br.com.kraftcity.military;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/** A sighting records a position. Losing sight never advances that position or refreshes its deadline. */
final class MilitaryPursuit {
    private record Sighting(Vec3 position,long tick,boolean visible){}
    private static final Map<Villager,Map<UUID,Sighting>> SIGHTINGS=new WeakHashMap<>();

    static void observe(Villager soldier,LivingEntity target,boolean visible){
        SIGHTINGS.computeIfAbsent(soldier,v->new HashMap<>())
            .put(target.getUUID(),new Sighting(target.position(),soldier.level.getGameTime(),visible));
    }

    static boolean remembered(Villager soldier,LivingEntity target){
        var entry=SIGHTINGS.getOrDefault(soldier,Map.of()).get(target.getUUID());
        int search=MilitaryModule.SEARCH_TICKS.get();
        return entry!=null&&soldier.level.getGameTime()-entry.tick<(entry.visible?search:Math.max(400,search));
    }

    static Vec3 lastPosition(Villager soldier,LivingEntity target){
        return SIGHTINGS.get(soldier).get(target.getUUID()).position;
    }

    static boolean seenBefore(Villager soldier,LivingEntity target){
        return SIGHTINGS.getOrDefault(soldier,Map.of()).containsKey(target.getUUID());
    }

    static void prune(Villager soldier,net.minecraft.server.level.ServerLevel level){
        var memory=SIGHTINGS.get(soldier);
        if(memory!=null)memory.keySet().removeIf(id->!(level.getEntity(id) instanceof LivingEntity entity)||!entity.isAlive());
    }

    private MilitaryPursuit(){}
}
