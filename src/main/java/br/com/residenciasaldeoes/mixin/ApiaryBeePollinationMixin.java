package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.industry.ApiaryWork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets="net.minecraft.world.entity.animal.Bee$BeePollinateGoal")
public abstract class ApiaryBeePollinationMixin {
    @Unique private Bee kraftcity$bee;

    @Inject(method="<init>",at=@At("RETURN"))
    private void kraftcity$captureBee(Bee bee,CallbackInfo callback){
        kraftcity$bee=bee;
    }

    @Inject(method="canBeeUse",at=@At("HEAD"),cancellable=true)
    private void kraftcity$spaceOutNectarCycles(CallbackInfoReturnable<Boolean> callback){
        var bee=kraftcity$bee;
        if(bee!=null&&bee.level instanceof ServerLevel level
                &&bee.getPersistentData().getLong(ApiaryWork.POLLINATE_AFTER)>level.getGameTime()
                &&ApiaryWork.reconcileStoredBond(level,bee.getPersistentData()))callback.setReturnValue(false);
    }
}
