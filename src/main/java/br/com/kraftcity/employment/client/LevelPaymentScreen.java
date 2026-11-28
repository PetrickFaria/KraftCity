package br.com.kraftcity.employment.client;

import br.com.kraftcity.employment.LevelPaymentMenu;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class LevelPaymentScreen extends AbstractContainerScreen<LevelPaymentMenu> {
    private Button confirm;

    public LevelPaymentScreen(LevelPaymentMenu menu,Inventory inventory,Component title){
        super(menu,inventory,title);
        imageWidth=176;
        imageHeight=198;
        inventoryLabelY=104;
    }

    @Override protected void init(){
        super.init();
        confirm=addRenderableWidget(new Button(leftPos+8,topPos+83,106,18,
                Component.literal("Confirmar"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0)));
        addRenderableWidget(new Button(leftPos+118,topPos+83,50,18,
                Component.literal("Cancelar"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,1)));
    }

    @Override protected void containerTick(){
        confirm.active=menu.completeSlots()>0;
    }

    @Override protected void renderLabels(PoseStack pose,int x,int y){
        super.renderLabels(pose,x,y);
        font.draw(pose,font.plainSubstrByWidth(menu.resident+" · "+menu.profession.label,158),8,22,0xFFF1D9A9);
        int size=menu.current==0?5:1;
        for(int i=0;i<size;i++)drawCenteredString(pose,font,"Lv"+(menu.current+i+1),
                menu.current==0?28+i*30:88,36,0xFFFFFF);
        int complete=menu.completeSlots();
        String status=menu.deposited()+" inseridas · "+(complete==0?"mínimo 10":"Lv"+(menu.current+complete)
                +" · custo "+(complete*LevelPaymentMenu.COST));
        drawCenteredString(pose,font,status,88,69,0xFFFFFF);
    }

    @Override protected void renderBg(PoseStack pose,float partial,int x,int y){
        fill(pose,leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF2D2419);
        fill(pose,leftPos+3,topPos+3,leftPos+imageWidth-3,topPos+imageHeight-3,0xFF594630);
        for(var slot:menu.slots)fill(pose,leftPos+slot.x-1,topPos+slot.y-1,leftPos+slot.x+17,topPos+slot.y+17,0xFF282016);
    }

    @Override public void render(PoseStack pose,int x,int y,float partial){
        renderBackground(pose);
        super.render(pose,x,y,partial);
        renderTooltip(pose,x,y);
    }
}
