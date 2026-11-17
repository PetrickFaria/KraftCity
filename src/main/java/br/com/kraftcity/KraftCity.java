package br.com.kraftcity;

import br.com.kraftcity.residential.ResidentialModule;
import br.com.residenciasaldeoes.ResidenciasDosAldeoes;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.common.Mod;

/** Public entry point. The legacy namespace is deliberately retained. */
@Mod(KraftCity.MOD_ID)
public final class KraftCity {
    public static final String MOD_ID = "residenciasaldeoes";
    public static final String NAME = "KraftCity";
    public static final CreativeModeTab CREATIVE_TAB = new CreativeModeTab("kraftcity") {
        @Override public ItemStack makeIcon() {
            return new ItemStack(ResidenciasDosAldeoes.ANCHOR_ITEM.get());
        }
        @Override public void fillItemList(NonNullList<ItemStack> items) {
            items.add(new ItemStack(ResidenciasDosAldeoes.ANCHOR_ITEM.get()));
            items.add(new ItemStack(ResidenciasDosAldeoes.REINFORCED_DOOR_ITEM.get()));
            items.add(new ItemStack(ResidenciasDosAldeoes.EMERALD_CHEST_ITEM.get()));
            items.add(new ItemStack(ResidenciasDosAldeoes.EMERALD_FRAGMENT.get()));
            items.add(new ItemStack(br.com.kraftcity.employment.WorkModule.INDUSTRIAL.get()));
            items.add(new ItemStack(br.com.kraftcity.employment.WorkModule.COMMERCIAL.get()));
        }
    };

    public KraftCity() {
        ResidentialModule.initialize();
        br.com.kraftcity.employment.WorkModule.initialize(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
    }
}
