package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.employment.Employment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WalkNodeEvaluator.class)
abstract class WorkerGatePathMixin extends NodeEvaluator {
    @Inject(method="getBlockPathType(Lnet/minecraft/world/level/BlockGetter;III)Lnet/minecraft/world/level/pathfinder/BlockPathTypes;",
            at=@At("RETURN"),cancellable=true)
    private void kraftcity$closedGate(BlockGetter level,int x,int y,int z,CallbackInfoReturnable<BlockPathTypes> callback){
        if(callback.getReturnValue()==BlockPathTypes.FENCE&&mob instanceof Villager worker&&Employment.active(worker)
                &&level.getBlockState(new BlockPos(x,y,z)).getBlock() instanceof FenceGateBlock){
            callback.setReturnValue(BlockPathTypes.WALKABLE_DOOR);
        }
    }
}
