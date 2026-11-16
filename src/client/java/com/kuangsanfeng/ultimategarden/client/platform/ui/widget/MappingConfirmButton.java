package com.kuangsanfeng.ultimategarden.client.platform.ui.widget;

import com.kuangsanfeng.ultimategarden.client.function.mapping.MappingConfirmation;
import com.kuangsanfeng.ultimategarden.client.platform.ui.theme.ThemePalette;
import com.kuangsanfeng.ultimategarden.client.platform.ui.render.RoundedRect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import java.util.function.Supplier;

/** Shared Mapping danger interaction; keyboard activation cannot bypass the click sequence. */
public final class MappingConfirmButton extends StyledButton {
    private final MappingConfirmation confirmation;
    private final Supplier<Object> context;
    private final String operation,label,confirmLabel;
    private final Runnable confirmed;
    public MappingConfirmButton(int x,int y,int w,int h,String label,String confirmLabel,Supplier<ThemePalette> palette,
            MappingConfirmation confirmation,Supplier<Object> context,String operation,Runnable confirmed){
        super(x,y,w,h,label,false,palette,()->{});this.label=label;this.confirmLabel=confirmLabel;this.confirmation=confirmation;this.context=context;this.operation=operation;this.confirmed=confirmed;
    }
    @Override public void onPress(InputWithModifiers input){
        if(!(input instanceof net.minecraft.client.input.MouseButtonEvent)||!active)return;
        if(confirmation.click(context.get(),operation,System.nanoTime()))confirmed.run();
    }
    @Override protected void extractContents(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        var p=palette.get();boolean armed=confirmation.armed(context.get(),operation);
        RoundedRect.fill(g,getX(),getY(),width,height,9,p.bg(active?(armed?0xFFF4DAD9:0xFFE0F0EF):p.border()));
        var font=Minecraft.getInstance().font;var lines=font.split(Component.literal(armed?confirmLabel:label),Math.max(20,width-16));int y=getY()+(height-lines.size()*10)/2;
        for(var line:lines){g.text(font,line,getX()+(width-font.width(line))/2,y,0xFF283535,false);y+=10;}
    }
}
