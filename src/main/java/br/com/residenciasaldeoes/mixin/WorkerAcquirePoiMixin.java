package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.employment.Employment;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.ai.behavior.AcquirePoi;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Block only vanilla work POI acquisition. Residential bed/HOME acquisition remains vanilla. */
@Mixin(AcquirePoi.class)
public abstract class WorkerAcquirePoiMixin {
    @Shadow @Final private MemoryModuleType<GlobalPos> memoryToAcquire;
    @Inject(method="checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/PathfinderMob;)Z",at=@At("HEAD"),cancellable=true)
    private void kraftcity$workPoi(ServerLevel level,PathfinderMob mob,CallbackInfoReturnable<Boolean> ci){
        if((memoryToAcquire==MemoryModuleType.JOB_SITE||memoryToAcquire==MemoryModuleType.POTENTIAL_JOB_SITE)
                &&mob instanceof Villager v&&Employment.active(v))ci.setReturnValue(false);
    }
}
