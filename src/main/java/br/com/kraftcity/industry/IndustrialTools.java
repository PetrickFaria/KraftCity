package br.com.kraftcity.industry;

import br.com.kraftcity.KraftCity;
import java.util.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.*;
import net.minecraftforge.eventbus.api.IEventBus;

/** Explicit custom variants: vanilla iron shears and wooden rod retain their identities. */
public final class IndustrialTools {
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,KraftCity.MOD_ID);
    public static final CreativeModeTab TAB=new CreativeModeTab("kraftcity_tools"){
        @Override public ItemStack makeIcon(){return new ItemStack(Items.EMERALD);}
        @Override public void fillItemList(net.minecraft.core.NonNullList<ItemStack> items){
            matrix().forEach(row->row.forEach(item->items.add(new ItemStack(item))));
        }
    };
    public static final Tier EMERALD=new Tier(){
        public int getUses(){return 1024;}public float getSpeed(){return 7;}public float getAttackDamageBonus(){return 2.5F;}
        public int getLevel(){return 2;}public int getEnchantmentValue(){return 18;}
        public Ingredient getRepairIngredient(){return Ingredient.of(Items.EMERALD);}
    };
    static {net.minecraftforge.common.TierSortingRegistry.registerTier(EMERALD,new net.minecraft.resources.ResourceLocation(KraftCity.MOD_ID,"emerald"),List.of(Tiers.IRON),List.of(Tiers.DIAMOND));}
    public static final RegistryObject<Item> HOE=ITEMS.register("emerald_hoe",()->new HoeItem(EMERALD,-2,-1,new Item.Properties().tab(TAB)));
    public static final RegistryObject<Item> AXE=ITEMS.register("emerald_axe",()->new AxeItem(EMERALD,6,-3.1F,new Item.Properties().tab(TAB)));
    public static final RegistryObject<Item> SWORD=ITEMS.register("emerald_sword",()->new SwordItem(EMERALD,3,-2.4F,new Item.Properties().tab(TAB)));
    public static final Map<Integer,RegistryObject<Item>> SHEARS=new LinkedHashMap<>(),RODS=new LinkedHashMap<>();
    public static final int STONE=6; // Keep the legacy numeric tiers stable.
    private static final List<Integer> MATERIAL_ORDER=List.of(0,STONE,1,2,3,4,5);
    public static final String[] NAMES={"wood","iron","gold","emerald","diamond","netherite","stone"};
    public static final int[] USES={59,238,32,1024,1561,2031,131};
    static {for(int i:MATERIAL_ORDER){
        final int tier=i;
        if(i!=1)SHEARS.put(i,ITEMS.register(NAMES[i]+"_shears",()->new TierShears(tier)));
        if(i!=0)RODS.put(i,ITEMS.register(NAMES[i]+"_fishing_rod",()->new TierRod(tier)));
    }}
    private static Item.Properties properties(int tier,int durability){var p=new Item.Properties().tab(TAB).durability(durability);return tier==5?p.fireResistant():p;}
    private static boolean repair(int tier,ItemStack material){return switch(tier){case 0->material.is(net.minecraft.tags.ItemTags.PLANKS);case 1->material.is(Items.IRON_INGOT);case 2->material.is(Items.GOLD_INGOT);case 3->material.is(Items.EMERALD);case 4->material.is(Items.DIAMOND);case STONE->material.is(net.minecraft.tags.ItemTags.STONE_TOOL_MATERIALS);default->material.is(Items.NETHERITE_INGOT);};}
    public static class TierShears extends ShearsItem {public final int tier;TierShears(int t){super(properties(t,USES[t]));tier=t;}
        @Override public boolean isValidRepairItem(ItemStack tool,ItemStack material){return repair(tier,material)||super.isValidRepairItem(tool,material);}}

    public static class TierRod extends FishingRodItem {public final int tier;TierRod(int t){super(properties(t,t==1?250:USES[t]));tier=t;}
        @Override public boolean isValidRepairItem(ItemStack tool,ItemStack material){return repair(tier,material)||super.isValidRepairItem(tool,material);}}

    /** Categories with fixed material tiers, in creative tab and documentation order. */
    public static List<List<Item>> matrix(){
        return List.of(
            List.of(Items.WOODEN_SWORD,Items.STONE_SWORD,Items.IRON_SWORD,Items.GOLDEN_SWORD,SWORD.get(),Items.DIAMOND_SWORD,Items.NETHERITE_SWORD),
            List.of(Items.WOODEN_AXE,Items.STONE_AXE,Items.IRON_AXE,Items.GOLDEN_AXE,AXE.get(),Items.DIAMOND_AXE,Items.NETHERITE_AXE),
            List.of(Items.WOODEN_HOE,Items.STONE_HOE,Items.IRON_HOE,Items.GOLDEN_HOE,HOE.get(),Items.DIAMOND_HOE,Items.NETHERITE_HOE),
            MATERIAL_ORDER.stream().map(tier->tier==1?Items.SHEARS:SHEARS.get(tier).get()).toList(),
            MATERIAL_ORDER.stream().map(tier->tier==0?Items.FISHING_ROD:RODS.get(tier).get()).toList()
        );
    }

    public static int tier(ItemStack stack){
        Item item=stack.getItem();if(item instanceof TierShears t)return t.tier;if(item instanceof TierRod t)return t.tier;
        if(item instanceof ShearsItem)return 1;if(item instanceof FishingRodItem)return 0;
        if(item instanceof TieredItem t){Tier a=t.getTier();if(a==Tiers.NETHERITE)return 5;if(a==Tiers.DIAMOND)return 4;if(a==EMERALD)return 3;if(a==Tiers.GOLD)return 2;if(a==Tiers.IRON)return 1;}
        return 0; // Stone is conservative wood yield; no fictional stone rule.
    }
    public static int farmerYield(ItemStack tool,net.minecraft.util.RandomSource random){
        int maximum=1;
        if(tool.getItem() instanceof HoeItem hoe){
            Tier tier=hoe.getTier();
            if(tier==Tiers.STONE)maximum=2;
            else if(tier==Tiers.IRON)maximum=3;
            else if(tier==Tiers.GOLD)maximum=4;
            else if(tier==EMERALD)maximum=5;
            else if(tier==Tiers.DIAMOND)maximum=6;
            else if(tier==Tiers.NETHERITE)maximum=7;
        }
        return 1+random.nextInt(maximum);
    }
    public static void register(IEventBus bus){ITEMS.register(bus);}
    private IndustrialTools(){}
}
