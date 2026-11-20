package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.industry.FarmerLogistics;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
abstract class HarvestItemMergeMixin {
    @Inject(method="tryToMerge",at=@At("HEAD"),cancellable=true)
    private void kraftcity$preserveOrigin(ItemEntity other,CallbackInfo callback) {
        if(!FarmerLogistics.sameHarvestOrigin((ItemEntity)(Object)this,other))callback.cancel();
    }
}
