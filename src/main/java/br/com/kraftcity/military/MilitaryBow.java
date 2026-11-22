package br.com.kraftcity.military;

import br.com.kraftcity.employment.EmploymentData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;

final class MilitaryBow {
    static final int INTERVAL=120;
    private static final String DEADLINE = "KraftCityBowReadyAt";

    static long readyAt(EmploymentData.Station station,Villager soldier){
        var data=soldier.getPersistentData();
        if(data.contains(DEADLINE))return data.getLong(DEADLINE);
        if(station.military.hasUUID("bowWorker"))
            return station.military.getUUID("bowWorker").equals(soldier.getUUID())?station.military.getLong("bowReadyAt"):0;
        // Old saves had one timestamp per post. Adopt it for the currently assigned soldier.
        return station.military.getLong("bowReadyAt");
    }

    static boolean admit(ServerLevel level,Villager soldier,AbstractArrow arrow){
        var station=Military.station(soldier);
        if(!Military.military(station)||arrow.shotFromCrossbow()
            ||!arrow.getPersistentData().getBoolean("KCSoldierBow")&&!(soldier.getMainHandItem().getItem() instanceof BowItem))return true;
        long tick=level.getGameTime();
        if(tick<readyAt(station,soldier))return false;
        long next=tick+INTERVAL;
        soldier.getPersistentData().putLong(DEADLINE,next);
        station.military.putLong("bowReadyAt",next);
        station.military.putUUID("bowWorker",soldier.getUUID());
        station.setChanged();
        arrow.getPersistentData().putLong("KCSoldierBowTick",tick);
        return true;
    }

    private MilitaryBow(){}
}
