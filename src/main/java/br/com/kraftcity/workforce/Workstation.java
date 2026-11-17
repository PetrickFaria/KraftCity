package br.com.kraftcity.workforce;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** A future workstation type and its dimension-qualified position. */
public record Workstation(ResourceLocation type, ResourceKey<Level> dimension, BlockPos position) {
    public Workstation {
        Objects.requireNonNull(type);
        Objects.requireNonNull(dimension);
        position = Objects.requireNonNull(position).immutable();
    }
}
