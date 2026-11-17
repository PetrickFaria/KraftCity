package br.com.kraftcity.commerce;

import br.com.kraftcity.employment.EmploymentData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

public final class CommercialEvents {
    private int cursor;
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e){
        var server=ServerLifecycleHooks.getCurrentServer();if(server==null||e.phase!=TickEvent.Phase.END)return;
        var list=EmploymentData.get(server.overworld()).stations().stream().filter(CommercialWork::commercial).toList();
        for(int i=0;i<Math.min(8,list.size());i++){var s=list.get(Math.floorMod(cursor++,list.size()));var l=server.getLevel(s.dimension);if(l!=null)CommercialWork.tick(l,s);}
    }
    @SubscribeEvent public void recipes(net.minecraftforge.event.OnDatapackSyncEvent e){CommercialRecipes.invalidate();}
    @SubscribeEvent public void stop(ServerStoppedEvent e){cursor=0;CommercialWork.clearSamples();CommercialRecipes.invalidate();}
}
