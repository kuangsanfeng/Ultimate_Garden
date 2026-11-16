package com.kuangsanfeng.ultimategarden.client.platform.ui.hud;
import com.kuangsanfeng.ultimategarden.client.platform.ui.hud.HudAdjustmentController;


import com.kuangsanfeng.ultimategarden.client.platform.ui.render.RoundedRect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Shared drawing primitives for the existing dark, two-column HUD style. */
public final class HudPanel {
    public static void background(GuiGraphicsExtractor g, int width, int height, int border) {
        RoundedRect.fill(g, 0, 0, width, height, 8, 0xdf142420);
        RoundedRect.outline(g, 0, 0, width, height, 8, border);
    }
    public static void title(GuiGraphicsExtractor g, String title) {
        g.text(Minecraft.getInstance().font, Component.literal(title).withStyle(net.minecraft.ChatFormatting.BOLD), 11, 11, 0xffffffff, false);
    }
    public static int rowHeight(String label, String value, int width) {
        var font = Minecraft.getInstance().font;
        return font.width(label) + font.width(value) + 32 > width ? 28 : 19;
    }
    public static void row(GuiGraphicsExtractor g, String label, String value, int width, int y, int height, int color) {
        var font = Minecraft.getInstance().font;
        g.text(font, label, 11, y, 0xffffffff, false);
        g.text(font, value, width - font.width(value) - 11, height == 28 ? y + 11 : y, color, false);
        g.fill(11, y + height - 5, width - 11, y + height - 4, 0xff626b66);
    }
    public static void editorOverlay(GuiGraphicsExtractor g, com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager configs) {
        var layout=HudAdjustmentController.selectedLayout();
        if(layout!=null){
            int x=(int)layout.x(),y=(int)layout.y(),w=(int)layout.width(),h=(int)layout.height();
            RoundedRect.outline(g,x-2,y-2,w+4,h+4,8,0xffffffff);
            for(double hx:new double[]{layout.x(),layout.x()+layout.width()})for(double hy:new double[]{layout.y(),layout.y()+layout.height()})
                g.fill((int)hx-2,(int)hy-2,(int)hx+3,(int)hy+3,0xffffffff);
        }
        var c=Minecraft.getInstance();
        var language=new com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageManager(configs);
        String instructions=c.font.plainSubstrByWidth(language.text("ui.ultimategarden.hud.adjust.instructions"),Math.max(1,c.getWindow().getGuiScaledWidth()-16));
        g.text(c.font,instructions,8,c.getWindow().getGuiScaledHeight()-12,0xffffffff,false);
    }
}
