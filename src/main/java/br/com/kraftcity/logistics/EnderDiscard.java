package br.com.kraftcity.logistics;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

public final class EnderDiscard {
    private static final String OWNER = "KraftCityDiscardOwner";

    private EnderDiscard() {}

    static boolean authorize(EnderChestBlockEntity chest, ServerPlayer player) {
        var data=chest.getPersistentData();
        if (data.hasUUID(OWNER)) return data.getUUID(OWNER).equals(player.getUUID());
        data.putUUID(OWNER,player.getUUID());
        chest.setChanged();
        return true;
    }

    static IItemHandler inventory(EnderChestBlockEntity chest) {
        if (!(chest.getLevel() instanceof net.minecraft.server.level.ServerLevel level) || chest.isRemoved()) return null;
        var data=chest.getPersistentData();
        if (!data.hasUUID(OWNER)) return null;
        var player=level.getServer().getPlayerList().getPlayer(data.getUUID(OWNER));
        if (player==null || player instanceof FakePlayer || !level.mayInteract(player,chest.getBlockPos())) return null;
        return new InvWrapper(player.getEnderChestInventory()) {
            @Override public net.minecraft.world.item.ItemStack extractItem(int slot,int amount,boolean simulate) {
                return net.minecraft.world.item.ItemStack.EMPTY;
            }
        };
    }
}
