package br.com.residenciasaldeoes.mixin;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ZombieVillager.class)
public abstract class MilitaryCureMixin {
    @Inject(method="finishConversion",at=@At("HEAD"),cancellable=true)
    private void kraftcity$cure(ServerLevel level,CallbackInfo ci){if((Object)this instanceof br.com.kraftcity.military.SoldierZombie zombie){br.com.kraftcity.military.Military.cure(level,zombie);ci.cancel();}}
}
