package br.com.residenciasaldeoes.mixin;

import br.com.kraftcity.military.Military;
import net.minecraft.world.entity.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** LivingEntity's defaults do not wear villager armor/shields. Apply the vanilla player costs only to soldiers. */
@Mixin(LivingEntity.class)
public abstract class MilitaryEquipmentMixin {
    @Inject(method="hurtArmor",at=@At("HEAD")) private void kraftcity$armor(DamageSource source,float damage,CallbackInfo ci){
        var v=(LivingEntity)(Object)this;if(v.level.isClientSide||!Military.isSoldier(v)||source.isBypassArmor())return;
        int amount=Math.max(1,(int)(damage/4));for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}){
            var stack=v.getItemBySlot(slot);if(stack.getItem() instanceof ArmorItem&&(!source.isFire()||!stack.getItem().isFireResistant()))stack.hurtAndBreak(amount,v,x->x.broadcastBreakEvent(slot));
        }var s=Military.station(v);if(s!=null)s.setChanged();
    }
    @Inject(method="hurtHelmet",at=@At("HEAD")) private void kraftcity$helmet(DamageSource source,float damage,CallbackInfo ci){
        var v=(LivingEntity)(Object)this;if(v.level.isClientSide||!Military.isSoldier(v))return;var stack=v.getItemBySlot(EquipmentSlot.HEAD);
        if(stack.getItem() instanceof ArmorItem&&(!source.isFire()||!stack.getItem().isFireResistant()))stack.hurtAndBreak(Math.max(1,(int)(damage/4)),v,x->x.broadcastBreakEvent(EquipmentSlot.HEAD));var s=Military.station(v);if(s!=null)s.setChanged();
    }
    @Inject(method="hurtCurrentlyUsedShield",at=@At("HEAD")) private void kraftcity$shield(float damage,CallbackInfo ci){
        var v=(LivingEntity)(Object)this;if(v.level.isClientSide||!Military.isSoldier(v)||damage<3)return;
        var stack=v.getUseItem();if(stack.canPerformAction(net.minecraftforge.common.ToolActions.SHIELD_BLOCK))stack.hurtAndBreak(1+(int)Math.floor(damage),v,x->x.broadcastBreakEvent(v.getUsedItemHand()));var s=Military.station(v);if(s!=null)s.setChanged();
    }
}
