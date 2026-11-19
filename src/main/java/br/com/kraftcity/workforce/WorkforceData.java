package br.com.kraftcity.workforce;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** Server-owned, cross-dimension store, independent of residential data and GUI.
 * Access/mutation must occur on the server thread. Created lazily by future callers.
 * Unknown profession/workstation identifiers are retained without activating them.
 */
public final class WorkforceData extends SavedData {
    public static final String NAME = "kraftcity_workforce";
    private static final int SCHEMA = 1;
    private final Map<UUID, WorkerRecord> workers = new LinkedHashMap<>();
    public record Progress(int level,long xp) {
        public Progress { if(level<0||xp<0)throw new IllegalArgumentException("Negative progression"); }
    }
    private final Map<UUID,Map<ResourceLocation,Progress>> history=new LinkedHashMap<>();
    public boolean hasEmploymentHistory(UUID worker){
        var record=workers.get(worker);
        return history.containsKey(worker)||record!=null&&record.profession().isPresent();
    }
    public Progress progress(UUID worker,ResourceLocation profession){
        Progress saved=history.getOrDefault(worker,Map.of()).get(profession);
        if(saved!=null)return saved;
        WorkerRecord current=workers.get(worker);
        return new Progress(current!=null&&current.profession().filter(profession::equals).isPresent()?current.level():0,0);
    }
    /** Explicit server API; no XP earning or level progression rules are introduced here. */
    public void setProgress(UUID worker,ResourceLocation profession,int level,long xp){
        history.computeIfAbsent(worker,k->new LinkedHashMap<>()).put(profession,new Progress(level,xp));
        WorkerRecord current=workers.get(worker);
        if(current!=null&&current.profession().filter(profession::equals).isPresent())
            workers.put(worker,new WorkerRecord(worker,current.profession(),level,current.workstation(),current.area(),current.inventory()));
        setDirty();
    }
    public boolean rebindHistory(UUID previous,UUID current){
        if(previous.equals(current))return true;
        if(workers.containsKey(current)||history.containsKey(current))return false;
        WorkerRecord old=workers.remove(previous);var past=history.remove(previous);
        if(old!=null)workers.put(current,new WorkerRecord(current,old.profession(),old.level(),Optional.empty(),old.area(),old.inventory()));
        if(past!=null)history.put(current,past);
        if(old!=null||past!=null)setDirty();return true;
    }

    public static WorkforceData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(WorkforceData::load, WorkforceData::new, NAME);
    }

    public Optional<WorkerRecord> worker(UUID id) { return Optional.ofNullable(workers.get(id)); }
    public Collection<WorkerRecord> workers() { return List.copyOf(workers.values()); }
    public void put(WorkerRecord worker) { workers.put(worker.id(), worker); setDirty(); }
    public boolean remove(UUID id) {
        if (workers.remove(id) == null) return false;
        setDirty();
        return true;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        tag.putInt("schema", SCHEMA);
        ListTag entries = new ListTag();
        for (WorkerRecord worker : workers.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", worker.id());
            worker.profession().ifPresent(id -> entry.putString("profession", id.toString()));
            entry.putInt("level", worker.level());
            worker.workstation().ifPresent(post -> {
                CompoundTag p = new CompoundTag();
                p.putString("type", post.type().toString());
                p.putString("dimension", post.dimension().location().toString());
                p.putLong("position", post.position().asLong());
                entry.put("workstation", p);
            });
            worker.area().ifPresent(area -> {
                CompoundTag a = new CompoundTag();
                a.putString("dimension", area.dimension().location().toString());
                a.putLong("min", area.min().asLong());
                a.putLong("max", area.max().asLong());
                entry.put("area", a);
            });
            ListTag inventory = new ListTag();
            for (ItemStack stack : worker.inventory()) inventory.add(stack.save(new CompoundTag()));
            entry.put("inventory", inventory);
            entries.add(entry);
        }
        tag.put("workers", entries);
        ListTag past=new ListTag();
        history.forEach((id,professions)->professions.forEach((profession,progress)->{
            CompoundTag e=new CompoundTag();e.putUUID("id",id);e.putString("profession",profession.toString());
            e.putInt("level",progress.level());e.putLong("xp",progress.xp());past.add(e);
        }));
        tag.put("profession_history",past);
        return tag;
    }

    public static WorkforceData load(CompoundTag tag) {
        if (tag.getInt("schema") != SCHEMA) {
            throw new IllegalArgumentException("Unsupported KraftCity workforce schema: " + tag.getInt("schema"));
        }
        WorkforceData data = new WorkforceData();
        for (Tag raw : tag.getList("workers", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            Optional<ResourceLocation> profession = entry.contains("profession", Tag.TAG_STRING)
                    ? Optional.of(new ResourceLocation(entry.getString("profession"))) : Optional.empty();
            Optional<Workstation> post = Optional.empty();
            if (entry.contains("workstation", Tag.TAG_COMPOUND)) {
                CompoundTag p = entry.getCompound("workstation");
                post = Optional.of(new Workstation(new ResourceLocation(p.getString("type")),
                        dimension(p), BlockPos.of(p.getLong("position"))));
            }
            Optional<WorkArea> area = Optional.empty();
            if (entry.contains("area", Tag.TAG_COMPOUND)) {
                CompoundTag a = entry.getCompound("area");
                area = Optional.of(new WorkArea(dimension(a), BlockPos.of(a.getLong("min")), BlockPos.of(a.getLong("max"))));
            }
            List<ItemStack> inventory = new java.util.ArrayList<>();
            for (Tag stack : entry.getList("inventory", Tag.TAG_COMPOUND)) inventory.add(ItemStack.of((CompoundTag) stack));
            WorkerRecord worker = new WorkerRecord(entry.getUUID("id"), profession, entry.getInt("level"), post, area, inventory);
            if (data.workers.putIfAbsent(worker.id(), worker) != null) {
                throw new IllegalArgumentException("Duplicate worker UUID: " + worker.id());
            }
        }
        for(Tag raw:tag.getList("profession_history",Tag.TAG_COMPOUND)){
            CompoundTag e=(CompoundTag)raw;
            data.history.computeIfAbsent(e.getUUID("id"),k->new LinkedHashMap<>()).put(new ResourceLocation(e.getString("profession")),new Progress(e.getInt("level"),e.getLong("xp")));
        }
        return data;
    }

    private static ResourceKey<Level> dimension(CompoundTag tag) {
        return ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(tag.getString("dimension")));
    }
}
