package br.com.kraftcity.military.client;

import br.com.kraftcity.military.*;
import br.com.kraftcity.employment.client.WorkerTextures;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid="residenciasaldeoes",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class SoldierScreen extends AbstractContainerScreen<SoldierMenu> {
    public boolean blacklist;
    private boolean settings;
    private boolean panelOnly(){return settings||blacklist;}
    private int category=2,origin,page;
    private String query="";
    private EditBox search;
    public SoldierScreen(SoldierMenu m,Inventory inv,Component title){super(m,inv,title);imageWidth=246;imageHeight=225;inventoryLabelX=42;inventoryLabelY=135;}
    @SubscribeEvent public static void setup(FMLClientSetupEvent e){e.enqueueWork(()->net.minecraft.client.gui.screens.MenuScreens.register(MilitaryModule.MENU.get(),SoldierScreen::new));}
    @SubscribeEvent public static void renderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(MilitaryModule.ZOMBIE.get(),net.minecraft.client.renderer.entity.ZombieVillagerRenderer::new);}
    public static void receive(MilitaryNetwork.State s){var mc=Minecraft.getInstance();if(mc.player!=null&&mc.player.containerMenu instanceof SoldierMenu m&&m.containerId==s.menu()&&m.stationId.equals(s.station())){m.state=s;if(!s.entries().isEmpty())m.catalog=s.entries();if(mc.screen instanceof SoldierScreen screen&&(!screen.blacklist||!s.entries().isEmpty()))screen.buttons();}}
    @Override protected void init(){super.init();buttons();send(3,"");}
    private void send(int command,String value){MilitaryNetwork.CHANNEL.sendToServer(new MilitaryNetwork.Command(menu.containerId,menu.stationId,command,value));}
    private Button button(int x,int y,int w,String label,Runnable r){return addRenderableWidget(new Button(leftPos+x,topPos+y,w,18,Component.literal(label),b->r.run()));}
    private String name(MilitaryNetwork.Entry e){String key="entity."+e.id().replace(':','.');String translated=Component.translatable(key).getString();return translated.equals(key)?e.name():translated;}
    private List<MilitaryNetwork.Entry> filtered(){String q=query.toLowerCase(Locale.ROOT);return menu.catalog.stream().filter(e->e.category()==category&&(origin==0||origin==1&&e.id().startsWith("minecraft:")||origin==2&&!e.id().startsWith("minecraft:"))&&(e.id().toLowerCase(Locale.ROOT).contains(q)||name(e).toLowerCase(Locale.ROOT).contains(q))).toList();}
    private void buttons(){clearWidgets();if(blacklist){
        String[] cats={"Pacíficos","Neutros","Hostis"};for(int i=0;i<3;i++){final int c=i;button(8+78*i,24,74,(category==i?"> ":"")+cats[i],()->{category=c;page=0;buttons();});}
        button(8,46,98,"Origem: "+new String[]{"Todas","Minecraft","Mods"}[origin],()->{origin=(origin+1)%3;page=0;buttons();});
        search=addRenderableWidget(new EditBox(font,leftPos+112,topPos+46,126,18,Component.literal("Buscar criatura")));search.setValue(query);search.setResponder(v->{query=v;page=0;buttonsForRows();});
        buttonsForRows();return;
    }
        var state=menu.state;
        if(settings){
            button(8,5,24,"‹",()->{settings=false;buttons();});
            button(36,44,174,"Blacklist",()->{blacklist=true;page=0;buttons();send(3,"");});
            button(36,68,174,"Limites",()->MilitaryPreview.toggle(menu.pos));
            if(state!=null&&state.employed()){
                button(36,92,174,state.discard()!=null?"Desvincular baú":"Vincular baú especial",()->{
                    if(state.discard()==null)send(5,"");
                    else Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(confirmed->{
                        Minecraft.getInstance().setScreen(this);
                        if(confirmed)send(6,"");
                    },Component.literal("Desvincular baú?"),Component.literal(state.discard().toShortString())));
                });
            }
            else if(state!=null)button(36,92,174,"Treinar residente",()->send(4,""));
            if(state!=null&&state.employed())addRenderableWidget(new Button(leftPos+36,topPos+146,174,18,
            Component.translatable("gui.kraftcity.dismiss"),b->br.com.kraftcity.employment.WorkNetwork.CHANNEL.sendToServer(
                new br.com.kraftcity.employment.WorkNetwork.Dismiss(menu.containerId,menu.stationId,state.epoch()))));
            return;
        }
        button(214,5,24,"›",()->{settings=true;buttons();});
        boolean oldItems=java.util.stream.IntStream.range(9,32).anyMatch(i->!menu.inventory.getItem(i).isEmpty());
        if(oldItems||menu.legacyStorage)button(menu.legacyStorage?134:8,87,menu.legacyStorage?104:58,menu.legacyStorage?"Voltar ao estoque":"Recuperar",
            ()->{menu.legacyStorage=!menu.legacyStorage;buttons();});
        if(menu.legacyStorage)return;
        var modes=new Military.Mode[]{Military.Mode.FOLLOW,Military.Mode.FREE,Military.Mode.GUARD};
        String[] names={"Seguir","Patrulhar","Guarnecer"};
        for(int i=0;i<modes.length;i++){
            var mode=modes[i];
            button(8+78*i,67,74,(state!=null&&state.mode()==mode.ordinal()?"> ":"")+names[i],()->send(0,mode.name()));
        }
        button((imageWidth-107)/2,87,52,state!=null&&state.night()?"> Noturno":"Noturno",()->send(1,"NIGHT"));
        button((imageWidth-107)/2+55,87,52,state!=null&&!state.night()?"> Diurno":"Diurno",()->send(1,"DAY"));

    }
    private final List<Button> rows=new ArrayList<>();
    private void buttonsForRows(){for(var r:rows)removeWidget(r);rows.clear();var list=filtered();page=Math.max(0,Math.min(page,Math.max(0,(list.size()-1)/6)));
        for(int i=0;i<6&&page*6+i<list.size();i++){var e=list.get(page*6+i);String label=(e.selected()?"[✓] ":"[ ] ")+font.plainSubstrByWidth(name(e)+" — "+e.id(),198);rows.add(button(8,68+21*i,230,label,()->send(2,e.id())));}
        rows.add(button(8,198,36,"<",()->{page--;buttonsForRows();}));rows.add(button(48,198,36,">",()->{page++;buttonsForRows();}));rows.add(button(108,198,130,"Voltar ao soldado",()->{blacklist=false;buttons();}));
    }
    @Override protected void renderBg(PoseStack pose,float partial,int x,int y){
        fill(pose,leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF252F29);
        fill(pose,leftPos+3,topPos+3,leftPos+imageWidth-3,topPos+imageHeight-3,0xFF435847);
        if(panelOnly())return;
        fill(pose,leftPos+4,topPos+4,leftPos+imageWidth-4,topPos+29,0xFF44372A);
        int progress=menu.state!=null&&menu.state.employed()
            ?Military.progressWidth(menu.state.level(),menu.state.kills(),WorkerTextures.XP_WIDTH):0;
        WorkerTextures.drawExperience(pose,leftPos+(imageWidth-WorkerTextures.XP_WIDTH)/2,topPos+WorkerTextures.XP_Y,progress,true);
        for(var slot:menu.slots){
            if(!slot.isActive())continue;
            fill(pose,leftPos+slot.x-1,topPos+slot.y-1,leftPos+slot.x+17,topPos+slot.y+17,0xFF202820);
            fill(pose,leftPos+slot.x,topPos+slot.y,leftPos+slot.x+16,topPos+slot.y+16,0xFFAAB79C);
        }
    }
    @Override protected void renderLabels(PoseStack pose,int x,int y){
        var state=menu.state;
        String title="Soldado";
        if(blacklist)title="Blacklist — inimigos autorizados";
        else if(settings)title="Configurações";
        else if(state!=null&&state.employed()){
            String suffix=" (Soldado) - Lv"+state.level();
            var name=state.workerName();
            int room=imageWidth-72-font.width(suffix);
            if(font.width(name)>room)name=font.plainSubstrByWidth(name,Math.max(0,room-font.width("…"))).stripTrailing()+"…";
            title=name+suffix;
        }
        drawCenteredString(pose,font,title,imageWidth/2,8,0xFFF6E8CC);
        if(blacklist)return;
        if(settings){
            if(state!=null&&state.discard()!=null)drawCenteredString(pose,font,"Baú: "+state.discard().toShortString(),imageWidth/2,120,0xFFFFFF);
            return;
        }
        if(menu.legacyStorage)font.draw(pose,"Recuperar itens da versão anterior",42,34,0xFFFFFF);
        else{
            String[] labels={"Arma","Esc.","Cap.","Peito","Pern.","Botas"};
            for(int i=0;i<6;i++)drawCenteredString(pose,font,labels[i],32+36*i,38,0xFFFFFF);
            font.draw(pose,"Armazenamento",42,106,0xFFFFFF);
        }
        if(state!=null){
            String hp="Vida: "+Math.round(state.hp())+"/"+Math.round(state.max());
            font.draw(pose,hp,8,29,0xFFFFFF);
            if(state.zombie())font.draw(pose,"Fraqueza + Maçã Dourada",89,29,0xFFFFDD88);
        }
        font.draw(pose,playerInventoryTitle,42,135,0xFFFFFF);
    }
    @Override public void render(PoseStack pose,int x,int y,float partial){renderBackground(pose);if(!panelOnly()){super.render(pose,x,y,partial);renderTooltip(pose,x,y);}else{renderBg(pose,partial,x,y);pose.pushPose();pose.translate(leftPos,topPos,0);renderLabels(pose,x,y);pose.popPose();for(var r:renderables)r.render(pose,x,y,partial);}}
    @Override public boolean mouseClicked(double x,double y,int b){if(!panelOnly())return super.mouseClicked(x,y,b);for(var child:children())if(child.mouseClicked(x,y,b)){setFocused(child);return true;}return false;}
    @Override public boolean mouseReleased(double x,double y,int b){return panelOnly()?false:super.mouseReleased(x,y,b);}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){return panelOnly()?false:super.mouseDragged(x,y,b,dx,dy);}
    @Override public boolean mouseScrolled(double x,double y,double delta){if(blacklist){page+=delta<0?1:-1;buttonsForRows();return true;}if(settings)return false;return super.mouseScrolled(x,y,delta);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(blacklist){if(key==256){blacklist=false;buttons();return true;}return search!=null&&search.isFocused()&&search.keyPressed(key,scan,modifiers);}if(settings){if(key==256){settings=false;buttons();return true;}return false;}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean charTyped(char c,int modifiers){return blacklist&&search!=null&&search.isFocused()?search.charTyped(c,modifiers):super.charTyped(c,modifiers);}
}
