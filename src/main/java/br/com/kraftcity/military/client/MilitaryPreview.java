package br.com.kraftcity.military.client;

import br.com.kraftcity.military.Military;
import br.com.kraftcity.employment.Profession;
import br.com.kraftcity.employment.WorkModule;
import com.mojang.blaze3d.vertex.BufferBuilder;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="residenciasaldeoes",value=Dist.CLIENT)
public final class MilitaryPreview {
    private static final RenderType FACES=RenderType.create("residenciasaldeoes:military_preview",
        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
        256,false,true,FaceStates.create());
    private static final class FaceStates extends net.minecraft.client.renderer.RenderStateShard {
        private FaceStates(){super("military_faces",()->{},()->{});}
        private static RenderType.CompositeState create(){
            return RenderType.CompositeState.builder().setShaderState(POSITION_COLOR_SHADER)
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false);
        }
    }
    private static final Set<BlockPos> POSTS=new HashSet<>();
    private static ClientLevel level;

    private static void updateWorld(){
        var current=Minecraft.getInstance().level;
        if(current!=level){
            POSTS.clear();
            level=current;
        }
    }

    public static boolean enabled(BlockPos pos){
        updateWorld();
        return POSTS.contains(pos);
    }

    static void toggle(BlockPos pos){
        updateWorld();
        if(!POSTS.remove(pos))POSTS.add(pos.immutable());
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
            ||br.com.kraftcity.industry.client.WorkPreview.isOculusShadowPass())return;
        updateWorld();
        if(level==null||POSTS.isEmpty())return;
        POSTS.removeIf(pos->{
            if(!level.hasChunkAt(pos))return false;
            var state=level.getBlockState(pos);
            return !state.is(WorkModule.MILITARY.get())&&!state.is(WorkModule.PROFESSIONAL.get(Profession.SOLDIER).get());
        });
        if(POSTS.isEmpty())return;
        var pose=event.getPoseStack();
        var camera=event.getCamera().getPosition();
        var buffers=MultiBufferSource.immediate(new BufferBuilder(1024));
        var faceBuffers=MultiBufferSource.immediate(new BufferBuilder(2048));
        var faces=faceBuffers.getBuffer(FACES);
        var lines=buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-camera.x,-camera.y,-camera.z);
        for(var pos:POSTS){
            var bounds=Military.areaBounds(pos);
            if(bounds.inflate(96).contains(camera)){
                drawFaces(faces,pose.last().pose(),bounds);
                LevelRenderer.renderLineBox(pose,lines,bounds,1F,.13F,.15F,.8F);
            }
        }
        pose.popPose();
        faceBuffers.endBatch(FACES);
        buffers.endBatch(RenderType.lines());
    }

    private static void drawFaces(com.mojang.blaze3d.vertex.VertexConsumer buffer,com.mojang.math.Matrix4f pose,
                                  net.minecraft.world.phys.AABB box){
        double[][] corners={{box.minX,box.minY,box.minZ},{box.maxX,box.minY,box.minZ},
            {box.maxX,box.maxY,box.minZ},{box.minX,box.maxY,box.minZ},{box.minX,box.minY,box.maxZ},
            {box.maxX,box.minY,box.maxZ},{box.maxX,box.maxY,box.maxZ},{box.minX,box.maxY,box.maxZ}};
        for(int[] face:new int[][]{{0,1,2,3},{4,5,6,7},{0,4,7,3},{1,5,6,2},{0,1,5,4},{3,2,6,7}})
            for(int vertex:face){
                var point=corners[vertex];
                buffer.vertex(pose,(float)point[0],(float)point[1],(float)point[2]).color(1F,.13F,.15F,.035F).endVertex();
            }
    }

    private MilitaryPreview(){}
}
