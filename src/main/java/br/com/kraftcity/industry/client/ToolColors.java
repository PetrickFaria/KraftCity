package br.com.kraftcity.industry.client;

import br.com.kraftcity.KraftCity;
import br.com.kraftcity.industry.IndustrialTools;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterTextureAtlasSpriteLoadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid=KraftCity.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ToolColors {
    private static final int[] COLORS={0xAC7948,0xDFDFDF,0xFFD850,0x48E88C,0x65DAF5,0x545361,0x949494};
    private static final ResourceLocation CAST=new ResourceLocation("cast");

    @SubscribeEvent public static void register(RegisterTextureAtlasSpriteLoadersEvent event){
        event.register("material_tool",(atlas,resources,info,resource,width,height,x,y,mipmap,image)->{
            recolor(resources,info.name(),image);
            return new MaterialSprite(atlas,info,width,height,x,y,mipmap,image);
        });
    }

    @SubscribeEvent public static void clientSetup(FMLClientSetupEvent event){
        event.enqueueWork(()->{
            var cast=ItemProperties.getProperty(Items.FISHING_ROD,CAST);
            IndustrialTools.RODS.values().forEach(item->ItemProperties.register(item.get(),CAST,cast));
        });
    }

    private static void recolor(ResourceManager resources,ResourceLocation sprite,NativeImage output){
        String name=sprite.getPath().substring("item/".length());
        int separator=name.indexOf('_');
        String material=name.substring(0,separator);
        String tool=name.substring(separator+1);
        int tier=java.util.Arrays.asList(IndustrialTools.NAMES).indexOf(material);
        boolean rod=tool.startsWith("fishing_rod");
        boolean shears=tool.equals("shears");
        String source=rod||shears?tool:"diamond_"+tool;
        // Use the installed texture's original alpha and geometry, and keep the iron handle pixels verbatim.
        try(var original=readVanilla(resources,source);
            var iron=rod||shears?null:readVanilla(resources,"iron_"+tool)){
            for(int y=0;y<output.getHeight();y++){
                for(int x=0;x<output.getWidth();x++){
                    int pixel=sample(original,output,x,y);
                    int red=pixel&255,green=(pixel>>>8)&255,blue=(pixel>>>16)&255;
                    boolean brown=red>green&&green>blue;
                    if(rod&&brown)pixel=shade(pixel,COLORS[tier],red,137);
                    if(shears&&red==green&&green==blue&&red>66)pixel=shade(pixel,COLORS[tier],red,213);
                    if(iron!=null)pixel=brown?(pixel&0xFF000000)|(sample(iron,output,x,y)&0xFFFFFF)
                        :multiply(pixel,COLORS[tier]);
                    output.setPixelRGBA(x,y,pixel);
                }
            }
        }catch(IOException error){
            throw new IllegalStateException("Unable to load vanilla texture for "+sprite,error);
        }
    }

    private static NativeImage readVanilla(ResourceManager resources,String name)throws IOException{
        try(var in=resources.open(new ResourceLocation("minecraft","textures/item/"+name+".png"))){
            return NativeImage.read(in);
        }
    }

    private static int sample(NativeImage image,NativeImage target,int x,int y){
        return image.getPixelRGBA(x*image.getWidth()/target.getWidth(),y*image.getHeight()/target.getHeight());
    }

    private static int multiply(int pixel,int rgb){
        int red=(pixel&255)*((rgb>>>16)&255)/255;
        int green=((pixel>>>8)&255)*((rgb>>>8)&255)/255;
        int blue=((pixel>>>16)&255)*(rgb&255)/255;
        return (pixel&0xFF000000)|(blue<<16)|(green<<8)|red;
    }

    private static int shade(int pixel,int rgb,int tone,int maximum){
        int r=Math.min(255,((rgb>>>16)&255)*tone/maximum);
        int g=Math.min(255,((rgb>>>8)&255)*tone/maximum);
        int b=Math.min(255,(rgb&255)*tone/maximum);
        return (pixel&0xFF000000)|(b<<16)|(g<<8)|r;
    }

    private static final class MaterialSprite extends TextureAtlasSprite {
        private MaterialSprite(TextureAtlas atlas,Info info,int width,int height,int x,int y,int mipmap,NativeImage image){
            super(atlas,info,mipmap,width,height,x,y,image);
        }
    }
}
