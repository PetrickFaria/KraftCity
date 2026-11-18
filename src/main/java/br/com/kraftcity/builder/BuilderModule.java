package br.com.kraftcity.builder;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraftforge.common.*;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.*;

public final class BuilderModule {
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"residenciasaldeoes");
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,"residenciasaldeoes");
    public static final RegistryObject<Item> BLANK=ITEMS.register("empty_blueprint",()->new Item(new Item.Properties()));
    public static final RegistryObject<BlueprintItem> PAPER=ITEMS.register("blueprint",()->new BlueprintItem(false));
    public static final RegistryObject<BlueprintItem> ENGINEER=ITEMS.register("engineer_paper",()->new BlueprintItem(true));
    public static final RegistryObject<MenuType<BuilderMenu>> MENU=MENUS.register("builder",()->IForgeMenuType.create(BuilderMenu::new));
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue MAX_VOLUME,MAX_EDGE,MAX_FILE,MAX_RAW,MAX_JOBS,SCAN_BUDGET,MAX_FILES,LIBRARY_BLOCKS;
    static{var b=new ForgeConfigSpec.Builder();MAX_VOLUME=b.defineInRange("volumeMaximo",262144,1,1048576);MAX_EDGE=b.defineInRange("dimensaoMaxima",128,1,512);MAX_FILE=b.defineInRange("arquivoComprimidoMaxBytes",8388608,1024,33554432);MAX_RAW=b.defineInRange("nbtDescomprimidoMaxBytes",33554432,4096,67108864);MAX_JOBS=b.defineInRange("obrasSimultaneas",8,1,32);SCAN_BUDGET=b.defineInRange("verificacoesPorTick",256,16,2048);LIBRARY_BLOCKS=b.defineInRange("blocosTotaisBiblioteca",524288,1,4194304);MAX_FILES=b.defineInRange("arquivosBiblioteca",256,1,2048);SPEC=b.build();}
    public static void initialize(IEventBus bus){ITEMS.register(bus);MENUS.register(bus);ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,SPEC,"kraftcity-builder-server.toml");MinecraftForge.EVENT_BUS.register(new BuilderEvents());BuilderNetwork.register();}
}
