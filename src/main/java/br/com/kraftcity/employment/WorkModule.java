package br.com.kraftcity.employment;

import br.com.kraftcity.KraftCity;
import java.util.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.*;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.*;

public final class WorkModule {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,KraftCity.MOD_ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,KraftCity.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,KraftCity.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,KraftCity.MOD_ID);
    public static final RegistryObject<WorkBlock> INDUSTRIAL=block("industrial",Profession.Sector.INDUSTRIAL,null);
    public static final RegistryObject<WorkBlock> COMMERCIAL=block("commercial",Profession.Sector.COMMERCIAL,null);
    public static final RegistryObject<WorkBlock> MILITARY=block("military",Profession.Sector.MILITARY,null);
    public static WorkBlock generic(Profession.Sector sector){return switch(sector){case INDUSTRIAL->INDUSTRIAL.get();case COMMERCIAL->COMMERCIAL.get();case MILITARY->MILITARY.get();};}
    public static final Map<Profession,RegistryObject<WorkBlock>> PROFESSIONAL=new EnumMap<>(Profession.class);
    static { for(var p:Profession.values())PROFESSIONAL.put(p,block("work_"+p.path,p.sector,p)); }
    private static RegistryObject<WorkBlock> block(String id,Profession.Sector sector,Profession profession){
        var block=BLOCKS.register(id,()->new WorkBlock(sector,profession));ITEMS.register(id,()->new WorkBlockItem(block.get()));return block;
    }
    public static final RegistryObject<BlockEntityType<WorkBlockEntity>> ENTITY=ENTITIES.register("workstation",()->BlockEntityType.Builder.of(WorkBlockEntity::new,BLOCKS.getEntries().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<MenuType<WorkMenu>> MENU=MENUS.register("workstation",()->IForgeMenuType.create(WorkMenu::new));
    public static void initialize(IEventBus bus){
        br.com.kraftcity.api.ResidentialWorkIndicator.register(Employment::indicator);
        br.com.kraftcity.industry.IndustrialTools.register(bus);
        br.com.kraftcity.military.MilitaryModule.initialize(bus);
        BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);MENUS.register(bus);
        bus.addListener(br.com.kraftcity.commerce.CommerceConfigMigration::loaded);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,EmploymentConfig.SPEC,"kraftcity-employment-server.toml");
        MinecraftForge.EVENT_BUS.register(new br.com.kraftcity.industry.IndustrialEvents());
        MinecraftForge.EVENT_BUS.register(new br.com.kraftcity.commerce.CommercialEvents());
        MinecraftForge.EVENT_BUS.register(new EmploymentEvents());WorkNetwork.register();br.com.kraftcity.industry.IndustryNetwork.register();
    }
    private WorkModule(){}
}
