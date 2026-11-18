package br.com.kraftcity.builder;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraftforge.registries.ForgeRegistries;

/** Native 1.19.2 StructureTemplate NBT, also the on-disk format used by Create 0.5.0.i. */
public final class Blueprint {
    public record Cell(BlockPos pos,BlockState state){}
    public final UUID id;public final String name;public final BlockPos size;public final List<Cell> cells;public final String digest;
    public final Map<BlockPos,Integer> indices;public final Map<Item,Integer> materials;public final List<String> problems;
    public Blueprint(UUID id,String name,BlockPos size,List<Cell> cells,String digest){
        this.id=id;this.name=name;this.size=size;this.cells=cells.stream().map(c->c.state().getBlock() instanceof BedBlock?new Cell(c.pos(),c.state().setValue(BedBlock.OCCUPIED,false)):c).toList();cells=this.cells;this.digest=fingerprint(size,this.cells);
        var byPos=new HashMap<BlockPos,Integer>();for(int i=0;i<cells.size();i++)byPos.put(cells.get(i).pos,i);indices=Map.copyOf(byPos);
        var counts=new LinkedHashMap<Item,Integer>();var errors=new LinkedHashSet<String>();for(int i=0;i<cells.size();i++){
            var c=cells.get(i);if(unsafe(c.state))errors.add("Bloco não autorizado: "+key(c.state));
            Item item=material(c.state);if(item==Items.AIR)errors.add("Sem procedimento seguro: "+key(c.state));else if(primary(i))counts.merge(item,1,Integer::sum);if(c.state.hasProperty(BlockStateProperties.WATERLOGGED)&&c.state.getValue(BlockStateProperties.WATERLOGGED))counts.merge(Items.WATER_BUCKET,1,Integer::sum);
            if(c.state.getBlock() instanceof CropBlock&&c.state.getValue(((CropBlock)c.state.getBlock()).getAgeProperty())>0)errors.add("Cultivo maduro exige crescimento: "+key(c.state));
            if(pairOffset(c.state)!=null){Integer peer=indices.get(c.pos.offset(pairOffset(c.state)));if(peer==null||cells.get(peer).state.getBlock()!=c.state.getBlock()||pairOffset(cells.get(peer).state)==null||!cells.get(peer).pos.offset(pairOffset(cells.get(peer).state)).equals(c.pos))errors.add("Par incompleto: "+key(c.state));}
        }materials=Map.copyOf(counts);problems=List.copyOf(errors);
    }
    private static String fingerprint(BlockPos size,List<Cell> cells){try{var sha=java.security.MessageDigest.getInstance("SHA-256");sha.update((size.getX()+","+size.getY()+","+size.getZ()+";").getBytes(java.nio.charset.StandardCharsets.UTF_8));var ordered=new ArrayList<>(cells);ordered.sort(java.util.Comparator.comparing(Cell::pos));for(var c:ordered)sha.update((c.pos.asLong()+":"+c.state.toString()+";").getBytes(java.nio.charset.StandardCharsets.UTF_8));return java.util.HexFormat.of().formatHex(sha.digest());}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public static String key(BlockState s){return ForgeRegistries.BLOCKS.getKey(s.getBlock()).toString();}
    public static boolean unsafe(BlockState s){String id=key(s);return id.startsWith("residenciasaldeoes:")&&!id.equals("residenciasaldeoes:reinforced_door")||s.getBlock() instanceof CommandBlock||s.is(Blocks.STRUCTURE_BLOCK)||s.is(Blocks.JIGSAW)||s.is(Blocks.SPAWNER)||s.is(Blocks.BEDROCK)||s.is(Blocks.BARRIER)||s.is(Blocks.END_PORTAL)||s.is(Blocks.NETHER_PORTAL)||s.is(Blocks.END_GATEWAY)||s.is(Blocks.MOVING_PISTON);}
    public static Item material(BlockState s){if(s.is(Blocks.WATER))return s.getValue(LiquidBlock.LEVEL)==0?Items.WATER_BUCKET:Items.AIR;if(s.is(Blocks.LAVA))return s.getValue(LiquidBlock.LEVEL)==0?Items.LAVA_BUCKET:Items.AIR;return s.getBlock().asItem();}
    public static BlockPos pairOffset(BlockState s){if(s.hasProperty(BedBlock.PART)&&s.getBlock() instanceof BedBlock)return BlockPos.ZERO.relative(s.getValue(BedBlock.FACING),s.getValue(BedBlock.PART)==BedPart.FOOT?1:-1);if(s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF))return new BlockPos(0,s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.LOWER?1:-1,0);return null;}
    public boolean primary(int i){var s=cells.get(i).state;return !(s.getBlock() instanceof BedBlock&&s.getValue(BedBlock.PART)==BedPart.HEAD)&&(!s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)||s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.LOWER);}
    public int peer(int i){var c=cells.get(i);var offset=pairOffset(c.state);return offset==null?-1:indices.getOrDefault(c.pos.offset(offset),-1);}
    public CompoundTag save(){var t=new CompoundTag();t.putUUID("id",id);t.putString("name",name);t.putString("digest",digest);var dims=new ListTag();dims.add(IntTag.valueOf(size.getX()));dims.add(IntTag.valueOf(size.getY()));dims.add(IntTag.valueOf(size.getZ()));t.put("size",dims);
        var palette=new ListTag();var states=new ArrayList<BlockState>();var stateIds=new HashMap<BlockState,Integer>();var blocks=new ListTag();for(var c:cells){int index=stateIds.getOrDefault(c.state,-1);if(index<0){index=states.size();states.add(c.state);stateIds.put(c.state,index);palette.add(NbtUtils.writeBlockState(c.state));}var b=new CompoundTag();var pos=new ListTag();pos.add(IntTag.valueOf(c.pos.getX()));pos.add(IntTag.valueOf(c.pos.getY()));pos.add(IntTag.valueOf(c.pos.getZ()));b.put("pos",pos);b.putInt("state",index);blocks.add(b);}t.put("palette",palette);t.put("blocks",blocks);t.put("entities",new ListTag());t.putInt("DataVersion",net.minecraft.SharedConstants.getCurrentVersion().getWorldVersion());return t;
    }
    public static Blueprint read(CompoundTag t,String name,String digest){
        var dims=t.getList("size",Tag.TAG_INT);if(dims.size()!=3)throw new IllegalArgumentException("Dimensões ausentes");var size=new BlockPos(dims.getInt(0),dims.getInt(1),dims.getInt(2));long volume=(long)size.getX()*size.getY()*size.getZ();
        if(size.getX()<1||size.getY()<1||size.getZ()<1||size.getX()>BuilderModule.MAX_EDGE.get()||size.getY()>BuilderModule.MAX_EDGE.get()||size.getZ()>BuilderModule.MAX_EDGE.get()||volume>BuilderModule.MAX_VOLUME.get())throw new IllegalArgumentException("Limite de dimensão/volume excedido");
        if(t.contains("palettes"))throw new IllegalArgumentException("Paletas alternativas não suportadas; exporte uma paleta única");
        var palette=t.getList("palette",Tag.TAG_COMPOUND);if(palette.isEmpty()||palette.size()>BuilderModule.MAX_VOLUME.get())throw new IllegalArgumentException("Paleta inválida");var states=new ArrayList<BlockState>();
        for(var raw:palette){var p=(CompoundTag)raw;var key=ResourceLocation.tryParse(p.getString("Name"));if(key==null||!ForgeRegistries.BLOCKS.containsKey(key))throw new IllegalArgumentException("Bloco desconhecido: "+p.getString("Name"));var block=ForgeRegistries.BLOCKS.getValue(key);if(p.contains("Properties")&&!p.contains("Properties",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Properties inválido");var props=p.getCompound("Properties");for(String prop:props.getAllKeys()){var property=block.getStateDefinition().getProperty(prop);if(property==null||property.getValue(props.getString(prop)).isEmpty())throw new IllegalArgumentException("Estado inválido: "+key+" "+prop);}states.add(NbtUtils.readBlockState(p));}
        var cells=new ArrayList<Cell>();var seen=new HashSet<BlockPos>();var rawBlocks=t.getList("blocks",Tag.TAG_COMPOUND);if(rawBlocks.size()>volume)throw new IllegalArgumentException("Lista de blocos excede o volume");
        for(var raw:rawBlocks){var b=(CompoundTag)raw;var p=b.getList("pos",Tag.TAG_INT);int index=b.getInt("state");if(!b.contains("state",Tag.TAG_INT)||p.size()!=3||index<0||index>=states.size())throw new IllegalArgumentException("Célula inválida");var at=new BlockPos(p.getInt(0),p.getInt(1),p.getInt(2));if(at.getX()<0||at.getY()<0||at.getZ()<0||at.getX()>=size.getX()||at.getY()>=size.getY()||at.getZ()>=size.getZ()||!seen.add(at))throw new IllegalArgumentException("Posição fora da região/duplicada");var state=states.get(index);if(!state.isAir()&&!state.is(Blocks.STRUCTURE_VOID))cells.add(new Cell(at,state));}
        cells.sort(Comparator.<Cell>comparingInt(c->c.pos.getY()).thenComparingInt(c->c.state.getMaterial().isSolidBlocking()?0:1).thenComparingInt(c->c.pos.getZ()).thenComparingInt(c->c.pos.getX()));
        if(cells.isEmpty())throw new IllegalArgumentException("Esquema sem blocos efetivos");return new Blueprint(t.hasUUID("id")?t.getUUID("id"):UUID.randomUUID(),name,size,cells,digest);
    }
    public static BlockPos transform(BlockPos p,BlockPos size,int turns,int mirror){int x=mirror==1?size.getX()-1-p.getX():p.getX(),z=mirror==2?size.getZ()-1-p.getZ():p.getZ();return switch(Math.floorMod(turns,4)){case 1->new BlockPos(size.getZ()-1-z,p.getY(),x);case 2->new BlockPos(size.getX()-1-x,p.getY(),size.getZ()-1-z);case 3->new BlockPos(z,p.getY(),size.getX()-1-x);default->new BlockPos(x,p.getY(),z);};}
    public static BlockState transform(BlockState s,int turns,int mirror){return s.mirror(mirror==1?Mirror.FRONT_BACK:mirror==2?Mirror.LEFT_RIGHT:Mirror.NONE).rotate(Rotation.values()[Math.floorMod(turns,4)]);}
    public BlockPos transformedSize(int turns){return turns%2==0?size:new BlockPos(size.getZ(),size.getY(),size.getX());}
}
