package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.employment.Employment;
import br.com.kraftcity.industry.FarmerLogistics;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
abstract class ProfessionalFoodMixin {
    @Inject(method="pickUpItem",at=@At("HEAD"),cancellable=true)
    private void kraftcity$protectDrops(ItemEntity item,CallbackInfo callback) {
        if(FarmerLogistics.blocksVanillaPickup((Villager)(Object)this,item))callback.cancel();
    }

    @Inject(method="canBreed",at=@At("HEAD"),cancellable=true)
    private void kraftcity$blockWorkerBreeding(CallbackInfoReturnable<Boolean> callback) {
        if(Employment.active((Villager)(Object)this))callback.setReturnValue(false);
    }
}
