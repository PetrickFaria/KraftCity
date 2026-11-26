package br.com.kraftcity.commerce;

import br.com.kraftcity.employment.EmploymentConfig;
import br.com.kraftcity.employment.EmploymentData;
import br.com.kraftcity.employment.Profession;
import br.com.kraftcity.employment.WorkBlockEntity;
import br.com.kraftcity.employment.WorkplaceVisits;
import br.com.kraftcity.industry.FarmerLogistics;
import br.com.kraftcity.industry.IndustrialIdle;
import br.com.kraftcity.industry.IndustrialWork;
import br.com.kraftcity.logistics.DiscardBinding;
import br.com.kraftcity.logistics.DiscardChests;
import java.util.Comparator;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.eventbus.api.Event;

public final class BakerWork {
    private static final String QUOTA = "KraftCityBakerQuota";
    private static final String BATCH = "baker_batch";
    private static final WeakHashMap<Villager, Source> ROUTE = new WeakHashMap<>();
    private static final String RETURN = "baker_return";
    private static final int RETRY_TICKS = 40;
    private static final WeakHashMap<Villager, Long> NEXT_SCAN = new WeakHashMap<>();
    private static final java.util.Set<Villager> TAKING = new java.util.HashSet<>();

    private BakerWork() {}

    public static int wheatLimit(int level) {
        return 15 * Math.max(1, Math.min(5, level));
    }

    private static long day(ServerLevel level) {
        // Commercial work starts again at tick 23000, ahead of the vanilla day boundary.
        return Math.floorDiv(level.getDayTime(), 24000) + (Math.floorMod(level.getDayTime(), 24000) >= 23000 ? 1 : 0);
    }

    private static CompoundTag quota(ServerLevel level, Villager worker) {
        var saved = worker.getPersistentData().getCompound(QUOTA);
        return saved.contains("day") && saved.getLong("day") >= day(level) ? saved : new CompoundTag();
    }

    public static int collected(ServerLevel level, Villager worker) {
        return quota(level, worker).getInt("wheat");
    }

    private static void increment(ServerLevel level, Villager worker, String key, int count) {
        var data = quota(level, worker);
        data.putLong("day", Math.max(day(level), data.contains("day") ? data.getLong("day") : Long.MIN_VALUE));
        data.putInt(key, data.getInt(key) + count);
        worker.getPersistentData().put(QUOTA, data);
    }

    static void recordBread(ServerLevel level, Villager worker) {
        increment(level, worker, "bread", 1);
    }

    static boolean atPost(ServerLevel level, EmploymentData.Station station, Villager worker) {
        var feet = station.pos.above();
        if (!level.hasChunkAt(feet.above()) || !(level.getBlockEntity(station.pos) instanceof WorkBlockEntity post)
                || !station.id.equals(post.identity())
                || !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) return false;
        if (!worker.blockPosition().equals(feet) || Math.abs(worker.getY() - feet.getY()) > .125) {
            FarmerLogistics.approachCell(level, worker, feet, false);
            return false;
        }
        worker.getNavigation().stop();
        return true;
    }

    static boolean deposit(ServerLevel level, EmploymentData.Station station, Villager worker) {
        var offers = DiscardChests.offers(station, stack -> stack.is(Items.BREAD));
        if (!DiscardChests.hasOffers(offers)) return false;
        var target = DiscardBinding.boundPosition(level, station);
        if (target == null || !DiscardChests.accepts(level, station, worker, target, offers)) return false;
        IndustrialIdle.interrupt(worker);
        WorkplaceVisits.allowSupplyVisit(worker);
        int travel = FarmerLogistics.approachDiscard(level, worker, target);
        if (travel < 0) return false;
        CommercialWork.state(station, "Levando pão ao baú vinculado.");
        if (travel > 0) {
            int before = CommercialWork.count(station, Items.BREAD);
            DiscardChests.deposit(level, station, worker, target, offers, "commercial_deposit");
            if (CommercialWork.count(station, Items.BREAD) == before) return false;
            markReturn(station);
        }
        return true;
    }

    static boolean handleLogistics(ServerLevel level, EmploymentData.Station station, Villager worker) {
        if (station.commerce.getBoolean(RETURN)) {
            if (!atPost(level, station, worker)) {
                CommercialWork.state(station, "Voltando ao posto de padeiro.");
                return true;
            }
            station.commerce.remove(RETURN);
            station.setChanged();
            if (CommercialWork.count(station, Items.WHEAT) < 3) return true;
        }
        int limit = wheatLimit(CommercialWork.level(level, station));
        boolean canBake = quota(level, worker).getInt("bread") < limit / 3
                && CommercialWork.count(station, Items.WHEAT) >= 3;
        if (!canBake && station.commerce.contains(BATCH)) {
            station.commerce.remove(BATCH);
            station.setChanged();
        }
        if (!station.commerce.getBoolean(BATCH) && deposit(level, station, worker)) return true;
        if (canBake) {
            IndustrialIdle.interrupt(worker);
            if (!station.commerce.getBoolean(BATCH)) {
                station.commerce.putBoolean(BATCH, true);
                station.setChanged();
            }
            return false;
        }
        if (quota(level, worker).getInt("bread") >= limit / 3) {
            atPost(level, station, worker);
            CommercialWork.state(station, "Limite diário de pães atingido.");
            return true;
        }
        int remaining = limit - collected(level, worker);
        if (remaining <= 0) {
            atPost(level, station, worker);
            CommercialWork.state(station, "Cota de coleta atingida.");
            return true;
        }
        var route = ROUTE.remove(worker);
        if (route != null) {
            if (posStillBound(level, station, route) && visit(level, station, worker, route)) return true;
            worker.getNavigation().stop();
        }
        long now = level.getGameTime();
        if (now >= NEXT_SCAN.getOrDefault(worker, Long.MIN_VALUE)) {
            var farmers = EmploymentData.get(level).stations().stream()
                    .filter(source -> source.profession == Profession.FARMER && source.dimension.equals(station.dimension))
                    .map(source -> new Source(source, DiscardBinding.boundPosition(level, source)))
                    .filter(source -> validSource(level, station, source))
                    .sorted(Comparator.<Source>comparingDouble(source -> source.pos.distSqr(station.pos))
                            .thenComparing(source -> source.pos)).toList();
            for (var source : farmers) {
                if (visit(level, station, worker, source)) return true;
            }
            NEXT_SCAN.put(worker, now + RETRY_TICKS);
        }
        IndustrialIdle.tick(level, station, worker);
        CommercialWork.state(station, "Aguardando trigo no descarte dos Fazendeiros.");
        return true;
    }

