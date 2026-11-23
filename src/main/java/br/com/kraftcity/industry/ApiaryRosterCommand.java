package br.com.kraftcity.industry;

import br.com.kraftcity.employment.Profession;
import br.com.kraftcity.employment.WorkBlockEntity;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="residenciasaldeoes")
public final class ApiaryRosterCommand {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("kraftcity").then(
            Commands.literal("apiario").requires(source->source.hasPermission(2)).then(
                Commands.literal("reparar").then(
                    Commands.argument("posto",BlockPosArgument.blockPos()).then(
                        Commands.argument("vinculos",StringArgumentType.greedyString())
                            .executes(context->repair(context.getSource(),
                                BlockPosArgument.getLoadedBlockPos(context,"posto"),
                                StringArgumentType.getString(context,"vinculos"))))))));
    }

    private static int repair(CommandSourceStack source,BlockPos pos,String ids){
        var level=source.getLevel();
        if(!(level.getBlockEntity(pos) instanceof WorkBlockEntity block)||block.station()==null
                ||!block.station().active||block.station().profession!=Profession.BEEKEEPER){
            source.sendFailure(Component.literal("Informe a posição de um posto de Apicultor ativo."));
            return 0;
        }
        var station=block.station();
        var roster=station.industry.getCompound(ApiaryWork.BEES);
        var requested=new LinkedHashSet<String>();
        try{
            for(var id:ids.trim().split("\\s+"))requested.add(UUID.fromString(id).toString());
        }catch(IllegalArgumentException invalid){
            source.sendFailure(Component.literal("Informe os UUIDs dos vínculos ausentes, separados por espaço."));
            return 0;
        }
        if(!roster.getAllKeys().containsAll(requested)){
            source.sendFailure(Component.literal("Um dos vínculos não consta no posto. Vínculos atuais: "+roster.getAllKeys()));
            return 0;
        }

        // The former flight bounds extended seven blocks past the selected area.
        var area=IndustrialWork.area(level,station).inflate(8);
        var first=new ChunkPos(new BlockPos(area.minX,0,area.minZ));
        var last=new ChunkPos(new BlockPos(area.maxX,0,area.maxZ));
        for(int x=first.x;x<=last.x;x++){
            for(int z=first.z;z<=last.z;z++){
                var chunk=level.getChunkSource().getChunkNow(x,z);
                if(chunk==null||!level.areEntitiesLoaded(ChunkPos.asLong(x,z))){
                    source.sendFailure(Component.literal("Aguarde o carregamento dos arredores do posto antes de reparar."));
                    return 0;
                }
                for(var blockEntity:chunk.getBlockEntities().values()){
                    if(!(blockEntity instanceof BeehiveBlockEntity hive))continue;
                    for(var occupant:hive.writeBees()){
                        var bee=((CompoundTag)occupant).getCompound(BeehiveBlockEntity.ENTITY_DATA);
                        if(matches(bee.getCompound("ForgeData"),station.id,requested)){
                            source.sendFailure(Component.literal("Um vínculo informado está numa colmeia em "+hive.getBlockPos().toShortString()+"."));
                            return 0;
                        }
                    }
                }
            }
        }
        for(var world:level.getServer().getAllLevels()){
            for(var entity:world.getAllEntities()){
                if(entity instanceof Bee bee&&matches(bee.getPersistentData(),station.id,requested)){
                    source.sendFailure(Component.literal("Um vínculo informado ainda pertence a uma abelha carregada."));
                    return 0;
                }
            }
        }

        var retired=station.industry.getCompound(ApiaryWork.ARCHIVED_BEES);
        for(var id:requested){
            roster.remove(id);
            retired.putBoolean(id,true);
        }
        station.industry.put(ApiaryWork.ARCHIVED_BEES,retired);
        station.setChanged();
        source.sendSuccess(Component.literal(requested.size()+" vínculo(s) ausente(s) arquivado(s) no posto. A capacidade foi liberada."),true);
        return requested.size();
    }

    private static boolean matches(CompoundTag data,UUID station,Set<String> ids){
        return data.hasUUID(ApiaryWork.OWNER)&&station.equals(data.getUUID(ApiaryWork.OWNER))
            &&data.hasUUID(ApiaryWork.ID)&&ids.contains(data.getUUID(ApiaryWork.ID).toString());
    }

    private ApiaryRosterCommand(){}
}
