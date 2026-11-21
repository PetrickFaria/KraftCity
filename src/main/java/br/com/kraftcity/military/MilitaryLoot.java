package br.com.kraftcity.military;

import br.com.kraftcity.employment.EmploymentData;
import java.util.Comparator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

final class MilitaryLoot {
    static boolean collect(ServerLevel level,EmploymentData.Station station,Villager soldier,MilitaryArea area,boolean approach){
        var items=level.getEntitiesOfClass(ItemEntity.class,soldier.getBoundingBox().inflate(5),
            item->item.isAlive()&&!item.hasPickUpDelay()&&soldier.distanceToSqr(item)<=25&&area.contains(item.position())
                &&soldier.hasLineOfSight(item)&&capacity(station,item.getItem())>0);
        items.sort(Comparator.comparingDouble(soldier::distanceToSqr));
        for(var item:items){
            if(soldier.distanceToSqr(item)<=1.44){
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
            if(approach&&area.move(soldier,item.position(),MilitaryArea.WALK))return true;
        }
        return false;
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
