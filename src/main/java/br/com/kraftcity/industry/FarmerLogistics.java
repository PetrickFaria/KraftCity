package br.com.kraftcity.industry;

import br.com.kraftcity.employment.Employment;
import br.com.kraftcity.employment.EmploymentData;
import br.com.kraftcity.employment.Profession;
import br.com.kraftcity.logistics.DiscardChests;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.event.ForgeEventFactory;

public final class FarmerLogistics {
    private static final Map<Villager,Route> ROUTES=new WeakHashMap<>();
    private static final Map<Villager,Pickup> PICKUPS=new WeakHashMap<>();
    private record Route(BlockPos target,long since){}
    private record Pickup(UUID item,long nextScan){}
    private FarmerLogistics(){}

    private static boolean visible(ServerLevel l,Villager v,Vec3 target,BlockPos allowedHit){
        var eye=v.getEyePosition();
        int minX=(int)Math.floor(Math.min(eye.x,target.x))>>4,maxX=(int)Math.floor(Math.max(eye.x,target.x))>>4;
        int minZ=(int)Math.floor(Math.min(eye.z,target.z))>>4,maxZ=(int)Math.floor(Math.max(eye.z,target.z))>>4;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(l.getChunkSource().getChunkNow(x,z)==null)return false;
        var hit=l.clip(new ClipContext(v.getEyePosition(),target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,v));
        return hit.getType()==HitResult.Type.MISS||allowedHit!=null&&hit.getBlockPos().equals(allowedHit);
    }

    private static final Map<Villager,Map<BlockPos,Long>> BLOCKED=new WeakHashMap<>();
    private static final Map<Villager,Jump> JUMPS=new WeakHashMap<>();
    private static final Map<Villager,Map<UUID,Long>> UNREACHABLE_ITEMS=new WeakHashMap<>();
    private record Jump(BlockPos soil,long until,int attempts){}

    /** 1 arrived, 0 navigating, -1 inaccessible. */
    static int approachCell(ServerLevel l,Villager v,BlockPos pos,boolean adjacent){
        return approach(l,v,pos,adjacent?1.38:.68,adjacent?1:0);
    }

    private static int approach(ServerLevel l,Villager v,BlockPos pos,double radius,int accuracy){
        if(!l.hasChunkAt(pos))return -1;
        var feet=Vec3.atBottomCenterOf(pos);
        var center=Vec3.atCenterOf(pos);
        double dx=v.getX()-feet.x,dz=v.getZ()-feet.z;
        boolean sameColumn=new BlockPos(v.getX(),pos.getY(),v.getZ()).equals(pos);
        if((accuracy!=0||sameColumn)&&dx*dx+dz*dz<=radius*radius&&Math.abs(v.getY()-feet.y)<=.85&&visible(l,v,center,pos)){
            ROUTES.remove(v);
            v.getNavigation().stop();
            v.getLookControl().setLookAt(center.x,center.y,center.z);
            return 1;
        }
        if(v.isNoAi())return -1;
        long now=l.getGameTime();
        var blocked=BLOCKED.computeIfAbsent(v,k->new HashMap<>());
        blocked.values().removeIf(until->until<=now);
        if(blocked.containsKey(pos))return -1;
        var route=ROUTES.get(v);
        if(route==null||!route.target.equals(pos)){
            route=new Route(pos.immutable(),now);
            ROUTES.put(v,route);
            var path=v.getNavigation().createPath(pos,accuracy);
            if(path==null||!path.canReach()||!v.getNavigation().moveTo(path,.8)){
                ROUTES.remove(v);
                blocked.put(pos.immutable(),now+100);
                return -1;
            }
        }
        if(now-route.since>=100||v.getNavigation().isDone()){
            ROUTES.remove(v);
            v.getNavigation().stop();
            blocked.put(pos.immutable(),now+100);
            return -1;
        }
        return 0;
    }

