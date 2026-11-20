package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.industry.HarvestTrace;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
abstract class HarvestTraceMixin {
    @Shadow public abstract Level getLevel();

    @Shadow public abstract BlockState getBlockState(BlockPos pos);

    @Inject(method="setBlockState",at=@At(value="INVOKE",
        target="Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private void kraftcity$traceWrite(BlockPos pos,BlockState state,boolean moving,CallbackInfoReturnable<BlockState> result) {
        if(getLevel() instanceof ServerLevel level&&HarvestTrace.watches(level)){
            HarvestTrace.changed(level,pos,getBlockState(pos),state);
        }
    }
}
