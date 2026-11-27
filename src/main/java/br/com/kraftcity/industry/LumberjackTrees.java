package br.com.kraftcity.industry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.BlockEvent;

/** Conservative recognition of a rooted trunk with a naturally supported crown. */
final class LumberjackTrees extends SavedData {
    private static final int HEIGHT = 32;
    private static final int RADIUS = 4;
    private static final int MAX_LOGS = 160;
    private final Set<Long> constructed = new HashSet<>();

    record Log(BlockPos pos, CompoundTag state) {}
    record Tree(BlockPos base, List<Log> logs) {}

    private static LumberjackTrees read(CompoundTag nbt) {
        var result = new LumberjackTrees();
        for (long pos : nbt.getLongArray("constructed")) result.constructed.add(pos);
        return result;
    }

    private static LumberjackTrees get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(LumberjackTrees::read, LumberjackTrees::new, "kraftcity_placed_logs");
    }

    @Override public CompoundTag save(CompoundTag nbt) {
        nbt.putLongArray("constructed", constructed.stream().mapToLong(Long::longValue).toArray());
        return nbt;
    }

    static void placed(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.isCanceled()) return;
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiple) {
            var snapshots = multiple.getReplacedBlockSnapshots();
            // Forge wraps player use of bone meal in the same event as construction.
            if (event.getEntity() instanceof Player player
                    && (player.getMainHandItem().is(Items.BONE_MEAL) || player.getOffhandItem().is(Items.BONE_MEAL))
                    && snapshots.stream().anyMatch(snapshot ->
                        snapshot.getReplacedBlock().getBlock() instanceof SaplingBlock
                            && level.getBlockState(snapshot.getPos()).is(BlockTags.LOGS))) {
                var data = get(level);
                var visited = new HashSet<Long>();
                for (var snapshot : snapshots) {
                    var pos = snapshot.getPos();
                    // Only a confirmed replacement of a non-log can retire an old record.
                    if (visited.add(pos.asLong()) && !snapshot.getReplacedBlock().is(BlockTags.LOGS)
                            && level.getBlockState(pos).is(BlockTags.LOGS)
                            && data.constructed.remove(pos.asLong())) data.setDirty();
                }
                return;
            }
            for (var snapshot : snapshots) mark(level, snapshot.getPos(), level.getBlockState(snapshot.getPos()));
        } else {
            mark(level, event.getPos(), event.getPlacedBlock());
        }
    }

    private static void mark(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.is(BlockTags.LOGS)) return;
        var data = get(level);
        if (data.constructed.add(pos.asLong())) data.setDirty();
    }

    static boolean placed(ServerLevel level, BlockPos pos) {
        return get(level).constructed.contains(pos.asLong());
    }

    static Tree identify(ServerLevel level, BlockPos base, AABB area, BlockPos workPost) {
        if (!area.contains(Vec3.atBottomCenterOf(base)) || !loaded(level, base)) return null;
        var trunk = level.getBlockState(base);
        if (!trunk.is(BlockTags.LOGS) || !grounded(level, base, trunk)) return null;
        if (placed(level, base)) return rejected(base, "player-placed root", base);
        if (!loaded(level, base.above(2)) || !level.getBlockState(base.above()).is(trunk.getBlock())
                || !level.getBlockState(base.above(2)).is(trunk.getBlock())) return rejected(base, "short trunk", base);

        var pending = new ArrayDeque<BlockPos>();
        var seen = new HashSet<BlockPos>();
        var roots = new ArrayList<BlockPos>();
        var leaves = new HashSet<BlockPos>();
        var logs = new ArrayList<Log>();
        pending.add(base);
        seen.add(base);
        while (!pending.isEmpty()) {
            BlockPos pos = pending.remove();
            if (logs.size() >= MAX_LOGS || !loaded(level, pos) || placed(level, pos)
                    || !level.getWorldBorder().isWithinBounds(pos)) return rejected(base, "unsafe or player-placed log", pos);
            if (pos.getY() == base.getY()) {
                if (!grounded(level, pos, trunk) || !area.contains(Vec3.atBottomCenterOf(pos))) return rejected(base, "invalid root", pos);
                roots.add(pos);
            }
            logs.add(new Log(pos, NbtUtils.writeBlockState(level.getBlockState(pos))));
            for (var side : Direction.values()) {
                BlockPos neighbor = pos.relative(side);
                if (!loaded(level, neighbor)) return null;
                var state = level.getBlockState(neighbor);
                if (state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)
                        && state.getValue(LeavesBlock.DISTANCE) <= 6 && pos.getY() >= base.getY() + 2) leaves.add(neighbor);
                if (state.is(BlockTags.LOGS) && !state.is(trunk.getBlock())) return rejected(base, "touching other log species", neighbor);
                // A sapling can grow alongside the assigned work block at ground level.
                boolean besidePost = pos.getY() == base.getY() && neighbor.equals(workPost);
                // A rooted tree can share its lowest layer with exposed terrain.
                boolean terrainAtRoot = pos.getY() == base.getY() && side.getAxis().isHorizontal()
                        && state.is(BlockTags.BASE_STONE_OVERWORLD);
                if (!besidePost && !terrainAtRoot && state.isCollisionShapeFullBlock(level, neighbor) && !state.is(BlockTags.LOGS)
                        && !state.is(BlockTags.DIRT) && !state.is(Blocks.MANGROVE_ROOTS)
                        && !state.is(Blocks.MUDDY_MANGROVE_ROOTS) && !state.is(BlockTags.LEAVES)) return rejected(base, "adjoining solid block", neighbor);
            }
            // Vanilla branches can touch the leader at an edge or a corner.
            for (BlockPos cell : BlockPos.betweenClosed(pos.offset(-1,-1,-1), pos.offset(1,1,1))) {
                if (!loaded(level, cell)) return null;
                if (!level.getBlockState(cell).is(trunk.getBlock()) || seen.contains(cell)) continue;
                if (cell.getY() < base.getY() || cell.getY() - base.getY() >= HEIGHT
                        || Math.abs(cell.getX() - base.getX()) > RADIUS
                        || Math.abs(cell.getZ() - base.getZ()) > RADIUS) return rejected(base, "trunk crossed tree envelope", cell);
                BlockPos next = cell.immutable();
                seen.add(next);
                pending.add(next);
            }
        }
        if (leaves.size() < 5) return rejected(base, "insufficient natural foliage", base);
        if (!validRoots(roots)) return rejected(base, "multiple unrelated roots", base);
        logs.sort(Comparator.comparingInt((Log log) -> log.pos().getY())
                .thenComparingInt(log -> log.pos().getX()).thenComparingInt(log -> log.pos().getZ()));
        return new Tree(base.immutable(), List.copyOf(logs));
    }

    private static Tree rejected(BlockPos base, String reason, BlockPos pos) {
        if (LumberjackWork.TRACE) LumberjackWork.LOGGER.info("Lumberjack tree {} rejected: {} at {}", base, reason, pos);
        return null;
    }

    private static boolean validRoots(List<BlockPos> roots) {
        if (roots.size() == 1) return true;
        if (roots.size() != 4) return false;
        int x = roots.stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int z = roots.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        return roots.stream().allMatch(root -> root.getX() <= x + 1 && root.getZ() <= z + 1);
    }

    static boolean rootSupport(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(Blocks.MUD) || state.is(Blocks.MANGROVE_ROOTS)
                || state.is(Blocks.MUDDY_MANGROVE_ROOTS);
    }

    private static boolean grounded(ServerLevel level, BlockPos pos, BlockState trunk) {
        BlockState floor = level.getBlockState(pos.below());
        return floor.is(BlockTags.DIRT) || floor.is(Blocks.MUD) || trunk.is(Blocks.MANGROVE_LOG)
                && (floor.is(Blocks.MANGROVE_ROOTS) || floor.is(Blocks.MUDDY_MANGROVE_ROOTS));
    }

    static boolean loaded(ServerLevel level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.hasChunkAt(pos);
    }
}
