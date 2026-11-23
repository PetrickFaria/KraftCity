package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.employment.EmploymentData;
import br.com.kraftcity.employment.Profession;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
abstract class ApiaryHandSaveMixin {
    @Inject(method="addAdditionalSaveData",at=@At("RETURN"))
    private void kraftcity$saveRealHandOnly(CompoundTag tag,CallbackInfo callback){
        if(!((Object)this instanceof Villager worker)||!(worker.level instanceof ServerLevel level))return;
        var data=worker.getPersistentData();
        if(!data.contains("KCOriginalHand",Tag.TAG_COMPOUND))return;
        var station=EmploymentData.get(level).assigned(worker.getUUID());
        if(station==null||(station.profession!=Profession.BEEKEEPER&&station.profession!=Profession.LUMBERJACK))return;
        var hands=tag.getList("HandItems",Tag.TAG_COMPOUND);
        if(!hands.isEmpty())hands.set(0,data.getCompound("KCOriginalHand").copy());
        var chances=tag.getList("HandDropChances",Tag.TAG_FLOAT);
        if(!chances.isEmpty())chances.set(0,FloatTag.valueOf(.085F));
    }
}
