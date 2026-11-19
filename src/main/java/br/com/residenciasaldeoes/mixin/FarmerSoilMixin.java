package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.industry.FarmerLogistics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FarmBlock.class)
abstract class FarmerSoilMixin {
    @Inject(method="fallOn",at=@At("HEAD"))
    private void kraftcity$intentionalLanding(Level level,BlockState state,BlockPos pos,Entity entity,float distance,CallbackInfo ci){
        if(distance>0&&level instanceof ServerLevel server&&entity instanceof Villager villager
                &&FarmerLogistics.landOnFruitSoil(server,villager,pos)){
            FarmBlock.turnToDirt(state,level,pos);
            com.mojang.logging.LogUtils.getLogger().debug("Farmer {} landed on and converted {}",villager.getUUID(),pos);
        }
    }
}
