package br.com.kraftcity.military;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.*;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class MilitaryModule {
    private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,"residenciasaldeoes");
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,"residenciasaldeoes");
    public static final RegistryObject<EntityType<SoldierZombie>> ZOMBIE=ENTITIES.register("soldier_zombie",()->EntityType.Builder.of(SoldierZombie::new,MobCategory.MONSTER).sized(.6F,1.95F).clientTrackingRange(10).build("soldier_zombie"));
    public static final RegistryObject<MenuType<SoldierMenu>> MENU=MENUS.register("soldier",()->IForgeMenuType.create(SoldierMenu::new));
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue HEAL_TICKS, CONTRIBUTION_TICKS, DETECTION;
    static{var b=new ForgeConfigSpec.Builder();HEAL_TICKS=b.comment("Provisorio: 1 ponto de vida a cada N ticks ativos; dormindo intervalo N/2.").defineInRange("curaNormalTicks",200,2,24000);CONTRIBUTION_TICKS=b.comment("Provisorio: validade de contribuicoes de combate; tick absoluto salvo no alvo.").defineInRange("contribuicaoTicks",1200,20,24000);DETECTION=b.comment("Raio de busca local. O maximo efetivo e 32 e os limites fixos do posto continuam valendo.").defineInRange("deteccaoPosto",48,15,128);SPEC=b.build();}
    public static void initialize(IEventBus bus){
        ENTITIES.register(bus);MENUS.register(bus);bus.addListener(MilitaryModule::attributes);bus.addListener(MilitaryModule::villagerAttributes);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,SPEC,"kraftcity-military-server.toml");
        MinecraftForge.EVENT_BUS.register(new MilitaryEvents());MilitaryNetwork.register();
    }
    private static void attributes(EntityAttributeCreationEvent e){e.put(ZOMBIE.get(),Zombie.createAttributes().build());}
    private static void villagerAttributes(EntityAttributeModificationEvent e){e.add(EntityType.VILLAGER,Attributes.ATTACK_DAMAGE,2);}
}
