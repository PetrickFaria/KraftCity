package br.com.kraftcity.industry;

import br.com.kraftcity.employment.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;

/** A chunk index avoids scanning all jobs for every farmland random tick. */
public final class Irrigation {
    private record Index(long revision,Map<Long,List<EmploymentData.Station>> chunks){}
    private static final Map<ServerLevel,Index> CACHE=new WeakHashMap<>();
    public static boolean hydrates(LevelReader reader,BlockPos soil){
        if(!(reader instanceof ServerLevel l))return false;var data=EmploymentData.get(l);var index=CACHE.get(l);
        if(index==null||index.revision!=data.revision()){
            Map<Long,List<EmploymentData.Station>> chunks=new HashMap<>();
            for(var s:data.stations())if(s.active&&s.profession==Profession.FARMER&&s.dimension.equals(l.dimension())&&s.getItem(30).is(Items.WATER_BUCKET)){
                for(int x=(s.pos.getX()-4)>>4;x<=(s.pos.getX()+4)>>4;x++)for(int z=(s.pos.getZ()-4)>>4;z<=(s.pos.getZ()+4)>>4;z++)
                    chunks.computeIfAbsent(ChunkPos.asLong(x,z),k->new ArrayList<>()).add(s);
            }index=new Index(data.revision(),chunks);CACHE.put(l,index);
        }
        for(var s:index.chunks.getOrDefault(ChunkPos.asLong(soil.getX()>>4,soil.getZ()>>4),List.of()))if(s.active&&l.hasChunkAt(s.pos)&&s.getItem(30).is(Items.WATER_BUCKET)
            &&Math.abs(s.pos.getX()-soil.getX())<=Math.min(4,IndustrialRules.size(l,s)/2)&&Math.abs(s.pos.getZ()-soil.getZ())<=Math.min(4,IndustrialRules.size(l,s)/2)
            &&soil.getY()>=s.pos.getY()-1&&soil.getY()<=s.pos.getY()&&l.getBlockEntity(s.pos) instanceof WorkBlockEntity be&&s.id.equals(be.identity()))return true;
        return false;
    }
    private Irrigation(){}
}
