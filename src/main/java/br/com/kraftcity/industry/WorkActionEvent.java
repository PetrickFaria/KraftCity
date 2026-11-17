package br.com.kraftcity.industry;

import br.com.kraftcity.employment.EmploymentData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.eventbus.api.*;

/** Protection plugins can veto any industrial action, including non-block actions. */
@Cancelable
public final class WorkActionEvent extends Event {
    public final ServerLevel level;public final EmploymentData.Station station;public final Villager worker;
    public final BlockPos position;public final Entity target;public final String action;
    public WorkActionEvent(ServerLevel l,EmploymentData.Station s,Villager v,BlockPos p,Entity e,String a){level=l;station=s;worker=v;position=p;target=e;action=a;}
}
