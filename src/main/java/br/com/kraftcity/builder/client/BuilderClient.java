package br.com.kraftcity.builder.client;

import br.com.kraftcity.builder.*;
import java.util.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="residenciasaldeoes",value=Dist.CLIENT)
public final class BuilderClient {
    private static UUID schema;private static BlockPos size=BlockPos.ZERO,origin=BlockPos.ZERO,cornerA,cornerB;private static int rotation,mirror,total;private static String name="",previewDimension="";
    private static final List<Blueprint.Cell> CELLS=new ArrayList<>();
    private record Mesh(VertexBuffer vbo,AABB bounds){}
    private static final List<Mesh> MESHES=new ArrayList<>();private static final Deque<List<Blueprint.Cell>> BAKE=new ArrayDeque<>();
    private static BufferBuilder building;private static List<Blueprint.Cell> group;private static int groupCursor;private static AABB groupBounds;
    private static final RenderType HOLOGRAM=RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
    private static List<CompoundTag> jobs=List.of();private static final Map<Integer,Long> WORKING=new HashMap<>();
    private record Flight(Vec3 from,Vec3 to,BlockState state,long start,int duration){}
    private static final List<Flight> FLIGHTS=new ArrayList<>();
    public static void receive(CompoundTag t){var mc=Minecraft.getInstance();switch(t.getString("type")){
        case "menu"->{if(mc.player!=null&&mc.player.containerMenu instanceof BuilderMenu m&&m.containerId==t.getInt("menu")&&m.stationId.equals(t.getUUID("station"))){m.snapshot=t;if(t.hasUUID("selectedSchema"))m.selectedSchema=t.getUUID("selectedSchema");if(t.hasUUID("selectedJob"))m.selectedJob=t.getUUID("selectedJob");if(t.contains("library"))m.library=t.getList("library",Tag.TAG_COMPOUND).stream().map(x->(CompoundTag)x).toList();if(mc.screen instanceof BuilderScreen s)s.buttons();}}
        case "materials"->{if(mc.player!=null&&mc.player.containerMenu instanceof BuilderMenu m&&m.containerId==t.getInt("menu")&&m.stationId.equals(t.getUUID("station"))&&mc.screen instanceof BuilderScreen s)s.materials(t);}
        case "editor"->mc.setScreen(new BlueprintEditorScreen(t.getString("mode").equals("engineer")));
        case "selection_clear"->{cornerA=null;cornerB=null;}
        case "selection"->{cornerA=BlockPos.of(t.getLong("a"));cornerB=BlockPos.of(t.getLong("b"));}
        case "preview"->{UUID next=t.getUUID("schema");boolean different=!next.equals(schema);boolean moved=rotation!=t.getInt("rotation")||mirror!=t.getInt("mirror");schema=next;previewDimension=t.getString("dimension");name=t.getString("name");size=BlockPos.of(t.getLong("size"));origin=BlockPos.of(t.getLong("origin"));rotation=t.getInt("rotation");mirror=t.getInt("mirror");total=t.getInt("total");if(different){CELLS.clear();clearMeshes();}else if(moved)prepareMeshes();}
        case "cells"->{if(schema==null||!schema.equals(t.getUUID("schema"))||t.getInt("start")!=CELLS.size())break;var positions=t.getLongArray("positions");var states=t.getIntArray("states");if(positions.length!=states.length||positions.length>512||CELLS.size()+positions.length>total||total>1048576)break;for(int i=0;i<positions.length;i++)CELLS.add(new Blueprint.Cell(BlockPos.of(positions[i]),Block.stateById(states[i])));if(CELLS.size()==total)prepareMeshes();}
        case "clear"->{schema=null;CELLS.clear();cornerA=null;cornerB=null;clearMeshes();}
        case "flight"->{if(mc.level!=null){FLIGHTS.add(new Flight(new Vec3(t.getDouble("x"),t.getDouble("y"),t.getDouble("z")),Vec3.atCenterOf(BlockPos.of(t.getLong("target"))),Block.stateById(t.getInt("state")),t.getLong("time"),Math.max(1,t.getInt("duration"))));WORKING.put(t.getInt("entity"),mc.level.getGameTime()+25);}}
        case "works"->{jobs=t.getList("jobs",Tag.TAG_COMPOUND).stream().map(x->(CompoundTag)x).toList();if(mc.level!=null){WORKING.clear();for(var j:jobs)for(var raw:j.getList("workers",Tag.TAG_COMPOUND)){var w=(CompoundTag)raw;if(w.getBoolean("working"))WORKING.put(w.getInt("entity"),mc.level.getGameTime()+15);}}}
    }}
    public static boolean working(net.minecraft.world.entity.Entity e){return e!=null&&e.level.isClientSide&&!(e instanceof net.minecraft.world.entity.LivingEntity v&&v.isSleeping())&&WORKING.getOrDefault(e.getId(),-1L)>=e.level.getGameTime();}
    public static int previewCells(){return CELLS.size();}
    public static int flyingBlocks(){return FLIGHTS.size();}
    private static void clearMeshes(){for(var m:MESHES)m.vbo.close();MESHES.clear();BAKE.clear();if(building!=null){building.discard();building=null;}group=null;}
    private static void prepareMeshes(){clearMeshes();if(CELLS.size()!=total)return;var positions=new HashMap<BlockPos,BlockState>();for(var c:CELLS)positions.put(c.pos(),c.state());var chunks=new LinkedHashMap<BlockPos,List<Blueprint.Cell>>();for(var c:CELLS){boolean enclosed=c.state().getMaterial().isSolidBlocking();if(enclosed)for(var dir:Direction.values()){var n=positions.get(c.pos().relative(dir));if(n==null||!n.getMaterial().isSolidBlocking()){enclosed=false;break;}}if(enclosed)continue;var at=Blueprint.transform(c.pos(),size,rotation,mirror);var cell=new Blueprint.Cell(at,Blueprint.transform(c.state(),rotation,mirror));chunks.computeIfAbsent(new BlockPos(at.getX()>>4,at.getY()>>4,at.getZ()>>4),k->new ArrayList<>()).add(cell);}BAKE.addAll(chunks.values());}
    private static void bake(){var mc=Minecraft.getInstance();int budget=256;while(budget-->0){if(group==null){group=BAKE.poll();if(group==null)return;groupCursor=0;building=new BufferBuilder(65536);building.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);groupBounds=null;}var c=group.get(groupCursor++);var pose=new PoseStack();pose.translate(c.pos().getX(),c.pos().getY(),c.pos().getZ());var out=new Alpha(building,.32F);if(c.state().getBlock() instanceof LiquidBlock)fluid(c.state(),pose,out);else mc.getBlockRenderer().renderSingleBlock(c.state(),pose,type->out,15728880,OverlayTexture.NO_OVERLAY);var box=new AABB(c.pos());groupBounds=groupBounds==null?box:groupBounds.minmax(box);if(groupCursor>=group.size()){var buffer=new VertexBuffer();buffer.bind();buffer.upload(building.end());VertexBuffer.unbind();MESHES.add(new Mesh(buffer,groupBounds));building=null;group=null;}}}
    private static void fluid(BlockState state,PoseStack pose,VertexConsumer out){boolean water=state.is(Blocks.WATER);var sprite=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(new net.minecraft.resources.ResourceLocation("minecraft",water?"block/water_still":"block/lava_still"));int color=water?0x3F76E4:0xFFFFFF;float[][][] faces={{{0,1,0},{0,1,1},{1,1,1},{1,1,0}},{{0,0,1},{0,0,0},{1,0,0},{1,0,1}},{{0,0,0},{0,1,0},{1,1,0},{1,0,0}},{{1,0,1},{1,1,1},{0,1,1},{0,0,1}},{{0,0,1},{0,1,1},{0,1,0},{0,0,0}},{{1,0,0},{1,1,0},{1,1,1},{1,0,1}}};int[][] normals={{0,1,0},{0,-1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0}};for(int f=0;f<6;f++)for(int i=0;i<4;i++){var v=faces[f][i];out.vertex(pose.last().pose(),v[0],v[1],v[2]).color((color>>16)&255,(color>>8)&255,color&255,255).uv(i==0||i==1?sprite.getU0():sprite.getU1(),i==0||i==3?sprite.getV1():sprite.getV0()).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(pose.last().normal(),normals[f][0],normals[f][1],normals[f][2]).endVertex();}}
    private static boolean shadows(){if(!net.minecraftforge.fml.ModList.get().isLoaded("oculus"))return false;try{return (Boolean)Class.forName("net.coderbot.iris.shadows.ShadowRenderingState").getMethod("areShadowsCurrentlyBeingRendered").invoke(null);}catch(ReflectiveOperationException|LinkageError e){return true;}}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||shadows())return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;var pose=event.getPoseStack();var cam=event.getCamera().getPosition();
        if(schema!=null&&mc.level.dimension().location().toString().equals(previewDimension)){bake();HOLOGRAM.setupRenderState();RenderSystem.depthMask(false);pose.pushPose();pose.translate(origin.getX()-cam.x,origin.getY()-cam.y,origin.getZ()-cam.z);for(var mesh:MESHES)if(event.getFrustum().isVisible(mesh.bounds.move(origin))){mesh.vbo.bind();mesh.vbo.drawWithShader(pose.last().pose(),RenderSystem.getProjectionMatrix(),GameRenderer.getRendertypeEntityTranslucentShader());}VertexBuffer.unbind();pose.popPose();HOLOGRAM.clearRenderState();RenderSystem.depthMask(true);var ts=rotation%2==0?size:new BlockPos(size.getZ(),size.getY(),size.getX());box(event,new AABB(origin,origin.offset(ts)),.3F,.65F,1F);}
        if(cornerA!=null&&cornerB!=null){var min=new BlockPos(Math.min(cornerA.getX(),cornerB.getX()),Math.min(cornerA.getY(),cornerB.getY()),Math.min(cornerA.getZ(),cornerB.getZ()));var max=new BlockPos(Math.max(cornerA.getX(),cornerB.getX())+1,Math.max(cornerA.getY(),cornerB.getY())+1,Math.max(cornerA.getZ(),cornerB.getZ())+1);box(event,new AABB(min,max),.85F,.55F,1F);}
        var buffers=MultiBufferSource.immediate(new BufferBuilder(4096));long now=mc.level.getGameTime();FLIGHTS.removeIf(f->now-f.start>f.duration+2);for(var f:FLIGHTS){float p=Math.max(0,Math.min(1,(now+event.getPartialTick()-f.start)/f.duration));var at=f.from.lerp(f.to,p).add(0,Math.sin(p*Math.PI)*Math.min(4,f.from.distanceTo(f.to)/4),0);pose.pushPose();pose.translate(at.x-cam.x,at.y-cam.y,at.z-cam.z);pose.scale(.3F,.3F,.3F);pose.translate(-.5,-.5,-.5);if(f.state.getBlock() instanceof LiquidBlock)fluid(f.state,pose,buffers.getBuffer(HOLOGRAM));else mc.getBlockRenderer().renderSingleBlock(f.state,pose,buffers,15728880,OverlayTexture.NO_OVERLAY);pose.popPose();}buffers.endBatch();
        for(var j:jobs){var p=BlockPos.of(j.getLong("origin"));if(cam.distanceToSqr(Vec3.atCenterOf(p))>4096)continue;int total=Math.max(1,j.getInt("total")),percent=Math.min(100,j.getInt("done")*100/total);pose.pushPose();pose.translate(p.getX()+.5-cam.x,p.getY()-cam.y,p.getZ()+.5-cam.z);pose.mulPose(event.getCamera().rotation());pose.scale(-.025F,-.025F,.025F);String label=j.getString("name")+" · "+percent+"%";mc.font.drawInBatch(label,-mc.font.width(label)/2F,0,0xC8FFFFFF,false,pose.last().pose(),buffers,false,0x50203040,15728880);String bar="█".repeat(percent/10)+"░".repeat(10-percent/10);mc.font.drawInBatch(bar,-mc.font.width(bar)/2F,11,0xC8A0EEA0,false,pose.last().pose(),buffers,false,0,15728880);pose.popPose();}buffers.endBatch();
    }
    private static void box(RenderLevelStageEvent e,AABB bounds,float r,float g,float b){var pose=e.getPoseStack();var cam=e.getCamera().getPosition();var buffers=MultiBufferSource.immediate(new BufferBuilder(1024));pose.pushPose();pose.translate(-cam.x,-cam.y,-cam.z);LevelRenderer.renderLineBox(pose,buffers.getBuffer(RenderType.lines()),bounds,r,g,b,.8F);pose.popPose();buffers.endBatch();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){schema=null;CELLS.clear();FLIGHTS.clear();WORKING.clear();jobs=List.of();cornerA=null;cornerB=null;clearMeshes();}
    private static final class Alpha implements VertexConsumer {
        private final VertexConsumer out;private final float alpha;Alpha(VertexConsumer out,float alpha){this.out=out;this.alpha=alpha;}
        public VertexConsumer vertex(double x,double y,double z){out.vertex(x,y,z);return this;}public VertexConsumer color(int r,int g,int b,int a){out.color(r,g,b,(int)(a*alpha));return this;}public VertexConsumer uv(float u,float v){out.uv(u,v);return this;}public VertexConsumer overlayCoords(int u,int v){out.overlayCoords(u,v);return this;}public VertexConsumer uv2(int u,int v){out.uv2(u,v);return this;}public VertexConsumer normal(float x,float y,float z){out.normal(x,y,z);return this;}public void endVertex(){out.endVertex();}public void defaultColor(int r,int g,int b,int a){out.defaultColor(r,g,b,(int)(a*alpha));}public void unsetDefaultColor(){out.unsetDefaultColor();}
    }
}
