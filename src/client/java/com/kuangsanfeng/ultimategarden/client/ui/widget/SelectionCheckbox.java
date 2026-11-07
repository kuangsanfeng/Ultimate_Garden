package com.kuangsanfeng.ultimategarden.client.ui.widget;

import com.kuangsanfeng.ultimategarden.client.ui.theme.ThemePalette;
import com.kuangsanfeng.ultimategarden.client.ui.render.RoundedRect;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.function.*;

/** Independent, session-only multi-selection; no configuration writes. */
public final class SelectionCheckbox extends StyledButton {
    private final BooleanSupplier checked;
    public SelectionCheckbox(int x,int y,String label,Supplier<ThemePalette> palette,BooleanSupplier checked,Runnable changed) {
        super(x,y,20,20,label,false,palette,changed); this.checked=checked;
    }
    @Override protected void extractContents(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        var p=palette.get();
        RoundedRect.fill(g,getX(),getY(),20,20,5,p.bg(isHoveredOrFocused()?p.selected():p.surface()));
        RoundedRect.outline(g,getX(),getY(),20,20,5,p.fg(checked.getAsBoolean()?p.accent():p.muted()));
        if(checked.getAsBoolean()) {
            for(int i=0;i<4;i++)g.fill(getX()+4+i,getY()+9+i,getX()+6+i,getY()+11+i,p.fg(p.accent()));
            for(int i=0;i<7;i++)g.fill(getX()+7+i,getY()+12-i,getX()+9+i,getY()+14-i,p.fg(p.accent()));
        }
    }
}
