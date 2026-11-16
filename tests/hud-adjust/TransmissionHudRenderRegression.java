import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.HudConfig;
import com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.instant_transmission.InstantTransmissionHud;
import com.kuangsanfeng.ultimategarden.client.platform.ui.hud.HudAdjustmentController;
import com.kuangsanfeng.ultimategarden.client.platform.ui.screen.UltimateGardenScreen;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.*;
import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import net.minecraft.client.Minecraft;import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.nio.file.*;import com.google.gson.*;
public class TransmissionHudRenderRegression {
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("Transmission render "+checks);}
 public static void main(String[] args)throws Exception{
  var root=Files.createTempDirectory("ug-hud-visible-");var config=new ConfigManager(root);Ultimate_GardenClient.configs=config;
  var hud=config.getHudConfig();check(hud.isShowInstantTransmissionHud());
  var renderer=new InstantTransmissionHud(config);var c=Minecraft.getInstance();
  hud.setShowHud(false);hud.setShowZombieShootoutHud(false);
  var g=new GuiGraphicsExtractor();renderer.render(g);check(g.backgrounds==1);check(g.title.equals("Instant Transmission")); // No test session required
  hud.setShowInstantTransmissionHud(false);g=new GuiGraphicsExtractor();renderer.render(g);check(g.backgrounds==0);
  hud.setShowInstantTransmissionHud(true);g=new GuiGraphicsExtractor();renderer.render(g);check(g.backgrounds==1);
  var controller=InstantTransmissionController.INSTANCE;
  controller.value=new TransmissionHudSnapshot(true,false,"WAIT_POSITION","MANUAL_STOP",null,42,570,3,10.,-35.,10.,-35.,false,10.,-35.,null,0,3,3,2,5L);
  g=new GuiGraphicsExtractor();renderer.render(g);check(g.backgrounds==1);check(g.values.contains("Stopped By User"));
  controller.value=controller.value.stopped("COMPLETED");g=new GuiGraphicsExtractor();renderer.render(g);check(g.backgrounds==1);check(g.values.contains("All Groups Completed"));
  var finalResult=controller.value;hud.setShowInstantTransmissionHud(false);g=new GuiGraphicsExtractor();renderer.render(g);check(g.backgrounds==0&&controller.value==finalResult);
  var bounds=new HudConfig.Bounds(.2,.3,.4,.5);hud.setBounds(HudConfig.HudId.INSTANT_TRANSMISSION,bounds.x(),bounds.y(),bounds.width(),bounds.height());
  var menu=new UltimateGardenScreen();c.screen=menu;HudAdjustmentController.begin(menu);
  g=new GuiGraphicsExtractor();renderer.render(g);check(g.backgrounds==1);check(!hud.isShowInstantTransmissionHud());
  HudAdjustmentController.finish(false);check(!hud.isShowInstantTransmissionHud());
  g=new GuiGraphicsExtractor();renderer.render(g);check(g.backgrounds==0);
  var reloaded=new ConfigManager(root).getHudConfig();check(!reloaded.isShowInstantTransmissionHud());check(reloaded.bounds(HudConfig.HudId.INSTANT_TRANSMISSION).equals(bounds));check(!reloaded.isShowHud()&&!reloaded.isShowZombieShootoutHud());
  hud.setShowInstantTransmissionHud(true);check(config.save(ConfigManager.Module.HUD));check(new ConfigManager(root).getHudConfig().isShowInstantTransmissionHud());
  for(boolean editing:new boolean[]{false,true})for(boolean enabled:new boolean[]{false,true}){hud.setShowInstantTransmissionHud(enabled);check(hud.showTransmission(editing)==(editing||enabled));}
  var legacy=new HudConfig();check(!legacy.load(new JsonObject()));check(legacy.isShowInstantTransmissionHud());
  var invalid=new JsonObject();invalid.addProperty("showInstantTransmissionHud","false");check(legacy.load(invalid));check(legacy.isShowInstantTransmissionHud());
  System.out.println("PASS: "+checks+" production Instant Transmission render visibility, preview, final-result and persistence checks; game/drawing boundaries stubbed.");
 }
}
