package br.com.kraftcity.military;

import br.com.kraftcity.employment.EmploymentData;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.WeakHashMap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import br.com.kraftcity.employment.Profession;
import br.com.residenciasaldeoes.data.ResidenceData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

final class MilitaryLoot {
    private record Survey(long tick,Military.Mode mode,List<ItemEntity> items){}
    private static final WeakHashMap<Villager,Survey> SURVEYS=new WeakHashMap<>();
    private static final WeakHashMap<Villager,List<AABB>> ROUTING=new WeakHashMap<>();

    static void finish(Villager soldier){
        if(ROUTING.remove(soldier)!=null)soldier.getNavigation().stop();
    }

    static Vec3 limitMovement(Villager soldier,Vec3 movement){
        var areas=ROUTING.get(soldier);
        if(areas==null)return movement;
        var from=soldier.position();
        var to=from.add(movement);
        for(var box:areas){
            if(!box.contains(from)&&(box.contains(to)||box.clip(from,to).isPresent()))return Vec3.ZERO;
        }
        return movement;
    }

    static boolean collect(ServerLevel level,EmploymentData.Station station,Villager soldier,MilitaryArea area,boolean approach){
        if(java.util.stream.IntStream.range(0,SoldierMenu.STORAGE).allMatch(i->{
            var stack=station.getItem(i);
            return !stack.isEmpty()&&stack.getCount()>=stack.getMaxStackSize();
        }))return false;
        var mode=Military.mode(station);
        var center=Vec3.atCenterOf(station.pos);
        var survey=SURVEYS.get(soldier);
        List<ItemEntity> items;
        if(!approach)items=level.getEntitiesOfClass(ItemEntity.class,soldier.getBoundingBox().inflate(1.2));
        else if(survey==null||survey.mode()!=mode||level.getGameTime()-survey.tick()>=15){
            items=level.getEntitiesOfClass(ItemEntity.class,mode==Military.Mode.GUARD
                ?new AABB(station.pos).inflate(25):area.bounds(),item->item.isAlive()&&!item.hasPickUpDelay());
            items.sort(Comparator.<ItemEntity>comparingDouble(soldier::distanceToSqr).thenComparingInt(ItemEntity::getId));
            SURVEYS.put(soldier,new Survey(level.getGameTime(),mode,items));
        }else items=survey.items();
        if(items.isEmpty())return false;
        var protectedAreas=approach?protectedAreas(level,area):List.<AABB>of();
        int attempts=0;
        for(var item:items){
            var point=item.position();
            if(!item.isAlive()||item.hasPickUpDelay()||!area.contains(point)||capacity(station,item.getItem())==0
                ||mode==Military.Mode.GUARD&&center.distanceToSqr(point)>625
                ||blocked(protectedAreas,point))continue;
            if(soldier.distanceToSqr(item)<=1.44&&soldier.hasLineOfSight(item)){
                var stack=item.getItem();
                int before=stack.getCount();
                for(int i=0;i<SoldierMenu.STORAGE&&!stack.isEmpty();i++){
                    var stored=station.getItem(i);
                    int amount=Math.min(space(stored,stack),stack.getCount());
                    if(amount==0)continue;
                    if(stored.isEmpty()){var transfer=stack.copy();transfer.setCount(amount);station.setItem(i,transfer);}
                    else stored.grow(amount);
                    stack.shrink(amount);
                }
                soldier.take(item,before-stack.getCount());
                if(stack.isEmpty())item.discard();
                else item.setItem(stack.copy());
                station.setChanged();
                return true;
            }
            if(approach&&attempts++<16){
                if(area.move(soldier,point,MilitaryArea.WALK,node->!blocked(protectedAreas,node))){
                    ROUTING.put(soldier,protectedAreas);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean blocked(List<AABB> areas,Vec3 point){
        return areas.stream().anyMatch(box->box.contains(point));
    }

    private static List<AABB> protectedAreas(ServerLevel level,MilitaryArea area){
        var result=new ArrayList<AABB>();
        for(var home:ResidenceData.get(level).homes()){
            if(!home.dimension.equals(level.dimension())||!level.hasChunkAt(home.pos))continue;
            int radius=home.size/2;
            var box=new AABB(home.pos.offset(-radius,1,-radius),home.pos.offset(radius+1,home.size+1,radius+1));
            if(area.intersects(box))result.add(box);
        }
        for(var post:EmploymentData.get(level).stations()){
            if(!post.active||!post.dimension.equals(level.dimension())||post.sector==Profession.Sector.MILITARY
                ||!level.hasChunkAt(post.pos))continue;
            var box=post.sector==Profession.Sector.INDUSTRIAL
                ?br.com.kraftcity.industry.IndustrialWork.area(level,post):new AABB(post.pos);
            if(area.intersects(box))result.add(box);
            if(post.worker!=null)br.com.kraftcity.workforce.WorkforceData.get(level).worker(post.worker)
                .flatMap(br.com.kraftcity.workforce.WorkerRecord::area)
                .filter(work->work.dimension().equals(level.dimension()))
                .map(work->new AABB(work.min(),work.max().offset(1,1,1)))
                .filter(area::intersects).ifPresent(result::add);
        }
        return result;
    }

    private static int capacity(EmploymentData.Station station,ItemStack stack){
        for(int i=0;i<SoldierMenu.STORAGE;i++){
            int space=space(station.getItem(i),stack);
            if(space>0)return space;
        }
        return 0;
    }

    private static int space(ItemStack stored,ItemStack stack){
        if(stored.isEmpty())return stack.getMaxStackSize();
        return ItemStack.isSameItemSameTags(stored,stack)?Math.max(0,stored.getMaxStackSize()-stored.getCount()):0;
    }
}
