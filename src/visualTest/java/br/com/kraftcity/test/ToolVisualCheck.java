package br.com.kraftcity.test;

import br.com.kraftcity.KraftCity;
import br.com.kraftcity.industry.IndustrialTools;
import br.com.kraftcity.employment.client.WorkerPose;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import com.mojang.blaze3d.vertex.PoseStack;
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
    private static final Item[] IRON={Items.IRON_SWORD,Items.IRON_AXE,Items.IRON_HOE};
    private static Item[] emerald(){
        return new Item[]{IndustrialTools.SWORD.get(),IndustrialTools.AXE.get(),IndustrialTools.HOE.get()};
    }

    private final Villager[] workers=new Villager[6];
    private int phase;

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
        System.out.println("TOOL_TEXTURES_AND_HAND_TRANSFORMS_PASSED");
        var screen=new ToolVisualCheck();
        for(int i=0;i<screen.workers.length;i++){
            var worker=EntityType.VILLAGER.create(mc.level);
            worker.setNoAi(true);
            worker.moveTo(mc.player.getX(),mc.player.getY()-25,mc.player.getZ(),0,0);
            mc.level.putNonPlayerEntity(-900-i,worker);
            screen.workers[i]=worker;
        }
        mc.setScreen(screen);
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
        TextureAtlasSprite a=mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(new ResourceLocation("minecraft","item/"+source));
        TextureAtlasSprite b=mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(new ResourceLocation(KraftCity.MOD_ID,"item/"+variant));
        check(a.getWidth()==b.getWidth()&&a.getHeight()==b.getHeight(),"Sprite dimensions "+variant);
        boolean changed=false;
        for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++){
            int original=a.getPixelRGBA(0,x,y),pixel=b.getPixelRGBA(0,x,y);
            check((original>>>24)==(pixel>>>24),"Sprite silhouette "+variant+" at "+x+","+y);
            if((original>>>24)==0)continue;
            int red=original&255,green=(original>>>8)&255,blue=(original>>>16)&255;
            if((preserveBrown&&red>green&&green>blue)||(source.equals("iron_hoe")&&red==24&&green==24&&blue==24))
                check(original==pixel,"Handle color "+variant);
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
        drawCenteredString(pose,font,title,width/2,height/2-60,0xFFFFFF);
        if(phase!=0){
            renderComparison(pose);
            return;
        }
        Item[] emerald=emerald();
        drawCenteredString(pose,font,Component.literal("Ferro"),width/2-54,height/2-42,0xFFFFFF);
        drawCenteredString(pose,font,Component.literal("Esmeralda"),width/2+54,height/2-42,0xFFFFFF);
        for(int i=0;i<IRON.length;i++){
            itemRenderer.renderGuiItem(new ItemStack(IRON[i]),width/2-62,height/2-27+i*23);
            itemRenderer.renderGuiItem(new ItemStack(emerald[i]),width/2+46,height/2-27+i*23);
        }
        drawRow(IndustrialTools.RODS,height/2+52);
        drawRow(IndustrialTools.SHEARS,height/2+76);
    }

    private void drawRow(Map<Integer,? extends java.util.function.Supplier<Item>> items,int y){
        int x=width/2-65;
        for(int i=0;i<IndustrialTools.NAMES.length;i++){
            var item=items.get(i);
            itemRenderer.renderGuiItem(new ItemStack(item==null?(i==0?Items.FISHING_ROD:Items.SHEARS):item.get()),x,y);
            x+=23;
        }
    }

    private void renderComparison(PoseStack pose){
        String label=switch(phase){
            case 1->"Primeira pessoa";
            case 2->"Terceira pessoa";
            case 3->"Trabalhador com a ferramenta na mão";
            case 4->"Animação de trabalho";
            default->"Ferramenta nas costas";
        };
        drawCenteredString(pose,font,Component.literal(label),width/2,height/2-105,0xFFFFFF);
        var green=emerald();
        var buffers=minecraft.renderBuffers().bufferSource();
        for(int i=0;i<IRON.length;i++){
            int x=width/2-150+i*108,y=height/2;
            var ironStack=new ItemStack(IRON[i]);
            var emeraldStack=new ItemStack(green[i]);
            if(phase<3){
                var view=phase==1?ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND:ItemTransforms.TransformType.THIRD_PERSON_RIGHT_HAND;
                renderTool(pose,ironStack,view,x,y,buffers);
                renderTool(pose,emeraldStack,view,x+54,y,buffers);
            }else{
                renderWorker(workers[i*2],ironStack,x,y+42);
                renderWorker(workers[i*2+1],emeraldStack,x+54,y+42);
            }
        }
    }

    private void renderTool(PoseStack pose,ItemStack stack,ItemTransforms.TransformType view,int x,int y,
        net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers){
        pose.pushPose();
        pose.translate(x,y,100);
        pose.scale(58,-58,58);
        itemRenderer.renderStatic(stack,view,LightTexture.FULL_BRIGHT,0,pose,buffers,0);
        buffers.endBatch();
        pose.popPose();
    }

    private void renderWorker(Villager worker,ItemStack item,int x,int y){
        boolean held=phase!=5;
        worker.setItemSlot(EquipmentSlot.MAINHAND,held?item:ItemStack.EMPTY);
        WorkerPose.receive(worker.getId(),worker.getUUID(),true,held,held?ItemStack.EMPTY:item);
        worker.attackAnim=phase==4?.5F:0;
        worker.oAttackAnim=worker.attackAnim;
        InventoryScreen.renderEntityInInventoryRaw(x,y,34,held?0.9F:8.5F,0F,worker);
    }

    public static void capture(Minecraft mc,int phase){
        check(mc.screen instanceof ToolVisualCheck,"Tool comparison screen missing");
        var screen=(ToolVisualCheck)mc.screen;
        check(screen.phase==phase,"Tool comparison phase missing");
        String name=new String[]{"materials","first-person","third-person","worker-hand","worker-animation","worker-back"}[phase];
        Screenshot.grab(new File("../build"),"visual-tool-"+name+".png",mc.getMainRenderTarget(),message->{});
        if(++screen.phase>5){
            for(var worker:screen.workers)mc.level.removeEntity(worker.getId(),Entity.RemovalReason.DISCARDED);
            mc.setScreen(null);
        }
    }
}
