package br.com.kraftcity.industry.client;

import br.com.kraftcity.employment.Profession;
import br.com.kraftcity.employment.client.WorkerPose;
import br.com.residenciasaldeoes.ResidenciasDosAldeoes;
import br.com.residenciasaldeoes.client.ResidentAppearanceModels;
import br.com.residenciasaldeoes.client.ResidentOpacity;
import br.com.residenciasaldeoes.client.ResidentSkinRenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Cosmetic headwear for the traditional Villager apiary worker. */
@Mod.EventBusSubscriber(modid = ResidenciasDosAldeoes.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BeekeeperHelmetLayer extends RenderLayer<Villager, VillagerModel<Villager>> {
    private static final ModelLayerLocation LAYER = new ModelLayerLocation(id("beekeeper_helmet"), "main");
    private static final ResourceLocation HAT_TEXTURE = id("textures/entity/beekeeper_helmet.png");
    private static final ResourceLocation VEIL_TEXTURE = id("textures/entity/beekeeper_veil.png");
    private static final float VEIL_HALF_WIDTH = 7.5F / 16;
    private static final float VEIL_FRONT = -10.5F / 16;
    private static final float VEIL_BACK = 5.5F / 16;
    private static final float VEIL_TOP = -10F / 16;
    private static final float VEIL_BOTTOM = 3F / 16;
    private final ModelPart hat;

    private BeekeeperHelmetLayer(VillagerRenderer renderer, ModelPart hat) {
        super(renderer);
        this.hat = hat;
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(ResidenciasDosAldeoes.MODID, path);
    }

    @SubscribeEvent
    public static void registerModel(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LAYER, BeekeeperHelmetLayer::model);
    }

    @SubscribeEvent
    public static void addLayer(EntityRenderersEvent.AddLayers event) {
        if (event.getRenderer(EntityType.VILLAGER) instanceof VillagerRenderer renderer) {
            renderer.addLayer(new BeekeeperHelmetLayer(renderer, event.getEntityModels().bakeLayer(LAYER)));
        }
    }

    private static LayerDefinition model() {
        var mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("hat", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8F, -11F, -11F, 16, 1, 17)
                .texOffs(0, 22).addBox(-5.5F, -17F, -6F, 11, 6, 11)
                .texOffs(48, 22).addBox(-5.75F, -12.8F, -6.25F, 11.5F, 1.8F, 11.5F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, Villager villager,
                       float stride, float amount, float partial, float age, float yaw, float pitch) {
        if (villager.isInvisible() || ResidentAppearanceModels.usesSteve(villager)
                || WorkerPose.profession(villager) != Profession.BEEKEEPER) return;
        int opacity = ResidentOpacity.renderingPercent();
        if (opacity == 0) return;

        pose.pushPose();
        try {
            getParentModel().getHead().translateAndRotate(pose);
            hat.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(HAT_TEXTURE)), light, OverlayTexture.NO_OVERLAY);
            // These quads emit vertices directly, so they apply the resident fade here.
            // Preview buffers already scale incoming vertex alpha.
            int alpha = ResidentOpacity.isPreviewRendering() ? 255 : Math.round(255 * opacity / 100F);
            var veil = buffers.getBuffer(ResidentSkinRenderType.blended(VEIL_TEXTURE));
            var head = pose.last();
            panel(head, veil, light, alpha, -VEIL_HALF_WIDTH, VEIL_FRONT, VEIL_HALF_WIDTH, VEIL_FRONT, 0, -1);
            panel(head, veil, light, alpha, -VEIL_HALF_WIDTH, VEIL_BACK, -VEIL_HALF_WIDTH, VEIL_FRONT, -1, 0);
            panel(head, veil, light, alpha, VEIL_HALF_WIDTH, VEIL_FRONT, VEIL_HALF_WIDTH, VEIL_BACK, 1, 0);
        } finally {
            pose.popPose();
        }
    }

    private static void panel(PoseStack.Pose pose, VertexConsumer out, int light, int alpha,
                              float left, float near, float right, float far, float nx, float nz) {
        vertex(pose, out, light, alpha, left, VEIL_TOP, near, 0, 0, nx, nz);
        vertex(pose, out, light, alpha, left, VEIL_BOTTOM, near, 0, 1, nx, nz);
        vertex(pose, out, light, alpha, right, VEIL_BOTTOM, far, 1, 1, nx, nz);
        vertex(pose, out, light, alpha, right, VEIL_TOP, far, 1, 0, nx, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer out, int light, int alpha,
                               float x, float y, float z, float u, float v, float nx, float nz) {
        out.vertex(pose.pose(), x, y, z).color(255, 255, 255, alpha).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(pose.normal(), nx, 0, nz).endVertex();
    }
}
