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
    private int category=2,origin,page;
    private String query="";
    private EditBox search;
    public SoldierScreen(SoldierMenu m,Inventory inv,Component title){super(m,inv,title);imageWidth=350;imageHeight=283;inventoryLabelX=94;inventoryLabelY=190;}
    @SubscribeEvent public static void setup(FMLClientSetupEvent e){e.enqueueWork(()->net.minecraft.client.gui.screens.MenuScreens.register(MilitaryModule.MENU.get(),SoldierScreen::new));}
    @SubscribeEvent public static void renderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(MilitaryModule.ZOMBIE.get(),net.minecraft.client.renderer.entity.ZombieVillagerRenderer::new);}
    public static void receive(MilitaryNetwork.State s){var mc=Minecraft.getInstance();if(mc.player!=null&&mc.player.containerMenu instanceof SoldierMenu m&&m.containerId==s.menu()&&m.stationId.equals(s.station())){m.state=s;if(!s.entries().isEmpty())m.catalog=s.entries();if(mc.screen instanceof SoldierScreen screen&&(!screen.blacklist||!s.entries().isEmpty()))screen.buttons();}}
    @Override protected void init(){super.init();buttons();send(3,"");}
    private void send(int command,String value){MilitaryNetwork.CHANNEL.sendToServer(new MilitaryNetwork.Command(menu.containerId,menu.stationId,command,value));}
    private Button button(int x,int y,int w,String label,Runnable r){return addRenderableWidget(new Button(leftPos+x,topPos+y,w,18,Component.literal(label),b->r.run()));}
    private String name(MilitaryNetwork.Entry e){String key="entity."+e.id().replace(':','.');String translated=Component.translatable(key).getString();return translated.equals(key)?e.name():translated;}
    private List<MilitaryNetwork.Entry> filtered(){String q=query.toLowerCase(Locale.ROOT);return menu.catalog.stream().filter(e->e.category()==category&&(origin==0||origin==1&&e.id().startsWith("minecraft:")||origin==2&&!e.id().startsWith("minecraft:"))&&(e.id().toLowerCase(Locale.ROOT).contains(q)||name(e).toLowerCase(Locale.ROOT).contains(q))).toList();}
    private void buttons(){clearWidgets();if(blacklist){
        String[] cats={"Pacíficos","Neutros","Hostis"};for(int i=0;i<3;i++){final int c=i;button(8+112*i,24,108,(category==i?"> ":"")+cats[i],()->{category=c;page=0;buttons();});}
        button(8,46,130,"Origem: "+new String[]{"Todas","Minecraft","Mods"}[origin],()->{origin=(origin+1)%3;page=0;buttons();});
        search=addRenderableWidget(new EditBox(font,leftPos+146,topPos+46,194,18,Component.literal("Buscar criatura")));search.setValue(query);search.setResponder(v->{query=v;page=0;buttonsForRows();});
        buttonsForRows();return;
    }
        boolean oldItems=java.util.stream.IntStream.range(9,32).anyMatch(i->!menu.inventory.getItem(i).isEmpty());
        if(oldItems||menu.legacyStorage)button(180,82,162,menu.legacyStorage?"Voltar ao armazenamento":"Itens da versão anterior",
            ()->{menu.legacyStorage=!menu.legacyStorage;buttons();});
        var state=menu.state;
        if(state!=null&&state.employed())addRenderableWidget(new Button(leftPos+276,topPos+2,66,14,
            Component.translatable("gui.kraftcity.dismiss"),b->br.com.kraftcity.employment.WorkNetwork.CHANNEL.sendToServer(
                new br.com.kraftcity.employment.WorkNetwork.Dismiss(menu.containerId,menu.stationId,state.epoch()))));
        var modes=new Military.Mode[]{Military.Mode.FOLLOW,Military.Mode.FREE,Military.Mode.GUARD};
        String[] names={"Seguir","Patrulhar","Guarnecer"};
        for(int i=0;i<modes.length;i++){
            var mode=modes[i];
            button(8+82*i,104,78,(state!=null&&state.mode()==mode.ordinal()?"> ":"")+names[i],()->send(0,mode.name()));
        }
        button(258,104,84,"Limites",()->MilitaryPreview.toggle(menu.pos));
        button(8,128,76,state!=null&&state.night()?"> Noturno":"Noturno",()->send(1,"NIGHT"));
        button(90,128,76,state!=null&&!state.night()?"> Diurno":"Diurno",()->send(1,"DAY"));
        button(258,128,84,"Blacklist",()->{blacklist=true;page=0;buttons();send(3,"");});
        if(state!=null&&state.employed())
            button(8,151,84,state.discard()!=null?"Desvincular baú":"Vincular baú",()->send(state.discard()!=null?6:5,""));
        if(!menu.legacyStorage&&menu.state!=null&&!menu.state.employed())button(180,28,162,"Treinar residente",()->send(4,""));
    }
    private final List<Button> rows=new ArrayList<>();
    private void buttonsForRows(){for(var r:rows)removeWidget(r);rows.clear();var list=filtered();page=Math.max(0,Math.min(page,Math.max(0,(list.size()-1)/8)));
        for(int i=0;i<8&&page*8+i<list.size();i++){var e=list.get(page*8+i);String label=(e.selected()?"[✓] ":"[ ] ")+font.plainSubstrByWidth(name(e)+" — "+e.id(),310);rows.add(button(8,74+23*i,334,label,()->send(2,e.id())));}
        rows.add(button(8,264,66,"<",()->{page--;buttonsForRows();}));rows.add(button(80,264,66,">",()->{page++;buttonsForRows();}));rows.add(button(212,264,130,"Voltar ao soldado",()->{blacklist=false;buttons();}));
    }
    @Override protected void renderBg(PoseStack pose,float partial,int x,int y){
        fill(pose,leftPos,topPos,leftPos+350,topPos+283,0xFF252F29);
        fill(pose,leftPos+3,topPos+3,leftPos+347,topPos+280,0xFF435847);
        if(blacklist)return;
        fill(pose,leftPos+4,topPos+4,leftPos+346,topPos+29,0xFF44372A);
        int progress=menu.state!=null&&menu.state.employed()
            ?menu.state.level()>=5?WorkerTextures.XP_WIDTH:(int)(WorkerTextures.XP_WIDTH*(menu.state.xp()%75000)/75000):0;
        WorkerTextures.drawExperience(pose,leftPos+(350-WorkerTextures.XP_WIDTH)/2,topPos+WorkerTextures.XP_Y,progress,true);
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
        else if(state!=null&&state.employed()){
            String suffix=" (Soldado) - Lv"+state.level();
            var name=state.workerName();
            int room=imageWidth-134-font.width(suffix);
            if(font.width(name)>room)name=font.plainSubstrByWidth(name,Math.max(0,room-font.width("…"))).stripTrailing()+"…";
            title=name+suffix;
        }
        drawCenteredString(pose,font,title,175,8,0xFFF6E8CC);
        if(blacklist)return;
        if(menu.legacyStorage)font.draw(pose,"Recuperar itens da versão anterior",94,30,0xFFFFFF);
        else{
            font.draw(pose,"Equipamentos",8,42,0xFFFFFF);
            if(state!=null&&state.discard()!=null)font.draw(pose,"Baú: "+state.discard().toShortString(),205,42,0xFFFFFF);
            font.draw(pose,"Armazenamento",94,153,0xFFFFFF);
            String[] labels={"Arma","Esc.","Cap.","Peito","Pern.","Botas"};
            for(int i=0;i<6;i++)font.draw(pose,labels[i],16+36*i,52,0xFFFFFF);
        }
        var s=menu.state;
        if(s!=null){
            font.draw(pose,"Vida: "+Math.round(s.hp())+"/"+Math.round(s.max()),8,88,0xFFFFFF);
            if(!menu.legacyStorage)font.draw(pose,s.zombie()?"Soldado Zumbi: Fraqueza + Maçã Dourada":s.employed()?"Em serviço conforme o turno":"Sem soldado",8,30,0xFFFFDD88);
        }
        font.draw(pose,playerInventoryTitle,94,190,0xFFFFFF);
    }
    @Override public void render(PoseStack pose,int x,int y,float partial){renderBackground(pose);if(!blacklist){super.render(pose,x,y,partial);renderTooltip(pose,x,y);}else{renderBg(pose,partial,x,y);pose.pushPose();pose.translate(leftPos,topPos,0);renderLabels(pose,x,y);pose.popPose();for(var r:renderables)r.render(pose,x,y,partial);}}
    @Override public boolean mouseClicked(double x,double y,int b){if(!blacklist)return super.mouseClicked(x,y,b);for(var child:children())if(child.mouseClicked(x,y,b)){setFocused(child);return true;}return false;}
    @Override public boolean mouseReleased(double x,double y,int b){return blacklist?false:super.mouseReleased(x,y,b);}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){return blacklist?false:super.mouseDragged(x,y,b,dx,dy);}
    @Override public boolean mouseScrolled(double x,double y,double delta){if(blacklist){page+=delta<0?1:-1;buttonsForRows();return true;}return super.mouseScrolled(x,y,delta);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(blacklist){if(key==256){blacklist=false;buttons();return true;}return search!=null&&search.isFocused()&&search.keyPressed(key,scan,modifiers);}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean charTyped(char c,int modifiers){return blacklist&&search!=null&&search.isFocused()?search.charTyped(c,modifiers):super.charTyped(c,modifiers);}
}
