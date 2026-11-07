import java.nio.file.*;
import com.kuangsanfeng.ultimategarden.client.config.*;
import com.kuangsanfeng.ultimategarden.client.ui.hud.HudAdjustmentController;
import com.kuangsanfeng.ultimategarden.client.ui.screen.UltimateGardenScreen;
import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import net.minecraft.client.Minecraft;
public final class HudAdjustmentRegression {
 static int checks;
 static void check(boolean v){checks++;if(!v)throw new AssertionError("HUD adjustment check "+checks);}
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("ug-hud-adjust-");
  var configs=new ConfigManager(root); Ultimate_GardenClient.configs=configs;
  configs.getHudConfig().setShowHud(false);
  var c=Minecraft.getInstance();
  // Four sides, four corners, interior move; execute production controller code.
  for(int[] point:new int[][]{{100,200},{400,200},{250,100},{250,300},{100,100},{400,100},{100,300},{400,300},{250,200}}){
   var menu=new UltimateGardenScreen();c.screen=menu;
   HudAdjustmentController.begin(menu);check(c.screen==null);check(HudAdjustmentController.isActive());
   HudAdjustmentController.layout(100,100,300,200,150,100);
   HudAdjustmentController.move(point[0],point[1]); HudAdjustmentController.button(0,1);
   HudAdjustmentController.move(point[0]+30,point[1]+20);HudAdjustmentController.button(0,0);
   var h=configs.getHudConfig();check(h.width()>=.15);check(h.height()>=100.0/700);check(h.x()>=0&&h.y()>=0);
   check(h.x()!=.1 || h.y()!=100.0/700 || h.width()!=.3 || h.height()!=200.0/700);
   HudAdjustmentController.finish(true);check(!HudAdjustmentController.isActive());check(c.screen==menu&&menu.returned&&menu.saved);
   var loaded=new ConfigManager(root).getHudConfig();check(loaded.x()==h.x()&&loaded.y()==h.y()&&loaded.width()==h.width()&&loaded.height()==h.height());
   check(org.lwjgl.glfw.GLFW.live==0);
   check(!h.isShowHud() && !loaded.isShowHud());
  }
  var menu=new UltimateGardenScreen(); c.screen=menu; HudAdjustmentController.begin(menu);
  HudAdjustmentController.layout(100,100,300,200,150,100);
  HudAdjustmentController.move(100,100);HudAdjustmentController.button(0,1);HudAdjustmentController.move(10000,10000);
  check(configs.getHudConfig().width()>=.15);check(configs.getHudConfig().height()>=100.0/700);
  c.level=new Object();HudAdjustmentController.tick();check(!HudAdjustmentController.isActive());check(!menu.returned);
  System.out.println("PASS: "+checks+" production HUD drag/resize, bounds, save/reload, cursor cleanup and world-change checks; native boundaries are stubbed.");
 }
}