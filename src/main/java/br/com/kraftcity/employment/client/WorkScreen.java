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
    private static final int LUMBER_AREA_X=18, LUMBER_AREA_STEP=54, LUMBER_BUTTON_WIDTH=52;
    private static final int FARM_AREA_X=(WorkMenu.FARM_WIDTH-172)/2, FARM_AREA_Y=123, XP_WIDTH=182, XP_Y=22;
    private static final String[] FARM_INPUTS={"Enxada","Farinha de Osso","Balde de Água"};

    public WorkScreen(WorkMenu menu,Inventory inventory,Component title){
        super(menu,inventory,title);
        imageWidth=menu.fieldLayout?WorkMenu.FARM_WIDTH:350;
        imageHeight=menu.fieldLayout?WorkMenu.FARM_HEIGHT:!menu.hiring&&menu.sector==Profession.Sector.COMMERCIAL?296:280;
        inventoryLabelX=menu.fieldLayout?WorkMenu.FARM_GRID_X:94;
        inventoryLabelY=menu.fieldLayout?146:!menu.hiring&&menu.sector==Profession.Sector.COMMERCIAL?203:187;
        titleLabelY=6;
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->{
        net.minecraft.client.gui.screens.MenuScreens.register(WorkModule.MENU.get(),WorkScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(WorkModule.LEVEL_PAYMENT.get(),LevelPaymentScreen::new);
    });}
    public static void receive(WorkNetwork.Snapshot snapshot){
        var mc=Minecraft.getInstance();if(mc.player!=null&&mc.player.containerMenu instanceof WorkMenu m&&m.containerId==snapshot.menu()&&m.stationId.equals(snapshot.station())){
            m.snapshot=snapshot;if(mc.screen instanceof WorkScreen screen)screen.buttons();
        }
    }
    @Override protected void init(){super.init();buttons();}
    private void buttons(){
        clearWidgets();if(menu.fieldLayout){farmerButtons();return;}if(!menu.hiring){
            if(menu.profession!=null){
                for(int i=0;i<5;i++){
                    final int size=5+2*i;
                    var button=addRenderableWidget(new Button(leftPos+8+i*36,topPos+165,34,18,Component.literal(size+"×"+size),b->option(size,0)));
                    button.active=menu.snapshot!=null&&(menu.snapshot.level()>=5||size==br.com.kraftcity.industry.IndustrialRules.maximumSize(menu.snapshot.level()));
                }
                addRenderableWidget(new LimitsButton(leftPos+191,topPos+165,70,18,Component.literal("Limites"),b->option(0,1),()->menu.snapshot!=null&&menu.snapshot.preview()));
                var current=menu.snapshot;
                if(current!=null&&current.employed())addUpgrade(217,121,current.level());
                if(current!=null&&current.employed())addRenderableWidget(new Button(leftPos+276,topPos+2,66,14,
                    Component.translatable("gui.kraftcity.dismiss"),b->WorkNetwork.CHANNEL.sendToServer(new WorkNetwork.Dismiss(menu.containerId,menu.stationId,current.epoch()))));
                if(menu.sector==Profession.Sector.COMMERCIAL)addRenderableWidget(new Button(leftPos+8,topPos+121,96,16,Component.literal("Cancelar operação"),b->option(0,4)));
                if(br.com.kraftcity.logistics.DiscardBinding.supportsManualBinding(menu.profession)&&current!=null&&current.employed())
                    addRenderableWidget(new Button(leftPos+264,topPos+165,78,18,
                        Component.literal(current.discardLinked()?"Desvincular":"Vincular baú"),
                        b->option(0,current.discardLinked()?6:5)));
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
            addRenderableWidget(new Button(leftPos+8,topPos+30+i*20,162,18,Component.literal((c.id().equals(selected)?"> ":"")+font.plainSubstrByWidth(c.name(),92)+" ["+c.id().toString().substring(0,6)+"]"),b->{
                selected=c.id();
                buttons();
            }));
        }
        addRenderableWidget(new Button(leftPos+8,topPos+151,48,18,Component.literal("<"),b->{page--;buttons();})).active=page>0;
        addRenderableWidget(new Button(leftPos+122,topPos+151,48,18,Component.literal(">"),b->{page++;buttons();})).active=(page+1)*6<candidates.size();
        if(menu.profession!=null)profession=menu.profession;
        boolean industrial=menu.sector==Profession.Sector.INDUSTRIAL;
        int row=0;for(var p:Profession.values())if(p.sector==menu.sector&&(menu.profession==null||menu.profession==p)){
            addRenderableWidget(new Button(leftPos+181,topPos+(industrial?27:30)+row++*(industrial?16:20),161,industrial?16:18,Component.literal((profession==p?"> ":"")+p.label),b->{profession=p;buttons();}));
        }
        Button hire=addRenderableWidget(new Button(leftPos+216,topPos+155,126,20,Component.literal("Contratar"),b->{
            if(selected!=null&&profession!=null)WorkNetwork.CHANNEL.sendToServer(new WorkNetwork.Hire(menu.containerId,menu.stationId,selected,profession.ordinal()));
        }));
        hire.active=snapshot!=null&&selected!=null&&snapshot.cost()>=0&&profession!=null;
    }
    private void farmerButtons(){
        var state=menu.snapshot;
        addRenderableWidget(new Button(leftPos+imageWidth-23,topPos+5,17,15,
            Component.literal(menu.settingsPage?"‹":"›"),b->{
                if(menu.getCarried().isEmpty()){menu.settingsPage=!menu.settingsPage;buttons();}
            })).active=menu.getCarried().isEmpty();
        if(!menu.settingsPage){
            boolean centered=IndustrialRules.centeredArea(menu.profession);
            for(int i=0;i<5;i++){
                int size=centered?IndustrialRules.lumberjackWidth(i):IndustrialRules.minimumSize(menu.profession)+i*2;
                String dimensions=size+"×"+size+(centered?"×"+IndustrialRules.LUMBERJACK_HEIGHT:"");
                var button=addRenderableWidget(new Button(leftPos+areaButtonX(i),topPos+FARM_AREA_Y,
                    centered?LUMBER_BUTTON_WIDTH:32,17,Component.literal(dimensions),b->option(size,0)));
                button.active=state!=null&&size<=IndustrialRules.maximumSize(menu.profession,state.level());
            }
            if(state!=null&&!state.employed())addRenderableWidget(new Button(leftPos+222,topPos+142,75,16,
                Component.literal("Contratar"),b->hireAction(-2)));
            return;
        }
        addRenderableWidget(new LimitsButton(leftPos+77,topPos+106,152,18,
            Component.literal("Exibir Limites"),b->option(0,1),()->menu.snapshot!=null&&menu.snapshot.preview()));
        if(state==null)return;
        addRenderableWidget(new Button(leftPos+77,topPos+80,152,18,
            Component.literal(state.discardLinked()?"Baú Especial Vinculado":"Vincular Baú Especial"),b->{
                if(!state.discardLinked()){
                    option(0,5);
                    minecraft.player.closeContainer();
                    return;
                }
                minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(confirmed->{
                    minecraft.setScreen(this);
                    if(confirmed)option(0,6);
                },Component.literal("Desvincular o Baú de Descarte?"),
                    Component.literal("Confirme para remover a associação deste posto."),
                    Component.literal("Desvincular"),Component.literal("Cancelar")));
            }));
        if(state.employed()){
            addUpgrade(77,52,state.level());
            addRenderableWidget(new Button(leftPos+77,topPos+132,152,18,Component.translatable("gui.kraftcity.dismiss"),
                b->minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(confirmed->{
                    minecraft.setScreen(this);
                    if(confirmed)WorkNetwork.CHANNEL.sendToServer(new WorkNetwork.Dismiss(menu.containerId,menu.stationId,state.epoch()));
                },Component.literal("Demitir trabalhador?"),
                    Component.literal("O trabalhador conserva seu histórico profissional."),
                    Component.literal("Demitir"),Component.literal("Cancelar")))));
        }else{
            addRenderableWidget(new Button(leftPos+77,topPos+132,152,18,Component.literal("Contratar"),b->hireAction(-2)));
        }
        if(state.paymentBalance()){
            addRenderableWidget(new Button(leftPos+243,topPos+144,54,16,Component.literal("Saldo"),b->hireAction(-3),
                (button,pose,mouseX,mouseY)->renderTooltip(pose,Component.literal("Receber esmeraldas do pagamento anterior"),mouseX,mouseY)));
        }
    }
    private void addUpgrade(int x,int y,int level){
        var button=addRenderableWidget(new Button(leftPos+x,topPos+y,118,18,
                Component.literal(level>=5?"Nível máximo":"Aumentar Nível"),
                b->WorkNetwork.CHANNEL.sendToServer(new WorkNetwork.Upgrade(menu.containerId,menu.stationId))));
        button.active=level<5;
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
        if(state==null||!state.employed())return menu.profession.label;
        String suffix=" ("+menu.profession.label+") - Lv. "+state.level();
        String name=state.worker();
        int available=width-font.width(suffix);
        if(font.width(name)>available)name=font.plainSubstrByWidth(name,Math.max(0,available-font.width("…"))).stripTrailing()+"…";
        return name+suffix;
    }
    private void option(int size,int command){WorkNetwork.CHANNEL.sendToServer(new br.com.kraftcity.industry.IndustryNetwork.Options(menu.containerId,menu.stationId,size,command));}
    @Override protected void renderBg(PoseStack pose,float partial,int mx,int my){
        fill(pose,leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF30271F);
        fill(pose,leftPos+3,topPos+3,leftPos+imageWidth-3,topPos+imageHeight-3,0xFF66513A);
        if(menu.fieldLayout)drawFarmerBackground(pose);
        for(var slot:menu.slots){if(!slot.isActive())continue;int x=leftPos+slot.x,y=topPos+slot.y;fill(pose,x-1,y-1,x+17,y+17,0xFF251F18);fill(pose,x,y,x+16,y+16,0xFFB69B73);}
    }
    private int areaButtonX(int option){
        return IndustrialRules.centeredArea(menu.profession)?LUMBER_AREA_X+option*LUMBER_AREA_STEP:FARM_AREA_X+option*35;
    }
    private void drawFarmerBackground(PoseStack pose){
        fill(pose,leftPos+4,topPos+4,leftPos+imageWidth-4,topPos+29,0xFF44372A);
        WorkerTextures.drawExperience(pose,leftPos+(imageWidth-XP_WIDTH)/2,topPos+XP_Y,farmerXpWidth(),false);
        if(menu.settingsPage)return;
        fill(pose,leftPos+62,topPos+65,leftPos+244,topPos+121,0xFF4C3D2E);
        fill(pose,leftPos+62,topPos+154,leftPos+244,topPos+232,0xFF4C3D2E);
        var state=menu.snapshot;
        int minSize=IndustrialRules.minimumSize(menu.profession);
        if(state!=null&&state.size()>=minSize&&state.size()<=IndustrialRules.maximumSize(menu.profession,5)){
            int index=IndustrialRules.centeredArea(menu.profession)?IndustrialRules.lumberjackOption(state.size()):(state.size()-minSize)/2;
            int x=leftPos+areaButtonX(index);
            int width=IndustrialRules.centeredArea(menu.profession)?LUMBER_BUTTON_WIDTH:32;
            fill(pose,x-1,topPos+FARM_AREA_Y-1,x+width+1,topPos+FARM_AREA_Y+18,0xFFBFD375);
        }
    }
    @Override protected void renderLabels(PoseStack pose,int mx,int my){
        if(menu.fieldLayout){
            drawCenteredString(pose,font,farmerTitle(imageWidth-54),imageWidth/2,8,0xFFF6E8CC);
            if(menu.settingsPage)return;
            String[] inputs=menu.profession==Profession.COLLECTOR?new String[0]:menu.farmer?FARM_INPUTS:menu.profession==Profession.LUMBERJACK
                ?new String[]{"Machado","Mudas"}:new String[]{"Tesoura","Frasco de Vidro","Flor"};
            for(int i=0;i<inputs.length;i++)
                drawCenteredString(pose,font,inputs[i],WorkMenu.farmAuxX(i)+8,31,0xFFF6E8CC);
            drawCenteredString(pose,font,"Inventário compartilhado",imageWidth/2,59,0xFFF6E8CC);
            if(menu.profession==Profession.BEEKEEPER)font.draw(pose,"Altura 5",245,128,0xFFF6E8CC);
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
            if(s!=null&&!s.status().isEmpty()){
                font.draw(pose,font.plainSubstrByWidth(s.status(),168),8,176,0xFFFFCC66);
            }
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
        if(!menu.fieldLayout||menu.snapshot==null||!menu.snapshot.employed())return;
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
