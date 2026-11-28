package br.com.kraftcity.builder.client;

import br.com.kraftcity.builder.*;
import java.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class BuilderScreen extends AbstractContainerScreen<BuilderMenu> {
    private int page,jobPage,materialPage;private boolean materialView;private CompoundTag materialData=new CompoundTag();
    public void materials(CompoundTag t){materialData=t;materialView=true;materialPage=0;buttons();}
    public BuilderScreen(BuilderMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=440;imageHeight=296;inventoryLabelX=258;inventoryLabelY=203;}
    @Override protected void init(){super.init();buttons();}
    private void command(int op,String value){BuilderNetwork.CHANNEL.sendToServer(new BuilderNetwork.Command(menu.containerId,menu.stationId,op,value));}
    private Button button(int x,int y,int w,String label,Runnable r){return addRenderableWidget(new Button(leftPos+x,topPos+y,w,16,Component.literal(label),b->r.run()));}
    private boolean professionalSettings;

    public void buttons(){clearWidgets();
        if(professionalSettings){
            button(8,4,88,"Voltar",()->{professionalSettings=false;buttons();});
            int level=menu.snapshot.getInt("level");
            if(menu.snapshot.getBoolean("employed"))
                button(8,31,130,level>=5?"Nível máximo":"Aumentar Nível",
                        ()->br.com.kraftcity.employment.WorkNetwork.CHANNEL.sendToServer(new br.com.kraftcity.employment.WorkNetwork.Upgrade(menu.containerId,menu.stationId))).active=level<5;
            return;
        }
        if(materialView){button(8,26,130,"Total do esquema",()->command(9,"schema"));button(146,26,130,"Faltas da obra",()->command(9,"job")).active=menu.selectedJob!=null;button(302,26,130,"Voltar",()->{materialView=false;buttons();});button(8,272,80,"<",()->{materialPage=Math.max(0,materialPage-1);buttons();});button(94,272,80,">",()->{materialPage++;buttons();});return;}page=Math.max(0,Math.min(page,Math.max(0,(menu.library.size()-1)/7)));for(int i=0;i<7&&page*7+i<menu.library.size();i++){var e=menu.library.get(page*7+i);var id=e.getUUID("id");button(8,31+i*20,170,(id.equals(menu.selectedSchema)?"> ":"")+font.plainSubstrByWidth(e.getString("name"),150),()->command(0,id.toString()));}
        button(8,174,40,"<",()->{page--;buttons();});button(52,174,40,">",()->{page++;buttons();});button(96,174,82,"Atualizar",()->command(2,""));
        button(184,26,68,"Materiais",()->command(9,"schema"));
        int required=1000;for(var e:menu.library)if(e.getUUID("id").equals(menu.selectedSchema))required=Math.min(1000,e.getInt("blocks"));button(270,126,116,"Gravar (5 s)",()->command(1,"")).active=menu.selectedSchema!=null&&!menu.snapshot.getBoolean("printing")&&menu.stock.getItem(29).is(BuilderModule.BLANK.get())&&menu.stock.getItem(30).isEmpty()&&menu.snapshot.getLong("inkAvailable")>=required;
        button(270,184,76,"Construir",()->command(3,"")).active=menu.snapshot.getBoolean("planValid");button(350,184,80,menu.snapshot.getBoolean("replace")?"Substituir":"Preservar",()->command(7,""));
        var jobs=menu.snapshot.getList("jobs",Tag.TAG_COMPOUND);jobPage=Math.max(0,Math.min(jobPage,Math.max(0,jobs.size()-1)));if(!jobs.isEmpty()){var e=jobs.getCompound(jobPage);button(8,213,228,(e.getUUID("id").equals(menu.selectedJob)?"> ":"")+font.plainSubstrByWidth(e.getString("name")+" "+e.getInt("done")+"/"+e.getInt("total"),207),()->command(4,e.getUUID("id").toString()));}
        button(8,232,32,"<",()->{jobPage--;buttons();});button(44,232,32,">",()->{jobPage++;buttons();});
        if(menu.snapshot.getBoolean("employed")){
            button(284,2,76,"Opções",()->{professionalSettings=true;buttons();});
            long epoch=menu.snapshot.getLong("employment_epoch");
            addRenderableWidget(new Button(leftPos+366,topPos+2,66,14,Component.translatable("gui.kraftcity.dismiss"),
                b->br.com.kraftcity.employment.WorkNetwork.CHANNEL.sendToServer(
                    new br.com.kraftcity.employment.WorkNetwork.Dismiss(menu.containerId,menu.stationId,epoch))));
            button(40,272,80,"Pausar/Retomar",()->command(5,"")).active=menu.selectedJob!=null;button(124,272,112,"Cancelar obra",()->command(6,"")).active=menu.selectedJob!=null;}else button(40,272,196,"Contratar residente",()->command(8,""));
    }
    @Override protected void renderBg(PoseStack p,float partial,int x,int y){fill(p,leftPos,topPos,leftPos+440,topPos+296,0xFF222D3A);fill(p,leftPos+3,topPos+3,leftPos+437,topPos+293,0xFF3E536B);if(!materialView)for(var s:menu.slots){fill(p,leftPos+s.x-1,topPos+s.y-1,leftPos+s.x+17,topPos+s.y+17,0xFF18212B);fill(p,leftPos+s.x,topPos+s.y,leftPos+s.x+16,topPos+s.y+16,0xFF9EAFBF);}if(!materialView){bar(p,270,106,116,menu.snapshot.getInt("ink"),1000,0xFFD0A0F4);bar(p,270,166,160,menu.snapshot.getInt("charge"),500,0xFF96E79C);bar(p,270,146,160,menu.snapshot.getInt("print"),100,0xFFA8BEE7);var job=selected();if(job!=null){int width=154*Math.min(job.getInt("done"),job.getInt("total"))/Math.max(1,job.getInt("total"));fill(p,leftPos+82,topPos+245,leftPos+236,topPos+249,0xFF17202A);fill(p,leftPos+82,topPos+245,leftPos+82+width,topPos+249,0xFF96E79C);}}}
    private CompoundTag selected(){for(var raw:menu.snapshot.getList("jobs",Tag.TAG_COMPOUND)){var e=(CompoundTag)raw;if(e.getUUID("id").equals(menu.selectedJob))return e;}return null;}
    private void bar(PoseStack p,int x,int y,int width,int n,int total,int color){fill(p,leftPos+x,topPos+y,leftPos+x+width,topPos+y+8,0xFF17202A);fill(p,leftPos+x,topPos+y,leftPos+x+width*Math.min(total,Math.max(0,n))/total,topPos+y+8,color);}
    @Override protected void renderLabels(PoseStack p,int x,int y){drawCenteredString(p,font,materialView?Component.literal("Materiais — "+materialData.getString("name")):title,220,6,0xFFFFFF);if(materialView){var rows=materialData.getList("rows",Tag.TAG_COMPOUND);materialPage=Math.max(0,Math.min(materialPage,Math.max(0,(rows.size()-1)/16)));for(int i=0;i<16&&materialPage*16+i<rows.size();i++){var e=rows.getCompound(materialPage*16+i);font.draw(p,font.plainSubstrByWidth(Component.translatable(e.getString("item")).getString(),235),8,52+i*13,0xFFFFFF);font.draw(p,"Total "+e.getInt("total")+" · estoque "+e.getInt("stock")+" · falta "+e.getInt("missing"),240,52+i*13,0xFFE1D4F5);}return;}font.draw(p,"Biblioteca",8,20,0xFFFFFF);font.draw(p,"Materiais globais (3×9)",258,16,0xFFFFFF);font.draw(p,"Vazio",200,96,0xFFFFFF);font.draw(p,"Tinta",236,96,0xFFFFFF);font.draw(p,"Saída",392,96,0xFFFFFF);font.draw(p,"Tinta "+menu.snapshot.getInt("ink")+"/1000",270,94,0xFFFFFF);font.draw(p,"Planta",194,154,0xFFFFFF);font.draw(p,"Esmer.",236,154,0xFFFFFF);font.draw(p,"Carga: "+menu.snapshot.getInt("charge")+"/500",280,156,0xFFFFFF);font.draw(p,playerInventoryTitle,258,203,0xFFFFFF);font.draw(p,"Obras globais",8,201,0xFFFFFF);
        for(var e:menu.library)if(e.getUUID("id").equals(menu.selectedSchema)){var size=BlockPos.of(e.getLong("size"));font.draw(p,font.plainSubstrByWidth(size.getX()+"×"+size.getY()+"×"+size.getZ()+" · "+e.getInt("blocks")+" blocos",170),8,191,0xFFDBEAFB);}
        var selected=menu.snapshot.getList("jobs",Tag.TAG_COMPOUND);for(var raw:selected){var e=(CompoundTag)raw;if(e.getUUID("id").equals(menu.selectedJob)){int total=Math.max(1,e.getInt("total"));font.draw(p,font.plainSubstrByWidth(e.getInt("done")*100/total+"% · ativos "+e.getInt("workers"),154),82,235,0xFFB8F0BB);font.draw(p,font.plainSubstrByWidth(e.getInt("placed")+" colocados · "+(e.getBoolean("replace")?"Substituir":"Preservar")+" · "+e.getString("status"),228),8,251,0xFFFFD68A);}}
        font.draw(p,"Taxa",8,264,0xFFC7D6E7);font.draw(p,font.plainSubstrByWidth(menu.snapshot.getString("status"),246),184,80,0xFFFFD68A);
    }
    @Override public void render(PoseStack p,int x,int y,float partial){renderBackground(p);if(!materialView){super.render(p,x,y,partial);renderTooltip(p,x,y);var job=selected();if(job!=null&&x>=leftPos+8&&x<leftPos+236&&y>=topPos+251&&y<topPos+264)renderTooltip(p,Component.literal(job.getString("status")),x,y);}else{renderBg(p,partial,x,y);p.pushPose();p.translate(leftPos,topPos,0);renderLabels(p,x,y);p.popPose();for(var r:renderables)r.render(p,x,y,partial);}}
    @Override public boolean mouseClicked(double x,double y,int b){if(!materialView)return super.mouseClicked(x,y,b);for(var child:children())if(child.mouseClicked(x,y,b))return true;return false;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(materialView){if(key==256){materialView=false;buttons();}return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){return !materialView&&super.mouseDragged(x,y,b,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int b){return !materialView&&super.mouseReleased(x,y,b);}
}
