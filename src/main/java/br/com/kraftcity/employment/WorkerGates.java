package br.com.kraftcity.employment;

import br.com.kraftcity.industry.IndustrialWork;
import br.com.residenciasaldeoes.VillagerResidenceEvents;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.eventbus.api.Event;

public final class WorkerGates {
    private record OpenGate(Block block,UUID worker,long closeAfter){}
    private static final Map<ServerLevel,Map<BlockPos,OpenGate>> OPENED=new WeakHashMap<>();
    private static final Map<Villager,Long> NEXT_OPEN=new WeakHashMap<>();

    private WorkerGates(){}

    static void openAlongPath(ServerLevel level,Villager worker){
        if(worker.isNoAi()||worker.isSleeping())return;
        var station=EmploymentData.get(level).assigned(worker.getUUID());
        if(station==null||!station.active)return;
        var path=worker.getNavigation().getPath();
        if(path==null||path.isDone())return;
        long now=level.getGameTime();
        if(NEXT_OPEN.getOrDefault(worker,Long.MIN_VALUE)>now)return;
        for(int i=path.getNextNodeIndex();i<Math.min(path.getNodeCount(),path.getNextNodeIndex()+2);i++){
            var pos=path.getNode(i).asBlockPos();
            if(!level.hasChunkAt(pos)||worker.distanceToSqr(Vec3.atCenterOf(pos))>4)return;
            var state=level.getBlockState(pos);
            boolean door=state.is(BlockTags.WOODEN_DOORS)&&state.getBlock() instanceof DoorBlock
                &&VillagerResidenceEvents.ownsWorkerRest(worker)
                &&!worker.getPersistentData().contains("residenciasaldeoes_night_door_task")
                &&!worker.getPersistentData().contains("residenciasaldeoes_emerald_transfer");
            if(!door&&!(state.getBlock() instanceof FenceGateBlock))continue;
            if(door&&state.getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER){
                pos=pos.below();
                state=level.getBlockState(pos);
                if(!(state.getBlock() instanceof DoorBlock))continue;
            }
            if(state.getValue(BlockStateProperties.OPEN))continue;
            NEXT_OPEN.put(worker,now+10);
            var actor=IndustrialWork.actor(level,station,worker);
            var hit=new BlockHitResult(Vec3.atCenterOf(pos),worker.getDirection().getOpposite(),pos,false);
            if(!IndustrialWork.allowed(level,station,worker,pos,null,door?"open_door":"open_gate")||!level.mayInteract(actor,pos))return;
            var interaction=ForgeHooks.onRightClickBlock(actor,InteractionHand.MAIN_HAND,pos,hit);
            if(interaction.isCanceled()||interaction.getUseBlock()==Event.Result.DENY||level.getBlockState(pos)!=state)return;
            if(door)((DoorBlock)state.getBlock()).setOpen(null,level,state,pos,true);
            else state.use(level,actor,InteractionHand.MAIN_HAND,hit);
            var opened=level.getBlockState(pos);
            if(opened.is(state.getBlock())&&opened.getValue(BlockStateProperties.OPEN)){
                OPENED.computeIfAbsent(level,key->new HashMap<>()).put(pos,new OpenGate(opened.getBlock(),worker.getUUID(),now+15));
            }
            return;
        }
    }

    static void closeVacant(ServerLevel level){
        var gates=OPENED.get(level);
        if(gates==null)return;
        long now=level.getGameTime();
        gates.entrySet().removeIf(entry->{
            var pos=entry.getKey();
            if(!level.hasChunkAt(pos))return false;
            var state=level.getBlockState(pos);
            if(!state.is(entry.getValue().block())||!state.getValue(BlockStateProperties.OPEN)
                ||state.getValue(BlockStateProperties.POWERED))return true;
            if(now<entry.getValue().closeAfter()||!level.getEntities(null,new AABB(pos).inflate(.85,1,.85)).isEmpty())return false;
            if(!(level.getEntity(entry.getValue().worker()) instanceof Villager worker))return false;
            var station=EmploymentData.get(level).assigned(worker.getUUID());
            boolean door=state.getBlock() instanceof DoorBlock;
            if(station==null||!IndustrialWork.allowed(level,station,worker,pos,null,door?"close_door":"close_gate")
                ||level.getBlockState(pos)!=state)return true;
            if(door)((DoorBlock)state.getBlock()).setOpen(null,level,state,pos,false);
            else if(level.setBlock(pos,state.setValue(FenceGateBlock.OPEN,false),Block.UPDATE_ALL)){
                level.playSound(null,pos,net.minecraft.sounds.SoundEvents.FENCE_GATE_CLOSE,
                    net.minecraft.sounds.SoundSource.BLOCKS,.8F,1F);
            }
            return true;
        });
    }

}
