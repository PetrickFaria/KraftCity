package br.com.residenciasaldeoes.mixin;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Villager.class)
public abstract class IndustrialNavigationMixin {
    @Inject(method="customServerAiStep",at=@At("HEAD"),cancellable=true)
    private void kraftcity$workNavigation(CallbackInfo ci){if(br.com.kraftcity.industry.IndustrialWork.ownsNavigation((Villager)(Object)this)||br.com.kraftcity.commerce.CommercialWork.ownsNavigation((Villager)(Object)this))ci.cancel();}
}
