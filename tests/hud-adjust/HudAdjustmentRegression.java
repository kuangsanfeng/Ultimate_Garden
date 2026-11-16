
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.HudConfig;
import java.nio.file.*;

import com.kuangsanfeng.ultimategarden.client.platform.ui.hud.HudAdjustmentController;
import com.kuangsanfeng.ultimategarden.client.platform.ui.screen.UltimateGardenScreen;
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
  // Both HUDs use the same controller and keep independent persisted geometry.
  var hud=configs.getHudConfig();
  hud.setShowHud(false);hud.setShowZombieShootoutHud(false);
  hud.setFarmOnlyWhileRunning(true);hud.setZombieOnlyWhileRunning(true);
  hud.setBounds(.05,.05,.2,.2);
  hud.setBounds(HudConfig.HudId.ZOMBIE_SHOOTOUT,.5,.1,.25,.4);
  var farmBefore=hud.bounds(HudConfig.HudId.FARM_MACRO);
  menu=new UltimateGardenScreen();c.screen=menu;HudAdjustmentController.begin(menu);
  HudAdjustmentController.layout(HudConfig.HudId.FARM_MACRO,50,35,200,140,100,70);
  HudAdjustmentController.layout(HudConfig.HudId.ZOMBIE_SHOOTOUT,500,70,250,280,125,140);
  HudAdjustmentController.move(600,200);HudAdjustmentController.button(0,1);
  check(HudAdjustmentController.selected()==HudConfig.HudId.ZOMBIE_SHOOTOUT);
  HudAdjustmentController.move(640,230);HudAdjustmentController.button(0,0);
  var zombieMoved=hud.bounds(HudConfig.HudId.ZOMBIE_SHOOTOUT);
  check(zombieMoved.x()==.54 && zombieMoved.y()==100.0/700);
  check(hud.bounds(HudConfig.HudId.FARM_MACRO).equals(farmBefore));
  // Resize the zombie corner, then move the farm without changing the zombie.
  HudAdjustmentController.move(790,380);HudAdjustmentController.button(0,1);
  HudAdjustmentController.move(820,410);HudAdjustmentController.button(0,0);
  var zombieResized=hud.bounds(HudConfig.HudId.ZOMBIE_SHOOTOUT);
  check(zombieResized.width()>.25 && zombieResized.height()>.4);
  check(hud.bounds(HudConfig.HudId.FARM_MACRO).equals(farmBefore));
  HudAdjustmentController.move(150,100);HudAdjustmentController.button(0,1);
  check(HudAdjustmentController.selected()==HudConfig.HudId.FARM_MACRO);
  HudAdjustmentController.move(180,120);HudAdjustmentController.button(0,0);
  check(!hud.bounds(HudConfig.HudId.FARM_MACRO).equals(farmBefore));
  check(hud.bounds(HudConfig.HudId.ZOMBIE_SHOOTOUT).equals(zombieResized));
  HudAdjustmentController.finish(true);
  var restored=new ConfigManager(root).getHudConfig();
  for(var id:HudConfig.HudId.values())check(restored.bounds(id).equals(hud.bounds(id)));
  check(!restored.isShowHud()&&!restored.isShowZombieShootoutHud());
  check(restored.isFarmOnlyWhileRunning()&&restored.isZombieOnlyWhileRunning());
  // Changing GUI resolution changes screen geometry, never the stored normalized bounds.
  c.window.w=500;c.window.h=350;c.screen=menu;HudAdjustmentController.begin(menu);
  HudAdjustmentController.layout(HudConfig.HudId.FARM_MACRO,hud.x()*500,hud.y()*350,hud.width()*500,hud.height()*350,50,35);
  HudAdjustmentController.layout(HudConfig.HudId.ZOMBIE_SHOOTOUT,zombieResized.x()*500,zombieResized.y()*350,zombieResized.width()*500,zombieResized.height()*350,62.5,70);
  HudAdjustmentController.finish(true);
  check(new ConfigManager(root).getHudConfig().bounds(HudConfig.HudId.ZOMBIE_SHOOTOUT).equals(zombieResized));
  var legacy=new HudConfig();var old=new com.google.gson.JsonObject();var oldBounds=new com.google.gson.JsonObject();
  oldBounds.addProperty("x",.12);oldBounds.addProperty("y",.23);oldBounds.addProperty("width",.34);oldBounds.addProperty("height",.45);old.add("bounds",oldBounds);old.addProperty("showHud",false);
  check(!legacy.load(old));check(legacy.x()==.12&&legacy.y()==.23&&legacy.width()==.34&&legacy.height()==.45&&!legacy.isShowHud());
  check(legacy.bounds(HudConfig.HudId.ZOMBIE_SHOOTOUT).width()==0);
  legacy.setBounds(HudConfig.HudId.ZOMBIE_SHOOTOUT,.2,.3,.4,.5);
  var saved=legacy.toJson(old);var copied=new HudConfig();check(!copied.load(saved));
  for(var id:HudConfig.HudId.values())check(copied.bounds(id).equals(legacy.bounds(id)));
  for(double invalid:new double[]{-1,2,Double.NaN,Double.POSITIVE_INFINITY}){
   try{legacy.setBounds(HudConfig.HudId.ZOMBIE_SHOOTOUT,invalid,0,.2,.2);throw new AssertionError();}
   catch(IllegalArgumentException expected){check(legacy.bounds(HudConfig.HudId.ZOMBIE_SHOOTOUT).x()==.2);}
  }
  System.out.println("PASS: "+checks+" production HUD drag/resize, bounds, save/reload, cursor cleanup and world-change checks; native boundaries are stubbed.");
 }
}