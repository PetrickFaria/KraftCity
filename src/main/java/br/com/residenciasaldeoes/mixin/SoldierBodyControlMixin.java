package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.military.MilitaryCombatMovement;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class SoldierBodyControlMixin {
    @Inject(method="tickHeadTurn",at=@At("HEAD"),cancellable=true)
    private void kraftcity$preserveCombatFacing(float direction,float stride,CallbackInfoReturnable<Float> cir){
        if(MilitaryCombatMovement.preserveFacing((Mob)(Object)this,false,0))cir.setReturnValue(stride);
    }
}
