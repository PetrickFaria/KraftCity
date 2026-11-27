package br.com.kraftcity.industry;

import br.com.kraftcity.employment.EmploymentData;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

final class LumberjackCanopy {
    private static final int MAX_LEAVES = 4096;
    private static final int SUPPORT_DISTANCE = 6;

    private record Cell(BlockPos pos, int distance) {}

    static long[] snapshot(ServerLevel level, List<LumberjackTrees.Log> logs) {
        var queue = new ArrayDeque<Cell>();
        var visited = new HashSet<BlockPos>();
        var leaves = new java.util.ArrayList<BlockPos>();
        for (var log : logs) {
            queue.add(new Cell(log.pos(), 0));
            visited.add(log.pos());
        }
        while (!queue.isEmpty()) {
            var cell = queue.remove();
            if (cell.distance >= SUPPORT_DISTANCE) continue;
            for (var direction : Direction.values()) {
                var pos = cell.pos.relative(direction);
                if (!visited.add(pos) || !LumberjackTrees.loaded(level, pos)) continue;
                if (!natural(level.getBlockState(pos))) continue;
                if (leaves.size() >= MAX_LEAVES) return new long[0];
                leaves.add(pos);
                queue.add(new Cell(pos, cell.distance + 1));
            }
        }
        return leaves.stream().mapToLong(BlockPos::asLong).toArray();
    }

    static void finish(ServerLevel level, EmploymentData.Station station, Villager worker, long[] leaves) {
        var unsupported = new java.util.ArrayList<BlockPos>();
        for (long leaf : leaves) {
            var pos = BlockPos.of(leaf);
            if (LumberjackTrees.loaded(level, pos) && natural(level.getBlockState(pos))
                    && !LumberjackTrees.placed(level, pos) && !hasOtherSupport(level, pos)) {
                unsupported.add(pos);
            }
        }
        var actor = IndustrialWork.actor(level, station, worker);
        for (var pos : unsupported) {
            var state = level.getBlockState(pos);
            if (natural(state) && level.mayInteract(actor, pos)
                    && IndustrialWork.breakAllowed(level, station, worker, pos)
                    && level.getBlockState(pos) == state && !LumberjackTrees.placed(level, pos)) {
                var drops = level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)
                        ? Block.getDrops(state, level, pos, level.getBlockEntity(pos), worker, ItemStack.EMPTY)
                        : List.<ItemStack>of();
                if (!level.destroyBlock(pos, false, worker)) continue;
                for (var stack : drops) {
                    if (stack.isEmpty()) continue;
                    var drop = new ItemEntity(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, stack);
                    drop.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    drop.setDefaultPickUpDelay();
                    drop.getPersistentData().putUUID(FarmerLogistics.HARVEST_STATION, station.id);
                    level.addFreshEntity(drop);
                }
            }
        }
    }

    private static boolean hasOtherSupport(ServerLevel level, BlockPos leaf) {
        var queue = new ArrayDeque<Cell>();
        var visited = new HashSet<BlockPos>();
        queue.add(new Cell(leaf, 0));
        visited.add(leaf);
        while (!queue.isEmpty()) {
            var cell = queue.remove();
            if (cell.distance >= SUPPORT_DISTANCE) continue;
            for (var direction : Direction.values()) {
                var pos = cell.pos.relative(direction);
                if (!visited.add(pos)) continue;
                if (!LumberjackTrees.loaded(level, pos)) return true;
                var state = level.getBlockState(pos);
                if (state.is(BlockTags.LOGS)) return true;
                if (state.is(BlockTags.LEAVES)) queue.add(new Cell(pos, cell.distance + 1));
            }
        }
        return false;
    }

    private static boolean natural(BlockState state) {
        return state.getBlock() instanceof LeavesBlock && state.hasProperty(LeavesBlock.PERSISTENT)
                && !state.getValue(LeavesBlock.PERSISTENT);
    }
}
