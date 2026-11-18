package br.com.kraftcity.builder.client;

import br.com.kraftcity.builder.BuilderNetwork;
import java.util.UUID;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;

public final class BlueprintEditorScreen extends Screen {
    public final boolean engineer;private EditBox name;
    public BlueprintEditorScreen(boolean engineer){super(Component.literal(engineer?"Papel do Engenheiro":"Posicionar esquema"));this.engineer=engineer;}
    private void send(int op,String value){BuilderNetwork.CHANNEL.sendToServer(new BuilderNetwork.Command(-1,new UUID(0,0),op,value));}
    private void button(int x,int y,int w,String label,Runnable action){addRenderableWidget(new Button(x,y,w,18,Component.literal(label),b->action.run()));}
    @Override protected void init(){int x=width/2-136,y=height-100;if(engineer){name=addRenderableWidget(new EditBox(font,x,y,272,18,Component.literal("Nome do esquema")));name.setMaxLength(80);name.setValue("Minha construção");button(x,y+24,132,"Salvar seleção",()->{send(100,name.getValue());minecraft.setScreen(null);});button(x+140,y+24,132,"Cancelar seleção",()->{send(101,"");minecraft.setScreen(null);});}else{
        String[] values={"x-","x+","z-","z+","y-","y+"};String[] labels={"X−","X+","Z−","Z+","Alt.−","Alt.+"};for(int i=0;i<6;i++){final int n=i;button(x+i*46,y,44,labels[i],()->send(110,values[n]));}
        button(x,y+24,132,"Girar 90°",()->send(111,""));button(x+140,y+24,132,"Espelhar",()->send(112,""));button(x,y+48,132,"Confirmar posição",()->{send(113,"");minecraft.setScreen(null);});button(x+140,y+48,132,"Cancelar",()->{send(114,"");minecraft.setScreen(null);});}}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(PoseStack p,int x,int y,float partial){fill(p,width/2-145,height-120,width/2+145,height-15,0xB0203040);drawCenteredString(p,font,title,width/2,height-114,0xFFFFFF);super.render(p,x,y,partial);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==256&&!engineer){minecraft.setScreen(null);return true;}return super.keyPressed(key,scan,modifiers);}
}
