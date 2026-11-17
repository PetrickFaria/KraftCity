package br.com.kraftcity.employment.client;

import br.com.kraftcity.employment.*;
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
    public WorkScreen(WorkMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=350;imageHeight=280;inventoryLabelX=94;inventoryLabelY=187;titleLabelY=6;}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->net.minecraft.client.gui.screens.MenuScreens.register(WorkModule.MENU.get(),WorkScreen::new));}
    public static void receive(WorkNetwork.Snapshot snapshot){
        var mc=Minecraft.getInstance();if(mc.player!=null&&mc.player.containerMenu instanceof WorkMenu m&&m.containerId==snapshot.menu()&&m.stationId.equals(snapshot.station())){
            m.snapshot=snapshot;if(mc.screen instanceof WorkScreen screen)screen.buttons();
        }
    }
    @Override protected void init(){super.init();buttons();}
    private void buttons(){
        clearWidgets();if(!menu.hiring){
            if(menu.snapshot!=null&&!menu.snapshot.employed())
                addRenderableWidget(new Button(leftPos+218,topPos+150,124,20,Component.literal("Contratar"),b->WorkNetwork.CHANNEL.sendToServer(new WorkNetwork.Hire(menu.containerId,menu.stationId,new UUID(0,0),-2))));
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
        Button hire=addRenderableWidget(new Button(leftPos+216,topPos+155,126,20,Component.literal("Contratar"),b->{
            if(selected!=null&&profession!=null)WorkNetwork.CHANNEL.sendToServer(new WorkNetwork.Hire(menu.containerId,menu.stationId,selected,profession.ordinal()));
        }));
        hire.active=snapshot!=null&&snapshot.cost()>=0&&selected!=null&&profession!=null;
    }
    @Override protected void renderBg(PoseStack pose,float partial,int mx,int my){
        fill(pose,leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF30271F);
        fill(pose,leftPos+3,topPos+3,leftPos+imageWidth-3,topPos+imageHeight-3,0xFF66513A);
        for(var slot:menu.slots){int x=leftPos+slot.x,y=topPos+slot.y;fill(pose,x-1,y-1,x+17,y+17,0xFF251F18);fill(pose,x,y,x+16,y+16,0xFFB69B73);}
    }
    @Override protected void renderLabels(PoseStack pose,int mx,int my){
        drawCenteredString(pose,font,title,imageWidth/2,6,0xFFFFFF);
        font.draw(pose,playerInventoryTitle,inventoryLabelX,inventoryLabelY,0xFFFFFF);
        var s=menu.snapshot;
        if(menu.hiring){
            font.draw(pose,"Residentes desempregados",8,19,0xFFFFFF);font.draw(pose,"Profissões",181,19,0xFFFFFF);
            if(s!=null&&s.candidates().isEmpty())font.draw(pose,"Nenhum elegível carregado.",8,35,0xFFE1C6A7);
            font.draw(pose,"Pág. "+(page+1),66,156,0xFFFFFF);
            font.draw(pose,s==null?"Sincronizando...":s.cost()<0?"Preço comercial pendente":s.cost()+" esmeraldas",181,181,0xFFFFFF);
            if(s!=null&&!s.status().isEmpty())font.draw(pose,font.plainSubstrByWidth(s.status(),164),8,176,0xFFFFCC66);
        }else{
            font.draw(pose,"Inventário comum (3 × 9)",94,16,0xFFFFFF);
            String[] labels={"Ferr.","Ins.1","Ins.2","Equip."};for(int i=0;i<4;i++)font.draw(pose,labels[i],94+i*36,91,0xFFFFFF);
            font.draw(pose,"Esmer.",270,91,0xFFFFFF);
            if(s!=null){font.draw(pose,"Trabalhador: "+font.plainSubstrByWidth(s.worker(),240),8,142,0xFFFFFF);font.draw(pose,"Nível: "+s.level()+"   XP: "+s.xp(),8,155,0xFFFFFF);}
            font.draw(pose,"Produção indisponível nesta versão.",8,173,0xFFFFCC66);
        }
    }
    @Override public void render(PoseStack pose,int mx,int my,float partial){renderBackground(pose);super.render(pose,mx,my,partial);renderTooltip(pose,mx,my);}
}
