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

public final class FarmerLogistics {
    private static final Map<Villager,Route> ROUTES=new WeakHashMap<>();
    private static final Map<Villager,Pickup> PICKUPS=new WeakHashMap<>();
    private record Route(BlockPos target,long since){}
    private record Pickup(UUID item,long nextScan){}
    private FarmerLogistics(){}

    private static boolean visible(ServerLevel l,Villager v,Vec3 target,BlockPos allowedHit){
        var hit=l.clip(new ClipContext(v.getEyePosition(),target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,v));
        return hit.getType()==HitResult.Type.MISS||allowedHit!=null&&hit.getBlockPos().equals(allowedHit);
    }

    /** 1 arrived, 0 navigating, -1 inaccessible. */
    static int approach(ServerLevel l,EmploymentData.Station s,Villager v,BlockPos pos){
        if(!l.hasChunkAt(pos))return -1;
        var center=Vec3.atCenterOf(pos);
        if(v.distanceToSqr(center)<=9&&visible(l,v,center,pos)){
            ROUTES.remove(v);
            v.getNavigation().stop();
            v.getLookControl().setLookAt(center.x,center.y,center.z);
            return 1;
        }
        if(v.isNoAi())return -1;
        long now=l.getGameTime();
        var route=ROUTES.get(v);
        if(route==null||!route.target.equals(pos)){
            route=new Route(pos.immutable(),now);
            ROUTES.put(v,route);
            var path=v.getNavigation().createPath(pos,1);
            if(path==null||!path.canReach()||!v.getNavigation().moveTo(path,.8)){
                ROUTES.remove(v);
                return -1;
            }
        }
        if(now-route.since>=100||v.getNavigation().isDone()){
            ROUTES.remove(v);
            v.getNavigation().stop();
            return -1;
        }
        return 0;
    }

    static boolean collect(ServerLevel l,EmploymentData.Station s,Villager v){
        var state=PICKUPS.get(v);
        long now=l.getGameTime();
        var area=IndustrialWork.area(l,s).inflate(3,1,3);
        ItemEntity item=state!=null&&state.item!=null&&l.getEntity(state.item) instanceof ItemEntity e?e:null;
        if(!eligible(l,s,area,item)){
            if(state!=null&&state.nextScan>now)return false;
            item=l.getEntitiesOfClass(ItemEntity.class,area,e->eligible(l,s,area,e)).stream()
                .min(Comparator.comparingDouble(v::distanceToSqr)).orElse(null);
            PICKUPS.put(v,new Pickup(item==null?null:item.getUUID(),now+40));
        }
        if(item==null)return false;
        int travel=approach(l,s,v,item.blockPosition());
        if(travel==0)return true;
        if(travel>0&&visible(l,v,item.position().add(0,.1,0),null)
            &&IndustrialWork.allowed(l,s,v,item.blockPosition(),item,"farmer_collect")
            &&eligible(l,s,area,item)&&Employment.valid(l,v.getUUID())&&v.getUUID().equals(s.worker)){
            var take=item.getItem().copy();
            while(!take.isEmpty()&&!IndustrialWork.insert(s,take,false))take.shrink(1);
            if(!take.isEmpty()&&IndustrialWork.insert(s,take,true)){
                var remaining=item.getItem().copy();
                remaining.shrink(take.getCount());
                if(remaining.isEmpty())item.discard();else item.setItem(remaining);
            }
        }
        PICKUPS.put(v,new Pickup(null,now+40));
        return travel>0;
    }
    private static boolean eligible(ServerLevel l,EmploymentData.Station s,net.minecraft.world.phys.AABB area,ItemEntity e){
        if(e==null||!e.isAlive()||e.hasPickUpDelay()||!area.contains(e.position())||!l.hasChunkAt(e.blockPosition()))return false;
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
            s.industry.remove("farmer_discard_until");
            if(hasExcess(l,s)){
                var pos=target(l,s,v);
                if(pos!=null){
                    s.industry.putLong("farmer_discard_pos",pos.asLong());
                    s.industry.putLong("farmer_discard_until",now+100);
                }
            }
            s.setChanged();
        }
        if(!s.industry.contains("farmer_discard_until"))return false;
        var pos=BlockPos.of(s.industry.getLong("farmer_discard_pos"));
        int result=now<s.industry.getLong("farmer_discard_until")&&supports(l,pos)?approach(l,s,v,pos):-1;
        if(result==0)return true;
        if(result>0)deposit(l,s,v,pos);
        s.industry.remove("farmer_discard_until");
        s.setChanged();
        return false;
    }
}
