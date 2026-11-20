package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.industry.FarmerLogistics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FarmBlock.class)
abstract class FarmerSoilMixin {
    @Redirect(method="fallOn",at=@At(value="INVOKE",remap=false,
        target="Lnet/minecraftforge/common/ForgeHooks;onFarmlandTrample(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;FLnet/minecraft/world/entity/Entity;)Z"))
    private boolean kraftcity$trample(Level level,BlockPos pos,BlockState dirt,float distance,Entity entity){
        if(level instanceof ServerLevel server&&entity instanceof Villager villager){
            if(FarmerLogistics.allowsTrample(villager,pos)){
                return distance>0&&FarmerLogistics.landOnFruitSoil(server,villager,pos,distance);
            }
            if(FarmerLogistics.protectedSoil(server,villager,pos))return false;
        }
        return ForgeHooks.onFarmlandTrample(level,pos,dirt,distance,entity);
    }
}
