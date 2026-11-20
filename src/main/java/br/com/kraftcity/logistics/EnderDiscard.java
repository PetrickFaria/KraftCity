package br.com.kraftcity.logistics;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

public final class EnderDiscard {
    private static final String OWNER = "KraftCityDiscardOwner";

    private EnderDiscard() {}

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void bind(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand()!=InteractionHand.MAIN_HAND || event.getUseBlock()==Event.Result.DENY || event.getUseItem()==Event.Result.DENY
            || !(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer
            || !player.isShiftKeyDown() || !event.getItemStack().is(Items.ENDER_EYE)) return;
        var level=player.getLevel();
        var pos=event.getPos();
        if (player.server.getPlayerList().getPlayer(player.getUUID())!=player
            || !level.mayInteract(player,pos) || !level.getBlockState(pos).is(Blocks.ENDER_CHEST)
            || !(level.getBlockEntity(pos) instanceof EnderChestBlockEntity chest)) return;
        var data=chest.getPersistentData();
        String message;
        if (!data.hasUUID(OWNER)) {
            data.putUUID(OWNER,player.getUUID());
            chest.setChanged();
            message="Descarte do Fazendeiro autorizado neste Baú do Ender enquanto você estiver conectado.";
        } else if (data.getUUID(OWNER).equals(player.getUUID())) {
            data.remove(OWNER);
            chest.setChanged();
            message="Autorização de descarte removida deste Baú do Ender.";
        } else {
            message="Este Baú do Ender já está vinculado a outro jogador.";
        }
        player.sendSystemMessage(Component.literal(message));
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
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
