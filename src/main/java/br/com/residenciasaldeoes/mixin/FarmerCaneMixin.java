package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.industry.Irrigation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SugarCaneBlock.class)
public abstract class FarmerCaneMixin {
    @Inject(method="canSurvive",at=@At("RETURN"),cancellable=true)
    private void kraftcity$virtualWater(BlockState state,LevelReader level,BlockPos pos,CallbackInfoReturnable<Boolean> result){
        if(!result.getReturnValue()&&Irrigation.supportsCane(level,pos))result.setReturnValue(true);
    }
}
