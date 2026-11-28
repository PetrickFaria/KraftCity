package br.com.kraftcity.test;

import br.com.kraftcity.employment.LevelPaymentMenu;
import br.com.kraftcity.employment.WorkMenu;
import br.com.kraftcity.employment.client.LevelPaymentScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;

final class PaymentVisualCheck {
    static boolean ready(Minecraft mc,int inventorySlot){
        return mc.screen instanceof LevelPaymentScreen&&mc.player.containerMenu instanceof LevelPaymentMenu
                &&mc.player.getInventory().getItem(inventorySlot).is(Items.EMERALD);
    }

    static boolean synchronizedFunds(Minecraft mc,int total,int full){
        return mc.player.containerMenu instanceof LevelPaymentMenu menu&&menu.deposited()==total&&menu.completeSlots()==full;
    }

    static boolean employed(Minecraft mc){
        return mc.player.containerMenu instanceof WorkMenu menu&&!menu.hiring&&menu.snapshot!=null&&menu.snapshot.employed();
    }

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
        if(!synchronizedFunds(mc,total,full))
            throw new IllegalStateException("Payment did not synchronize: expected "+total+" emeralds and "+full+" complete slots");
        mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId,0);
    }
}