    static void stayInField(ServerLevel l,EmploymentData.Station s,Villager v){
        var area=IndustrialWork.area(l,s);
        if(area.contains(v.position().add(0,.125,0))){
            if(ROUTES.remove(v)!=null)v.getNavigation().stop();
            return;
        }
        if(v.isNoAi())return;
        int size=IndustrialRules.size(l,s);
        var cells=FarmerWork.zigzag(s.pos,size,0);
        var candidates=new ArrayList<BlockPos>();
        for(var base:cells)for(int y=0;y<FarmerWork.HEIGHT;y++){
            var pos=base.above(y);
            if(l.hasChunkAt(pos)&&l.getBlockState(pos).getCollisionShape(l,pos).isEmpty()
                &&l.getBlockState(pos.above()).getCollisionShape(l,pos.above()).isEmpty()
                &&!l.getBlockState(pos.below()).getCollisionShape(l,pos.below()).isEmpty())candidates.add(pos);
        }
        candidates.sort(Comparator.comparingDouble(pos->v.distanceToSqr(Vec3.atBottomCenterOf(pos))));
        for(var pos:candidates.stream().limit(8).toList())if(approachCell(l,v,pos,false)>=0)return;
    }

    static void jumpOn(Villager v,BlockPos soil){
        v.getNavigation().stop();
        JUMPS.put(v,new Jump(soil.immutable(),v.level.getGameTime()+18,1));
        v.getJumpControl().jump();
    }
    static boolean isJumping(Villager v){
        var jump=JUMPS.get(v);
        if(jump==null)return false;
        if(v.level.getGameTime()<jump.until)return true;
        if(jump.attempts<4&&v.isOnGround()&&v.level.getBlockState(jump.soil).is(net.minecraft.world.level.block.Blocks.FARMLAND)
            &&v.level.getBlockState(jump.soil.above()).isAir()&&v.distanceToSqr(Vec3.atBottomCenterOf(jump.soil.above()))<.36){
            JUMPS.put(v,new Jump(jump.soil,v.level.getGameTime()+18,jump.attempts+1));
            v.getJumpControl().jump();
            return true;
        }
        JUMPS.remove(v);
        return false;
    }
    public static boolean allowsTrample(Villager v,BlockPos soil){
        var jump=JUMPS.get(v);
        return jump!=null&&v.level.getGameTime()<=jump.until&&jump.soil.equals(soil)
            &&v.level.getBlockState(soil.above()).isAir();
    }

