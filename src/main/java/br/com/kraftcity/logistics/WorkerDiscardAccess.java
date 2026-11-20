package br.com.kraftcity.logistics;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/** Optional interop for block entities that authorize automation using the actual worker. */
public interface WorkerDiscardAccess {
    /**
     * Returns the insertion handler for this worker and face on the server, or null
     * when access is denied. The provider resolves the channel or owner using its own
     * public contract. KraftCity never substitutes a player identity for the worker.
     */
    @Nullable IItemHandler discardInventory(Villager worker,Direction face);
}
