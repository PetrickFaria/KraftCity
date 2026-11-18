package br.com.kraftcity.builder;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** One server-wide authority; station tokens never contain any of this storage. */
public final class BuilderData extends SavedData {
    public static final String NAME="kraftcity_builder";
    public static final int INK=27,EMERALDS=28,BLANK=29,OUTPUT=30,PLAN=31;
    public final SimpleContainer stock=new SimpleContainer(32){@Override public void setChanged(){super.setChanged();BuilderData.this.setDirty();}};
    public final Map<UUID,Blueprint> library=new LinkedHashMap<>();
    public final Map<UUID,Paper> papers=new LinkedHashMap<>();
    public final Map<UUID,Job> jobs=new LinkedHashMap<>();
    public int ink,charge;public UUID printing;public int printTicks,printPaid;public String libraryStatus="Biblioteca pronta.";
    public static final class Paper {
        public final UUID id,schema,owner;public boolean confirmed,issued;public ResourceKey<Level> dimension=Level.OVERWORLD;public BlockPos origin=BlockPos.ZERO;public int rotation,mirror;public UUID job;
        public Paper(UUID id,UUID schema,UUID owner){this.id=id;this.schema=schema;this.owner=owner;}
    }
    public static final class Task {
        public int index,elapsed;public UUID worker;public long flightAt=-1,lastActive=-100;
        public Task(int index,UUID worker){this.index=index;this.worker=worker;}
    }
    public static final class Job {
        public final UUID id,paper,owner;public final ResourceKey<Level> dimension;public final BlockPos origin;public final int rotation,mirror;public final boolean replace;
        public boolean paused,cancelled;public int cursor,placed;public final BitSet done=new BitSet(),credits=new BitSet();
        public final Map<UUID,Integer> workers=new LinkedHashMap<>();public final Map<Integer,Task> tasks=new LinkedHashMap<>();
        public String status="Convocando Construtores.";public long alertAt=-300;public String alert="";
        public Job(UUID id,Paper p,boolean replace){this.id=id;paper=p.id;owner=p.owner;dimension=p.dimension;origin=p.origin;rotation=p.rotation;mirror=p.mirror;this.replace=replace;}
    }
    public static BuilderData get(ServerLevel l){return l.getServer().overworld().getDataStorage().computeIfAbsent(BuilderData::load,BuilderData::new,NAME);}
    public void refill(){while(ink<=900&&stock.getItem(INK).is(Items.INK_SAC)){stock.removeItem(INK,1);ink+=100;setDirty();}while(charge<=400&&stock.getItem(EMERALDS).is(Items.EMERALD)){stock.removeItem(EMERALDS,1);charge+=100;setDirty();}}
    public long inkAvailable(){return (long)ink+(stock.getItem(INK).is(Items.INK_SAC)?stock.getItem(INK).getCount()*100L:0);}
    public boolean spendInk(int amount){if(amount<0||inkAvailable()<amount)return false;while(amount>0){refill();int n=Math.min(amount,ink);ink-=n;amount-=n;}setDirty();return true;}
    public boolean print(UUID schema,UUID owner){var bp=library.get(schema);if(bp==null||printing!=null||!stock.getItem(BLANK).is(BuilderModule.BLANK.get())||!stock.getItem(OUTPUT).isEmpty()||inkAvailable()<Math.min(1000,bp.cells.size()))return false;var p=new Paper(UUID.randomUUID(),schema,owner);papers.put(p.id,p);printing=p.id;printTicks=0;printPaid=0;stock.removeItem(BLANK,1);setDirty();return true;}
    public void printTick(){refill();if(printing==null)return;var p=papers.get(printing);var bp=p==null?null:library.get(p.schema);if(bp==null){libraryStatus="Gravação suspensa: referência ausente.";return;}int reserve=(int)Math.min(inkAvailable(),bp.cells.size()-printPaid);if(reserve>0){spendInk(reserve);printPaid+=reserve;}
        if(printPaid<bp.cells.size()){libraryStatus="Gravação pausada: reponha tinta ("+printPaid+"/"+bp.cells.size()+").";setDirty();return;}if(++printTicks<100){setDirty();return;}printTicks=100;if(!stock.getItem(OUTPUT).isEmpty()){libraryStatus="Gravação aguardando saída livre.";setDirty();return;}p.issued=true;var item=new ItemStack(BuilderModule.PAPER.get());item.getOrCreateTag().putUUID("KraftCityPaper",p.id);stock.setItem(OUTPUT,item);printing=null;printTicks=0;printPaid=0;setDirty();}
    public String start(UUID paper,boolean replace){var p=papers.get(paper);if(p==null||!p.issued||!p.confirmed||p.job!=null||!library.containsKey(p.schema))return "Papel inválido ou já utilizado.";var bp=library.get(p.schema);if(!bp.problems.isEmpty())return bp.problems.get(0);if(jobs.values().stream().filter(j->!j.cancelled&&j.done.cardinality()<library.get(papers.get(j.paper).schema).cells.size()).count()>=BuilderModule.MAX_JOBS.get())return "Limite de obras ativas.";
        var size=bp.transformedSize(p.rotation);for(var other:jobs.values())if(!other.cancelled){var op=papers.get(other.paper);var ob=library.get(op.schema);var os=ob.transformedSize(other.rotation);if(other.dimension.equals(p.dimension)&&other.done.cardinality()<ob.cells.size()&&new net.minecraft.world.phys.AABB(other.origin,other.origin.offset(os)).intersects(new net.minecraft.world.phys.AABB(p.origin,p.origin.offset(size))))return "Outra obra ativa ocupa esta região.";}
        var j=new Job(UUID.randomUUID(),p,replace);p.job=j.id;jobs.put(j.id,j);setDirty();return "Obra iniciada.";
    }
    public String resume(Job j){if(!j.cancelled)return "Obra já ativa.";var b=library.get(papers.get(j.paper).schema);if(j.done.cardinality()>=b.cells.size())return "Obra concluída.";if(jobs.values().stream().filter(o->!o.cancelled&&o.done.cardinality()<library.get(papers.get(o.paper).schema).cells.size()).count()>=BuilderModule.MAX_JOBS.get())return "Limite de obras ativas.";var size=b.transformedSize(j.rotation);var box=new net.minecraft.world.phys.AABB(j.origin,j.origin.offset(size));for(var o:jobs.values())if(o!=j&&!o.cancelled&&o.dimension.equals(j.dimension)){var other=library.get(papers.get(o.paper).schema);if(o.done.cardinality()<other.cells.size()&&box.intersects(new net.minecraft.world.phys.AABB(o.origin,o.origin.offset(other.transformedSize(o.rotation)))))return "Outra obra ativa ocupa esta região.";}j.cancelled=false;j.paused=false;j.status="Retomando obra existente.";setDirty();return "Obra retomada.";}
    public UUID assigned(UUID worker){for(var j:jobs.values())if(!j.cancelled&&j.workers.containsKey(worker))return j.id;return null;}
    @Override public CompoundTag save(CompoundTag root){root.putInt("schema",1);root.putInt("ink",ink);root.putInt("charge",charge);root.put("stock",items(stock));if(printing!=null)root.putUUID("printing",printing);root.putInt("printTicks",printTicks);root.putInt("printPaid",printPaid);
        var lib=new ListTag();for(var b:library.values())lib.add(b.save());root.put("library",lib);var ps=new ListTag();for(var p:papers.values()){var t=new CompoundTag();t.putUUID("id",p.id);t.putUUID("schemaId",p.schema);t.putUUID("owner",p.owner);t.putBoolean("confirmed",p.confirmed);t.putBoolean("issued",p.issued);t.putString("dimension",p.dimension.location().toString());t.putLong("origin",p.origin.asLong());t.putInt("rotation",p.rotation);t.putInt("mirror",p.mirror);if(p.job!=null)t.putUUID("job",p.job);ps.add(t);}root.put("papers",ps);
        var js=new ListTag();for(var j:jobs.values()){var t=new CompoundTag();t.putUUID("id",j.id);t.putUUID("paper",j.paper);t.putBoolean("replace",j.replace);t.putBoolean("paused",j.paused);t.putBoolean("cancelled",j.cancelled);t.putInt("cursor",j.cursor);t.putInt("placed",j.placed);t.putLongArray("done",j.done.toLongArray());t.putLongArray("credits",j.credits.toLongArray());t.putString("status",j.status);var ws=new ListTag();for(var w:j.workers.entrySet()){var e=new CompoundTag();e.putUUID("id",w.getKey());e.putInt("role",w.getValue());ws.add(e);}t.put("workers",ws);var ts=new ListTag();for(var task:j.tasks.values()){var e=new CompoundTag();e.putInt("index",task.index);e.putUUID("worker",task.worker);e.putInt("elapsed",task.elapsed);ts.add(e);}t.put("tasks",ts);js.add(t);}root.put("jobs",js);return root;
    }
    public static ListTag items(Container c){var list=new ListTag();for(int i=0;i<c.getContainerSize();i++)if(!c.getItem(i).isEmpty()){var e=c.getItem(i).save(new CompoundTag());e.putInt("slot",i);list.add(e);}return list;}
    public static BuilderData load(CompoundTag root){if(root.getInt("schema")!=1)throw new IllegalArgumentException("Unknown builder schema");var d=new BuilderData();d.ink=root.getInt("ink");d.charge=root.getInt("charge");if(d.ink<0||d.ink>1000||d.charge<0||d.charge>500)throw new IllegalArgumentException("Invalid builder charge");for(var raw:root.getList("stock",Tag.TAG_COMPOUND)){var e=(CompoundTag)raw;int slot=e.getInt("slot");if(slot<0||slot>=32)throw new IllegalArgumentException("Invalid builder slot");d.stock.setItem(slot,ItemStack.of(e));}
        for(var raw:root.getList("library",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;var b=Blueprint.read(t,t.getString("name"),t.getString("digest"));d.library.put(b.id,b);}
        for(var raw:root.getList("papers",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;var p=new Paper(t.getUUID("id"),t.getUUID("schemaId"),t.getUUID("owner"));p.confirmed=t.getBoolean("confirmed");p.issued=t.getBoolean("issued");p.dimension=ResourceKey.create(Registry.DIMENSION_REGISTRY,new ResourceLocation(t.getString("dimension")));p.origin=BlockPos.of(t.getLong("origin"));p.rotation=Math.floorMod(t.getInt("rotation"),4);p.mirror=Math.max(0,Math.min(2,t.getInt("mirror")));p.job=t.hasUUID("job")?t.getUUID("job"):null;d.papers.put(p.id,p);}
        for(var raw:root.getList("jobs",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;var p=d.papers.get(t.getUUID("paper"));if(p==null||!d.library.containsKey(p.schema))throw new IllegalArgumentException("Orphan builder job");var j=new Job(t.getUUID("id"),p,t.getBoolean("replace"));j.paused=t.getBoolean("paused");j.cancelled=t.getBoolean("cancelled");j.cursor=t.getInt("cursor");j.placed=t.getInt("placed");j.done.or(BitSet.valueOf(t.getLongArray("done")));j.credits.or(BitSet.valueOf(t.getLongArray("credits")));j.status=t.getString("status");for(var e:t.getList("workers",Tag.TAG_COMPOUND)){var w=(CompoundTag)e;j.workers.put(w.getUUID("id"),w.getInt("role"));}for(var e:t.getList("tasks",Tag.TAG_COMPOUND)){var task=(CompoundTag)e;var a=new Task(task.getInt("index"),task.getUUID("worker"));a.elapsed=Math.max(0,task.getInt("elapsed"));j.tasks.put(a.index,a);}d.jobs.put(j.id,j);}
        d.printing=root.hasUUID("printing")?root.getUUID("printing"):null;d.printPaid=Math.max(0,root.getInt("printPaid"));d.printTicks=Math.max(0,Math.min(100,root.getInt("printTicks")));d.setDirty(false);return d;
    }
}
