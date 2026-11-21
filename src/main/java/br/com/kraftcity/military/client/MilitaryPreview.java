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
        var lines=buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-camera.x,-camera.y,-camera.z);
        for(var pos:POSTS){
            var bounds=Military.areaBounds(pos);
            if(bounds.inflate(96).contains(camera))LevelRenderer.renderLineBox(pose,lines,bounds,1F,.13F,.15F,.8F);
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private MilitaryPreview(){}
}
