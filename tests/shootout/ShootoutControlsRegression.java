
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim.ShootoutAimCompletion;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim.ShootoutAimCorrection;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.model.ShootoutModel;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.model.ShootoutSpeed;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.targeting.ShootoutTargetLoss;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootoutConfig;
import com.kuangsanfeng.ultimategarden.client.platform.window.WindowsInputInterceptor;
import com.kuangsanfeng.ultimategarden.client.platform.window.WindowsState;



import static com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootoutConfig.Setting.*;
import com.google.gson.JsonObject;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;

public final class ShootoutControlsRegression {
 static int checks;static void check(boolean value){checks++;if(!value)throw new AssertionError("Shootout controls "+checks);}
 public static void main(String[] args)throws Exception{
  var loss=new ShootoutTargetLoss();check(!loss.expired(false,0));check(!loss.expired(false,99_999_999));check(loss.expired(false,100_000_000));
  loss.clear();check(!loss.expired(false,1_000_000_000));check(!loss.expired(true,1_050_000_000));check(!loss.expired(false,1_100_000_000));check(!loss.expired(false,1_199_999_999));check(loss.expired(false,1_200_000_000));
  var cfg=new ZombieShootoutConfig();check(cfg.isAutoAimEnabled()&&!cfg.isAutoShootEnabled()&&!cfg.autoShootEffective());check(cfg.get(SHOOT_RATIO)==1.5&&cfg.get(AIM_TOLERANCE)==.5);
  cfg.setAutoShootEnabled(true);
  int[] callbacks={0};cfg.setAutomationChangeListener(()->callbacks[0]++);
  cfg.setAutoAimEnabled(false);check(!cfg.autoShootEffective()&&cfg.isAutoShootEnabled());check(callbacks[0]==1);cfg.setAutoAimEnabled(false);check(callbacks[0]==1);
  cfg.setAutoAimEnabled(true);check(cfg.autoShootEffective());cfg.setAutoShootEnabled(false);check(!cfg.autoShootEffective()&&cfg.isAutoAimEnabled());
  var window=new WindowsState();var interceptor=new WindowsInputInterceptor(window);interceptor.setExternalOwner(()->true);interceptor.setExternalMouseOwners(cfg::isAutoAimEnabled,cfg::autoShootEffective);
  for(boolean aim:new boolean[]{false,true})for(boolean shoot:new boolean[]{false,true}){
   cfg.setAutoAimEnabled(aim);cfg.setAutoShootEnabled(shoot);check(interceptor.blockMouseMove()==aim);check(interceptor.blockMouseButton(1)==(aim&&shoot));check(interceptor.blockKeyboard(87));check(!interceptor.blockMouseButton(1,0));check(interceptor.blockMouseButton(1,1)==(aim&&shoot));
   window.setInputIntercepting(true);check(interceptor.blockMouseMove()&&interceptor.blockMouseButton(1));window.setInputIntercepting(false);
  }
  interceptor.setUiInputAllowed(()->true);check(!interceptor.blockMouseMove()&&!interceptor.blockMouseButton(1));
  for(double ratio:new double[]{0,.25,.5,1,1.5,2,999.99}){
   cfg.set(SHOOT_RATIO,ratio);check(cfg.get(SHOOT_RATIO)==ratio);
   check(ShootoutAimCompletion.aligned(179,0,-179,0,2*ratio)==(ratio>=1));
   check(ShootoutAimCompletion.aligned(0,0,0,0,2*ratio));
  }
  for(double bad:new double[]{-.01,1000,Double.NaN,Double.POSITIVE_INFINITY}){try{cfg.set(SHOOT_RATIO,bad);throw new AssertionError();}catch(IllegalArgumentException expected){check(cfg.get(SHOOT_RATIO)==999.99);}}
  cfg.setAutoAimEnabled(false);cfg.setAutoShootEnabled(true);var copy=new ZombieShootoutConfig();check(!copy.load(cfg.toJson(new JsonObject())));check(!copy.isAutoAimEnabled()&&copy.isAutoShootEnabled());check(copy.get(SHOOT_RATIO)==999.99);
  var root=Files.createTempDirectory("ug-controls-");var configs=new ConfigManager(root);var live=configs.getZombieShootoutConfig();live.setAutoAimEnabled(false);live.setAutoShootEnabled(true);live.set(SHOOT_RATIO,.25);check(configs.save(ConfigManager.Module.ZOMBIE_SHOOTOUT));var saved=new ConfigManager(root).getZombieShootoutConfig();check(!saved.isAutoAimEnabled()&&saved.isAutoShootEnabled()&&saved.get(SHOOT_RATIO)==.25&&saved.get(AIM_TOLERANCE)==.5);
  var correction=new ShootoutAimCorrection();check(!correction.ready(3.,2,.5,0,()->.8));check(correction.ready(.7,2,.5,1,()->{throw new AssertionError();}));check(!ShootoutAimCompletion.aligned(0,0,.7,0,.5));check(!correction.active());check(correction.ready(.4,2,.5,2,()->{throw new AssertionError();}));check(ShootoutAimCompletion.aligned(0,0,.4,0,.5));
  var model=ShootoutModel.load();String[] classes={"LEATHER_ADULT","LEATHER_BABY","IRON_ADULT","IRON_BABY","GOLD_ADULT","GOLD_BABY","DIAMOND_ADULT","DIAMOND_BABY"};double[] speeds={.118768,.166667,.135417,.187500,.149144,.208307,.164702,.230583};
  for(int i=0;i<classes.length;i++){
   double initial=model.speeds.get(classes[i]);check(initial==speeds[i]);var estimator=new ShootoutSpeed();check(estimator.observe(0,"A",0,initial,6).speedMagnitude()==initial);
   for(int t=1;t<=6;t++){var e=estimator.observe(t,"A",t*.1,initial,6);double expected=t<6?(initial+t*.1)/(t+1):.1;check(Math.abs(e.speedMagnitude()-expected)<1e-10);}
   for(int t=7;t<=13;t++)estimator.observe(t,"A",.6,initial,6);check(estimator.measuredSpeed()==0);
  }
  var lamp=new Vec3(8,2,17);for(int ping:new int[]{0,50,100,200}){var solution=model.solve(Vec3.ZERO,t->lamp);check(solution!=null);check(model.arrow(Vec3.ZERO,solution.yaw(),solution.pitch(),solution.ticks()).distanceTo(lamp)<1e-6);check(solution.pitch()< -Math.toDegrees(Math.atan2(lamp.y,Math.hypot(lamp.x,lamp.z))));}
  String controller=Files.readString(Path.of("src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java"));
  check(controller.contains("flight->t.lamp!=null?initial:aimPoint(t,wait+latency+flight)"));check(controller.contains("flight->firingTarget.lamp!=null?initial:aimPoint(firingTarget,1+latency+flight)"));
  check(controller.contains("if(!config().isAutoAimEnabled()){")&&controller.contains("\"AUTO_AIM_DISABLED\""));check(controller.contains("if(!config().autoShootEffective()){")&&controller.contains("!shootAligned(c,solution)"));check(controller.contains("config().get(AIM_TOLERANCE)*config().get(SHOOT_RATIO)"));
  System.out.println("PASS: "+checks+" target-loss timing, control/input matrix, independent thresholds, persistence, eight speeds and stationary gravity-compensated lamp checks. Not Verified In-Game.");
 }
}
