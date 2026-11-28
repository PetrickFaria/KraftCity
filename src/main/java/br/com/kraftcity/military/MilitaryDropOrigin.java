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
    private static final String HOSTILE = "KraftCityHostileDrop";
    private static final String PERSONAL = "KraftCityPlayerToss";
    private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> COMMON =
            net.minecraft.tags.TagKey.create(net.minecraft.core.Registry.ITEM_REGISTRY,
                    new net.minecraft.resources.ResourceLocation("kraftcity", "military_common_drops"));
    private static final String ORIGIN = "KraftCityMilitaryDropWorker";

    static void record(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Mob) || !(event.getEntity().level instanceof ServerLevel level)) return;
        boolean hostile = event.getEntity() instanceof net.minecraft.world.entity.monster.Enemy;
        Villager soldier = event.getSource().getEntity() instanceof Villager v && Military.isSoldier(v) ? v : null;
        var station = soldier == null ? null : Military.station(soldier);
        for (var drop : event.getDrops()) {
            if (station != null && within(station, event.getEntity().position()))
                drop.getPersistentData().putUUID(ORIGIN, soldier.getUUID());
            else if (hostile) drop.getPersistentData().putBoolean(HOSTILE, true);
        }
    }

    static void playerToss(ItemEntity item) {
        item.getPersistentData().putBoolean(PERSONAL, true);
    }

    private static UUID worker(ItemEntity item) {
        var data = item.getPersistentData();
        return data.hasUUID(ORIGIN) ? data.getUUID(ORIGIN) : null;
    }

    static boolean belongsTo(ItemEntity item, Villager soldier) {
        if (item.getOwner() != null || item.getPersistentData().getBoolean(PERSONAL)) return false;
        UUID owner = worker(item);
        return owner != null ? owner.equals(soldier.getUUID())
                : item.getPersistentData().getBoolean(HOSTILE)
                || (!item.getItem().isDamageableItem() && !item.getItem().hasTag() && item.getItem().is(COMMON));
    }

    public static boolean same(ItemEntity a, ItemEntity b) {
        return Objects.equals(worker(a), worker(b))
                && a.getPersistentData().getBoolean(HOSTILE) == b.getPersistentData().getBoolean(HOSTILE)
                && a.getPersistentData().getBoolean(PERSONAL) == b.getPersistentData().getBoolean(PERSONAL);
    }

    public static boolean reserved(ServerLevel level, ItemEntity item) {
        UUID worker = worker(item);
        if (worker == null || item.getOwner() != null || item.getPersistentData().getBoolean(PERSONAL)) return false;
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
