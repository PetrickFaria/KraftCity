package br.com.kraftcity.industry;

import br.com.kraftcity.employment.EmploymentData;
import br.com.kraftcity.employment.Profession;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;

final class ApiaryHiveStock {
    static final int COMB_RESERVE=6;
    private static final int PLANK_COST=3;
    private static final Map<Villager,Long> NEXT_SCAN=new WeakHashMap<>();

    private ApiaryHiveStock(){}

    static boolean isHive(ItemStack item){
        return item.getItem() instanceof BlockItem block && block.getBlock() instanceof BeehiveBlock;
    }

    private static int vacantSlot(EmploymentData.Station station){
        for(int slot=0;slot<EmploymentData.COMMON;slot++){
            if(station.getItem(slot).isEmpty())return slot;
        }
        return -1;
    }

    static boolean hasVacancy(EmploymentData.Station station){return vacantSlot(station)>=0;}

    static boolean craft(EmploymentData.Station station){
        if(IndustrialWork.find(station,ApiaryHiveStock::isHive,0,EmploymentData.COMMON)>=0)return false;
        var plan=new SimpleContainer(EmploymentData.COMMON);
        int combs=COMB_RESERVE,planks=PLANK_COST;
        for(int slot=0;slot<EmploymentData.COMMON;slot++){
            var stack=station.getItem(slot).copy();
            if(stack.is(Items.HONEYCOMB)){
                int used=Math.min(combs,stack.getCount());
                combs-=used;
                stack.shrink(used);
            }else if(stack.is(ItemTags.PLANKS)){
                int used=Math.min(planks,stack.getCount());
                planks-=used;
                stack.shrink(used);
            }
            plan.setItem(slot,stack);
        }
        if(combs>0||planks>0||!plan.addItem(new ItemStack(Items.BEEHIVE)).isEmpty())return false;
        for(int slot=0;slot<EmploymentData.COMMON;slot++){
            if(!ItemStack.matches(station.getItem(slot),plan.getItem(slot)))station.setItem(slot,plan.getItem(slot));
        }
        station.setChanged();
        return true;
    }

    static boolean work(ServerLevel level,EmploymentData.Station station,Villager worker,AABB area,List<BlockPos> active){
        long now=level.getGameTime();
        if(worker.isNoAi()||now<NEXT_SCAN.getOrDefault(worker,0L)
                ||!area.expandTowards(0,-1,0).contains(worker.position()))return false;
        NEXT_SCAN.put(worker,now+20);
        var min=new BlockPos(area.minX,area.minY,area.minZ);
        var max=new BlockPos(area.maxX-1,area.maxY-1,area.maxZ-1);
        int limit=ApiaryWork.hiveCapacity(level,station);
        if(active.size()>=limit){
            int vacant=vacantSlot(station);
            if(vacant<0)return false;
            for(var cell:BlockPos.betweenClosed(min,max)){
                var pos=cell.immutable();
                if(active.contains(pos)||!level.hasChunkAt(pos)||!belongsHere(level,station,pos)
                        ||!(level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive))continue;
                if(ApiaryWork.allHome(level,station,hive)&&collect(level,station,worker,pos,hive,vacant))return true;
            }
        }else{
            int slot=IndustrialWork.find(station,ApiaryHiveStock::isHive,0,EmploymentData.COMMON);
            if(slot<0)return craft(station);
            var sites=new java.util.ArrayList<BlockPos>();
            for(var cell:BlockPos.betweenClosed(min,max)){
                var pos=cell.immutable();
                if(level.hasChunkAt(pos)&&level.isEmptyBlock(pos)&&isolated(level,pos.below())
                        &&belongsHere(level,station,pos)&&facing(level,station,pos,area)!=null
                        &&level.getEntities(worker,new AABB(pos)).isEmpty())sites.add(pos);
            }
            sites.sort(Comparator.comparingDouble(pos->worker.distanceToSqr(Vec3.atCenterOf(pos))));
            for(var pos:sites)if(place(level,station,worker,pos,slot,area))return true;
        }
        NEXT_SCAN.put(worker,now+80);
        return false;
    }