    static void rescan(Villager v){PICKUPS.remove(v);}
    static boolean collect(ServerLevel l,EmploymentData.Station s,Villager v){
        return collect(l,s,v,false);
    }
    private static boolean collect(ServerLevel l,EmploymentData.Station s,Villager v,boolean closing){
        var state=PICKUPS.get(v);
        long now=l.getGameTime();
        var area=IndustrialWork.area(l,s).inflate(3,1,3);
        var rejected=UNREACHABLE_ITEMS.computeIfAbsent(v,k->new HashMap<>());
        rejected.values().removeIf(until->until<=now);
        ItemEntity item=state!=null&&state.item!=null&&l.getEntity(state.item) instanceof ItemEntity e?e:null;
        if(!eligible(l,s,area,item)){
            if(state!=null&&state.nextScan>now)return closing&&state.item==null;
            item=l.getEntitiesOfClass(ItemEntity.class,area,e->!rejected.containsKey(e.getUUID())&&eligible(l,s,area,e)).stream()
                .min(Comparator.comparingDouble(v::distanceToSqr)).orElse(null);
            if(item!=null||!closing)PICKUPS.put(v,new Pickup(item==null?null:item.getUUID(),now+(closing?5:10)));
            else PICKUPS.remove(v);
        }
        if(item==null){
            if(!closing)return false;
            // Tile drops may still have their vanilla pickup delay at a time jump.
            boolean pending=!l.getEntitiesOfClass(ItemEntity.class,area,e->e.hasPickUpDelay()
                &&eligible(l,s,area,e,true)&&!rejected.containsKey(e.getUUID())).isEmpty();
            if(pending)PICKUPS.put(v,new Pickup(null,now+5));
            return pending;
        }
        boolean close=v.distanceToSqr(item)<=1.21&&visible(l,v,item.position().add(0,.1,0),null);
        if(!close){
            int travel=approach(l,v,item.blockPosition(),.4,0);
            if(travel==0)return true;
            if(travel<0||v.distanceToSqr(item)>1.21||!visible(l,v,item.position().add(0,.1,0),null)){
                rejected.put(item.getUUID(),now+100);
                rescan(v);
                return closing;
            }
        }
        if(IndustrialWork.allowed(l,s,v,item.blockPosition(),item,"farmer_collect")
            &&eligible(l,s,area,item)&&Employment.valid(l,v.getUUID())&&v.getUUID().equals(s.worker)){
            var take=item.getItem().copy();
            while(!take.isEmpty()&&!IndustrialWork.insert(s,take,false))take.shrink(1);
            if(!take.isEmpty()&&IndustrialWork.insert(s,take,true)){
                var remaining=item.getItem().copy();
                remaining.shrink(take.getCount());
                if(remaining.isEmpty())item.discard();else item.setItem(remaining);
                v.getNavigation().stop();
                rescan(v);
                return true;
            }
        }
        rejected.put(item.getUUID(),now+100);
        rescan(v);
        return closing;
    }
    private static boolean eligible(ServerLevel l,EmploymentData.Station s,net.minecraft.world.phys.AABB area,ItemEntity e){
        return eligible(l,s,area,e,false);
    }
    private static boolean eligible(ServerLevel l,EmploymentData.Station s,net.minecraft.world.phys.AABB area,ItemEntity e,boolean allowDelay){
        if(e==null||!e.isAlive()||!allowDelay&&e.hasPickUpDelay()||!area.contains(e.position())||!l.hasChunkAt(e.blockPosition()))return false;
        var stack=e.getItem();
        for(int i=0;i<27;i++){
            var stored=s.getItem(i);
            if(stored.isEmpty()||ItemStack.isSameItemSameTags(stored,stack)&&stored.getCount()<stored.getMaxStackSize())return true;
        }
        return false;
    }

    public static boolean supports(ServerLevel l,BlockPos pos){
        if(!l.hasChunkAt(pos))return false;
        if(DiscardChests.at(l,pos)!=null)return true;
        var be=l.getBlockEntity(pos);
        return be!=null&&!(be instanceof br.com.residenciasaldeoes.block.EmeraldChestBlockEntity)&&handler(be)!=null;
    }

    private static IItemHandler handler(net.minecraft.world.level.block.entity.BlockEntity be){
        var unsided=be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if(unsided!=null)return unsided;
        for(var side:Direction.values()){
            var sided=be.getCapability(ForgeCapabilities.ITEM_HANDLER,side).orElse(null);
            if(sided!=null)return sided;
        }
        return null;
    }

