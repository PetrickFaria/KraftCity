package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.military.MilitaryCombatMovement;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MoveControl.class)
public abstract class SoldierMoveControlMixin {
    @Shadow @Final protected Mob mob;
    @Shadow protected double wantedX,wantedZ;
    @Unique private boolean kraftcity$hasWaypoint;

    @Inject(method="tick",at=@At("HEAD"))
    private void kraftcity$rememberWaypoint(CallbackInfo ci){
        kraftcity$hasWaypoint=((MoveControl)(Object)this).hasWanted()
            ||!mob.isOnGround()&&mob.getNavigation().isInProgress();
    }

    @Inject(method="tick",at=@At("RETURN"))
    private void kraftcity$preserveCombatFacing(CallbackInfo ci){
        float travelYaw=kraftcity$hasWaypoint
            ?(float)Math.toDegrees(Math.atan2(wantedZ-mob.getZ(),wantedX-mob.getX()))-90F:mob.getYRot();
        MilitaryCombatMovement.preserveFacing(mob,true,travelYaw);
    }
}
