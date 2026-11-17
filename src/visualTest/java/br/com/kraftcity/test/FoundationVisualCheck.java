package br.com.kraftcity.test;

import br.com.kraftcity.workforce.*;
import br.com.residenciasaldeoes.ResidenciasDosAldeoes;
import br.com.residenciasaldeoes.VillagerResidenceEvents;
import br.com.residenciasaldeoes.client.AnchorScreen;
import br.com.residenciasaldeoes.client.ResidencePreview;
import br.com.residenciasaldeoes.network.ModNetwork;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Additional test-only scenes; never packaged in the installable JAR. */
public final class FoundationVisualCheck {
    private static final BlockPos FIRST = new BlockPos(30, -58, 8);
    private static final BlockPos SECOND = new BlockPos(40, -58, 8);
    private static final UUID WORKER = UUID.nameUUIDFromBytes("kraftcity-foundation-visual".getBytes(StandardCharsets.UTF_8));
    private FoundationVisualCheck() {}

    private static void open(ServerPlayer player, BlockPos pos) {
        var level = player.getLevel();
        player.teleportTo(level, pos.getX()+.5, -58, 10.5, 180F, 20F);
        ResidenciasDosAldeoes.ANCHOR.get().use(level.getBlockState(pos), level, pos, player,
                InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static void toggle(Minecraft mc, BlockPos pos, boolean enabled) {
        if (!(mc.screen instanceof AnchorScreen)) throw new IllegalStateException("Preview GUI did not open");
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.Action(pos, 7, enabled ? 1 : 0, null, ""));
    }

    @SuppressWarnings("unchecked")
    private static List<ModNetwork.PreviewEntry> previews() {
        try {
            var field = ResidencePreview.class.getDeclaredField("entries");
            field.setAccessible(true);
            return (List<ModNetwork.PreviewEntry>) field.get(null);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    public static void tick(Minecraft mc, int ticks) {
        var server = mc.getSingleplayerServer();
        var uuid = mc.player.getUUID();
        if (ticks == 590) server.execute(() -> {
            var level = server.overworld();
            for (int x=26; x<=44; x++) for (int z=4; z<=25; z++) level.setBlock(new BlockPos(x,-59,z), Blocks.STONE.defaultBlockState(), 3);
            for (BlockPos pos : List.of(FIRST, SECOND)) {
                level.setBlock(pos, ResidenciasDosAldeoes.ANCHOR.get().defaultBlockState(), 3);
                ((br.com.residenciasaldeoes.block.ResidentialAnchorBlockEntity) level.getBlockEntity(pos)).setSize(5);
            }
            WorkforceData.get(level).put(new WorkerRecord(WORKER, Optional.empty(), 0,
                    Optional.empty(), Optional.empty(), List.of(new ItemStack(Items.EMERALD, 3))));
            open(server.getPlayerList().getPlayer(uuid), FIRST);
        });
        if (ticks == 610) toggle(mc, FIRST, true);
        if (ticks == 620) {
            mc.player.closeContainer();
        }
        if (ticks == 630) server.execute(() -> open(server.getPlayerList().getPlayer(uuid), SECOND));
        if (ticks == 640) toggle(mc, SECOND, true);
        if (ticks == 650) {
            mc.player.closeContainer();
            server.execute(() -> server.getPlayerList().getPlayer(uuid).teleportTo(server.overworld(),35.5,-54,22.5,180F,25F));
        }
        if (ticks == 680) {
            long visible = previews().stream().filter(e -> (e.pos().equals(FIRST)||e.pos().equals(SECOND)) && e.permanent()).count();
            if (visible != 2) throw new IllegalStateException("Both residence limits were not synchronized");
            Screenshot.grab(new File("../build"), "visual-kraftcity-limits-two.png", mc.getMainRenderTarget(), message -> {});
            server.execute(() -> {
                server.overworld().removeBlock(FIRST, false);
                VillagerResidenceEvents.syncPreviews(server.getPlayerList().getPlayer(uuid));
            });
        }
        if (ticks == 710) {
            if (previews().stream().anyMatch(e -> e.pos().equals(FIRST))) throw new IllegalStateException("Removed residence left a ghost preview");
            Screenshot.grab(new File("../build"), "visual-kraftcity-limits-removed.png", mc.getMainRenderTarget(), message -> {});
            server.execute(() -> open(server.getPlayerList().getPlayer(uuid), SECOND));
        }
        if (ticks == 730) toggle(mc, SECOND, false);
        if (ticks == 740) {
            mc.player.closeContainer();
            server.execute(() -> server.getPlayerList().getPlayer(uuid).teleportTo(server.overworld(),35.5,-54,22.5,180F,25F));
        }
        if (ticks == 770) {
            if (previews().stream().noneMatch(e -> e.pos().equals(SECOND) && !e.permanent())) throw new IllegalStateException("Limits toggle did not synchronize");
            Screenshot.grab(new File("../build"), "visual-kraftcity-limits-off.png", mc.getMainRenderTarget(), message -> {});
            System.out.println("KRAFTCITY_LIMITS_TOGGLE_AND_REMOVAL_PASSED");
            mc.stop();
        }
    }

    public static void verifyRestore(net.minecraft.server.level.ServerLevel level) {
        var worker = WorkforceData.get(level).worker(WORKER).orElseThrow(() -> new IllegalStateException("Worker lost on process restart"));
        if (worker.level()!=0 || worker.profession().isPresent() || worker.inventory().size()!=1
                || !worker.inventory().get(0).is(Items.EMERALD) || worker.inventory().get(0).getCount()!=3) {
            throw new IllegalStateException("Workforce inventory/state changed on process restart");
        }
        System.out.println("WORKFORCE_WORLD_RESTART_PASSED");
    }
}
