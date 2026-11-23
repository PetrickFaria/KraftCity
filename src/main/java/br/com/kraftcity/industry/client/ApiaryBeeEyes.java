package br.com.kraftcity.industry.client;

import br.com.kraftcity.industry.ApiaryBeeAppearance;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.BeeModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.BeeRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="residenciasaldeoes",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ApiaryBeeEyes extends RenderLayer<Bee,BeeModel<Bee>> {
    private static final ResourceLocation EYES=new ResourceLocation("residenciasaldeoes","textures/entity/apiary_bee_eyes.png");
    private static final ResourceLocation CUSTOM_MODEL=new ResourceLocation("minecraft","optifine/cem/bee.jem");
    private static final ResourceLocation CUSTOM_MODEL_EMF=new ResourceLocation("minecraft","emf/cem/bee.jem");
    private final BeeRenderer renderer;
    private final Map<Bee,Integer> sparkles=new WeakHashMap<>();

    private ApiaryBeeEyes(BeeRenderer renderer){
        super(renderer);
        this.renderer=renderer;
    }

    @SubscribeEvent public static void register(EntityRenderersEvent.AddLayers event){
        BeeRenderer renderer=event.getRenderer(EntityType.BEE);
        if(renderer!=null)renderer.addLayer(new ApiaryBeeEyes(renderer));
    }

    @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,Bee bee,float stride,
        float amount,float partial,float age,float yaw,float pitch){
        if(bee.isInvisible()||bee.isAngry()||!((ApiaryBeeAppearance)bee).kraftcity$isBonded())return;
        var resources=Minecraft.getInstance().getResourceManager();
        var texture=resources.getResource(renderer.getTextureLocation(bee));
        if(texture.filter(resource->resource.sourcePackId().equals("Default")).isPresent()
            &&resources.getResource(CUSTOM_MODEL).isEmpty()&&resources.getResource(CUSTOM_MODEL_EMF).isEmpty()){
            getParentModel().renderToBuffer(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(EYES)),
                light,OverlayTexture.NO_OVERLAY,1,1,1,1);
        }else if(bee.tickCount>=sparkles.getOrDefault(bee,0)){
            sparkles.put(bee,bee.tickCount+80);
            bee.level.addParticle(ParticleTypes.END_ROD,bee.getX(),bee.getY()+bee.getBbHeight()+.13,bee.getZ(),0,.01,0);
        }
    }
}
