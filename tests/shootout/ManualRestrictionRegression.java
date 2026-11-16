
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim.ShootoutAimCompletion;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootoutConfig;
import com.kuangsanfeng.ultimategarden.client.platform.window.WindowsInputInterceptor;
import com.kuangsanfeng.ultimategarden.client.platform.window.WindowsState;



import com.google.gson.*;
import java.nio.file.*;

public final class ManualRestrictionRegression {
 static int checks;static void check(boolean value){checks++;if(!value)throw new AssertionError("Manual restriction "+checks);}
 public static void main(String[] args)throws Exception {
  var config=new ZombieShootoutConfig();check(config.isManualShotRestrictionEnabled());check(config.manualShotRestrictionEffective());
  for(boolean saved:new boolean[]{false,true})for(boolean aim:new boolean[]{false,true})for(boolean shoot:new boolean[]{false,true}){
   config.setManualShotRestrictionEnabled(saved);config.setAutoAimEnabled(aim);config.setAutoShootEnabled(shoot);
   check(config.manualShotRestrictionEditable()==(aim&&!shoot));check(config.manualShotRestrictionEffective()==(saved&&aim&&!shoot));check(config.isManualShotRestrictionEnabled()==saved);
   var copy=new ZombieShootoutConfig();check(!copy.load(config.toJson(new JsonObject())));check(copy.isManualShotRestrictionEnabled()==saved);check(copy.manualShotRestrictionEffective()==config.manualShotRestrictionEffective());
  }
  config.setAutoAimEnabled(true);config.setAutoShootEnabled(false);config.setManualShotRestrictionEnabled(true);
  check(config.manualShotRestrictionEffective());config.setAutoShootEnabled(true);check(!config.manualShotRestrictionEditable()&&!config.manualShotRestrictionEffective());check(config.isManualShotRestrictionEnabled());
  config.setAutoShootEnabled(false);check(config.manualShotRestrictionEditable()&&config.manualShotRestrictionEffective());
  config.setAutoAimEnabled(false);check(!config.manualShotRestrictionEditable()&&!config.manualShotRestrictionEffective()&&config.isManualShotRestrictionEnabled());config.setAutoAimEnabled(true);check(config.manualShotRestrictionEffective());
  var root=Files.createTempDirectory("ug-manual-restriction-");var configs=new ConfigManager(root);var cfg=configs.getZombieShootoutConfig();
  check(cfg.isManualShotRestrictionEnabled());cfg.setManualShotRestrictionEnabled(true);cfg.setAutoShootEnabled(false);check(configs.save(ConfigManager.Module.ZOMBIE_SHOOTOUT));
  var restored=new ConfigManager(root).getZombieShootoutConfig();check(restored.isManualShotRestrictionEnabled()&&restored.manualShotRestrictionEffective());
  restored.setAutoShootEnabled(true);var disabled=new ZombieShootoutConfig();check(!disabled.load(restored.toJson(new JsonObject())));check(disabled.isManualShotRestrictionEnabled()&&!disabled.manualShotRestrictionEffective());disabled.setAutoShootEnabled(false);check(disabled.manualShotRestrictionEffective());
  var legacy=new ZombieShootoutConfig();var old=JsonParser.parseString("{\"aimCompletionToleranceDeg\":2,\"shootToleranceRatio\":1.5,\"future\":true}").getAsJsonObject();check(!legacy.load(old));check(legacy.isManualShotRestrictionEnabled());check(legacy.toJson(old).get("future").getAsBoolean());
  for(JsonElement bad:new JsonElement[]{new JsonPrimitive("true"),new JsonPrimitive(1),JsonNull.INSTANCE}){var fresh=new ZombieShootoutConfig();var json=new JsonObject();json.add("manualShotRestrictionEnabled",bad);check(fresh.load(json));check(fresh.isManualShotRestrictionEnabled());}
  double tolerance=config.get(ZombieShootoutConfig.Setting.AIM_TOLERANCE)*config.get(ZombieShootoutConfig.Setting.SHOOT_RATIO);
  check(ShootoutAimCompletion.aligned(0,0,tolerance,0,tolerance));check(!ShootoutAimCompletion.aligned(0,0,tolerance+.001,0,tolerance));check(ShootoutAimCompletion.aligned(179,0,-179,0,tolerance)==(tolerance>=2));
  check(!ShootoutAimCompletion.aligned(0,0,Double.NaN,0,tolerance));check(ShootoutAimCompletion.aligned(0,90,120,90,0));
  config.set(ZombieShootoutConfig.Setting.SHOOT_RATIO,.25);check(!ShootoutAimCompletion.aligned(0,0,.6,0,config.get(ZombieShootoutConfig.Setting.AIM_TOLERANCE)*config.get(ZombieShootoutConfig.Setting.SHOOT_RATIO)));
  var windows=new WindowsState();var interceptor=new WindowsInputInterceptor(windows);interceptor.setExternalOwner(()->true);interceptor.setExternalMouseOwners(()->true,config::autoShootEffective);
  config.setAutoShootEnabled(false);check(!interceptor.blockMouseButton(1,1));config.setAutoShootEnabled(true);check(interceptor.blockMouseButton(1,1));check(!interceptor.blockMouseButton(1,0));config.setAutoShootEnabled(false);check(!interceptor.blockMouseButton(1,0));
  check(!ZombieShootoutController.INSTANCE.blockManualShot(1,1));check(!ZombieShootoutController.INSTANCE.blockManualShot(1,0));
  var base=Path.of("src/client/java/com/kuangsanfeng/ultimategarden/client");String controller=Files.readString(base.resolve("macro/zombie_shoot/ZombieShootoutController.java"));
  String gate=controller.substring(controller.indexOf(" public boolean blockManualShot"),controller.indexOf(" public void onManualButton"));
  check(gate.contains("action!=1||!running||!config().manualShotRestrictionEffective()"));check(gate.contains("c.screen!=null||c.getOverlay()!=null"));
  check(gate.contains("if(current==null)")&&gate.contains("if(!valid(current))")&&gate.contains("if(hudSolution==null)"));check(gate.contains("boolean admitted=shootAligned(c,hudSolution)")&&gate.contains("return !admitted"));
  check(!gate.contains("mouse.")&&!gate.contains("model.solve")&&!gate.contains("attempted("));
  String mixin=Files.readString(base.resolve("mixin/WindowsMouseMixin.java"));check(mixin.indexOf(".blockManualShot(")<mixin.indexOf(".onManualButton("));
  check(mixin.contains("boolean admitted=shootout$manualPressAdmitted;shootout$manualPressAdmitted=false"));check(mixin.contains("if(admitted&&window=="));
  String menu=Files.readString(base.resolve("platform/ui/adapters/menu/GardenMenuAdapter.java"));String row=menu.substring(menu.indexOf("toggle(\"ui.ultimategarden.shootout.manual_shot_restriction"),menu.indexOf("toggle(\"ui.ultimategarden.shootout.auto_start"));
  check(row.contains("automation::isManualShotRestrictionEnabled"));check(row.contains("automation.manualShotRestrictionEditable()"));check(row.contains("CollapsibleSection.NESTED_INDENT"));
  System.out.println("PASS: "+checks+" manual restriction parent transitions, saved value/persistence, tolerance boundary, release and pre-confirmation event wiring checks. Not Verified In-Game.");
 }
}
