package br.com.kraftcity.builder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class BlueprintItem extends Item {
    public final boolean engineer;
    public BlueprintItem(boolean engineer){super(new Item.Properties().stacksTo(1));this.engineer=engineer;}
    @Override public boolean isFoil(ItemStack stack){return !engineer&&stack.hasTag()&&stack.getTag().getBoolean("Confirmed");}
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){return useOn(context);}
    @Override public InteractionResult useOn(UseOnContext c){if(c.getPlayer() instanceof ServerPlayer p&&c.getHand()==InteractionHand.MAIN_HAND){var data=p.getPersistentData();if(engineer){
            if(p.isShiftKeyDown()){data.remove("KCEngineerA");data.remove("KCEngineerB");BlueprintLibrary.cancel(p.getUUID());BuilderNetwork.editor(p,"engineer",null);return InteractionResult.SUCCESS;}
            if(!data.contains("KCEngineerA")||!data.getString("KCEngineerDim").equals(p.level.dimension().location().toString())){data.putLong("KCEngineerA",c.getClickedPos().asLong());data.remove("KCEngineerB");data.putString("KCEngineerDim",p.level.dimension().location().toString());BuilderNetwork.notice(p,"Primeiro canto selecionado.");BuilderNetwork.selection(p,c.getClickedPos(),c.getClickedPos());}
            else{data.putLong("KCEngineerB",c.getClickedPos().asLong());BuilderNetwork.selection(p,BlockPos.of(data.getLong("KCEngineerA")),c.getClickedPos());BuilderNetwork.editor(p,"engineer",null);}
        }else if(c.getItemInHand().hasTag()&&c.getItemInHand().getTag().hasUUID("KraftCityPaper")){var id=c.getItemInHand().getTag().getUUID("KraftCityPaper");var store=BuilderData.get(p.getLevel());var paper=store.papers.get(id);if(paper==null||!paper.issued||!paper.owner.equals(p.getUUID())||paper.job!=null){BuilderNetwork.notice(p,"Papel sem referência válida ou já utilizado.");return InteractionResult.FAIL;}data.putUUID("KCPreviewPaper",id);data.putLong("KCPreviewOrigin",c.getClickedPos().relative(c.getClickedFace()).asLong());data.putInt("KCPreviewRotation",paper.rotation);data.putInt("KCPreviewMirror",paper.mirror);data.putString("KCPreviewDim",p.level.dimension().location().toString());BuilderNetwork.preview(p,paper);BuilderNetwork.editor(p,"placement",id);}
        }return InteractionResult.sidedSuccess(c.getLevel().isClientSide);}
    @Override public InteractionResultHolder<ItemStack> use(Level l,Player p,InteractionHand hand){if(p instanceof ServerPlayer server&&engineer)BuilderNetwork.editor(server,"engineer",null);return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand),l.isClientSide);}
}
