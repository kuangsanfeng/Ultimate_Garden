import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.*;
import com.kuangsanfeng.ultimategarden.client.config.*;
import static com.kuangsanfeng.ultimategarden.client.config.ZombieShootoutConfig.Setting.*;
import com.google.gson.*;
import java.nio.file.*;

public final class TanhAimRegression {
 static int checks;static void check(boolean ok){checks++;if(!ok)throw new AssertionError("Tanh aim "+checks);}
 public static void main(String[] args)throws Exception{
  double step=ShootoutTanhAim.step(30,500,30,.05);check(Math.abs(step/.05-380.7970779778824)<1e-9);check(Math.abs(step-19.03985389889412)<1e-10);
  check(ShootoutTanhAim.step(0,500,30,.05)==0);check(ShootoutTanhAim.step(30,500,30,0)==0);
  check(ShootoutTanhAim.step(30,500,30,100)==step);
  for(double error:new double[]{1e-8,.001,.1,1,5,30,90,180})for(double curvature:new double[]{Double.MIN_VALUE,.1,1,30,100,Double.MAX_VALUE}){
   double s=ShootoutTanhAim.step(error,500,curvature,.05);check(Double.isFinite(s)&&s>=0&&s<=error&&s<=25);
  }
  check(ShootoutTanhAim.step(1,500,30,.05)<ShootoutTanhAim.step(1,500,15,.05));
  for(double bad:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}){
   try{ShootoutTanhAim.step(30,500,bad,.05);throw new AssertionError();}catch(IllegalArgumentException expected){check(true);}
  }
  double[][] views={{0,0,30,0},{179,0,-179,0},{-179,0,179,0},{0,0,180,0},{0,90,0,-90},
    {45,89,-135,89},{0,-89,180,-89},{170,50,-170,-40},{0,0,90,30},{0,0,0,90},{20,90,150,90}};
  for(var v:views)for(double dt:new double[]{0,.001,.01,.05,1}){
   var r=ShootoutTanhAim.rotation(v[0],v[1],v[2],v[3],500,30,dt);
   double y=v[0]+r.yawDelta(),p=v[1]+r.pitchDelta();check(Double.isFinite(y)&&Double.isFinite(p)&&p>=-90&&p<=90);
   double moved=ShootoutAimCompletion.error(v[0],v[1],y,p),remaining=ShootoutAimCompletion.error(y,p,v[2],v[3]);
   check(moved<=r.step()+1e-5);check(remaining<=r.error()+1e-5);
   if(r.step()>1e-5)check(Math.abs(moved-r.step())<1e-5);
  }
  var config=new ZombieShootoutConfig();check(config.get(AIM_CURVATURE)==15);check(config.get(SNAP)==15);check(config.get(AIM_TOLERANCE)==.5);
  var old=JsonParser.parseString("{\"snapAngleDeg\":7,\"aimPeakSpeedDegPerSecond\":777,\"aimCompletionToleranceDeg\":2,\"future\":true}").getAsJsonObject();
  check(!config.load(old));check(config.get(AIM_CURVATURE)==15&&config.get(SNAP)==7&&config.get(AIM_SPEED)==777);
  for(double c:new double[]{Double.MIN_VALUE,.01,30,Double.MAX_VALUE}){config.set(AIM_CURVATURE,c);check(config.get(AIM_CURVATURE)==c);}
  for(double c:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}){try{config.set(AIM_CURVATURE,c);throw new AssertionError();}catch(IllegalArgumentException expected){check(config.get(AIM_CURVATURE)==Double.MAX_VALUE);}}
  config.set(AIM_CURVATURE,42.5);var saved=config.toJson(old);var restored=new ZombieShootoutConfig();check(!restored.load(saved));check(restored.get(AIM_CURVATURE)==42.5&&restored.get(SNAP)==7&&restored.get(AIM_SPEED)==777&&saved.get("future").getAsBoolean());
  var dir=Files.createTempDirectory("ug-tanh-");var configs=new ConfigManager(dir);configs.getZombieShootoutConfig().set(AIM_CURVATURE,42.5);check(configs.save(ConfigManager.Module.ZOMBIE_SHOOTOUT));check(new ConfigManager(dir).getZombieShootoutConfig().get(AIM_CURVATURE)==42.5);
  var base=Path.of("src/client/java/com/kuangsanfeng/ultimategarden/client");String controller=Files.readString(base.resolve("macro/zombie_shoot/ZombieShootoutController.java"));
  check(!controller.contains("get(SNAP)"));check(controller.contains("config().get(AIM_SPEED),config().get(AIM_CURVATURE)"));
  String screen=Files.readString(base.resolve("ui/screen/UltimateGardenScreen.java"));check(!screen.substring(screen.indexOf("private void shootoutSettings()"),screen.indexOf("private void shootoutInput(")).contains("Setting.SNAP"));
  check(AIM_CURVATURE.ordinal()==AIM_SPEED.ordinal()+1);
  System.out.println("PASS: "+checks+" Tanh numeric speed, whole-view rotation/budget, wrap/poles, curvature validation, legacy Snap persistence and menu wiring checks. Not Verified In-Game.");
 }
}
