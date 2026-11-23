package br.com.residenciasaldeoes.client;

import br.com.residenciasaldeoes.ResidenciasDosAldeoes;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A blend pass only for imported resident textures containing fractional alpha. */
@Mod.EventBusSubscriber(modid = ResidenciasDosAldeoes.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ResidentSkinRenderType extends RenderType {
    private static ShaderInstance shader;
    private static final ShaderStateShard SKIN_SHADER = new ShaderStateShard(() -> shader);
    private static final Function<ResourceLocation, RenderType> ALPHA = Util.memoize(texture ->
            create("resident_skin_alpha", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true,
                    CompositeState.builder()
                            .setShaderState(SKIN_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .createCompositeState(true)));

    private ResidentSkinRenderType() {
        super("resident_skin_alpha", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true, () -> {}, () -> {});
    }

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceManager(),
                new ResourceLocation(ResidenciasDosAldeoes.MODID, "resident_skin_alpha"), DefaultVertexFormat.NEW_ENTITY),
                loaded -> shader = loaded);
    }

    public static RenderType layer(ResourceLocation texture) {
        return VillagerSkinTextures.hasFractionalAlpha(texture) ? blended(texture) : RenderType.entityCutoutNoCull(texture);
    }

    public static RenderType blended(ResourceLocation texture) {
        return shader == null ? RenderType.entityTranslucent(texture) : ALPHA.apply(texture);
    }
}
