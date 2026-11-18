package br.com.kraftcity.builder;

import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.loading.FMLPaths;

public final class BlueprintLibrary {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"KraftCity-schematics");t.setDaemon(true);return t;});
    private static final Set<net.minecraft.server.MinecraftServer> REFRESHING=Collections.newSetFromMap(new WeakHashMap<>());
    public static Path root() throws IOException{var path=FMLPaths.CONFIGDIR.get().resolve("kraftcity/schematics");if(Files.isSymbolicLink(path.getParent()))throw new IOException("Pasta kraftcity não pode ser link simbólico");Files.createDirectories(path);if(Files.isSymbolicLink(path))throw new IOException("Pasta de esquemas não pode ser link simbólico");return path.toRealPath();}
    public static Blueprint readFile(Path root,Path path) throws Exception{
        if(Files.isSymbolicLink(path)||!path.toRealPath().startsWith(root.toRealPath())||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>BuilderModule.MAX_FILE.get())throw new IOException("Arquivo fora da pasta/limite");
        byte[] compressed=Files.readAllBytes(path);CompoundTag data;try(var in=new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(compressed)))){data=NbtIo.read(in,new NbtAccounter(BuilderModule.MAX_RAW.get()));}
        String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(compressed));data.remove("id");return Blueprint.read(data,path.getFileName().toString().replaceFirst("\\.nbt$",""),digest);
    }
    public static void refresh(ServerLevel l){var server=l.getServer();if(!REFRESHING.add(server))return;var d=BuilderData.get(l);d.libraryStatus="Lendo esquemas em segundo plano...";
        IO.submit(()->{var found=new ArrayList<Blueprint>();var errors=new ArrayList<String>();try{var dir=root();try(var paths=Files.list(dir)){for(var p:paths.filter(p->p.getFileName().toString().endsWith(".nbt")).sorted().limit(BuilderModule.MAX_FILES.get()).toList())try{found.add(readFile(dir,p));}catch(Exception e){errors.add(p.getFileName()+": "+e.getMessage());}}}catch(Exception e){errors.add(e.getMessage());}
            server.execute(()->{REFRESHING.remove(server);if(server.isStopped())return;var store=BuilderData.get(server.overworld());int added=0;for(var b:found)if(store.library.values().stream().noneMatch(old->old.digest.equals(b.digest))&&store.library.size()<BuilderModule.MAX_FILES.get()&&store.library.values().stream().mapToLong(x->x.cells.size()).sum()+b.cells.size()<=BuilderModule.LIBRARY_BLOCKS.get()){store.library.put(b.id,b);added++;}store.libraryStatus=errors.isEmpty()?"Biblioteca atualizada: "+added+" novos.":"Arquivo rejeitado: "+errors.get(0);store.setDirty();});});
    }
    private static final Map<UUID,Capture> CAPTURES=new LinkedHashMap<>();
    private static final class Capture {final UUID player;final ServerLevel level;final BlockPos min,max,size;final String name;final List<Blueprint.Cell> cells=new ArrayList<>();long cursor;Capture(ServerPlayer p,BlockPos a,BlockPos b,String name){player=p.getUUID();level=p.getLevel();min=new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()));max=new BlockPos(Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ()));size=max.subtract(min).offset(1,1,1);this.name=name;}}
    public static String capture(ServerPlayer p,BlockPos a,BlockPos b,String name){if(CAPTURES.containsKey(p.getUUID()))return "Captura já em andamento.";var c=new Capture(p,a,b,name.strip());long volume=(long)c.size.getX()*c.size.getY()*c.size.getZ();if(c.name.isEmpty()||c.name.length()>80||volume>BuilderModule.MAX_VOLUME.get()||c.size.getX()>BuilderModule.MAX_EDGE.get()||c.size.getY()>BuilderModule.MAX_EDGE.get()||c.size.getZ()>BuilderModule.MAX_EDGE.get())return "Nome/dimensões fora dos limites.";
        for(int x=c.min.getX()>>4;x<=c.max.getX()>>4;x++)for(int z=c.min.getZ()>>4;z<=c.max.getZ()>>4;z++)if(c.level.getChunkSource().getChunkNow(x,z)==null)return "Seleção contém chunks descarregados.";
        CAPTURES.put(p.getUUID(),c);return "Capturando blocos; NBT privado e entidades serão omitidos.";
    }
    public static void tickCaptures(net.minecraft.server.MinecraftServer server){int budget=BuilderModule.SCAN_BUDGET.get();for(var c:List.copyOf(CAPTURES.values())){if(budget<=0)break;var p=server.getPlayerList().getPlayer(c.player);if(p==null||p.level!=c.level){CAPTURES.remove(c.player);continue;}long volume=(long)c.size.getX()*c.size.getY()*c.size.getZ();boolean rejected=false;
            while(c.cursor<volume&&budget-->0){long i=c.cursor++;int x=(int)(i%c.size.getX()),z=(int)(i/c.size.getX()%c.size.getZ()),y=(int)(i/(c.size.getX()*c.size.getZ()));var at=c.min.offset(x,y,z);if(!c.level.hasChunkAt(at)||!c.level.mayInteract(p,at)||!p.mayBuild()||!captureAllowed(p,at)){BuilderNetwork.notice(p,"Captura recusada por chunk/permissão.");rejected=true;break;}var state=c.level.getBlockState(at);if(!state.isAir()&&!state.is(Blocks.STRUCTURE_VOID))c.cells.add(new Blueprint.Cell(new BlockPos(x,y,z),state));}
            if(rejected){CAPTURES.remove(c.player);continue;}if(c.cursor<volume)continue;CAPTURES.remove(c.player);if(c.cells.isEmpty()){BuilderNetwork.notice(p,"Seleção sem blocos.");continue;}
            p.getPersistentData().remove("KCEngineerA");p.getPersistentData().remove("KCEngineerB");var clear=new CompoundTag();clear.putString("type","selection_clear");BuilderNetwork.message(p,clear);var captured=List.copyOf(c.cells);
            IO.submit(()->{var schema=new Blueprint(UUID.randomUUID(),c.name,c.size,captured,"");String error="";try{var dir=root();var file=dir.resolve("kc-"+schema.id+".nbt");var tmp=Files.createTempFile(dir,"capture-",".tmp");NbtIo.writeCompressed(schema.save(),tmp.toFile());Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(Exception e){error=e.getMessage();}String exportError=error;
                server.execute(()->{if(server.isStopped())return;var store=BuilderData.get(server.overworld());if(store.library.size()>=BuilderModule.MAX_FILES.get()||store.library.values().stream().mapToLong(x->x.cells.size()).sum()+schema.cells.size()>BuilderModule.LIBRARY_BLOCKS.get()){var player=server.getPlayerList().getPlayer(c.player);if(player!=null)BuilderNetwork.notice(player,"Biblioteca no limite; arquivo exportado sem ativação.");return;}var old=store.library.values().stream().filter(x->x.digest.equals(schema.digest)).findFirst().orElse(null);if(old==null){store.library.put(schema.id,schema);store.setDirty();}var player=server.getPlayerList().getPlayer(c.player);if(player!=null)BuilderNetwork.notice(player,exportError.isEmpty()?"Esquema salvo: "+c.name+" ("+schema.cells.size()+" blocos). NBT privado removido.":"Esquema preservado no mundo; exportação: "+exportError);});});
        }}
    private static boolean captureAllowed(ServerPlayer p,BlockPos at){var e=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(p,net.minecraft.world.InteractionHand.MAIN_HAND,at,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at),Direction.UP,at,false));return !net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(e)&&e.getUseBlock()!=net.minecraftforge.eventbus.api.Event.Result.DENY;}
    public static void cancel(UUID player){CAPTURES.remove(player);}
    public static void stop(){CAPTURES.clear();REFRESHING.clear();}
}
