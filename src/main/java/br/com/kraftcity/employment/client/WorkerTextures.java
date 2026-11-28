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
    private static final ResourceLocation RED_XP=new ResourceLocation(KraftCity.MOD_ID,"soldier_xp");
    public static final int XP_WIDTH=182, XP_Y=22;
    public static final ResourceLocation DEFAULT_ROBE=new ResourceLocation("textures/entity/villager/type/plains.png");
    private static final Map<ArmSource,ArmTexture> ARMS=new LinkedHashMap<>(32,.75F,true);
    private static boolean xpLoaded,redXpLoaded;
    private static int serial;

    private record ArmSource(ResourceLocation skin,ResourceLocation robe) {}
    private record ArmTexture(AbstractTexture source,ResourceLocation generated) {}

    @SubscribeEvent
    public static void reload(RegisterClientReloadListenersEvent event){
        event.registerReloadListener((ResourceManagerReloadListener)resources->{
            var textures=Minecraft.getInstance().getTextureManager();
            if(xpLoaded)textures.release(XP);
            xpLoaded=false;
            if(redXpLoaded)textures.release(RED_XP);
            redXpLoaded=false;
            ARMS.values().forEach(arms->textures.release(arms.generated));
            ARMS.clear();
        });
    }

    public static ResourceLocation experience(){
        return experience(false);
    }

    private static ResourceLocation experience(boolean red){
        var texture=red?RED_XP:XP;
        if(red?redXpLoaded:xpLoaded)return texture;
        var mc=Minecraft.getInstance();
        try(var input=mc.getResourceManager().open(ICONS)){
            NativeImage image=NativeImage.read(input);
            for(int y=69*image.getHeight()/256;y<74*image.getHeight()/256;y++)
                for(int x=0;x<182*image.getWidth()/256;x++)
                    image.setPixelRGBA(x,y,red?red(image.getPixelRGBA(x,y)):cyan(image.getPixelRGBA(x,y)));
            mc.getTextureManager().register(texture,new DynamicTexture(image));
            if(red)redXpLoaded=true;else xpLoaded=true;
            return texture;
        }catch(IOException failure){
            return ICONS;
        }
    }

    private static int red(int pixel){
        int green=(pixel>>>8)&255;
        if(green<=(pixel&255)||green<=((pixel>>>16)&255))return pixel;
        return (pixel&0xFF000000)|((green*53/255)<<16)|((green*57/255)<<8)|(green*229/255);
    }

    public static void drawExperience(com.mojang.blaze3d.vertex.PoseStack pose,int x,int y,int progress,boolean red){
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,1);
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0,ICONS);
        net.minecraft.client.gui.GuiComponent.blit(pose,x,y,0F,64F,XP_WIDTH,5,256,256);
        if(progress>0){
            com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0,experience(red));
            net.minecraft.client.gui.GuiComponent.blit(pose,x,y,0F,69F,progress,5,256,256);
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
                sleeve=customSleeves(dynamic.getPixels());
            }else try(var input=mc.getResourceManager().open(skin);var original=NativeImage.read(input)){
                sleeve=sleeves(original,fabric);
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

    private static NativeImage customSleeves(NativeImage source){
        var result=new NativeImage(64,64,true);
        // Vanilla's crossed-arm sleeves are 4×8×4 cuboids at UV (44,22).
        // Preserve each face and stretch only the vertical sides to the straight-arm height.
        for(int y=22;y<38;y++){
            int sourceY=y<26?y:26+(y-26)*8/12;
            for(int x=44;x<60;x++){
                result.setPixelRGBA(x,y,source.getPixelRGBA(x*source.getWidth()/64,sourceY*source.getHeight()/64));
            }
        }
        return result;
    }

    private static NativeImage sleeves(NativeImage source,NativeImage fabric){
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
                pixel=sample(source,8+column,46+yOffset,biome);
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