    private static BlockPos target(ServerLevel l,EmploymentData.Station s,Villager v){
        var area=IndustrialWork.area(l,s);
        var actor=IndustrialWork.actor(l,s,v);
        if(s.industry.contains("discard")){
            var bound=BlockPos.of(s.industry.getLong("discard"));
            if(DiscardChests.inRange(area,bound)&&supports(l,bound)&&DiscardChests.canOpen(l,bound,actor))return bound;
        }
        return DiscardChests.around(l,s,area).stream().map(DiscardChests.Access::pos)
            .filter(pos->DiscardChests.canOpen(l,pos,actor)).findFirst().orElse(null);
    }
    private static ItemStack excess(EmploymentData.Station s,int slot,int reserve,Map<Item,Integer> remaining){
        var stack=s.getItem(slot);
        if(stack.isEmpty())return ItemStack.EMPTY;
        int keep=0;
        if(FarmerWork.plant(stack)!=null){
            int left=remaining.getOrDefault(stack.getItem(),reserve);
            keep=Math.min(left,stack.getCount());
            remaining.put(stack.getItem(),left-keep);
        }
        var result=stack.copy();
        result.shrink(keep);
        return result;
    }
    private static boolean hasExcess(ServerLevel l,EmploymentData.Station s){
        int size=IndustrialRules.size(l,s);
        Map<Item,Integer> remaining=new HashMap<>();
        for(int i=0;i<27;i++)if(!excess(s,i,size*size,remaining).isEmpty())return true;
        return false;
    }
    private static void deposit(ServerLevel l,EmploymentData.Station s,Villager v,BlockPos pos){
        if(!IndustrialWork.allowed(l,s,v,pos,null,"deposit")||!Employment.valid(l,v.getUUID())||!v.getUUID().equals(s.worker))return;
        var chest=DiscardChests.at(l,pos);
        var be=l.getBlockEntity(pos);
        var capability=chest==null&&be!=null&&supports(l,pos)?handler(be):null;
        if(chest==null&&capability==null)return;
        int size=IndustrialRules.size(l,s);
        Map<Item,Integer> remaining=new HashMap<>();
        for(int i=0;i<27;i++){
            var out=excess(s,i,size*size,remaining);
            if(out.isEmpty())continue;
            int offered=out.getCount();
            var rest=chest!=null?IndustrialWork.transfer(chest,out):ItemHandlerHelper.insertItemStacked(capability,out,false);
            int moved=offered-rest.getCount();
            if(moved>0)s.removeItem(i,moved);
        }
    }
    static void depositDirect(ServerLevel l,EmploymentData.Station s,Villager v){
        var pos=target(l,s,v);
        if(pos!=null)deposit(l,s,v,pos);
    }

    public static boolean ending(Villager v){
        if(!(v.level instanceof ServerLevel l))return false;
        var s=EmploymentData.get(l).assigned(v.getUUID());
        return s!=null&&s.profession==Profession.FARMER&&s.industry.contains("farmer_discard_until")
            &&s.industry.getLong("farmer_discard_until")>l.getGameTime();
    }

    /** Called before the resident sleep routine, after emergency navigation. */
    public static boolean beforeHome(ServerLevel l,Villager v){
        if(IndustrialWork.day(l)||v.isSleeping()||v.isNoAi()||l.isRaining()
            ||v.getPersistentData().getBoolean("residenciasaldeoes_home_return_forced")
            ||v.getLastHurtByMob()!=null&&v.getLastHurtByMob().isAlive())return false;
        var s=EmploymentData.get(l).assigned(v.getUUID());
        if(s==null||s.profession!=Profession.FARMER||!l.hasChunkAt(s.pos)||!Employment.valid(l,v.getUUID()))return false;
        long cycle=Math.floorDiv(l.getDayTime(),24000)+1;
        long now=l.getGameTime();
        if(s.industry.getLong("farmer_discard_day")!=cycle){
            s.industry.putLong("farmer_discard_day",cycle);
            s.industry.putLong("farmer_discard_until",now+220);
            s.industry.putLong("farmer_pickup_until",now+120);
            s.industry.remove("farmer_discard_pos");
            rescan(v);
            s.setChanged();
        }
        if(!s.industry.contains("farmer_discard_until"))return false;
        if(s.industry.contains("farmer_pickup_until")){
            if(now<s.industry.getLong("farmer_pickup_until")&&ForgeEventFactory.getMobGriefingEvent(l,v)){
                if(collect(l,s,v,true))return true;
            }
            s.industry.remove("farmer_pickup_until");
            var target=hasExcess(l,s)?target(l,s,v):null;
            if(target!=null)s.industry.putLong("farmer_discard_pos",target.asLong());
            s.setChanged();
        }
        if(s.industry.contains("farmer_discard_pos")){
            var pos=BlockPos.of(s.industry.getLong("farmer_discard_pos"));
            int result=now<s.industry.getLong("farmer_discard_until")&&supports(l,pos)?approach(l,v,pos,1.38,1):-1;
            if(result==0)return true;
            if(result>0)deposit(l,s,v,pos);
        }
        s.industry.remove("farmer_discard_until");
        s.industry.remove("farmer_discard_pos");
        s.setChanged();
        return false;
    }
}
