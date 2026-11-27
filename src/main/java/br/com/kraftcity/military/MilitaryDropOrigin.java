package br.com.kraftcity.military;

import br.com.kraftcity.employment.EmploymentData;
import br.com.kraftcity.employment.WorkBlockEntity;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

public final class MilitaryDropOrigin {
    private static final String ORIGIN = "KraftCityMilitaryDropWorker";

    static void record(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Mob) || !(event.getEntity().level instanceof ServerLevel level)) return;
        if (!(event.getSource().getEntity() instanceof Villager soldier) || !Military.isSoldier(soldier)) return;
        var station = Military.station(soldier);
        if (station == null || !within(station, event.getEntity().position())) return;
        for (var drop : event.getDrops()) {
            drop.getPersistentData().putUUID(ORIGIN, soldier.getUUID());
        }
    }

    private static UUID worker(ItemEntity item) {
        var data = item.getPersistentData();
        return data.hasUUID(ORIGIN) ? data.getUUID(ORIGIN) : null;
    }

    static boolean belongsTo(ItemEntity item, Villager soldier) {
        return item.getOwner() == null && soldier.getUUID().equals(worker(item));
    }

    public static boolean same(ItemEntity a, ItemEntity b) {
        return Objects.equals(worker(a), worker(b));
    }

    public static boolean reserved(ServerLevel level, ItemEntity item) {
        UUID worker = worker(item);
        if (worker == null) return false;
        var station = EmploymentData.get(level).assigned(worker);
        return Military.military(station) && station.active && station.dimension.equals(level.dimension())
                && level.hasChunkAt(station.pos) && level.getBlockEntity(station.pos) instanceof WorkBlockEntity post
                && station.id.equals(post.identity()) && within(station, item.position());
    }

    private static boolean within(EmploymentData.Station station, Vec3 position) {
        return Military.insideArea(station, position)
                && (Military.mode(station) != Military.Mode.GUARD || Vec3.atCenterOf(station.pos).distanceToSqr(position) <= 625);
    }
}