    private static boolean collect(ServerLevel level,EmploymentData.Station station,Villager worker,
                                   BlockPos pos,BeehiveBlockEntity hive,int slot){
        if(!ApiaryWork.visible(level,worker,Vec3.atCenterOf(pos),pos))
            return ApiaryWork.approachHive(level,station,worker,pos);
        if(!IndustrialWork.allowed(level,station,worker,pos,null,"apiary_hive_pickup")
                ||ForgeHooks.onBlockBreakEvent(level,GameType.SURVIVAL,IndustrialWork.actor(level,station,worker),pos)<0
                ||level.getBlockEntity(pos)!=hive||!ApiaryWork.allHome(level,station,hive)||!station.getItem(slot).isEmpty())return false;
        var state=hive.getBlockState();
        var item=new ItemStack(state.getBlock());
        hive.saveToItem(item);
        item.getOrCreateTagElement("BlockStateTag").putString(BeehiveBlock.HONEY_LEVEL.getName(),
            Integer.toString(state.getValue(BeehiveBlock.HONEY_LEVEL)));
        if(!level.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL))return false;
        station.setItem(slot,item);
        station.setChanged();
        worker.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    private static boolean place(ServerLevel level,EmploymentData.Station station,Villager worker,BlockPos pos,int slot,AABB area){
        var support=pos.below();
        var hit=new BlockHitResult(Vec3.atBottomCenterOf(pos),Direction.UP,support,false);
        if(!ApiaryWork.visible(level,worker,hit.getLocation(),support))
            return ApiaryWork.approachHive(level,station,worker,pos);
        var direction=facing(level,station,pos,area);
        var item=station.getItem(slot);
        if(direction==null||!isHive(item)||!IndustrialWork.allowed(level,station,worker,pos,null,"apiary_hive_place")
                ||!isolated(level,support)||!level.isEmptyBlock(pos)||!level.getEntities(worker,new AABB(pos)).isEmpty()
                ||station.getItem(slot)!=item)return false;
        var actor=IndustrialWork.actor(level,station,worker);
        var placed=item.copy();
        placed.setCount(1);
        placed.getOrCreateTagElement("BlockStateTag").putString(HorizontalDirectionalBlock.FACING.getName(),direction.getName());
        actor.setItemInHand(InteractionHand.MAIN_HAND,placed);
        var backup=item.copy();
        station.removeItem(slot,1);
        boolean success=false;
        try{
            success=placed.useOn(new UseOnContext(actor,InteractionHand.MAIN_HAND,hit)).consumesAction();
        }finally{
            actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            if(!success)station.setItem(slot,backup);
        }
        if(!success)return false;
        station.setChanged();
        worker.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    static boolean belongsHere(ServerLevel level,EmploymentData.Station station,BlockPos pos){
        if(ApiaryWork.claimed(level,station,pos))return false;
        for(var other:EmploymentData.get(level).stations()){
            if(other==station||!other.active||other.worker==null||other.profession!=Profession.BEEKEEPER
                    ||!station.dimension.equals(other.dimension))continue;
            if(IndustrialWork.area(level,other).contains(Vec3.atCenterOf(pos))){
                double ours=station.pos.distSqr(pos),theirs=other.pos.distSqr(pos);
                if(theirs<ours||theirs==ours&&other.id.compareTo(station.id)<0)return false;
            }
        }
        return true;
    }

    private static boolean isolated(ServerLevel level,BlockPos pos){
        var fence=level.getBlockState(pos);
        return fence.getBlock() instanceof FenceBlock&&!fence.getValue(FenceBlock.NORTH)
            &&!fence.getValue(FenceBlock.SOUTH)&&!fence.getValue(FenceBlock.EAST)&&!fence.getValue(FenceBlock.WEST);
    }

    private static Direction facing(ServerLevel level,EmploymentData.Station station,BlockPos pos,AABB area){
        var post=level.getBlockState(station.pos);
        if(!post.hasProperty(br.com.kraftcity.employment.WorkBlock.FACING))return null;
        var direction=post.getValue(br.com.kraftcity.employment.WorkBlock.FACING);
        var exit=pos.relative(direction);
        return area.contains(Vec3.atCenterOf(exit))&&level.hasChunkAt(exit)
                &&level.getBlockState(exit).getCollisionShape(level,exit).isEmpty()
                &&level.getFluidState(exit).isEmpty()?direction:null;
    }
}
