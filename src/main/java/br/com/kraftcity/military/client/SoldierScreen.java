package br.com.kraftcity.military.client;

import br.com.kraftcity.military.*;
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
    public SoldierScreen(SoldierMenu m,Inventory inv,Component title){super(m,inv,title);imageWidth=350;imageHeight=296;inventoryLabelX=94;inventoryLabelY=203;}
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
        String[] modes={"Livre","Guarda","Seguir"};for(int i=0;i<3;i++){final int c=i;button(8+82*i,145,78,(menu.state!=null&&menu.state.mode()==i?"> ":"")+modes[i],()->send(0,Military.Mode.values()[c].name()));}
        button(258,145,84,menu.state!=null&&menu.state.night()?"Noturno":"Diurno",()->send(1,menu.state!=null&&menu.state.night()?"DAY":"NIGHT"));
        button(8,169,164,"Blacklist de criaturas",()->{blacklist=true;page=0;buttons();send(3,"");});
        if(menu.state!=null&&!menu.state.employed())button(180,169,162,"Treinar residente",()->send(4,""));
    }
    private final List<Button> rows=new ArrayList<>();
    private void buttonsForRows(){for(var r:rows)removeWidget(r);rows.clear();var list=filtered();page=Math.max(0,Math.min(page,Math.max(0,(list.size()-1)/8)));
        for(int i=0;i<8&&page*8+i<list.size();i++){var e=list.get(page*8+i);String label=(e.selected()?"[✓] ":"[ ] ")+font.plainSubstrByWidth(name(e)+" — "+e.id(),310);rows.add(button(8,74+23*i,334,label,()->send(2,e.id())));}
        rows.add(button(8,264,66,"<",()->{page--;buttonsForRows();}));rows.add(button(80,264,66,">",()->{page++;buttonsForRows();}));rows.add(button(212,264,130,"Voltar ao soldado",()->{blacklist=false;buttons();}));
    }
    @Override protected void renderBg(PoseStack pose,float partial,int x,int y){fill(pose,leftPos,topPos,leftPos+350,topPos+296,0xFF252F29);fill(pose,leftPos+3,topPos+3,leftPos+347,topPos+293,0xFF435847);if(!blacklist)for(var slot:menu.slots){fill(pose,leftPos+slot.x-1,topPos+slot.y-1,leftPos+slot.x+17,topPos+slot.y+17,0xFF202820);fill(pose,leftPos+slot.x,topPos+slot.y,leftPos+slot.x+16,topPos+slot.y+16,0xFFAAB79C);}}
    @Override protected void renderLabels(PoseStack pose,int x,int y){drawCenteredString(pose,font,blacklist?"Blacklist — criaturas autorizadas":"Soldado",175,6,0xFFFFFF);if(blacklist)return;
        font.draw(pose,"Inventário comum (3 × 9)",94,16,0xFFFFFF);font.draw(pose,"Reservas",8,40,0xFFFFFF);String[] labels={"Arma","Esc.","Cap.","Peito","Pern.","Botas"};for(int i=0;i<6;i++)font.draw(pose,labels[i],16+36*i,91,0xFFFFFF);font.draw(pose,"Esmer.",267,91,0xFFFFFF);
        var s=menu.state;if(s!=null){font.draw(pose,"Vida: "+Math.round(s.hp())+"/"+Math.round(s.max())+"   Lv"+s.level()+"   XP: "+String.format(Locale.ROOT,"%.1f",s.xp()/1000.),8,124,0xFFFFFF);font.draw(pose,s.zombie()?"Soldado Zumbi: Fraqueza + Maçã Dourada":s.employed()?"Turno, cama e equipamentos persistentes":"Sem soldado: itens preservados no posto",8,191,0xFFFFDD88);}font.draw(pose,playerInventoryTitle,94,203,0xFFFFFF);
    }
    @Override public void render(PoseStack pose,int x,int y,float partial){renderBackground(pose);if(!blacklist){super.render(pose,x,y,partial);renderTooltip(pose,x,y);}else{renderBg(pose,partial,x,y);pose.pushPose();pose.translate(leftPos,topPos,0);renderLabels(pose,x,y);pose.popPose();for(var r:renderables)r.render(pose,x,y,partial);}}
    @Override public boolean mouseClicked(double x,double y,int b){if(!blacklist)return super.mouseClicked(x,y,b);for(var child:children())if(child.mouseClicked(x,y,b)){setFocused(child);return true;}return false;}
    @Override public boolean mouseReleased(double x,double y,int b){return blacklist?false:super.mouseReleased(x,y,b);}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){return blacklist?false:super.mouseDragged(x,y,b,dx,dy);}
    @Override public boolean mouseScrolled(double x,double y,double delta){if(blacklist){page+=delta<0?1:-1;buttonsForRows();return true;}return super.mouseScrolled(x,y,delta);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(blacklist){if(key==256){blacklist=false;buttons();return true;}return search!=null&&search.isFocused()&&search.keyPressed(key,scan,modifiers);}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean charTyped(char c,int modifiers){return blacklist&&search!=null&&search.isFocused()?search.charTyped(c,modifiers):super.charTyped(c,modifiers);}
}
