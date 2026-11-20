package br.com.kraftcity.industry;

import java.util.ArrayList;
import java.util.List;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod.EventBusSubscriber(modid="residenciasaldeoes")
public final class HarvestTrace {
    private static final boolean ENABLED=!FMLEnvironment.production&&Boolean.getBoolean("kraftcity.harvestTrace");
    private static final String DIMENSION=System.getProperty("kraftcity.harvestTrace.dimension","minecraft:overworld");
    private static final String POST=System.getProperty("kraftcity.harvestTrace.post","");
    private static final StackWalker STACK=StackWalker.getInstance();
    private static final List<String> entries=new ArrayList<>();
    private static boolean harvest;
    private static int tick=Integer.MIN_VALUE;

    private HarvestTrace() {}

    public static boolean watches(ServerLevel level) {
        return ENABLED&&level.getServer().isSameThread()&&DIMENSION.equals(level.dimension().location().toString());
    }

    private static void next(int current) {
        if(tick==current)return;
        if(harvest){
            var log=LogUtils.getLogger();
            log.info("KC_HARVEST_TICK {} dimension={} entries={}",tick,DIMENSION,entries.size());
            for(int i=0;i<entries.size();i++)log.info("KC_HARVEST_ENTRY {} {} {}",tick,i,entries.get(i));
        }
        entries.clear();
        harvest=false;
        tick=current;
    }

    private static String origin() {
        return STACK.walk(frames->frames.skip(2).limit(22).map(StackWalker.StackFrame::toString)
            .collect(java.util.stream.Collectors.joining(" <- ")));
    }

    static void selection(ServerLevel level,BlockPos post,Villager worker,BlockPos target,BlockState state) {
        if(!watches(level)||!POST.isEmpty()&&!POST.equals(post.getX()+","+post.getY()+","+post.getZ()))return;
        next(level.getServer().getTickCount());
        harvest=true;
        entries.add("selection post="+post+" worker="+worker.getUUID()+" at="+worker.position()+" target="+target+" state="+state);
    }

    public static void changed(ServerLevel level,BlockPos pos,BlockState before,BlockState after) {
        if(before==after)return;
        next(level.getServer().getTickCount());
        entries.add("block "+pos+" "+before+" -> "+after+" origin="+origin());
    }

    @SubscribeEvent
    public static void itemSpawn(EntityJoinLevelEvent event) {
        if(!(event.getLevel() instanceof ServerLevel level)||!watches(level)
            ||event.loadedFromDisk()||!(event.getEntity() instanceof ItemEntity item))return;
        next(level.getServer().getTickCount());
        entries.add("item "+item.getItem()+" at="+item.position()+" origin="+origin());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if(ENABLED&&event.phase==TickEvent.Phase.START)next(event.getServer().getTickCount());
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        if(ENABLED)next(Integer.MIN_VALUE);
    }
}
