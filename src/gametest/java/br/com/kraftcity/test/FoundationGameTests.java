package br.com.kraftcity.test;

import br.com.kraftcity.KraftCity;
import br.com.kraftcity.core.CityModule;
import br.com.kraftcity.workforce.*;
import br.com.residenciasaldeoes.ResidenciasDosAldeoes;
import br.com.residenciasaldeoes.data.ResidenceData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder("residenciasaldeoes")
@PrefixGameTestTemplate(false)
public final class FoundationGameTests {
    private static void check(boolean condition, String message) {
        if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void residential_place_remove_releases_links_beds_and_saved_home(GameTestHelper h) {
        BlockPos local = new BlockPos(6, 2, 6);
        BlockPos absolute = h.absolutePos(local);
        h.setBlock(local, ResidenciasDosAldeoes.ANCHOR.get());
        check(h.getLevel().getBlockEntity(absolute) instanceof br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity,
                "Placed residential block has no block entity");
        var data = ResidenceData.get(h.getLevel());
        var home = data.home(h.getLevel(), absolute);
        int[] sizes = {3, 5, 7, 9, 11};
        int[] capacities = {1, 3, 5, 7, 9};
        for (int i = 0; i < sizes.length; i++) {
            check(data.setSize(home, sizes[i]) && data.size(home) == sizes[i]
                    && data.capacity(home) == capacities[i], "Residential size/capacity changed");
        }
        check(!data.setSize(home, 4) && data.size(home) == 11, "Unsupported size accepted");
        var resident = h.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(5, 2, 6));
        check(data.link(h.getLevel(), absolute, resident).equals("ok"), "Resident link failed");
        var second = h.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(7, 2, 6));
        check(data.link(h.getLevel(), absolute, second).equals("ok"), "Second link failed");
        check(!data.setSize(home, 3) && data.size(home) == 11, "Overcrowded shrink accepted");
        BlockPos bed = absolute.offset(1, 0, 0);
        check(data.reserveBed(resident.getUUID(), h.getLevel().dimension(), absolute, bed), "Bed reservation failed");
        h.getLevel().removeBlock(absolute, false);
        check(data.owner(resident.getUUID()) == null && data.bedClaim(resident.getUUID()) == null
                && data.bedOwner(h.getLevel().dimension(), bed) == null, "Removal left a link or bed reservation");
        check(data.homes().stream().noneMatch(saved -> saved.pos.equals(absolute)
                && saved.dimension.equals(h.getLevel().dimension())), "Removed anchor left a ghost home");
        resident.discard();
        second.discard();
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void workforce_round_trip_preserves_unknown_profession_dimensions_and_inventory(GameTestHelper h) {
        UUID id = UUID.randomUUID();
        var profession = new ResourceLocation("future_module", "profession");
        var post = new Workstation(CityModule.INDUSTRIAL.id(), Level.NETHER, new BlockPos(-9, 42, 7));
        var area = new WorkArea(Level.OVERWORLD, new BlockPos(-20, -64, -8), new BlockPos(4, 30, 12));
        WorkforceData data = new WorkforceData();
        data.put(new WorkerRecord(id, Optional.of(profession), 7, Optional.of(post), Optional.of(area),
                List.of(new ItemStack(Items.EMERALD, 12), ItemStack.EMPTY)));
        var loaded = WorkforceData.load(data.save(new CompoundTag())).worker(id).orElseThrow();
        check(loaded.profession().orElseThrow().equals(profession) && loaded.level() == 7, "Profession/level lost");
        check(loaded.workstation().orElseThrow().equals(post) && loaded.area().orElseThrow().equals(area), "Dimension/coordinates lost");
        check(loaded.inventory().size() == 2 && loaded.inventory().get(0).is(Items.EMERALD)
                && loaded.inventory().get(0).getCount() == 12 && loaded.inventory().get(1).isEmpty(), "Inventory slots lost");
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void worker_snapshots_cannot_mutate_saved_inventory(GameTestHelper h) {
        ItemStack input = new ItemStack(Items.EMERALD, 8);
        List<ItemStack> inventory = new ArrayList<>(List.of(input));
        var worker = new WorkerRecord(UUID.randomUUID(), Optional.empty(), 0, Optional.empty(), Optional.empty(), inventory);
        input.setCount(1);
        inventory.clear();
        worker.inventory().get(0).setCount(2);
        check(worker.inventory().get(0).getCount() == 8, "Inventory aliased outside the snapshot");
        WorkforceData data = new WorkforceData();
        data.put(worker);
        check(data.isDirty(), "Mutation must mark SavedData dirty");
        var loaded = WorkforceData.load(data.save(new CompoundTag()));
        var restored = loaded.worker(worker.id()).orElseThrow();
        check(restored.profession().isEmpty() && restored.workstation().isEmpty() && restored.area().isEmpty(), "Unassigned fields changed");
        check(loaded.remove(worker.id()) && !loaded.remove(worker.id()) && loaded.workers().isEmpty(), "Removal failed");
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void workforce_storage_is_shared_across_dimensions_and_separate_from_residences(GameTestHelper h) {
        var data = WorkforceData.get(h.getLevel());
        var nether = h.getLevel().getServer().getLevel(Level.NETHER);
        check(nether != null && data == WorkforceData.get(nether), "Workforce split across dimensions");
        CompoundTag residential = ResidenceData.get(h.getLevel()).save(new CompoundTag()).copy();
        UUID id = UUID.randomUUID();
        data.put(new WorkerRecord(id, Optional.empty(), 0, Optional.empty(), Optional.empty(), List.of()));
        h.getLevel().getServer().overworld().getDataStorage().save();
        check(WorkforceData.load(data.save(new CompoundTag())).worker(id).isPresent(), "Worker not persisted");
        check(ResidenceData.get(h.getLevel()).save(new CompoundTag()).equals(residential), "Worker mutation touched residential data");
        data.remove(id);
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void foundation_rejects_unknown_schema_duplicate_workers_and_invalid_bounds(GameTestHelper h) {
        boolean schemaRejected = false;
        CompoundTag future = new CompoundTag(); future.putInt("schema", 99);
        try { WorkforceData.load(future); } catch (IllegalArgumentException expected) { schemaRejected = true; }
        check(schemaRejected, "Unknown schema must not silently discard data");
        boolean boundsRejected = false;
        try { new WorkArea(Level.OVERWORLD, new BlockPos(2, 0, 0), BlockPos.ZERO); }
        catch (IllegalArgumentException expected) { boundsRejected = true; }
        check(boundsRejected, "Reversed area accepted");
        WorkforceData data = new WorkforceData();
        data.put(new WorkerRecord(UUID.randomUUID(), Optional.empty(), 0, Optional.empty(), Optional.empty(), List.of()));
        CompoundTag duplicate = data.save(new CompoundTag());
        var list = duplicate.getList("workers", net.minecraft.nbt.Tag.TAG_COMPOUND);
        list.add(list.getCompound(0).copy());
        boolean duplicateRejected = false;
        try { WorkforceData.load(duplicate); } catch (IllegalArgumentException expected) { duplicateRejected = true; }
        check(duplicateRejected, "Duplicate UUID silently overwritten");
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void kraftcity_exposes_existing_items_without_registering_placeholder_blocks(GameTestHelper h) {
        check(net.minecraftforge.fml.ModList.get().getModContainerById(KraftCity.MOD_ID).orElseThrow()
                .getModInfo().getDisplayName().equals("KraftCity"), "Public mod name did not change");
        check(CityModule.values().length == 5 && java.util.Arrays.stream(CityModule.values())
                .filter(CityModule::implemented).count() == 1 && CityModule.RESIDENTIAL.implemented(), "Premature module activation");
        NonNullList<ItemStack> items = NonNullList.create();
        KraftCity.CREATIVE_TAB.fillItemList(items);
        check(items.size() == 4 && items.get(0).is(ResidenciasDosAldeoes.ANCHOR_ITEM.get()), "Creative items missing");
        check(ForgeRegistries.BLOCKS.getKey(ResidenciasDosAldeoes.ANCHOR.get())
                .equals(new ResourceLocation("residenciasaldeoes", "ancora_residencial")), "Residential ID changed");
        check(ForgeRegistries.BLOCKS.getKeys().stream().noneMatch(id -> id.getNamespace().equals("kraftcity")), "Placeholder blocks registered");
        for (String recipe : List.of("ancora_residencial", "reinforced_door", "emerald_from_fragment")) {
            check(h.getLevel().getRecipeManager().byKey(new ResourceLocation(KraftCity.MOD_ID, recipe)).isPresent(), "Recipe missing: " + recipe);
        }
        h.succeed();
    }
}
