package com.kuangsanfeng.ultimategarden.client.platform.ui.hud;

import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.ui.screen.UltimateGardenScreen;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.HudConfig.HudId;
import java.util.*;

/** Temporary native input routing; never writes macro or focus state. */
public final class HudAdjustmentController {
    private static UltimateGardenScreen menu;
    private static Object world;
    private static double left, top, width, height, minWidth, minHeight, mouseX, mouseY;
    private static double startX, startY, startLeft, startTop, startWidth, startHeight;
    public record Layout(double x,double y,double width,double height,double minWidth,double minHeight) { }
    private static final Map<HudId,Layout> layouts=new EnumMap<>(HudId.class);
    private static HudId selected=HudId.FARM_MACRO;
    public static HudId selected(){return selected;}
    public static Layout selectedLayout(){return layouts.get(selected);}
    private static int drag;
    private static final long[] cursors = new long[6];
    private HudAdjustmentController() { }
    public static boolean isActive() { return menu != null; }
    public static void begin(UltimateGardenScreen screen) {
        if (isActive()) return;
        var c=Minecraft.getInstance(); menu=screen; world=c.level; drag=0; layouts.clear(); selected=HudId.FARM_MACRO;
        c.setScreen(null);
        freeCursor();
    }
    public static void freeCursor() {
        if (isActive()) GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
    }
    public static void tick() {
        if (!isActive()) return;
        var c=Minecraft.getInstance();
        if (c.level!=world || c.player==null || c.screen!=null) finish(false);
        else freeCursor();
    }
    public static void layout(double x, double y, double w, double h, double minW, double minH) {
        layout(HudId.FARM_MACRO,x,y,w,h,minW,minH);
    }
    public static void layout(HudId id,double x,double y,double w,double h,double minW,double minH) {
        layouts.put(id,new Layout(x,y,w,h,minW,minH));
        if(id==selected)activate(layouts.get(id));
    }
    private static void activate(Layout layout){
        left=layout.x();top=layout.y();width=layout.width();height=layout.height();minWidth=layout.minWidth();minHeight=layout.minHeight();
    }
    private static HudId hit(){
        var ids=HudId.values();for(int i=ids.length-1;i>=0;i--){var layout=layouts.get(ids[i]);if(layout==null)continue;activate(layout);if(edges()!=0)return ids[i];}return null;
    }
    public static void move(double x, double y) {
        var c=Minecraft.getInstance(); var win=c.getWindow();
        mouseX=x*win.getGuiScaledWidth()/win.getScreenWidth();
        mouseY=y*win.getGuiScaledHeight()/win.getScreenHeight();
        updateCursor();
        if (drag==0) return;
        double dx=mouseX-startX, dy=mouseY-startY;
        double l=startLeft, t=startTop, r=l+startWidth, b=t+startHeight;
        double sw=win.getGuiScaledWidth(), sh=win.getGuiScaledHeight();
        if (drag==16) { l=Math.clamp(l+dx,0,Math.max(0,sw-startWidth)); t=Math.clamp(t+dy,0,Math.max(0,sh-startHeight)); r=l+startWidth; b=t+startHeight; }
        else {
            if ((drag&1)!=0) l=Math.clamp(l+dx,0,Math.max(0,r-minWidth));
            if ((drag&2)!=0) r=Math.clamp(r+dx,Math.min(sw,l+minWidth),sw);
            if ((drag&4)!=0) t=Math.clamp(t+dy,0,Math.max(0,b-minHeight));
            if ((drag&8)!=0) b=Math.clamp(b+dy,Math.min(sh,t+minHeight),sh);
        }
        Ultimate_GardenClient.getConfigManager().getHudConfig().setBounds(selected,l/sw,t/sh,(r-l)/sw,(b-t)/sh);
        layout(selected,l,t,r-l,b-t,minWidth,minHeight);
    }
    private static int edges() {
        if (mouseX<left-5 || mouseX>left+width+5 || mouseY<top-5 || mouseY>top+height+5) return 0;
        int edges=0;
        if (Math.abs(mouseX-left)<=5) edges|=1;
        else if (Math.abs(mouseX-left-width)<=5) edges|=2;
        if (Math.abs(mouseY-top)<=5) edges|=4;
        else if (Math.abs(mouseY-top-height)<=5) edges|=8;
        return edges==0 ? 16 : edges;
    }
    private static void updateCursor() {
        HudId hover=drag==0?hit():selected;
        int edge=drag==0 ? hover==null?0:edges() : drag;
        int index = edge==0 ? 0 : edge==16 ? 1 : edge==1 || edge==2 ? 2 : edge==4 || edge==8 ? 3 : edge==5 || edge==10 ? 4 : 5;
        if (cursors[index]==0) cursors[index]=GLFW.glfwCreateStandardCursor(switch(index) {
            case 1 -> GLFW.GLFW_RESIZE_ALL_CURSOR;
            case 2 -> GLFW.GLFW_HRESIZE_CURSOR;
            case 3 -> GLFW.GLFW_VRESIZE_CURSOR;
            case 4 -> GLFW.GLFW_RESIZE_NWSE_CURSOR;
            case 5 -> GLFW.GLFW_RESIZE_NESW_CURSOR;
            default -> GLFW.GLFW_ARROW_CURSOR;
        });
        GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().handle(), cursors[index]);
    }
    public static void button(int button, int action) {
        if (button!=GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        if (action==GLFW.GLFW_RELEASE) { drag=0; return; }
        if (action!=GLFW.GLFW_PRESS) return;
        var id=hit();if(id==null){drag=0;return;}selected=id;activate(layouts.get(id));
        drag=edges();
        if (drag==0) return;
        startX=mouseX; startY=mouseY; startLeft=left; startTop=top; startWidth=width; startHeight=height;
    }
    public static void finish(boolean returnToMenu) {
        if (!isActive()) return;
        GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().handle(),0);
        for (int i=0;i<cursors.length;i++) if (cursors[i]!=0) { GLFW.glfwDestroyCursor(cursors[i]); cursors[i]=0; }
        var target=menu; menu=null; drag=0; world=null; layouts.clear();
        boolean saved=Ultimate_GardenClient.getConfigManager().save(ConfigManager.Module.HUD);
        target.finishHudAdjustment(returnToMenu, saved);
        if (returnToMenu) Minecraft.getInstance().setScreen(target);
        Ultimate_GardenClient.getWindowsFocusController().reapplyCursorMode();
    }
}
