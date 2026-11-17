package br.com.kraftcity.logistics;

import br.com.kraftcity.employment.EmploymentData;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.AABB;

/** Only loaded block entities; deterministic closest-first ordering. No chunk requests. */
public final class DiscardChests {
    public record Access(BlockPos pos,Container inventory){}
    private record Cached(long at,BlockPos post,AABB area,List<BlockPos> positions){}
    private static final Map<ServerLevel,Map<UUID,Cached>> CACHE=new WeakHashMap<>();
    public static boolean inRange(AABB area,BlockPos p){
        double x=p.getX()+.5,y=p.getY()+.5,z=p.getZ()+.5;
        double dx=Math.max(0,Math.max(area.minX+.5-x,x-(area.maxX-.5)));
        double dy=Math.max(0,Math.max(area.minY+.5-y,y-(area.maxY-.5)));
        double dz=Math.max(0,Math.max(area.minZ+.5-z,z-(area.maxZ-.5)));
        return dx*dx+dy*dy+dz*dz<=25.000001;
    }
    public static Container at(ServerLevel l,BlockPos p){
        if(!l.hasChunkAt(p))return null;var be=l.getBlockEntity(p);
        // Currency chests retain their specialized residential semantics.
        if(be instanceof br.com.residenciasaldeoes.block.EmeraldChestBlockEntity)return null;
        if(be instanceof ChestBlockEntity&&l.getBlockState(p).getBlock() instanceof ChestBlock b){
            var state=l.getBlockState(p);
            if(state.getValue(ChestBlock.TYPE)!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE&&!l.hasChunkAt(p.relative(ChestBlock.getConnectedDirection(state))))return null;
            var chest=ChestBlock.getContainer(b,state,l,p,false);
            return chest;
        }
        return be instanceof BarrelBlockEntity barrel?barrel:null;
    }
    public static List<Access> around(ServerLevel l,EmploymentData.Station s,AABB area){
        var map=CACHE.computeIfAbsent(l,k->new HashMap<>());var cache=map.get(s.id);long now=l.getGameTime();
        if(cache==null||!cache.post.equals(s.pos)||!cache.area.equals(area)||now<cache.at||now-cache.at>=20){
            var positions=new ArrayList<BlockPos>();var search=area.inflate(5);
            for(int x=((int)Math.floor(search.minX))>>4;x<=((int)Math.floor(search.maxX))>>4;x++)
                for(int z=((int)Math.floor(search.minZ))>>4;z<=((int)Math.floor(search.maxZ))>>4;z++){
                    var chunk=l.getChunkSource().getChunkNow(x,z);if(chunk==null)continue;
                    for(var be:chunk.getBlockEntities().values())if((be instanceof ChestBlockEntity||be instanceof BarrelBlockEntity)&&inRange(area,be.getBlockPos())&&at(l,be.getBlockPos())!=null)positions.add(be.getBlockPos().immutable());
                }
            positions.sort(Comparator.<BlockPos>comparingDouble(p->p.distSqr(s.pos)).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));
            cache=new Cached(now,s.pos,area,List.copyOf(positions));map.put(s.id,cache);
        }
        var result=new ArrayList<Access>();Set<Long> seen=new HashSet<>();
        for(var p:cache.positions){Container c=at(l,p);if(c!=null&&inRange(area,p)&&seen.add(identity(l,p)))result.add(new Access(p,c));}
        return List.copyOf(result);
    }
    private static long identity(ServerLevel l,BlockPos p){
        var state=l.getBlockState(p);if(state.getBlock() instanceof ChestBlock&&state.getValue(ChestBlock.TYPE)!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE){
            var other=p.relative(ChestBlock.getConnectedDirection(state));return Math.min(p.asLong(),other.asLong());
        }return p.asLong();
    }
    public static boolean canOpen(ServerLevel l,BlockPos p,net.minecraft.world.entity.player.Player actor){
        if(!l.hasChunkAt(p))return false;var be=l.getBlockEntity(p);
        if(be instanceof BaseContainerBlockEntity container&&!container.canOpen(actor))return false;
        var state=l.getBlockState(p);if(state.getBlock() instanceof ChestBlock&&state.getValue(ChestBlock.TYPE)!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE){
            var other=p.relative(ChestBlock.getConnectedDirection(state));if(!l.hasChunkAt(other))return false;
            if(l.getBlockEntity(other) instanceof BaseContainerBlockEntity container&&!container.canOpen(actor))return false;
        }return true;
    }
    public static void invalidate(ServerLevel l){CACHE.remove(l);}
    private DiscardChests(){}
}
