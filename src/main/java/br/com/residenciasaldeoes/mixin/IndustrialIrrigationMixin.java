package br.com.residenciasaldeoes.mixin;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FarmBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(FarmBlock.class)
public abstract class IndustrialIrrigationMixin {
    @Inject(method="isNearWater",at=@At("HEAD"),cancellable=true)
    private static void kraftcity$bucketIrrigation(LevelReader l,BlockPos p,CallbackInfoReturnable<Boolean> ci){if(br.com.kraftcity.industry.Irrigation.hydrates(l,p))ci.setReturnValue(true);}
}
