package com.kuangsanfeng.ultimategarden.client.platform.ui.hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
public final class HudPanel {
 public static void background(GuiGraphicsExtractor g,int w,int h,int border){g.backgrounds++;}
 public static void title(GuiGraphicsExtractor g,String value){g.title=value;}
 public static int rowHeight(String label,String value,int width){return label.length()*6+value.length()*6+32>width?28:19;}
 public static void row(GuiGraphicsExtractor g,String label,String value,int width,int y,int h,int color){g.values.add(label);g.values.add(value);}
 public static void editorOverlay(GuiGraphicsExtractor g,ConfigManager config){}
}
