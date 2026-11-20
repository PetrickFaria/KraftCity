package br.com.kraftcity.employment;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** Single authority for block-owned inventories. Portable items carry an identity, never item copies. */
public final class EmploymentData extends SavedData {
    public static final String NAME="kraftcity_employment";
    public static final int COMMON=27, TOOL=27, INPUT_A=28, INPUT_B=29, EQUIPMENT=30, PAYMENT=31, SLOTS=32;
    private long revision;
    public long revision(){return revision;}
    private final Map<UUID,Station> stations=new LinkedHashMap<>();
    public static EmploymentData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(EmploymentData::load,EmploymentData::new,NAME);
    }
    private final CompoundTag detachedSoldiers=new CompoundTag();
    public void saveDetachedSoldier(UUID id,CompoundTag snapshot){detachedSoldiers.put(id.toString(),snapshot.copy());setDirty();}
    public CompoundTag detachedSoldier(UUID id){return detachedSoldiers.getCompound(id.toString()).copy();}
    public void forgetDetachedSoldier(UUID id){if(detachedSoldiers.contains(id.toString())){detachedSoldiers.remove(id.toString());setDirty();}}
    public Collection<Station> stations(){return List.copyOf(stations.values());}
    void remove(UUID id){if(stations.remove(id)!=null){revision++;setDirty();}}
    public Station station(UUID id){return stations.get(id);}
    public Station assigned(UUID worker){return stations.values().stream().filter(s->s.active&&worker.equals(s.worker)).findFirst().orElse(null);}
    public Station create(Profession.Sector sector,ServerLevel level,BlockPos pos){
        Station s=new Station(this,UUID.randomUUID(),sector);s.active=true;s.dimension=level.dimension();s.pos=pos.immutable();stations.put(s.id,s);setDirty();return s;
    }
    public static final class Station extends SimpleContainer {
        private final EmploymentData owner;
        public final UUID id;
        public final Profession.Sector sector;
        public Profession profession;
        public UUID worker;
        public boolean active;
        public ResourceKey<Level> dimension=Level.OVERWORLD;
        public BlockPos pos=BlockPos.ZERO;
        /** Additive stage-03 state; absent in schema-1 worlds means untouched defaults. */
        public CompoundTag industry=new CompoundTag();
        /** Stage 04: optional, no item copies or migration of existing slots. */
        public CompoundTag commerce=new CompoundTag();
        public CompoundTag military=new CompoundTag();
        public final SimpleContainer equipment=new SimpleContainer(6){@Override public void setChanged(){super.setChanged();Station.this.setChanged();}};
        private Station(EmploymentData owner,UUID id,Profession.Sector sector){super(SLOTS);this.owner=owner;this.id=id;this.sector=sector;}
        @Override public void setChanged(){super.setChanged();if(owner!=null){owner.revision++;owner.setDirty();}}
        public boolean at(ServerLevel level,BlockPos position){return active&&dimension.equals(level.dimension())&&pos.equals(position);}
    }
    @Override public CompoundTag save(CompoundTag root){
        root.putInt("schema",1);ListTag entries=new ListTag();
        for(Station s:stations.values()){
            CompoundTag t=new CompoundTag();t.putUUID("id",s.id);t.putString("sector",s.sector.name());
            if(s.profession!=null)t.putString("profession",s.profession.id().toString());
            if(s.worker!=null)t.putUUID("worker",s.worker);
            t.putBoolean("active",s.active);t.putString("dimension",s.dimension.location().toString());t.putLong("position",s.pos.asLong());
            ListTag items=new ListTag();for(int i=0;i<SLOTS;i++)if(!s.getItem(i).isEmpty()){
                CompoundTag item=s.getItem(i).save(new CompoundTag());item.putInt("slot",i);items.add(item);
            }
            t.put("military",s.military.copy());ListTag gear=new ListTag();for(int i=0;i<6;i++){CompoundTag e=s.equipment.getItem(i).save(new CompoundTag());e.putInt("slot",i);gear.add(e);}t.put("military_equipment",gear);
            t.put("items",items);t.put("industry",s.industry.copy());t.put("commerce",s.commerce.copy());entries.add(t);
        }
        root.put("stations",entries);root.put("detachedSoldiers",detachedSoldiers.copy());return root;
    }
    public static EmploymentData load(CompoundTag root){
        if(root.getInt("schema")!=1)throw new IllegalArgumentException("Unsupported employment schema");
        EmploymentData data=new EmploymentData();data.detachedSoldiers.merge(root.getCompound("detachedSoldiers"));
        for(Tag raw:root.getList("stations",Tag.TAG_COMPOUND)){
            CompoundTag t=(CompoundTag)raw;Station s=new Station(data,t.getUUID("id"),Profession.Sector.valueOf(t.getString("sector")));
            s.military=t.getCompound("military").copy();for(Tag rawGear:t.getList("military_equipment",Tag.TAG_COMPOUND)){var e=(CompoundTag)rawGear;int slot=e.getInt("slot");if(slot<0||slot>=6)throw new IllegalArgumentException("Invalid military equipment slot");s.equipment.setItem(slot,ItemStack.of(e));}
            s.industry=t.getCompound("industry").copy();s.commerce=t.getCompound("commerce").copy();
            s.profession=Profession.byId(t.getString("profession"));s.worker=t.hasUUID("worker")?t.getUUID("worker"):null;
            s.active=t.getBoolean("active");s.dimension=ResourceKey.create(Registry.DIMENSION_REGISTRY,new ResourceLocation(t.getString("dimension")));s.pos=BlockPos.of(t.getLong("position"));
            for(Tag item:t.getList("items",Tag.TAG_COMPOUND)){CompoundTag e=(CompoundTag)item;int slot=e.getInt("slot");if(slot<0||slot>=SLOTS)throw new IllegalArgumentException("Invalid station slot");s.setItem(slot,ItemStack.of(e));}
            if(data.stations.putIfAbsent(s.id,s)!=null)throw new IllegalArgumentException("Duplicate station identity");
        }
        data.setDirty(false);return data;
    }
}
