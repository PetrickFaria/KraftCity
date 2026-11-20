package br.com.kraftcity.employment.client;

import br.com.kraftcity.employment.*;
import br.com.kraftcity.industry.IndustrialRules;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid="residenciasaldeoes",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class WorkScreen extends AbstractContainerScreen<WorkMenu> {
    private UUID selected;
    private Profession profession;
    private int page;
    private static final int FARM_AREA_X=66, FARM_AREA_Y=123, XP_WIDTH=182, XP_Y=22;
    private static final String[] FARM_INPUTS={"Enxada","Farinha de Osso","Insumo","Balde de Água"};

    public WorkScreen(WorkMenu menu,Inventory inventory,Component title){
        super(menu,inventory,title);
        imageWidth=menu.farmer?WorkMenu.FARM_WIDTH:350;
        imageHeight=menu.farmer?WorkMenu.FARM_HEIGHT:!menu.hiring&&menu.sector==Profession.Sector.COMMERCIAL?296:280;
        inventoryLabelX=menu.farmer?WorkMenu.FARM_GRID_X:94;
        inventoryLabelY=menu.farmer?146:!menu.hiring&&menu.sector==Profession.Sector.COMMERCIAL?203:187;
        titleLabelY=6;
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->net.minecraft.client.gui.screens.MenuScreens.register(WorkModule.MENU.get(),WorkScreen::new));}
    public static void receive(WorkNetwork.Snapshot snapshot){
        var mc=Minecraft.getInstance();if(mc.player!=null&&mc.player.containerMenu instanceof WorkMenu m&&m.containerId==snapshot.menu()&&m.stationId.equals(snapshot.station())){
            m.snapshot=snapshot;if(mc.screen instanceof WorkScreen screen)screen.buttons();
        }
    }
    @Override protected void init(){super.init();buttons();}
    private void buttons(){
        clearWidgets();if(menu.farmer){farmerButtons();return;}if(!menu.hiring){
            if(menu.profession!=null){
                for(int i=0;i<5;i++){
                    final int size=5+2*i;
                    var button=addRenderableWidget(new Button(leftPos+8+i*36,topPos+165,34,18,Component.literal(size+"×"+size),b->option(size,0)));
                    button.active=menu.snapshot!=null&&(menu.snapshot.level()>=5||size==br.com.kraftcity.industry.IndustrialRules.maximumSize(menu.snapshot.level()));
                }
                addRenderableWidget(new Button(leftPos+191,topPos+165,70,18,Component.literal("Limites"),b->option(0,1)));
                var current=menu.snapshot;
                if(current!=null&&current.employed())addRenderableWidget(new Button(leftPos+276,topPos+2,66,14,
                    Component.translatable("gui.kraftcity.dismiss"),b->WorkNetwork.CHANNEL.sendToServer(new WorkNetwork.Dismiss(menu.containerId,menu.stationId,current.epoch()))));
                if(menu.sector==Profession.Sector.COMMERCIAL)addRenderableWidget(new Button(leftPos+8,topPos+121,96,16,Component.literal("Cancelar operação"),b->option(0,4)));
                if(menu.profession==Profession.BUTCHER)addRenderableWidget(new Button(leftPos+8,topPos+115,110,18,Component.literal("Cadastrar animais"),b->option(0,3)));
            }
            if(menu.snapshot!=null&&!menu.snapshot.employed())
                addRenderableWidget(new Button(leftPos+218,topPos+121,124,16,Component.literal("Contratar"),b->WorkNetwork.CHANNEL.sendToServer(new WorkNetwork.Hire(menu.containerId,menu.stationId,new UUID(0,0),-2))));
            return;
        }
        var snapshot=menu.snapshot;var candidates=snapshot==null?List.<WorkNetwork.Candidate>of():snapshot.candidates();
        if(selected!=null&&candidates.stream().noneMatch(c->c.id().equals(selected)))selected=null;
        page=Math.max(0,Math.min(page,Math.max(0,(candidates.size()-1)/6)));
        for(int i=0;i<6&&page*6+i<candidates.size();i++){
            var c=candidates.get(page*6+i);
            addRenderableWidget(new Button(leftPos+8,topPos+30+i*20,162,18,Component.literal((c.id().equals(selected)?"> ":"")+font.plainSubstrByWidth(c.name(),92)+" ["+c.id().toString().substring(0,6)+"]"),b->{selected=c.id();buttons();}));
        }
        addRenderableWidget(new Button(leftPos+8,topPos+151,48,18,Component.literal("<"),b->{page--;buttons();})).active=page>0;
        addRenderableWidget(new Button(leftPos+122,topPos+151,48,18,Component.literal(">"),b->{page++;buttons();})).active=(page+1)*6<candidates.size();
        if(menu.profession!=null)profession=menu.profession;
        int row=0;for(var p:Profession.values())if(p.sector==menu.sector&&(menu.profession==null||menu.profession==p)){
            addRenderableWidget(new Button(leftPos+181,topPos+30+row++*20,161,18,Component.literal((profession==p?"> ":"")+p.label),b->{profession=p;buttons();}));
        }
        Button hire=addRenderableWidget(new Button(leftPos+216,topPos+155,126,20,Component.literal(menu.sector==Profession.Sector.MILITARY?"Treinar":"Contratar"),b->{
            if(selected!=null&&profession!=null)WorkNetwork.CHANNEL.sendToServer(new WorkNetwork.Hire(menu.containerId,menu.stationId,selected,profession.ordinal()));
        }));
        hire.active=snapshot!=null&&selected!=null&&cost(snapshot)>=0&&profession!=null;
    }
    private void farmerButtons(){
        var state=menu.snapshot;
        for(int i=0;i<5;i++){
            int size=5+i*2;
            var button=addRenderableWidget(new Button(leftPos+FARM_AREA_X+i*35,topPos+FARM_AREA_Y,32,17,
                Component.literal(size+"×"+size),b->option(size,0)));
            button.active=state!=null&&size<=IndustrialRules.maximumSize(state.level());
        }
        addRenderableWidget(new Button(leftPos+246,topPos+FARM_AREA_Y,51,17,
            Component.literal("Limites"),b->option(0,1)));
        if(state==null)return;
        if(state.employed()){
            addRenderableWidget(new Button(leftPos+243,topPos+4,54,16,Component.translatable("gui.kraftcity.dismiss"),
                b->WorkNetwork.CHANNEL.sendToServer(new WorkNetwork.Dismiss(menu.containerId,menu.stationId,state.epoch()))));
        }else{
            addRenderableWidget(new Button(leftPos+235,topPos+4,62,16,Component.literal("Contratar"),b->hireAction(-2)));
        }
        if(state.paymentBalance()){
            addRenderableWidget(new Button(leftPos+243,topPos+144,54,16,Component.literal("Saldo"),b->hireAction(-3),
                (button,pose,mouseX,mouseY)->renderTooltip(pose,Component.literal("Receber esmeraldas do pagamento anterior"),mouseX,mouseY)));
        }
    }
    private void hireAction(int operation){
        WorkNetwork.CHANNEL.sendToServer(new WorkNetwork.Hire(menu.containerId,menu.stationId,new UUID(0,0),operation));
    }
    private int farmerXpWidth(){
        var state=menu.snapshot;
        return state!=null&&state.employed()?IndustrialRules.farmerXpBarWidth(state.level(),state.xp(),XP_WIDTH):0;
    }
    private String farmerTitle(int width){
        var state=menu.snapshot;
        if(state==null||!state.employed())return "Fazendeiro";
        String suffix=" (Fazendeiro) - Lv. "+state.level();
        String name=state.worker();
        int available=width-font.width(suffix);
        if(font.width(name)>available)name=font.plainSubstrByWidth(name,Math.max(0,available-font.width("…"))).stripTrailing()+"…";
        return name+suffix;
    }
    private int cost(WorkNetwork.Snapshot snapshot){
        return snapshot.candidates().stream().filter(candidate->candidate.id().equals(selected))
            .mapToInt(WorkNetwork.Candidate::cost).findFirst().orElse(snapshot.cost());
    }
    private void option(int size,int command){WorkNetwork.CHANNEL.sendToServer(new br.com.kraftcity.industry.IndustryNetwork.Options(menu.containerId,menu.stationId,size,command));}
    @Override protected void renderBg(PoseStack pose,float partial,int mx,int my){
        fill(pose,leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF30271F);
        fill(pose,leftPos+3,topPos+3,leftPos+imageWidth-3,topPos+imageHeight-3,0xFF66513A);
        if(menu.farmer)drawFarmerBackground(pose);
        for(var slot:menu.slots){int x=leftPos+slot.x,y=topPos+slot.y;fill(pose,x-1,y-1,x+17,y+17,0xFF251F18);fill(pose,x,y,x+16,y+16,0xFFB69B73);}
    }
    private void drawFarmerBackground(PoseStack pose){
        fill(pose,leftPos+4,topPos+4,leftPos+imageWidth-4,topPos+29,0xFF44372A);
        fill(pose,leftPos+62,topPos+65,leftPos+244,topPos+121,0xFF4C3D2E);
        fill(pose,leftPos+62,topPos+154,leftPos+244,topPos+232,0xFF4C3D2E);
        var state=menu.snapshot;
        if(state!=null&&state.size()>=5&&state.size()<=13){
            int x=leftPos+FARM_AREA_X+(state.size()-5)/2*35;
            fill(pose,x-1,topPos+FARM_AREA_Y-1,x+33,topPos+FARM_AREA_Y+18,0xFFBFD375);
        }
        int x=leftPos+(imageWidth-XP_WIDTH)/2;
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0,GUI_ICONS_LOCATION);
        RenderSystem.setShaderColor(1,1,1,1);
        blit(pose,x,topPos+XP_Y,0,64,XP_WIDTH,5);
        int progress=farmerXpWidth();
        if(progress>0)blit(pose,x,topPos+XP_Y,0,69,progress,5);
    }
    @Override protected void renderLabels(PoseStack pose,int mx,int my){
        if(menu.farmer){
            drawCenteredString(pose,font,farmerTitle(imageWidth-134),imageWidth/2,8,0xFFF6E8CC);
            for(int i=0;i<FARM_INPUTS.length;i++)
                drawCenteredString(pose,font,FARM_INPUTS[i],WorkMenu.farmAuxX(i)+8,31,0xFFF6E8CC);
            drawCenteredString(pose,font,"Inventário compartilhado",imageWidth/2,59,0xFFF6E8CC);
            font.draw(pose,playerInventoryTitle,inventoryLabelX,inventoryLabelY,0xFFF6E8CC);
            return;
        }
        drawCenteredString(pose,font,title,imageWidth/2,6,0xFFFFFF);
        font.draw(pose,playerInventoryTitle,inventoryLabelX,inventoryLabelY,0xFFFFFF);
        var s=menu.snapshot;
        if(menu.hiring){
            font.draw(pose,"Residentes desempregados",8,19,0xFFFFFF);font.draw(pose,"Profissões",181,19,0xFFFFFF);
            if(s!=null&&s.candidates().isEmpty())font.draw(pose,"Nenhum elegível carregado.",8,35,0xFFE1C6A7);
            font.draw(pose,"Pág. "+(page+1),66,156,0xFFFFFF);
            font.draw(pose,s==null?"Sincronizando...":cost(s)<0?"Preço comercial pendente":cost(s)==1?"1 esmeralda":cost(s)+" esmeraldas",181,181,0xFFFFFF);
            if(s!=null&&!s.status().isEmpty()){
                font.draw(pose,font.plainSubstrByWidth(s.status(),168),8,176,0xFFFFCC66);
            }
            if(menu.sector==Profession.Sector.INDUSTRIAL)font.draw(pose,"Até 15 esmeraldas",253,181,0xFFE1C6A7);
        }else{
            font.draw(pose,"Inventário comum (3 × 9)",94,16,0xFFFFFF);
            String[] labels=switch(menu.profession){
                case BUTCHER->new String[]{"Mach.","Alim.1","Alim.2","Alim.3"};
                case SHEPHERD->new String[]{"Tes.","Trigo","Ins.2","Equip."};
                case FISHERMAN->new String[]{"Vara","Ins.1","Ins.2","Equip."};
                case BREEDER->new String[]{"Ferr.","Osso","Carne","Equip."};
                case BEEKEEPER->new String[]{"Coleta","Flores","Ins.2","Equip."};
                case BAKER->new String[]{"Ferr.","Trigo","Ins.2","Equip."};
                case CONFECTIONER->new String[]{"Ferr.","Cana","Ins.2","Equip."};
                case GRILL_COOK->new String[]{"Ferr.","Carne","Ins.2","Comb."};
                default->new String[]{"Ferr.","Ins.1","Ins.2","Equip."};
            };for(int i=0;i<4;i++)font.draw(pose,labels[i],94+i*36,91,0xFFFFFF);
            font.draw(pose,"Esmer.",270,91,0xFFFFFF);
            if(s!=null){font.draw(pose,"Trabalhador: "+font.plainSubstrByWidth(s.worker(),240),8,142,0xFFFFFF);font.draw(pose,"Nível: "+s.level()+"   XP: "+s.xp()+"   Área: "+s.size()+"×"+s.size(),8,155,0xFFFFFF);}
            if(menu.sector==Profession.Sector.COMMERCIAL&&s!=null){
                if(s.employed()){
                    font.draw(pose,"Operação: "+s.elapsed()/20+" / "+s.duration()/20+" s",110,125,0xFFFFFF);
                    fill(pose,110,135,342,137,0xFF30271F);fill(pose,110,135,110+232*Math.min(s.elapsed(),s.duration())/Math.max(1,s.duration()),137,0xFF55CC77);
                }else font.draw(pose,"Pausa: "+s.elapsed()/20+" s",110,125,0xFFFFCC66);
                font.draw(pose,font.plainSubstrByWidth(s.operationStatus(),334),8,187,0xFFFFCC66);
            }
        }
    }
    @Override public void render(PoseStack pose,int mx,int my,float partial){
        renderBackground(pose);super.render(pose,mx,my,partial);renderTooltip(pose,mx,my);
        if(!menu.farmer||menu.snapshot==null||!menu.snapshot.employed())return;
        int x=mx-leftPos,y=my-topPos;
        if(x>=66&&x<240&&y>=6&&y<19){
            renderTooltip(pose,Component.literal(farmerTitle(Integer.MAX_VALUE)),mx,my);
        }else if(x>=(imageWidth-XP_WIDTH)/2&&x<(imageWidth+XP_WIDTH)/2&&y>=XP_Y&&y<XP_Y+5){
            var state=menu.snapshot;
            String text=state.level()>=IndustrialRules.MAX_LEVEL?"Nível máximo · "+state.xp()+" XP"
                :IndustrialRules.farmerXpIntoLevel(state.level(),state.xp())+" / "+IndustrialRules.FARMER_XP_PER_LEVEL+" XP";
            renderTooltip(pose,Component.literal(text),mx,my);
        }
    }
}
