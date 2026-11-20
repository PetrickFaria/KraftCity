package br.com.kraftcity.employment;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkHooks;

public final class WorkBlock extends BaseEntityBlock {
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING=HorizontalDirectionalBlock.FACING;
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    public final Profession.Sector sector;
    public final Profession profession;
    public WorkBlock(Profession.Sector sector,Profession profession){super(BlockBehaviour.Properties.of(Material.WOOD).strength(2.5F).sound(SoundType.WOOD));this.sector=sector;this.profession=profession;registerDefaultState(stateDefinition.any().setValue(FACING,net.minecraft.core.Direction.NORTH));}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new WorkBlockEntity(pos,state);}
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public PushReaction getPistonPushReaction(BlockState state){return PushReaction.BLOCK;}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(player instanceof ServerPlayer server&&level.getBlockEntity(pos) instanceof WorkBlockEntity be){
            var s=be.station();
            if(server.isShiftKeyDown()){
                server.getPersistentData().remove("KCRegisterAnimal");
                server.sendSystemMessage(Component.literal("Modo de associação encerrado."));
            }else if(s!=null)open(server,s);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    public static void open(ServerPlayer player,EmploymentData.Station s){
        open(player,s,null);
    }
    public static void open(ServerPlayer player,EmploymentData.Station s,java.util.UUID viaWorker){
        open(player,s,viaWorker,s.profession==null);
    }
    public static void open(ServerPlayer player,EmploymentData.Station s,java.util.UUID viaWorker,boolean hiring){
        if(s.sector==Profession.Sector.BUILDER&&s.profession!=null&&!hiring){br.com.kraftcity.builder.BuilderMenu.open(player,s,viaWorker);return;}
        if(s.sector==Profession.Sector.MILITARY&&s.profession!=null&&!hiring){br.com.kraftcity.military.SoldierMenu.open(player,s,viaWorker);return;}
        var provider=new SimpleMenuProvider((id,inventory,p)->new WorkMenu(id,inventory,s,hiring,viaWorker),Component.literal(s.profession==null?s.sector==Profession.Sector.INDUSTRIAL?"Bloco Industrial":s.sector==Profession.Sector.MILITARY?"Bloco Militar":s.sector==Profession.Sector.BUILDER?"Bloco do Construtor":"Bloco Comercial":s.profession.label));
        NetworkHooks.openScreen(player,provider,buffer->{buffer.writeBlockPos(s.pos);buffer.writeUUID(s.id);buffer.writeBoolean(hiring);buffer.writeEnum(s.sector);buffer.writeInt(s.profession==null?-1:s.profession.ordinal());buffer.writeBoolean(viaWorker!=null);if(viaWorker!=null)buffer.writeUUID(viaWorker);});
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        super.setPlacedBy(level,pos,state,placer,stack);
        if(level instanceof ServerLevel server&&level.getBlockEntity(pos) instanceof WorkBlockEntity be){
            if(stack.hasTag()&&stack.getTag().hasUUID("KraftCityStation")){
                var s=EmploymentData.get(server).station(stack.getTag().getUUID("KraftCityStation"));
                if(s==null||s.active)throw new IllegalStateException("Invalid portable station placement");
                s.active=true;s.dimension=server.dimension();s.pos=pos.immutable();s.setChanged();be.bind(s.id);
            }else be.station();
        }
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(state.getBlock()!=next.getBlock()&&level instanceof ServerLevel server
                &&!(Employment.CONVERTING.get()&&next.getBlock() instanceof WorkBlock)
                &&level.getBlockEntity(pos) instanceof WorkBlockEntity be){
            var s=be.station();if(s!=null){
                if(profession==Profession.FARMER)Employment.recoverFarmerStation(server,s);
                else Employment.makePortable(server,s);
            }
        }
        super.onRemove(state,level,pos,next,moving);
    }
}
