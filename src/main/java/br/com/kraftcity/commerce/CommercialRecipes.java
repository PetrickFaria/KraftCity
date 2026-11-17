package br.com.kraftcity.commerce;

import br.com.kraftcity.employment.Profession;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;

/** Resolve real game/datapack recipes; this module registers no food recipe. */
public final class CommercialRecipes {
    public record Plan(ResourceLocation id,Item input,int count,ItemStack output,int heat){
        public Plan{output=output.copy();}
        @Override public ItemStack output(){return output.copy();}
    }
    public static final List<Item> RAW=List.of(Items.BEEF,Items.PORKCHOP,Items.CHICKEN,Items.MUTTON,Items.RABBIT);
    private static final List<Item> COOKED=List.of(Items.COOKED_BEEF,Items.COOKED_PORKCHOP,Items.COOKED_CHICKEN,Items.COOKED_MUTTON,Items.COOKED_RABBIT);
    private static final AbstractContainerMenu GRID_MENU=new AbstractContainerMenu(null,-1){
        public ItemStack quickMoveStack(Player p,int i){return ItemStack.EMPTY;}public boolean stillValid(Player p){return false;}
    };
    private static final Map<ServerLevel,Map<Profession,List<Plan>>> CACHE=new WeakHashMap<>();
    public static List<Plan> plans(ServerLevel l,Profession profession){return CACHE.computeIfAbsent(l,k->new EnumMap<>(Profession.class)).computeIfAbsent(profession,k->resolve(l,profession));}
    public static void invalidate(){CACHE.clear();}
    private static List<Plan> resolve(ServerLevel l,Profession profession){
        var out=new ArrayList<Plan>();
        if(profession==Profession.BAKER||profession==Profession.CONFECTIONER){
            Item ingredient=profession==Profession.BAKER?Items.WHEAT:Items.SUGAR_CANE;
            Item product=profession==Profession.BAKER?Items.BREAD:Items.SUGAR;
            for(var recipe:l.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)){
                if(!recipe.getResultItem().is(product))continue;
                var ingredients=recipe.getIngredients();int count=0;boolean valid=true;
                for(var in:ingredients)if(!in.isEmpty()){count++;if(!in.test(new ItemStack(ingredient)))valid=false;}
                if(!valid||count==0||(profession==Profession.BAKER&&(count!=3||recipe.getResultItem().getCount()!=1)))continue;
                var grid=new CraftingContainer(GRID_MENU,3,3);
                if(recipe instanceof ShapedRecipe shaped){
                    if(shaped.getWidth()>3||shaped.getHeight()>3)continue;
                    for(int y=0;y<shaped.getHeight();y++)for(int x=0;x<shaped.getWidth();x++)if(!ingredients.get(y*shaped.getWidth()+x).isEmpty())grid.setItem(y*3+x,new ItemStack(ingredient));
                }else{int i=0;for(var in:ingredients)if(!in.isEmpty())grid.setItem(i++,new ItemStack(ingredient));}
                if(!recipe.matches(grid,l))continue;
                var result=recipe.assemble(grid);if(!result.is(product)||result.isEmpty())continue;
                if(recipe.getRemainingItems(grid).stream().anyMatch(s->!s.isEmpty()))continue;
                out.add(new Plan(recipe.getId(),ingredient,count,result,0));
            }
        }else if(profession==Profession.GRILL_COOK){
            for(var recipe:l.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING))for(int i=0;i<RAW.size();i++){
                if(recipe.getIngredients().size()!=1||!recipe.getIngredients().get(0).test(new ItemStack(RAW.get(i)))||!recipe.getResultItem().is(COOKED.get(i))||recipe.getResultItem().getCount()!=1)continue;
                out.add(new Plan(recipe.getId(),RAW.get(i),1,recipe.getResultItem(),recipe.getCookingTime()));
            }
        }
        out.sort(Comparator.comparing(p->p.id().toString()));return List.copyOf(out);
    }
    public static Plan byId(ServerLevel l,Profession profession,String id){return plans(l,profession).stream().filter(p->p.id().toString().equals(id)).findFirst().orElse(null);}
    public static boolean product(Profession profession,ItemStack s){return switch(profession){case BAKER->s.is(Items.BREAD);case CONFECTIONER->s.is(Items.SUGAR);case GRILL_COOK->COOKED.contains(s.getItem());default->false;};}
    private CommercialRecipes(){}
}
