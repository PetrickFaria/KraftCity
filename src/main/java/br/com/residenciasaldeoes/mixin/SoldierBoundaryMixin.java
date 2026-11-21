package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.military.Military;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
abstract class SoldierBoundaryMixin {
    @ModifyVariable(method="move",at=@At("HEAD"),argsOnly=true)
    private Vec3 kraftcity$keepSoldierInsidePost(Vec3 movement){
        return (Object)this instanceof Villager soldier?Military.limitMovement(soldier,movement):movement;
    }
}
