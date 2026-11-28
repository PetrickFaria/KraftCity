package br.com.kraftcity.test;

import br.com.kraftcity.employment.LevelPaymentMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;

final class PaymentVisualCheck {
    static void fund(Minecraft mc,int inventorySlot){
        if(!(mc.player.containerMenu instanceof LevelPaymentMenu menu))
            throw new IllegalStateException("Payment screen was not opened");
        int index=menu.current==0?5:1;
        for(;index<menu.slots.size();index++){
            var slot=menu.getSlot(index);
            if(slot.getContainerSlot()==inventorySlot&&slot.getItem().is(Items.EMERALD)){
                mc.gameMode.handleInventoryMouseClick(menu.containerId,index,0,ClickType.QUICK_MOVE,mc.player);
                return;
            }
        }
        throw new IllegalStateException("Emeralds not available in the player's inventory");
    }

    static void confirm(Minecraft mc,int total,int full){
        if(!(mc.player.containerMenu instanceof LevelPaymentMenu menu)||menu.deposited()!=total||menu.completeSlots()!=full)
            throw new IllegalStateException("Payment did not synchronize: expected "+total+" emeralds and "+full+" complete slots");
        mc.gameMode.handleInventoryButtonClick(menu.containerId,0);
    }
}
