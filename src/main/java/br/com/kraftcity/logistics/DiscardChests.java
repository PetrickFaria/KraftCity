package br.com.kraftcity.logistics;

import br.com.kraftcity.employment.EmploymentData;
import br.com.kraftcity.employment.Employment;
import br.com.kraftcity.employment.WorkBlock;
import br.com.kraftcity.industry.IndustrialWork;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
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
            if(!state.hasProperty(ChestBlock.TYPE)||!state.hasProperty(ChestBlock.FACING))return null;
            if(state.getValue(ChestBlock.TYPE)!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE&&!l.hasChunkAt(p.relative(ChestBlock.getConnectedDirection(state))))return null;
            var chest=ChestBlock.getContainer(b,state,l,p,false);
            return chest;
        }
        return be instanceof BarrelBlockEntity barrel?barrel:null;
    }
    public static List<Access> around(ServerLevel l,EmploymentData.Station s,AABB area){
        var map=CACHE.computeIfAbsent(l,k->new HashMap<>());var cache=map.get(s.id);long now=l.getGameTime();
        if(cache==null||!cache.post.equals(s.pos)||!cache.area.equals(area)||now<cache.at||now-cache.at>=20){
            var positions=loadedPositions(l,s,area,p->at(l,p)!=null);
            cache=new Cached(now,s.pos,area,List.copyOf(positions));map.put(s.id,cache);
        }
        var result=new ArrayList<Access>();Set<Long> seen=new HashSet<>();
        for(var p:cache.positions){Container c=at(l,p);if(c!=null&&inRange(area,p)&&seen.add(identity(l,p)))result.add(new Access(p,c));}
        return List.copyOf(result);
    }
    private static List<BlockPos> loadedPositions(ServerLevel level,EmploymentData.Station station,AABB area,
                                                   java.util.function.Predicate<BlockPos> accepts){
        var positions=discover(level,area,accepts);
        positions.sort(Comparator.<BlockPos>comparingDouble(p->p.distSqr(station.pos))
            .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));
        return positions;
    }

    private static List<BlockPos> discover(ServerLevel level,AABB area,java.util.function.Predicate<BlockPos> accepts){
        var positions=new ArrayList<BlockPos>();
        var search=area.inflate(5);
        for(int x=((int)Math.floor(search.minX))>>4;x<=((int)Math.floor(search.maxX))>>4;x++){
            for(int z=((int)Math.floor(search.minZ))>>4;z<=((int)Math.floor(search.maxZ))>>4;z++){
                var chunk=level.getChunkSource().getChunkNow(x,z);
                if(chunk==null)continue;
                for(var pos:chunk.getBlockEntitiesPos()){
                    if(inRange(area,pos)&&accepts.test(pos))positions.add(pos.immutable());
                }
            }
        }
        return positions;
    }

    /** The existing discovery cache above is also used by jobs withdrawing supplies. */
    public static List<BlockPos> destinations(ServerLevel level,EmploymentData.Station station,AABB area){
        var facing=Direction.NORTH;
        if(level.hasChunkAt(station.pos)){
            var state=level.getBlockState(station.pos);
            if(state.hasProperty(WorkBlock.FACING))facing=state.getValue(WorkBlock.FACING);
        }
        var left=facing.getCounterClockWise();
        var positions=discover(level,area,pos->exposed(level,pos)&&(at(level,pos)!=null||capability(level,pos)!=null));
        positions.sort(Comparator.<BlockPos>comparingDouble(p->borderDistance(area,p))
            .thenComparingInt(p->-(p.getX()-station.pos.getX())*left.getStepX()-(p.getZ()-station.pos.getZ())*left.getStepZ())
            .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));
        var seen=new HashSet<Long>();
        return positions.stream().filter(pos->seen.add(identity(level,pos))).toList();
    }

    private static boolean exposed(ServerLevel level,BlockPos pos){
        for(var direction:Direction.values()){
            var neighbor=pos.relative(direction);
            if(level.hasChunkAt(neighbor)&&!level.getBlockState(neighbor).isFaceSturdy(level,neighbor,direction.getOpposite()))return true;
        }
        return false;
    }

    private static double borderDistance(AABB area,BlockPos pos){
        double x=pos.getX()+.5,z=pos.getZ()+.5;
        double dx=Math.max(area.minX-x,x-area.maxX),dz=Math.max(area.minZ-z,z-area.maxZ);
        if(dx<=0&&dz<=0)return -Math.max(dx,dz);
        return Math.hypot(Math.max(0,dx),Math.max(0,dz));
    }

    public static BlockEntity capability(ServerLevel level,BlockPos pos){
        if(!level.hasChunkAt(pos))return null;
        var be=level.getBlockEntity(pos);
        if(be==null||be instanceof ChestBlockEntity||be instanceof BarrelBlockEntity
            ||be instanceof br.com.residenciasaldeoes.block.EmeraldChestBlockEntity)return null;
        for(var side:Direction.values())if(be.getCapability(ForgeCapabilities.ITEM_HANDLER,side).isPresent())return be;
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()?be:null;
    }

    public static IItemHandler accessibleHandler(BlockEntity be,Villager worker){
        var delta=worker.position().add(0,.75,0).subtract(Vec3.atCenterOf(be.getBlockPos()));
        var side=Direction.getNearest(delta.x,delta.y,delta.z);
        var handler=be.getCapability(ForgeCapabilities.ITEM_HANDLER,side).orElse(null);
        if(handler!=null)return handler;
        for(var direction:Direction.values())if(be.getCapability(ForgeCapabilities.ITEM_HANDLER,direction).isPresent())return null;
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
    }

    public static boolean hasOffers(int[] offers){
        for(var amount:offers)if(amount>0)return true;
        return false;
    }

    public static int[] offers(EmploymentData.Station station,java.util.function.Predicate<ItemStack> filter){
        int[] amounts=new int[EmploymentData.COMMON];
        for(int i=0;i<amounts.length;i++){
            var stack=station.getItem(i);
            if(!stack.isEmpty()&&filter.test(stack))amounts[i]=stack.getCount();
        }
        return amounts;
    }

    public static boolean accepts(ServerLevel level,EmploymentData.Station station,Villager worker,BlockPos pos,int[] offers){
        if(!canOpen(level,pos,IndustrialWork.actor(level,station,worker)))return false;
        var container=at(level,pos);
        var be=container==null?capability(level,pos):null;
        if(container==null&&be==null)return false;
        for(int i=0;i<offers.length;i++){
            if(offers[i]==0)continue;
            var stack=station.getItem(i).copy();
            stack.setCount(offers[i]);
            if(container!=null){
                for(int slot=0;slot<container.getContainerSize();slot++){
                    var stored=container.getItem(slot);
                    int limit=Math.min(container.getMaxStackSize(),stack.getMaxStackSize());
                    if(container.canPlaceItem(slot,stack)&&(stored.isEmpty()
                        ||ItemStack.isSameItemSameTags(stored,stack)&&stored.getCount()<limit))return true;
                }
            }else{
                boolean sided=false;
                for(var side:Direction.values()){
                    var handler=be.getCapability(ForgeCapabilities.ITEM_HANDLER,side).orElse(null);
                    if(handler==null)continue;
                    sided=true;
                    if(ItemHandlerHelper.insertItemStacked(handler,stack.copy(),true).getCount()<offers[i])return true;
                }
                if(!sided){
                    var handler=be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
                    if(handler!=null&&ItemHandlerHelper.insertItemStacked(handler,stack,true).getCount()<offers[i])return true;
                }
            }
        }
        return false;
    }

    public static void deposit(ServerLevel level,EmploymentData.Station station,Villager worker,BlockPos pos,int[] offers,String action){
        if(!level.hasChunkAt(pos)||!exposed(level,pos)||!IndustrialWork.allowed(level,station,worker,pos,null,action))return;
        var actor=IndustrialWork.actor(level,station,worker);
        if(!level.mayInteract(actor,pos))return;
        var hit=new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
        var permission=net.minecraftforge.common.ForgeHooks.onRightClickBlock(actor,net.minecraft.world.InteractionHand.MAIN_HAND,pos,hit);
        if(permission.isCanceled()||permission.getUseBlock()==net.minecraftforge.eventbus.api.Event.Result.DENY
            ||!level.hasChunkAt(pos)||!worker.getUUID().equals(station.worker)||!Employment.valid(level,worker.getUUID()))return;
        var container=at(level,pos);
        var be=container==null?capability(level,pos):null;
        var handler=be==null?null:accessibleHandler(be,worker);
        if(container==null&&handler==null)return;
        for(int i=0;i<offers.length;i++){
            int count=Math.min(offers[i],station.getItem(i).getCount());
            if(count==0)continue;
            var stack=station.getItem(i).copy();
            stack.setCount(count);
            var rest=container==null?ItemHandlerHelper.insertItemStacked(handler,stack,false):IndustrialWork.transfer(container,stack);
            int moved=count-Math.min(count,rest.getCount());
            if(moved>0){station.removeItem(i,moved);offers[i]-=moved;}
        }
    }

    private static long identity(ServerLevel l,BlockPos p){
        var state=l.getBlockState(p);if(state.getBlock() instanceof ChestBlock&&state.hasProperty(ChestBlock.TYPE)&&state.getValue(ChestBlock.TYPE)!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE){
            var other=p.relative(ChestBlock.getConnectedDirection(state));return Math.min(p.asLong(),other.asLong());
        }return p.asLong();
    }
    public static boolean canOpen(ServerLevel l,BlockPos p,net.minecraft.world.entity.player.Player actor){
        if(!l.hasChunkAt(p))return false;var be=l.getBlockEntity(p);
        if(be instanceof BaseContainerBlockEntity container&&!container.canOpen(actor))return false;
        var state=l.getBlockState(p);if(state.getBlock() instanceof ChestBlock&&state.hasProperty(ChestBlock.TYPE)&&state.getValue(ChestBlock.TYPE)!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE){
            var other=p.relative(ChestBlock.getConnectedDirection(state));if(!l.hasChunkAt(other))return false;
            if(l.getBlockEntity(other) instanceof BaseContainerBlockEntity container&&!container.canOpen(actor))return false;
        }return true;
    }
    public static void invalidate(ServerLevel l){CACHE.remove(l);}
    private DiscardChests(){}
}
