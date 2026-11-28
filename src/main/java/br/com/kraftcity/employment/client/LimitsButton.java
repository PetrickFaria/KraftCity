package br.com.kraftcity.employment.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class LimitsButton extends Button {
    private final BooleanSupplier enabled;

    public LimitsButton(int x,int y,int w,int h,Component name,OnPress press,BooleanSupplier enabled){
        super(x,y,w,h,name,press);
        this.enabled=enabled;
    }

    @Override public void renderButton(PoseStack pose,int mouseX,int mouseY,float partial){
        boolean lit=enabled.getAsBoolean();
        fill(pose,x,y,x+width,y+height,lit?0xFF8CE39F:0xFF625D52);
        fill(pose,x+1,y+1,x+width-1,y+height-1,lit?0xFF236B3B:0xFF35342F);
        if(isHoveredOrFocused())fill(pose,x+2,y+height-2,x+width-2,y+height-1,0x70FFFFFF);
        drawCenteredString(pose,Minecraft.getInstance().font,getMessage(),x+width/2,y+(height-8)/2,
                active?0xFFF2EBDD:0xFF9C998F);
    }
}