    private static boolean visit(ServerLevel level, EmploymentData.Station station, Villager worker, Source source) {
        var container = DiscardChests.at(level, source.pos);
        if (container == null || !container.hasAnyOf(java.util.Set.of(Items.WHEAT))
                || !DiscardChests.canOpen(level, source.pos, IndustrialWork.actor(level, station, worker))) return false;
        IndustrialIdle.interrupt(worker);
        WorkplaceVisits.allowSupplyVisit(worker);
        int travel = FarmerLogistics.approachDiscard(level, worker, source.pos);
        if (travel < 0) return false;
        CommercialWork.state(station, "Buscando trigo no descarte do Fazendeiro.");
        if (travel > 0) return take(level, station, worker, source);
        ROUTE.put(worker, source);
        return true;
    }

    private static boolean posStillBound(ServerLevel level, EmploymentData.Station station, Source source) {
        return validSource(level, station, source) && source.pos.equals(DiscardBinding.boundPosition(level, source.station));
    }

    private record Source(EmploymentData.Station station, BlockPos pos) {}

    private static boolean validSource(ServerLevel level, EmploymentData.Station baker, Source source) {
        var farmer = source.station;
        int range = EmploymentConfig.BAKER_SUPPLY_RANGE.get();
        return farmer.active && farmer.profession == Profession.FARMER && farmer.dimension.equals(baker.dimension)
                && source.pos != null && source.pos.distSqr(baker.pos) <= (double)range * range
                && level.hasChunkAt(source.pos) && level.hasChunkAt(farmer.pos)
                && level.getBlockEntity(farmer.pos) instanceof WorkBlockEntity post && farmer.id.equals(post.identity());
    }

    private static void markReturn(EmploymentData.Station station) {
        station.commerce.putBoolean(RETURN, true);
        station.setChanged();
    }

    private static boolean take(ServerLevel level, EmploymentData.Station station, Villager worker, Source source) {
        if (!TAKING.add(worker)) return false;
        try {
            var pos = source.pos;
            if (!IndustrialWork.allowed(level, station, worker, pos, null, "commercial_supply")) return false;
            var actor = IndustrialWork.actor(level, station, worker);
            if (!level.mayInteract(actor, pos)) return false;
            var permission = ForgeHooks.onRightClickBlock(actor, InteractionHand.MAIN_HAND, pos,
                    new BlockHitResult(Vec3.atCenterOf(pos), worker.getDirection().getOpposite(), pos, false));
            if (permission.isCanceled() || permission.getUseBlock() == Event.Result.DENY
                    || !CommercialWork.stillWorker(level, station, worker) || !IndustrialWork.day(level)
                    || !CommercialWork.ownsNavigation(worker)
                    || !posStillBound(level, station, source)
                    || !DiscardChests.canOpen(level, pos, actor)
                    || FarmerLogistics.approachDiscard(level, worker, pos) != 1) return false;
            var container = DiscardChests.at(level, pos);
            if (container == null) return false;
            int remaining = wheatLimit(CommercialWork.level(level, station)) - collected(level, worker);
            int moved = 0;
            for (int slot = 0; slot < container.getContainerSize() && moved < remaining; slot++) {
                var stack = container.getItem(slot);
                if (!stack.is(Items.WHEAT)) continue;
                var offered = stack.copy();
                offered.setCount(Math.min(stack.getCount(), remaining - moved));
                while (!offered.isEmpty() && !IndustrialWork.insert(station, offered, false)) offered.shrink(1);
                if (offered.isEmpty()) break;
                ItemStack original = stack.copy();
                ItemStack removed = container.removeItem(slot, offered.getCount());
                if (removed.isEmpty()) continue;
                if (!removed.is(Items.WHEAT) || removed.getCount() > offered.getCount()
                        || !IndustrialWork.insert(station, removed, true)) {
                    container.setItem(slot, original);
                    container.setChanged();
                    break;
                }
                moved += removed.getCount();
                increment(level, worker, "wheat", removed.getCount());
            }
            if (moved == 0) return false;
            container.setChanged();
            br.com.kraftcity.logistics.DiscardLids.afterApproval(level, level.getBlockEntity(pos));
            markReturn(station);
            return true;
        } finally {
            TAKING.remove(worker);
        }
    }
}
