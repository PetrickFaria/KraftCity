package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.employment.WorkerConversion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Mob.class)
public abstract class WorkerConversionMixin {
    @Redirect(method="convertTo",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean kraftcity$addConverted(Level level,Entity outcome){
        return WorkerConversion.addOutcome(level,(Mob)(Object)this,outcome);
    }
}
