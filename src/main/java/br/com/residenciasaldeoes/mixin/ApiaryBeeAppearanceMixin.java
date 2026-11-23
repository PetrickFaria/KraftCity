package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.industry.ApiaryBeeAppearance;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Bee.class)
abstract class ApiaryBeeAppearanceMixin implements ApiaryBeeAppearance {
    @Unique private static final EntityDataAccessor<Boolean> KRAFTCITY_BONDED=
        SynchedEntityData.defineId(Bee.class,EntityDataSerializers.BOOLEAN);

    @Inject(method="defineSynchedData",at=@At("RETURN"))
    private void kraftcity$defineBonded(CallbackInfo callback){
        ((Bee)(Object)this).getEntityData().define(KRAFTCITY_BONDED,false);
    }

    @Override public boolean kraftcity$isBonded(){
        return ((Bee)(Object)this).getEntityData().get(KRAFTCITY_BONDED);
    }

    @Override public void kraftcity$setBonded(boolean bonded){
        ((Bee)(Object)this).getEntityData().set(KRAFTCITY_BONDED,bonded);
    }
}
