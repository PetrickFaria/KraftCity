package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.builder.BuilderWork;
import br.com.kraftcity.commerce.CommercialWork;
import br.com.kraftcity.employment.Employment;
import br.com.kraftcity.employment.WorkplaceVisits;
import br.com.kraftcity.industry.IndustrialWork;
import br.com.kraftcity.military.Military;
import br.com.residenciasaldeoes.VillagerResidenceEvents;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class IndustrialNavigationMixin {
    @Inject(method="customServerAiStep",at=@At("HEAD"),cancellable=true)
    private void kraftcity$workNavigation(CallbackInfo ci){
        var worker=(Villager)(Object)this;
        if(Employment.fleeing(worker)||VillagerResidenceEvents.ownsWorkerRest(worker)||WorkplaceVisits.leaving(worker)
            ||BuilderWork.ownsNavigation(worker)||Military.isSoldier(worker)||IndustrialWork.ownsNavigation(worker)
            ||CommercialWork.ownsNavigation(worker))ci.cancel();
    }
}
