package br.com.kraftcity.test;

import br.com.kraftcity.KraftCity;
import br.com.kraftcity.builder.client.BuilderArmsLayer;
import br.com.residenciasaldeoes.client.ResidentAppearanceModels;
import br.com.residenciasaldeoes.data.ResidentModelType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.Vec3;
import br.com.kraftcity.industry.IndustrialTools;
import br.com.kraftcity.employment.client.WorkerPose;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import java.io.File;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ToolVisualCheck extends Screen {
    private static final String[] CAPTURES={"materials","first-person","third-person","worker-hand","worker-animation",
        "worker-back","worker-back-side","worker-back-running","worker-back-front"};
    private static final Item[] IRON={Items.IRON_SWORD,Items.IRON_AXE,Items.IRON_HOE};
    private static Item[] emerald(){
        return new Item[]{IndustrialTools.SWORD.get(),IndustrialTools.AXE.get(),IndustrialTools.HOE.get()};
    }

    private final Villager[] workers=new Villager[6];
    private int phase;
    private int renderedPhase=-1;

    private ToolVisualCheck(){super(Component.literal("Ferramentas"));}
    @Override public boolean isPauseScreen(){return false;}

    public static void verify(Minecraft mc){
        Item[] emerald=emerald();
        for(int i=0;i<IRON.length;i++){
            var iron=mc.getItemRenderer().getModel(new ItemStack(IRON[i]),mc.level,mc.player,0).getTransforms();
            var green=mc.getItemRenderer().getModel(new ItemStack(emerald[i]),mc.level,mc.player,0).getTransforms();
            for(var hand:new ItemTransforms.TransformType[]{ItemTransforms.TransformType.FIRST_PERSON_LEFT_HAND,
                ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND,ItemTransforms.TransformType.THIRD_PERSON_LEFT_HAND,
                ItemTransforms.TransformType.THIRD_PERSON_RIGHT_HAND}){
                var a=iron.getTransform(hand);
                var b=green.getTransform(hand);
                check(a.rotation.equals(b.rotation)&&a.translation.equals(b.translation)&&a.scale.equals(b.scale),"Iron vs emerald transform: "+hand);
            }
        }
        for(String tool:new String[]{"sword","axe","hoe"})compare(mc,"iron_"+tool,"emerald_"+tool,true);
        for(var entry:IndustrialTools.SHEARS.entrySet())
            compare(mc,"shears",IndustrialTools.NAMES[entry.getKey()]+"_shears",true);
        for(var entry:IndustrialTools.RODS.entrySet()){
            String material=IndustrialTools.NAMES[entry.getKey()];
            var cast=new ResourceLocation("cast");
            var rod=entry.getValue().get();
            check(net.minecraft.client.renderer.item.ItemProperties.getProperty(rod,cast)!=null,"Missing cast predicate: "+material);
            compare(mc,"fishing_rod",material+"_fishing_rod",false);
            compare(mc,"fishing_rod_cast",material+"_fishing_rod_cast",false);
            verifyCastModel(mc,rod,material);
        }
        var creative=net.minecraft.core.NonNullList.<ItemStack>create();
        IndustrialTools.TAB.fillItemList(creative);
        check(creative.stream().map(ItemStack::getItem).toList().equals(IndustrialTools.matrix().stream().flatMap(List::stream).toList()),
            "Professional creative tab matrix order");
        System.out.println("TOOL_TEXTURES_AND_HAND_TRANSFORMS_PASSED");
        var screen=new ToolVisualCheck();
        for(int i=0;i<screen.workers.length;i++){
            var worker=EntityType.VILLAGER.create(mc.level);
            worker.setNoAi(true);
            worker.moveTo(mc.player.getX(),mc.player.getY()-25,mc.player.getZ(),0,0);
            mc.level.putNonPlayerEntity(-900-i,worker);
            screen.workers[i]=worker;
        }
        screen.verifyMountedGeometry();
        mc.setScreen(screen);
    }

    private void verifyMountedGeometry(){
        Villager worker=workers[0];
        ModelPart origin=new ModelPart(List.of(),Map.of());
        for(Item item:new Item[]{Items.IRON_HOE,IndustrialTools.HOE.get(),Items.IRON_AXE,Items.IRON_SWORD,
            Items.SHEARS,IndustrialTools.SHEARS.get(3).get(),Items.FISHING_ROD,IndustrialTools.RODS.get(3).get(),
            IndustrialTools.SHEARS.get(IndustrialTools.STONE).get(),IndustrialTools.RODS.get(IndustrialTools.STONE).get(),Items.IRON_PICKAXE,Items.BOW,Items.CROSSBOW,Items.TRIDENT}){
            var stack=new ItemStack(item);
            worker.setItemSlot(EquipmentSlot.MAINHAND,stack);
            var held=new Geometry();
            BuilderArmsLayer.renderTool(new PoseStack(),type->held,LightTexture.FULL_BRIGHT,worker,origin);
            WorkerPose.receive(worker.getId(),worker.getUUID(),true,true,stack);
            var inactive=new Geometry();
            BuilderArmsLayer.renderBack(new PoseStack(),type->inactive,LightTexture.FULL_BRIGHT,worker,origin,3.5F);
            check(inactive.vertices.isEmpty(),"Working tool rendered on the back");
            worker.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
            WorkerPose.receive(worker.getId(),worker.getUUID(),true,false,stack);
            for(float rear:new float[]{2F,2.25F,3.5F}){
                var stowed=new Geometry();
                BuilderArmsLayer.renderBack(new PoseStack(),type->stowed,LightTexture.FULL_BRIGHT,worker,origin,rear);
                check(!held.vertices.isEmpty()&&held.vertices.size()==stowed.vertices.size(),"Tool vertex count differs: "+item);
                for(int i=0;i<held.vertices.size();i++){
                    int next=i%4==3?i-3:i+1;
                    var vertex=stowed.vertices.get(i);
                    check(Math.abs(held.vertices.get(i).distanceToSqr(held.vertices.get(next))
                        -vertex.distanceToSqr(stowed.vertices.get(next)))<1e-6,"Mounted tool scale differs: "+item);
                    check(vertex.z>=rear/16D && vertex.z<(rear+2)/16D,"Mounted tool clips or floats: "+item);
                }
            }
        }
        System.out.println("WORKER_BACK_TOOL_GEOMETRY_PASSED");
    }

    private static final class Geometry implements VertexConsumer {
        private final List<Vec3> vertices=new ArrayList<>();
        @Override public VertexConsumer vertex(double x,double y,double z){vertices.add(new Vec3(x,y,z));return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){return this;}
        @Override public VertexConsumer uv(float u,float v){return this;}
        @Override public VertexConsumer overlayCoords(int u,int v){return this;}
        @Override public VertexConsumer uv2(int u,int v){return this;}
        @Override public VertexConsumer normal(float x,float y,float z){return this;}
        @Override public void endVertex(){}
        @Override public void defaultColor(int r,int g,int b,int a){}
        @Override public void unsetDefaultColor(){}
    }

    private static void verifyCastModel(Minecraft mc,Item rod,String material){
        var player=mc.player;
        int slot=player.getInventory().selected;
        var prior=player.getInventory().getItem(slot);
        var priorHook=player.fishing;
        var stack=new ItemStack(rod);
        try{
            player.getInventory().setItem(slot,stack);
            player.fishing=new net.minecraft.world.entity.projectile.FishingHook(player,mc.level,0,0);
            var sprite=mc.getItemRenderer().getModel(stack,mc.level,player,0).getParticleIcon();
            check(sprite.getName().equals(new ResourceLocation(KraftCity.MOD_ID,"item/"+material+"_fishing_rod_cast")),
                "Cast did not select the loaded rod model: "+material);
        }finally{
            player.fishing=priorHook;
            player.getInventory().setItem(slot,prior);
        }
    }

    private static void compare(Minecraft mc,String source,String variant,boolean preserveBrown){
        var atlas=mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        TextureAtlasSprite a=atlas.apply(new ResourceLocation("minecraft","item/"+source));
        TextureAtlasSprite b=atlas.apply(new ResourceLocation(KraftCity.MOD_ID,"item/"+variant));
        TextureAtlasSprite mask=source.startsWith("iron_")
            ?atlas.apply(new ResourceLocation("minecraft","item/diamond_"+source.substring("iron_".length()))):a;
        check(a.getWidth()==b.getWidth()&&a.getHeight()==b.getHeight(),"Sprite dimensions "+variant);
        boolean changed=false;
        for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++){
            int original=a.getPixelRGBA(0,x,y),pixel=b.getPixelRGBA(0,x,y);
            check((original>>>24)==(pixel>>>24),"Sprite silhouette "+variant+" at "+x+","+y);
            if((original>>>24)==0)continue;
            int red=original&255,green=(original>>>8)&255,blue=(original>>>16)&255;
            int region=mask.getPixelRGBA(0,x,y);
            int regionRed=region&255,regionGreen=(region>>>8)&255,regionBlue=(region>>>16)&255;
            if(preserveBrown&&regionRed>regionGreen&&regionGreen>regionBlue)
                check(original==pixel,"Handle color "+variant+" at "+x+","+y);
            if(!preserveBrown&&!(red>green&&green>blue))check(original==pixel,"Rod line or hook "+variant);
            if(source.equals("shears")&&red==66&&green==66&&blue==66)check(original==pixel,"Shears outline "+variant);
            changed|=original!=pixel;
        }
        check(changed,"Sprite has no material color: "+variant);
    }

    private static void check(boolean condition,String message){
        if(!condition)throw new IllegalStateException(message);
    }

    @Override public void render(PoseStack pose,int mouseX,int mouseY,float tick){
        renderBackground(pose);
        if(phase!=0){
            renderComparison(pose);
            renderedPhase=phase;
            return;
        }
        drawCenteredString(pose,font,title,width/2,height/2-60,0xFFFFFF);
        Item[] emerald=emerald();
        drawCenteredString(pose,font,Component.literal("Ferro"),width/2-54,height/2-42,0xFFFFFF);
        drawCenteredString(pose,font,Component.literal("Esmeralda"),width/2+54,height/2-42,0xFFFFFF);
        for(int i=0;i<IRON.length;i++){
            itemRenderer.renderGuiItem(new ItemStack(IRON[i]),width/2-62,height/2-27+i*23);
            itemRenderer.renderGuiItem(new ItemStack(emerald[i]),width/2+46,height/2-27+i*23);
        }
        drawRow(IndustrialTools.matrix().get(3),height/2+52);
        drawRow(IndustrialTools.matrix().get(4),height/2+76);
        renderedPhase=phase;
    }

    private void drawRow(List<Item> items,int y){
        int x=width/2-77;
        for(var item:items){
            itemRenderer.renderGuiItem(new ItemStack(item),x,y);
            x+=23;
        }
    }

    private void renderComparison(PoseStack pose){
        String label=switch(phase){
            case 1->"Primeira pessoa";
            case 2->"Terceira pessoa";
            case 3->"Trabalhador com a ferramenta na mão";
            case 4->"Animação de trabalho";
            case 5->"Ferramentas nas costas";
            case 6->"Vista lateral durante a caminhada";
            case 7->"Vista traseira durante a corrida";
            default->"Vista frontal";
        };
        drawCenteredString(pose,font,Component.literal(label),width/2,height/2-105,0xFFFFFF);
        var green=emerald();
        Item[] stowed={IndustrialTools.HOE.get(),IndustrialTools.AXE.get(),IndustrialTools.SWORD.get(),
            IndustrialTools.SHEARS.get(3).get(),IndustrialTools.RODS.get(3).get(),Items.IRON_PICKAXE};
        var buffers=minecraft.renderBuffers().bufferSource();
        if(phase<3){
            var view=phase==1?ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND:ItemTransforms.TransformType.THIRD_PERSON_RIGHT_HAND;
            var stoneShears=new ItemStack(IndustrialTools.SHEARS.get(IndustrialTools.STONE).get());
            var stoneRod=new ItemStack(IndustrialTools.RODS.get(IndustrialTools.STONE).get());
            renderTool(pose,stoneShears,view,width/2-35,height/2+77,buffers);
            renderTool(pose,stoneRod,view,width/2+35,height/2+77,buffers);
        }
        for(int i=0;i<IRON.length;i++){
            int x=width/2-150+i*108,y=height/2;
            var ironStack=new ItemStack(IRON[i]);
            var emeraldStack=new ItemStack(green[i]);
            if(phase<3){
                var view=phase==1?ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND:ItemTransforms.TransformType.THIRD_PERSON_RIGHT_HAND;
                renderTool(pose,ironStack,view,x,y,buffers);
                renderTool(pose,emeraldStack,view,x+54,y,buffers);
            }else if(phase<=5){
                renderWorker(workers[i*2],ironStack,x,y+42);
                renderWorker(workers[i*2+1],emeraldStack,x+54,y+42);
            }else{
                renderWorker(workers[i*2],new ItemStack(stowed[i*2]),x,y+42);
                renderWorker(workers[i*2+1],new ItemStack(stowed[i*2+1]),x+54,y+42);
            }
        }
    }

    private void renderTool(PoseStack pose,ItemStack stack,ItemTransforms.TransformType view,int x,int y,
        net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers){
        pose.pushPose();
        pose.translate(x,y,100);
        pose.scale(58,-58,58);
        pose.mulPose(Vector3f.YP.rotationDegrees(30));
        pose.mulPose(Vector3f.XP.rotationDegrees(-12));
        itemRenderer.renderStatic(stack,view,LightTexture.FULL_BRIGHT,0,pose,buffers,0);
        buffers.endBatch();
        pose.popPose();
    }

    private void renderWorker(Villager worker,ItemStack item,int x,int y){
        boolean held=phase<5;
        ResidentAppearanceModels.set(worker.getUUID(),phase>=6&&worker.getId()%2!=0?ResidentModelType.STEVE:ResidentModelType.VILLAGER);
        float stride=phase==6?.45F:phase==7?.95F:0;
        worker.animationSpeed=worker.animationSpeedOld=stride;
        worker.animationPosition=4F;
        worker.setItemSlot(EquipmentSlot.MAINHAND,held?item:ItemStack.EMPTY);
        WorkerPose.receive(worker.getId(),worker.getUUID(),true,held,held?ItemStack.EMPTY:item);
        worker.attackAnim=phase==4?.5F:0;
        worker.oAttackAnim=worker.attackAnim;
        if(held)InventoryScreen.renderEntityInInventoryRaw(x,y,34,.9F,0F,worker);
        else renderMountedWorker(worker,x,y,switch(phase){case 6->270F;case 8->180F;default->12F;});
    }

    private void renderMountedWorker(Villager worker,int x,int y,float facing){
        worker.setYRot(facing);
        worker.yBodyRot=worker.yBodyRotO=facing;
        worker.yHeadRot=worker.yHeadRotO=facing;
        var view=RenderSystem.getModelViewStack();
        view.pushPose();
        view.translate(x,y,1050);
        view.scale(1,1,-1);
        RenderSystem.applyModelViewMatrix();
        var pose=new PoseStack();
        pose.translate(0,0,1000);
        pose.scale(34,34,34);
        pose.mulPose(Vector3f.ZP.rotationDegrees(180));
        var dispatcher=minecraft.getEntityRenderDispatcher();
        var buffers=minecraft.renderBuffers().bufferSource();
        Lighting.setupForEntityInInventory();
        dispatcher.setRenderShadow(false);
        try{
            dispatcher.render(worker,0,0,0,0,1,pose,buffers,LightTexture.FULL_BRIGHT);
            buffers.endBatch();
        }finally{
            dispatcher.setRenderShadow(true);
            view.popPose();
            RenderSystem.applyModelViewMatrix();
            Lighting.setupFor3DItems();
        }
    }

    public static void captureRenderedPhase(Minecraft mc){
        if(!(mc.screen instanceof ToolVisualCheck screen)||screen.renderedPhase!=screen.phase)return;
        String name=CAPTURES[screen.phase];
        Screenshot.grab(new File("../build"),"visual-tool-"+name+".png",mc.getMainRenderTarget(),message->{});
        if(++screen.phase==CAPTURES.length){
            for(var worker:screen.workers){
                ResidentAppearanceModels.set(worker.getUUID(),ResidentModelType.VILLAGER);
                mc.level.removeEntity(worker.getId(),Entity.RemovalReason.DISCARDED);
            }
            mc.setScreen(null);
        }
    }
}
