package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.logistics.DiscardLids;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep the worker's lid state independent of vanilla player opener counts. */
@Mixin(EnderChestBlockEntity.class)
public abstract class DiscardEnderLidMixin {
    @Shadow @Final private ChestLidController chestLidController;
    @Unique private boolean kraftcity$playerOpen;
    @Unique private boolean kraftcity$discardOpen;

    @Inject(method="triggerEvent",at=@At("HEAD"),cancellable=true)
    private void kraftcity$combineOpeners(int event,int value,CallbackInfoReturnable<Boolean> result){
        if(event==DiscardLids.OPEN_EVENT){
            kraftcity$discardOpen=value!=0;
            chestLidController.shouldBeOpen(kraftcity$discardOpen||kraftcity$playerOpen);
            result.setReturnValue(true);
        }else if(event==1){
            kraftcity$playerOpen=value>0;
            if(kraftcity$discardOpen){
                chestLidController.shouldBeOpen(true);
                result.setReturnValue(true);
            }
        }
    }
}
