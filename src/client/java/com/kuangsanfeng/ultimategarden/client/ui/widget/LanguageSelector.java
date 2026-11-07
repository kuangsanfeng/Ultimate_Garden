package com.kuangsanfeng.ultimategarden.client.ui.widget;

import com.kuangsanfeng.ultimategarden.client.ui.language.*;
import com.kuangsanfeng.ultimategarden.client.ui.render.RoundedRect;
import com.kuangsanfeng.ultimategarden.client.ui.theme.ThemePalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import java.util.function.*;

/** Floating language menu; its input is handled before page controls. */
public final class LanguageSelector extends StyledButton {
    private final LanguageManager languages;
    private final Consumer<LanguageType> selected;
    private final Runnable clearHelp;
    private boolean open;
    public LanguageSelector(int x, int y, Supplier<ThemePalette> palette, LanguageManager languages,
                            Consumer<LanguageType> selected, Runnable clearHelp) {
        super(x,y,112,22,languages.current().englishName(),false,palette,() -> { });
        this.languages=languages; this.selected=selected; this.clearHelp=clearHelp;
    }
    public boolean isOpen() { return open; }
    public void dismiss() { open=false; clearHelp.run(); }
    @Override public void onPress(InputWithModifiers input) { open=!open; clearHelp.run(); }
    private void flag(GuiGraphicsExtractor g, LanguageType type, int x, int y) {
        g.blit(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath("ultimate_garden",type.flagResource()),x,y,0,0,16,10,32,20,32,20,palette.get().fg(0xffffffff));
    }
    @Override protected void extractContents(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        var p=palette.get();
        RoundedRect.fill(g,getX(),getY(),width,height,9,p.bg(isHoveredOrFocused() || open ? p.selected() : p.surface()));
        flag(g,languages.current(),getX()+8,getY()+6);
        g.text(Minecraft.getInstance().font,languages.current().englishName(),getX()+29,getY()+7,p.fg(p.text()),false);
        for(int i=0;i<4;i++) g.fill(getX()+width-16+i,getY()+9+i,getX()+width-9-i,getY()+10+i,p.fg(p.muted()));
    }
    private int menuX() { return getX()+width-190; }
    private int menuY() { return getY()+height+5; }
    /** Returns true also on an outside click, so dismissal never activates a control underneath. */
    public boolean handleOverlayClick(MouseButtonEvent event) {
        if(!open) return false;
        if(isMouseOver(event.x(),event.y())) { dismiss(); return true; }
        int row=(int)((event.y()-menuY()-4)/28);
        if(event.button()==0 && event.x()>=menuX() && event.x()<menuX()+190 && event.y()>=menuY()+4 && row>=0 && row<2) {
            open=false; clearHelp.run(); selected.accept(LanguageType.values()[row]);
        } else dismiss();
        return true;
    }
    public void renderOverlay(GuiGraphicsExtractor g,int mouseX,int mouseY) {
        if(!open) return;
        var p=palette.get(); int x=menuX(), y=menuY(); var font=Minecraft.getInstance().font;
        g.nextStratum();
        RoundedRect.fill(g,x+2,y+3,190,64,10,p.bg(0x40000000));
        RoundedRect.outline(g,x,y,190,64,10,p.fg(p.border())); RoundedRect.fill(g,x+1,y+1,188,62,9,p.bg(p.surface()));
        for(int row=0;row<2;row++) {
            var type=LanguageType.values()[row]; int ry=y+4+row*28;
            if(mouseX>=x+3 && mouseX<x+187 && mouseY>=ry && mouseY<ry+28) RoundedRect.fill(g,x+3,ry,184,28,7,p.bg(p.selected()));
            flag(g,type,x+9,ry+9); g.text(font,type.optionLabel(),x+31,ry+10,p.fg(p.text()),false);
            if(type==languages.current()) {
                for(int i=0;i<3;i++) g.fill(x+173+i,ry+14+i,x+175+i,ry+16+i,p.fg(p.accent()));
                for(int i=0;i<6;i++) g.fill(x+176+i,ry+16-i,x+178+i,ry+18-i,p.fg(p.accent()));
            }
        }
    }
}
