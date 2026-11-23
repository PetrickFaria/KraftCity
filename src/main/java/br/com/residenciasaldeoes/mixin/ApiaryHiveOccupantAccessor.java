package br.com.residenciasaldeoes.mixin;

import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets="net.minecraft.world.level.block.entity.BeehiveBlockEntity$BeeData")
public interface ApiaryHiveOccupantAccessor {
    @Accessor("entityData") CompoundTag kraftcity$entityData();
    @Mutable @Accessor("minOccupationTicks") void kraftcity$setMinimum(int ticks);
}
