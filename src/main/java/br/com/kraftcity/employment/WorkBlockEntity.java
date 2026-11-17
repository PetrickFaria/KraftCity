package br.com.kraftcity.employment;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class WorkBlockEntity extends BlockEntity {
    private UUID identity;
    public WorkBlockEntity(BlockPos pos,BlockState state){super(WorkModule.ENTITY.get(),pos,state);}
    public UUID identity(){return identity;}
    public void bind(UUID id){identity=id;setChanged();}
    @Override public void onLoad(){
        super.onLoad();
        if(level instanceof ServerLevel server&&identity!=null){
            var s=EmploymentData.get(server).station(identity);
            // A surviving saved block wins over duplicate portable tokens after an interrupted save.
            if(s!=null&&!s.active&&s.dimension.equals(server.dimension())&&s.pos.equals(worldPosition)){s.active=true;s.setChanged();}
        }
    }
    public EmploymentData.Station station(){
        if(!(level instanceof ServerLevel server))return null;
        var data=EmploymentData.get(server);
        if(identity==null){
            var block=(WorkBlock)getBlockState().getBlock();var s=data.create(block.sector,server,worldPosition);
            s.profession=block.profession;s.setChanged();bind(s.id);
        }
        var s=data.station(identity);return s!=null&&s.at(server,worldPosition)?s:null;
    }
    @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);if(identity!=null)tag.putUUID("station",identity);}
    @Override public void load(CompoundTag tag){super.load(tag);identity=tag.hasUUID("station")?tag.getUUID("station"):null;}
}
