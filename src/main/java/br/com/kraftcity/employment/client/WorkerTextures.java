package br.com.kraftcity.employment.client;

import br.com.kraftcity.KraftCity;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=KraftCity.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class WorkerTextures {
    public static final ResourceLocation ICONS=new ResourceLocation("textures/gui/icons.png");
    private static final ResourceLocation XP=new ResourceLocation(KraftCity.MOD_ID,"worker_xp");
    public static final ResourceLocation DEFAULT_ROBE=new ResourceLocation("textures/entity/villager/type/plains.png");
    private static final Map<ArmSource,ArmTexture> ARMS=new LinkedHashMap<>(32,.75F,true);
    private static boolean xpLoaded;
    private static int serial;

    private record ArmSource(ResourceLocation skin,ResourceLocation robe) {}
    private record ArmTexture(AbstractTexture source,ResourceLocation generated) {}

    @SubscribeEvent
    public static void reload(RegisterClientReloadListenersEvent event){
        event.registerReloadListener((ResourceManagerReloadListener)resources->{
            var textures=Minecraft.getInstance().getTextureManager();
            if(xpLoaded)textures.release(XP);
            xpLoaded=false;
            ARMS.values().forEach(arms->textures.release(arms.generated));
            ARMS.clear();
        });
    }

    public static ResourceLocation experience(){
        if(xpLoaded)return XP;
        var mc=Minecraft.getInstance();
        try(var input=mc.getResourceManager().open(ICONS)){
            NativeImage image=NativeImage.read(input);
            for(int y=69*image.getHeight()/256;y<74*image.getHeight()/256;y++)
                for(int x=0;x<182*image.getWidth()/256;x++)
                    image.setPixelRGBA(x,y,cyan(image.getPixelRGBA(x,y)));
            mc.getTextureManager().register(XP,new DynamicTexture(image));
            xpLoaded=true;
            return XP;
        }catch(IOException failure){
            return ICONS;
        }
    }

    public static int cyan(int pixel){
        int green=(pixel>>>8)&255;
        if(green<=(pixel&255)||green<=((pixel>>>16)&255))return pixel;
        return (pixel&0xFF000000)|(green<<16)|((green*223/255)<<8);
    }

    public static ResourceLocation arms(ResourceLocation skin,ResourceLocation robe){
        var mc=Minecraft.getInstance();
        var textures=mc.getTextureManager();
        var source=textures.getTexture(skin);
        var key=new ArmSource(skin,robe);
        var previous=ARMS.get(key);
        if(previous!=null&&previous.source==source)return previous.generated;
        var garment=mc.getResourceManager().getResource(robe).isPresent()?robe:DEFAULT_ROBE;
        try(var robeFile=mc.getResourceManager().open(garment);var fabric=NativeImage.read(robeFile)){
            NativeImage sleeve;
            if(source instanceof DynamicTexture dynamic && dynamic.getPixels()!=null){
                sleeve=sleeves(dynamic.getPixels(),fabric,true);
            }else try(var input=mc.getResourceManager().open(skin);var original=NativeImage.read(input)){
                sleeve=sleeves(original,fabric,false);
            }
            if(previous!=null)textures.release(previous.generated);
            if(ARMS.size()>=128&&previous==null){
                var iterator=ARMS.values().iterator();
                var oldest=iterator.next();
                iterator.remove();
                textures.release(oldest.generated);
            }
            var resource=new ResourceLocation(KraftCity.MOD_ID,"worker_arms/"+serial++);
            textures.register(resource,new DynamicTexture(sleeve));
            ARMS.put(key,new ArmTexture(source,resource));
            return resource;
        }catch(IOException failure){
            return skin;
        }
    }

    private static NativeImage sleeves(NativeImage source,NativeImage fabric,boolean custom){
        var result=new NativeImage(64,64,true);
        int skin=findOpaque(source,8,8,16,12);
        if(skin==0)skin=findOpaque(source,0,0,32,18);
        if(skin==0)skin=0xFF7A9DB8;
        for(int y=22;y<38;y++)for(int x=44;x<60;x++){
            int row=y<26&&x>=52&&x<56?11:y-26;
            int column=(x-44)%4,yOffset=Math.max(0,row);
            int pixel;
            if(row<7){
                int biome=sample(fabric,24+column,29+yOffset%4,sample(fabric,29,31,0xFF36605E));
                int torso=custom?sample(source,24+column,29+yOffset%4,biome):biome;
                pixel=sample(source,8+column,46+yOffset,torso);
            }else pixel=sample(source,10+column,9+((row-7)%2),skin);
            result.setPixelRGBA(x,y,pixel|0xFF000000);
        }
        return result;
    }

    private static int findOpaque(NativeImage source,int x0,int y0,int x1,int y1){
        for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){
            int pixel=sample(source,x,y,0);
            if(pixel!=0)return pixel;
        }
        return 0;
    }

    private static int sample(NativeImage source,int x,int y,int fallback){
        int pixel=source.getPixelRGBA(x*source.getWidth()/64,y*source.getHeight()/64);
        return (pixel>>>24)==255?pixel:fallback;
    }

    private WorkerTextures(){}
}
