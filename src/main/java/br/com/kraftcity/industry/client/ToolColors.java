package br.com.kraftcity.industry.client;
import br.com.kraftcity.industry.IndustrialTools;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid="residenciasaldeoes",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ToolColors {
    private static final int[] COLORS={0xAC7948,0xDFDFDF,0xFFD850,0x48E88C,0x65E8E0,0x675663};
    @SubscribeEvent public static void register(RegisterColorHandlersEvent.Item e){
        e.register((s,t)->0x48E88C,IndustrialTools.HOE.get(),IndustrialTools.AXE.get(),IndustrialTools.SWORD.get());
        IndustrialTools.SHEARS.forEach((tier,item)->e.register((s,t)->COLORS[tier],item.get()));
        IndustrialTools.RODS.forEach((tier,item)->e.register((s,t)->COLORS[tier],item.get()));
    }
}
