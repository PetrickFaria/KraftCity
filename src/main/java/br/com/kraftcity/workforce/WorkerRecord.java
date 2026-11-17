package br.com.kraftcity.workforce;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Passive snapshot. No profession, progression or inventory size is assigned automatically. */
public record WorkerRecord(UUID id, Optional<ResourceLocation> profession, int level,
                           Optional<Workstation> workstation, Optional<WorkArea> area,
                           List<ItemStack> inventory) {
    public WorkerRecord {
        Objects.requireNonNull(id);
        Objects.requireNonNull(profession);
        Objects.requireNonNull(workstation);
        Objects.requireNonNull(area);
        if (level < 0) throw new IllegalArgumentException("Negative worker level");
        inventory = copyInventory(inventory);
    }

    private static List<ItemStack> copyInventory(List<ItemStack> inventory) {
        return Objects.requireNonNull(inventory).stream().map(stack -> Objects.requireNonNull(stack).copy()).toList();
    }

    @Override public List<ItemStack> inventory() { return copyInventory(inventory); }
}
