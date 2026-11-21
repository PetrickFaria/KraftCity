package br.com.kraftcity.logistics;

import br.com.kraftcity.KraftCity;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=KraftCity.MOD_ID)
public final class DiscardLids {
    public static final int OPEN_EVENT=98;
    private record Open(long until, Runnable close) {}
    private static final Map<BlockEntity, Open> OPEN = new IdentityHashMap<>();

    static void afterApproval(ServerLevel level, BlockEntity tile) {
        if(tile==null)return;
        activate(level,tile);
        var state=tile.getBlockState();
        if(tile instanceof ChestBlockEntity && state.hasProperty(ChestBlock.TYPE)
            && state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE){
            var other=tile.getBlockPos().relative(ChestBlock.getConnectedDirection(state));
            if(level.hasChunkAt(other) && level.getBlockEntity(other) instanceof ChestBlockEntity half)activate(level,half);
        }
    }

    private static void activate(ServerLevel level,BlockEntity tile){
        var existing = OPEN.get(tile);
        var close = existing==null ? open(level,tile) : existing.close;
        if (close!=null) OPEN.put(tile, new Open(level.getGameTime()+16,close));
    }

    private static Runnable open(ServerLevel level,BlockEntity tile){
        if(tile instanceof ChestBlockEntity || tile instanceof EnderChestBlockEntity){
            var pos=tile.getBlockPos();
            var block=tile.getBlockState().getBlock();
            level.blockEvent(pos,block,OPEN_EVENT,1);
            return ()->{if(!tile.isRemoved() && level.hasChunkAt(pos))level.blockEvent(pos,block,OPEN_EVENT,0);};
        }
        var id=ForgeRegistries.BLOCKS.getKey(tile.getBlockState().getBlock());
        return id!=null && id.getNamespace().equals("bausarcanos") ? ArcaneDiscard.open(tile) : null;
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase!=TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        var expired = new ArrayList<Runnable>();
        OPEN.entrySet().removeIf(entry->{
            var tile = entry.getKey();
            if (tile.getLevel()!=level || !tile.isRemoved() && level.getGameTime()<entry.getValue().until) return false;
            expired.add(entry.getValue().close);
            return true;
        });
        expired.forEach(Runnable::run);
    }

    @SubscribeEvent
    public static void unload(net.minecraftforge.event.level.LevelEvent.Unload event) {
        OPEN.keySet().removeIf(tile->tile.getLevel()==event.getLevel());
    }

    private DiscardLids() {}
}
