package br.com.kraftcity.logistics;

import br.com.kraftcity.employment.EmploymentData;
import br.com.kraftcity.employment.Profession;
import br.com.kraftcity.employment.WorkBlockEntity;
import br.com.kraftcity.industry.IndustrialWork;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class DiscardBinding {
    private static final String TARGET = "discard_binding_pos";
    private static final String ID = "discard_binding_id";
    private static final String TILE_ID = "KraftCityDiscardIdentity";
    private record Selection(ServerLevel level, UUID station, long expires) {}
    private static final Map<ServerPlayer, Selection> SELECTIONS = new WeakHashMap<>();

    public static void begin(ServerPlayer player, EmploymentData.Station station) {
        var level = player.getLevel();
        if (!valid(level, station) || !level.mayInteract(player, station.pos) || !realPlayer(player)) {
            message(player, "Você não tem acesso para vincular um recipiente a este trabalhador.");
            return;
        }
        if (linked(level, station)) {
            message(player, "Desvincule o recipiente atual antes de escolher outro.");
            return;
        }
        SELECTIONS.put(player, new Selection(level, station.id, level.getGameTime()+600));
        player.closeContainer();
        message(player, "Clique com o botão direito no recipiente de descarte em até 30 segundos.");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void select(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand()!=InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)) return;
        var selection = SELECTIONS.get(player);
        if (selection==null) return;
        var level = player.getLevel();
        var station = EmploymentData.get(level).station(selection.station);
        if (!realPlayer(player) || selection.level!=level || selection.expires<level.getGameTime() || !valid(level, station)
            || !level.mayInteract(player, station.pos) || linked(level, station)) {
            SELECTIONS.remove(player);
            message(player, "Seleção encerrada. Abra o posto para tentar novamente.");
            return;
        }
        if (event.isCanceled() || event.getUseBlock()==Event.Result.DENY || event.getUseItem()==Event.Result.DENY) {
            message(player, "O acesso ao recipiente foi bloqueado.");
            return;
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        var pos = event.getPos();
        if (!inRange(level,station,pos)) {
            message(player, "O recipiente deve estar a até cinco blocos da área de trabalho.");
            return;
        }
        if (!level.mayInteract(player, pos) || !DiscardChests.canOpen(level, pos, player)) {
            message(player, "Você não tem acesso a este recipiente.");
            return;
        }
        var tile = level.getBlockEntity(pos);
        if (station.profession==Profession.BAKER && (tile instanceof EnderChestBlockEntity || ArcaneDiscard.isEnder(tile))) {
            message(player, "Vincule um recipiente de descarte com inventário local.");
            return;
        }
        if (tile instanceof EnderChestBlockEntity chest && tile.getBlockState().is(Blocks.ENDER_CHEST)) {
            if (!EnderDiscard.authorize(chest, player)) {
                message(player, "Este Baú do Ender pertence a outro jogador.");
                return;
            }
        } else if (ArcaneDiscard.isEnder(tile)) {
            if (!ArcaneDiscard.authorize(tile, player, station)) {
                message(player, "Baú Arcano (Fim) bloqueado ou versão/canal incompatível.");
                return;
            }
        } else if (!supports(level, station, pos)) {
            message(player, "O recipiente está bloqueado ou não oferece um inventário de automação acessível.");
            return;
        }
        var data = tile.getPersistentData();
        if (!data.hasUUID(TILE_ID)) {
            data.putUUID(TILE_ID, UUID.randomUUID());
            tile.setChanged();
        }
        station.industry.putLong(TARGET, pos.asLong());
        station.industry.putUUID(ID, data.getUUID(TILE_ID));
        station.setChanged();
        SELECTIONS.remove(player);
        message(player, "Baú de Descarte vinculado.");
    }

    private static boolean supports(ServerLevel level, EmploymentData.Station station, BlockPos pos) {
        if (DiscardChests.at(level, pos)!=null) return true;
        var tile = DiscardChests.capability(level, pos);
        if (!(tile instanceof WorkerDiscardAccess)) return tile!=null;
        if (station.worker==null || !(level.getEntity(station.worker) instanceof Villager worker)) return false;
        for (var face : Direction.values()) if (DiscardChests.handler(tile, worker, face)!=null) return true;
        return false;
    }

    public static boolean supportsManualBinding(Profession profession){
        return profession!=null&&(profession.sector==Profession.Sector.INDUSTRIAL
            ||profession==Profession.BAKER||profession==Profession.SOLDIER);
    }

    private static boolean valid(ServerLevel level, EmploymentData.Station station) {
        return station!=null && supportsManualBinding(station.profession) && station.at(level, station.pos)
            && level.hasChunkAt(station.pos) && level.getBlockEntity(station.pos) instanceof WorkBlockEntity post
            && station.id.equals(post.identity());
    }

    private static boolean realPlayer(ServerPlayer player) {
        return player.mayBuild() && !player.isSpectator() && !(player instanceof FakePlayer)
            && player.server.getPlayerList().getPlayer(player.getUUID())==player;
    }

    public static boolean inRange(ServerLevel level,EmploymentData.Station station,BlockPos pos){
        if(station.profession==Profession.SOLDIER)
            return Math.abs(pos.getX()-station.pos.getX())<=5&&Math.abs(pos.getY()-station.pos.getY())<=5
                &&Math.abs(pos.getZ()-station.pos.getZ())<=5;
        return DiscardChests.inRange(IndustrialWork.area(level,station),pos);
    }

    public static BlockPos boundPosition(ServerLevel level,EmploymentData.Station station){
        return linked(level,station)?BlockPos.of(station.industry.getLong(TARGET)):null;
    }

    public static boolean linked(ServerLevel level, EmploymentData.Station station) {
        if (!station.industry.hasUUID(ID)) return false;
        var pos = BlockPos.of(station.industry.getLong(TARGET));
        if (!inRange(level,station,pos)) {
            clear(station);
            return false;
        }
        if (level.hasChunkAt(pos)) {
            var tile = level.getBlockEntity(pos);
            if (tile==null || !tile.getPersistentData().hasUUID(TILE_ID)
                || !station.industry.getUUID(ID).equals(tile.getPersistentData().getUUID(TILE_ID))
                || ArcaneDiscard.isEnder(tile) && !ArcaneDiscard.channelMatches(tile, station)) {
                clear(station);
                return false;
            }
        }
        return true;
    }

    static boolean targets(ServerLevel level, EmploymentData.Station station, BlockPos pos) {
        return linked(level, station) && station.industry.getLong(TARGET)==pos.asLong();
    }

    static boolean permitsEnder(ServerLevel level, EmploymentData.Station station, EnderChestBlockEntity chest) {
        if (!chest.getPersistentData().hasUUID(TILE_ID)) return true; // Previously authorized legacy endpoints.
        return targets(level, station, chest.getBlockPos());
    }

    public static void unlink(ServerPlayer player, EmploymentData.Station station) {
        if (!valid(player.getLevel(), station) || !realPlayer(player)
            || !player.getLevel().mayInteract(player, station.pos)) return;
        clear(station);
        message(player, "Baú de Descarte desvinculado.");
    }

    private static void clear(EmploymentData.Station station) {
        station.industry.remove(TARGET);
        station.industry.remove(ID);
        ArcaneDiscard.clear(station);
        station.setChanged();
    }

    public static List<BlockPos> destinations(ServerLevel level, EmploymentData.Station station, AABB area) {
        boolean hadBinding = station.industry.hasUUID(ID);
        if (linked(level, station)) {
            var target = BlockPos.of(station.industry.getLong(TARGET));
            return level.hasChunkAt(target) ? List.of(target) : List.of();
        }
        return hadBinding || station.profession == Profession.LUMBERJACK ? List.of() : DiscardChests.destinations(level, station, area);
    }

    private static void message(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
    }

    private DiscardBinding() {}
}
