package br.com.kraftcity.logistics;

import br.com.kraftcity.employment.EmploymentData;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.registries.ForgeRegistries;

/** Optional bridge to the public methods in Baús Arcanos 1.0.5. */
final class ArcaneDiscard {
    private static final String MOD = "bausarcanos";
    private static final String CHEST_CLASS = "br.com.petrick.bausarcanos.ArcaneChest";
    private static final ResourceLocation ENDER = new ResourceLocation(MOD, "bau_ender");
    private static final String OWNER = "discard_arcane_owner";
    private static final String CHANNEL = "discard_arcane_channel";
    private record Api(Field channel, Method get, Method inventory, Method open, Method close) {}

    private static final class Integration {
        private static final Api API = load();

        private static Api load() {
            var mod = ModList.get().getModContainerById(MOD);
            if (mod.isEmpty() || !mod.get().getModInfo().getVersion().toString().equals("1.0.5")) return null;
            try {
                ClassLoader loader = mod.get().getMod().getClass().getClassLoader();
                Class<?> chest = Class.forName(CHEST_CLASS, false, loader);
                Class<?> storage = Class.forName("br.com.petrick.bausarcanos.EnderStorage", false, loader);
                return new Api(chest.getField("channel"), storage.getMethod("get", MinecraftServer.class),
                    storage.getMethod("inventory", UUID.class, int.class), chest.getMethod("open"), chest.getMethod("close"));
            } catch (ReflectiveOperationException | LinkageError failure) {
                LogUtils.getLogger().warn("Baús Arcanos 1.0.5 storage API unavailable", failure);
                return null;
            }
        }
    }

    static boolean isEnder(BlockEntity tile) {
        return tile!=null && ENDER.equals(ForgeRegistries.BLOCKS.getKey(tile.getBlockState().getBlock()));
    }

    private static int channel(BlockEntity tile) {
        var api = Integration.API;
        if (api==null || !api.channel.getDeclaringClass().isInstance(tile)) return 0;
        try {
            int channel = api.channel.getInt(tile);
            return channel>=1 && channel<=5 ? channel : 0;
        } catch (IllegalAccessException failure) {
            return 0;
        }
    }

    static boolean authorize(BlockEntity tile, ServerPlayer player, EmploymentData.Station station) {
        int channel = channel(tile);
        if (channel==0 || !unobstructed(player.getLevel(), tile)) return false;
        station.industry.putUUID(OWNER, player.getUUID());
        station.industry.putInt(CHANNEL, channel);
        return true;
    }

    static boolean channelMatches(BlockEntity tile, EmploymentData.Station station) {
        int channel = channel(tile);
        return channel!=0 && station.industry.hasUUID(OWNER) && station.industry.getInt(CHANNEL)==channel;
    }

    static void clear(EmploymentData.Station station) {
        station.industry.remove(OWNER);
        station.industry.remove(CHANNEL);
    }

    private static boolean unobstructed(ServerLevel level, BlockEntity tile) {
        var above = tile.getBlockPos().above();
        return !level.getBlockState(above).isRedstoneConductor(level, above);
    }

    static IItemHandler inventory(ServerLevel level, BlockEntity tile, EmploymentData.Station station) {
        if (!DiscardBinding.targets(level, station, tile.getBlockPos()) || !channelMatches(tile, station)
            || !unobstructed(level, tile)) return null;
        var owner = level.getServer().getPlayerList().getPlayer(station.industry.getUUID(OWNER));
        if (owner==null || !level.mayInteract(owner, tile.getBlockPos())) return null;
        var api = Integration.API;
        try {
            Object storage = api.get.invoke(null, level.getServer());
            var inventory = (SimpleContainer)api.inventory.invoke(storage, owner.getUUID(), station.industry.getInt(CHANNEL));
            return new InvWrapper(inventory) {
                @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                    return ItemStack.EMPTY;
                }
            };
        } catch (ReflectiveOperationException | ClassCastException failure) {
            return null;
        }
    }

    static Runnable open(BlockEntity tile) {
        var api = Integration.API;
        if (api==null || !api.channel.getDeclaringClass().isInstance(tile)) return null;
        try {
            api.open.invoke(tile);
            return ()->{
                if (!tile.isRemoved()) {
                    try {
                        api.close.invoke(tile);
                    } catch (ReflectiveOperationException failure) {
                        LogUtils.getLogger().warn("Could not close Baús Arcanos lid", failure);
                    }
                }
            };
        } catch (ReflectiveOperationException failure) {
            LogUtils.getLogger().warn("Could not open Baús Arcanos lid", failure);
            return null;
        }
    }

    private ArcaneDiscard() {}
}
