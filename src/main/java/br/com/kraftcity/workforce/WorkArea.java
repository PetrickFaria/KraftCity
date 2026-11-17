package br.com.kraftcity.workforce;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Inclusive bounds only; no range, capacity, ownership or pathfinding rules. */
public record WorkArea(ResourceKey<Level> dimension, BlockPos min, BlockPos max) {
    public WorkArea {
        Objects.requireNonNull(dimension);
        min = Objects.requireNonNull(min).immutable();
        max = Objects.requireNonNull(max).immutable();
        if (min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ()) {
            throw new IllegalArgumentException("Work area bounds are reversed");
        }
    }
}
