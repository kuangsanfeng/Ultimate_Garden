
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim.ShootoutAimClock;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim.ShootoutTanhAim;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootoutConfig;


import static com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootoutConfig.Setting.*;
import com.google.gson.*;
import java.nio.file.*;

public final class AimRateRegression {
 static int checks;static void check(boolean value){checks++;if(!value)throw new AssertionError("Aim rate "+checks);}
 public static void main(String[] args)throws Exception{
  // Deterministic frame clocks: no sleep, no native display or network.
  for(int hz:new int[]{20,60,120})for(int fps:new int[]{15,30,60,120,144,240,1000}){
   var clock=new ShootoutAimClock();int updates=0;double elapsed=0;long previous=0;
   for(int f=0;f<=fps*10;f++){
    long now=Math.round(f*1e9/fps);double dt=clock.step(now,hz);
    if(dt>0){updates++;check(dt<=.05&&dt>0);check(dt<=(now-previous)/1e9+1e-9);previous=now;elapsed+=dt;}
    check(clock.step(now,hz)==0); // Another poll in this frame cannot catch up.
   }
   check(Math.abs(updates-10*Math.min(hz,fps))<=1);check(elapsed<=10+1e-8);
  }
  var clock=new ShootoutAimClock();check(clock.step(0,120)==0);check(clock.step(8_333_334,120)>0);
  check(clock.step(2_000_000_000,120)==.05);check(clock.step(2_000_000_000,120)==0);check(clock.step(2_001_000_000,120)==0);
  check(clock.step(2_010_000_000,60)==0);check(clock.step(2_020_000_000,60)==0);double dt=clock.step(2_026_666_667,60);check(Math.abs(dt-1./60)<1e-8);
  clock.reset();check(clock.step(20_000_000_000L,20)==0);check(clock.step(20_049_999_999L,20)==0);check(clock.step(20_050_000_000L,20)==.05);
  for(int hz:new int[]{20,60,120}){
   double interval=1./hz;double step=ShootoutTanhAim.step(30,500,30,interval);
   check(Math.abs(step/interval-380.7970779778824)<1e-9);check(step<=500*interval&&step<=30);
  }
  check(ShootoutTanhAim.step(30,500,30,1./120)<ShootoutTanhAim.step(30,500,30,1./60));check(ShootoutTanhAim.step(30,500,30,1./60)<ShootoutTanhAim.step(30,500,30,.05));
  var cfg=new ZombieShootoutConfig();check(cfg.get(AIM_UPDATE_RATE)==20);check(!cfg.load(JsonParser.parseString("{\"aimPeakSpeedDegPerSecond\":321,\"aimCompletionToleranceDeg\":2}").getAsJsonObject()));check(cfg.get(AIM_UPDATE_RATE)==20&&cfg.get(AIM_SPEED)==321);
  for(int hz:new int[]{20,60,120}){cfg.set(AIM_UPDATE_RATE,hz);var saved=cfg.toJson(new JsonObject());var restored=new ZombieShootoutConfig();check(!restored.load(saved));check(restored.get(AIM_UPDATE_RATE)==hz&&restored.get(AIM_SPEED)==321);}
  for(double invalid:new double[]{19,121,20.5,60.25,Double.NaN,Double.POSITIVE_INFINITY}){
   try{cfg.set(AIM_UPDATE_RATE,invalid);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
   var fresh=new ZombieShootoutConfig();var doc=new JsonObject();doc.addProperty("aimUpdateRateHz",invalid);check(fresh.load(doc));check(fresh.get(AIM_UPDATE_RATE)==20);
  }
  var dir=Files.createTempDirectory("ug-aim-rate-");var configs=new ConfigManager(dir);configs.getZombieShootoutConfig().set(AIM_UPDATE_RATE,120);check(configs.save(ConfigManager.Module.ZOMBIE_SHOOTOUT));check(new ConfigManager(dir).getZombieShootoutConfig().get(AIM_UPDATE_RATE)==120);
  var base=Path.of("src/client/java/com/kuangsanfeng/ultimategarden/client");String controller=Files.readString(base.resolve("macro/zombie_shoot/ZombieShootoutController.java"));
  String frame=controller.substring(controller.indexOf(" public void aimFrame()"),controller.indexOf(" private final Map<UUID,Target>"));
  for(String forbidden:new String[]{"model.solve(","observe();","mouse.click(",".send(","select();","aimReady("})check(!frame.contains(forbidden));
  for(String guard:new String[]{"c.isSameThread()","!running","!config().isAutoAimEnabled()","target!=current","!valid(target)","c.isPaused()","!c.isWindowActive()","!c.mouseHandler.isMouseGrabbed()"})check(frame.contains(guard));
  String tick=controller.substring(controller.indexOf(" private void tick()"),controller.indexOf(" private static net.minecraft.world.level.BlockGetter"));check(!tick.contains("mouse.aim("));check(tick.contains("requestFrameAim(solution)")&&tick.contains("requestFrameAim(fire)"));
  String mixin=Files.readString(base.resolve("mixin/ShootoutAimFrameMixin.java"));check(mixin.contains("method=\"runTick\"")&&mixin.contains("MouseHandler;handleAccumulatedMovement()V")&&mixin.contains("INSTANCE.aimFrame()"));
  String screen=Files.readString(base.resolve("platform/ui/adapters/menu/GardenMenuAdapter.java"));int i=screen.indexOf("Setting.AIM_UPDATE_RATE,0");check(i>screen.indexOf("Setting.AIM_CURVATURE,0"));check(screen.substring(screen.indexOf(';',i)+1).stripLeading().startsWith("divider();"));
  System.out.println("PASS: "+checks+" 20/60/120 Hz, high/low FPS, dropped deadlines, pause/rate changes, integer config and frame-only local input checks. Not Verified In-Game.");
 }
}
