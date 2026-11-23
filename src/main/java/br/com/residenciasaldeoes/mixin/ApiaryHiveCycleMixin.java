package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.employment.EmploymentConfig;
import br.com.kraftcity.industry.ApiaryWork;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeehiveBlockEntity.class)
public abstract class ApiaryHiveCycleMixin {
    @Unique private static final String KRAFTCITY$ORIGINAL_MINIMUM="KCApiaryHiveMinimum";

    @ModifyArg(method="storeBee",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/entity/BeehiveBlockEntity$BeeData;<init>(Lnet/minecraft/nbt/CompoundTag;II)V"),index=2)
    private int kraftcity$shortenNectarVisit(CompoundTag entity,int elapsed,int vanilla){
        if(vanilla<2400||!(((BeehiveBlockEntity)(Object)this).getLevel() instanceof ServerLevel level))return vanilla;
        var data=entity.getCompound("ForgeData");
        if(!ApiaryWork.reconcileStoredBond(level,data))return vanilla;
        int shortened=EmploymentConfig.APIARY_HIVE_TICKS.get();
        if(shortened>=vanilla)return vanilla;
        data.putInt(KRAFTCITY$ORIGINAL_MINIMUM,vanilla);
        return shortened;
    }

    @Inject(method="tickOccupants",at=@At("HEAD"))
    private static void kraftcity$restoreWildOccupants(Level world,BlockPos pos,BlockState state,List<?> occupants,
            BlockPos flower,CallbackInfo callback){
        if(!(world instanceof ServerLevel level))return;
        for(var occupant:occupants){
            var access=(ApiaryHiveOccupantAccessor)occupant;
            var data=access.kraftcity$entityData().getCompound("ForgeData");
            if(ApiaryWork.reconcileStoredBond(level,data)||!data.contains(KRAFTCITY$ORIGINAL_MINIMUM))continue;
            access.kraftcity$setMinimum(data.getInt(KRAFTCITY$ORIGINAL_MINIMUM));
            data.remove(KRAFTCITY$ORIGINAL_MINIMUM);
        }
    }

    @ModifyArg(method="releaseOccupant",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/entity/BeehiveBlockEntity;setBeeReleaseData(ILnet/minecraft/world/entity/animal/Bee;)V"),index=0)
    private static int kraftcity$moveRemainingTimeOutdoors(int elapsed,Bee bee){
        var data=bee.getPersistentData();
        int original=data.getInt(KRAFTCITY$ORIGINAL_MINIMUM);
        data.remove(KRAFTCITY$ORIGINAL_MINIMUM);
        if(original>0&&!bee.hasNectar()&&bee.level instanceof ServerLevel level
                &&ApiaryWork.reconcileStoredBond(level,data)){
            long cooldown=Math.max(0,original+1L-elapsed);
            if(cooldown>0)data.putLong(ApiaryWork.POLLINATE_AFTER,level.getGameTime()+cooldown);
        }
        return elapsed;
    }
}
