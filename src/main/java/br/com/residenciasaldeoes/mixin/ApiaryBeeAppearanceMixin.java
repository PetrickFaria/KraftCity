package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.industry.ApiaryBeeAppearance;
import br.com.kraftcity.industry.ApiaryWork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Bee.class)
abstract class ApiaryBeeAppearanceMixin implements ApiaryBeeAppearance {
    @Shadow BlockPos hivePos;

    @Unique private static final EntityDataAccessor<Boolean> KRAFTCITY_BONDED=
        SynchedEntityData.defineId(Bee.class,EntityDataSerializers.BOOLEAN);

    @Inject(method="defineSynchedData",at=@At("RETURN"))
    private void kraftcity$defineBonded(CallbackInfo callback){
        ((Bee)(Object)this).getEntityData().define(KRAFTCITY_BONDED,false);
    }

    @Inject(method="customServerAiStep",at=@At("HEAD"))
    private void kraftcity$forgetHiveOutsideSelection(CallbackInfo callback){
        var bee=(Bee)(Object)this;
        if(hivePos!=null&&bee.level instanceof ServerLevel level&&!ApiaryWork.canKeepHive(level,bee,hivePos))hivePos=null;
    }

    @Inject(method="doesHiveHaveSpace",at=@At("HEAD"),cancellable=true)
    private void kraftcity$discoverOnlyManagedHives(BlockPos pos,CallbackInfoReturnable<Boolean> callback){
        var bee=(Bee)(Object)this;
        if(bee.level instanceof ServerLevel level&&!ApiaryWork.canDiscoverHive(level,bee,pos))callback.setReturnValue(false);
    }

    @Override public boolean kraftcity$isBonded(){
        return ((Bee)(Object)this).getEntityData().get(KRAFTCITY_BONDED);
    }

    @Override public void kraftcity$setBonded(boolean bonded){
        ((Bee)(Object)this).getEntityData().set(KRAFTCITY_BONDED,bonded);
    }
}
