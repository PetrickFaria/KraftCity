package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.industry.ApiaryBeeFlight;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
abstract class ApiaryBeeMovementMixin {
    @ModifyVariable(method="move",at=@At("HEAD"),argsOnly=true)
    private Vec3 kraftcity$keepBondedBeeInItsApiary(Vec3 movement,MoverType type,Vec3 original){
        return type==MoverType.SELF&&(Object)this instanceof Bee bee?ApiaryBeeFlight.limitMovement(bee,movement):movement;
    }
}
