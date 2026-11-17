package br.com.kraftcity.employment;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.server.level.ServerLevel;

/** Even creative-mode copies of a recovery token can activate its ledger only once. */
public final class WorkBlockItem extends BlockItem {
    public WorkBlockItem(WorkBlock block){super(block,new Item.Properties().stacksTo(1));}
    @Override public InteractionResult place(BlockPlaceContext context){
        ItemStack stack=context.getItemInHand();
        if(context.getLevel() instanceof ServerLevel level&&stack.hasTag()&&stack.getTag().hasUUID("KraftCityStation")){
            var s=EmploymentData.get(level).station(stack.getTag().getUUID("KraftCityStation"));var b=(WorkBlock)getBlock();
            if(s==null||s.active||s.sector!=b.sector||s.profession!=b.profession)return InteractionResult.FAIL;
        }
        return super.place(context);
    }
}
