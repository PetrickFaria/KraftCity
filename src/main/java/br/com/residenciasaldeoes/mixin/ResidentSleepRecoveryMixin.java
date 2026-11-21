package br.com.residenciasaldeoes.mixin;

import br.com.residenciasaldeoes.VillagerResidenceEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class ResidentSleepRecoveryMixin {
    @Inject(method="stopSleeping",at=@At("HEAD"))
    private void kraftcity$endSleepSession(CallbackInfo callback){
        if((Object)this instanceof Villager resident&&!resident.level.isClientSide)
            VillagerResidenceEvents.resetSleepProgress(resident);
    }
}
