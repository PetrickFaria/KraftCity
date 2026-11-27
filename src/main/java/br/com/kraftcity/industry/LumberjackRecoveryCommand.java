package br.com.kraftcity.industry;

import br.com.kraftcity.employment.Profession;
import br.com.kraftcity.employment.WorkBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "residenciasaldeoes")
public final class LumberjackRecoveryCommand {
    private LumberjackRecoveryCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("kraftcity").then(
            Commands.literal("lenhador").requires(source -> source.hasPermission(2)).then(
                Commands.literal("recuperar_arvore").then(
                    Commands.argument("posto", BlockPosArgument.blockPos()).then(
                        Commands.argument("raiz", BlockPosArgument.blockPos()).then(
                            Commands.literal("confirmar").executes(context -> recover(context.getSource(),
                                BlockPosArgument.getLoadedBlockPos(context, "posto"),
                                BlockPosArgument.getLoadedBlockPos(context, "raiz")))))))));
    }

    private static int recover(CommandSourceStack source, BlockPos post, BlockPos root) {
        var level = source.getLevel();
        if (!(level.getBlockEntity(post) instanceof WorkBlockEntity block) || block.station() == null
                || !IndustrialWork.industrial(block.station()) || block.station().profession != Profession.LUMBERJACK) {
            source.sendFailure(Component.literal("Informe a posição de um posto de Lenhador ativo."));
            return 0;
        }

        // Old saves record coordinates only. The operator confirms the tree's origin;
        // the ordinary geometry checks and the professional area still constrain recovery.
        int removed = LumberjackTrees.recoverConfirmedTree(level, root, IndustrialWork.area(level, block.station()), post);
        if (removed == 0) {
            source.sendFailure(Component.literal("A raiz precisa pertencer a uma árvore válida, inteiramente marcada "
                + "como madeira colocada e com todas as raízes na área do posto."));
            return 0;
        }
        source.sendSuccess(Component.literal("Liberadas " + removed + " marcações da árvore em "
            + root.toShortString() + " para colheita pelo Lenhador."), true);
        return removed;
    }
}
