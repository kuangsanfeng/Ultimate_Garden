
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim.ShootoutAimCompletion;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.shot.ShootoutArrowConfirmation;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.shot.ShootoutShotClock;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootoutConfig;


import static com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootoutConfig.Setting.*;
import net.minecraft.world.phys.Vec3;import java.util.*;import com.google.gson.*;
public final class ShotConfirmationRegression {
 static int checks;static final UUID PLAYER=UUID.randomUUID(),OTHER=UUID.randomUUID();
 static void check(boolean b){checks++;if(!b)throw new AssertionError("Confirmation check "+checks);}
 static long ns(long tick){return tick*50_000_000L;}
 static ShootoutArrowConfirmation fresh(){var c=new ShootoutArrowConfirmation();c.reset(PLAYER);return c;}
 static ShootoutArrowConfirmation.Attempt attempt(long tick,Vec3 direction){return new ShootoutArrowConfirmation.Attempt(tick,ns(tick),Vec3.ZERO,direction,6,0);}
 static ShootoutArrowConfirmation.Observation arrow(UUID id,int entity,UUID owner,long tick,Vec3 position,Vec3 velocity){return new ShootoutArrowConfirmation.Observation(id,entity,"minecraft:arrow",owner,position,velocity,tick,ns(tick));}
 public static void main(String[] args)throws Exception{
  for(double angle:new double[]{10,5.1})check(!ShootoutAimCompletion.aligned(0,0,angle,0,5));
  for(double angle:new double[]{5,2,0})check(ShootoutAimCompletion.aligned(0,0,angle,0,5));
  check(!ShootoutAimCompletion.aligned(0,0,3,0,2));check(ShootoutAimCompletion.aligned(179,0,-179,0,2));
  check(ShootoutAimCompletion.aligned(0,90,180,90,0));check(!ShootoutAimCompletion.aligned(0,89,0,-89,5));check(ShootoutAimCompletion.aligned(123,17,123,17,0));
  check(ShootoutAimCompletion.error(Double.NaN,0,0,0)==null);check(ShootoutAimCompletion.error(0,Double.POSITIVE_INFINITY,0,0)==null);check(ShootoutAimCompletion.error(0,91,0,0)==null);check(!ShootoutAimCompletion.aligned(0,0,0,0,Double.NaN));
  var z=new Vec3(0,0,1);var position=new Vec3(0,0,3);UUID old=UUID.randomUUID();
  var c=fresh();c.observe(arrow(old,1,PLAYER,0,position,z));c.attempted(attempt(1,z),.55,.1);check(c.pending());check(c.confirmation()==null);
  c.observe(arrow(old,99,PLAYER,2,position,z));check(c.confirmation()==null);
  UUID foreign=UUID.randomUUID();c.observe(arrow(foreign,2,OTHER,2,position,z));check(c.detection()==ShootoutArrowConfirmation.Detection.CONFIRMED);check(c.confirmation().arrow().uuid().equals(foreign));
  c=fresh();c.attempted(attempt(1,z),.55,.1);UUID unknown=UUID.randomUUID();c.observe(arrow(unknown,3,null,3,position,z));check(c.detection()==ShootoutArrowConfirmation.Detection.CONFIRMED);check(c.confirmation().arrow().uuid().equals(unknown));check(c.confirmation().confirmationTick()==3);
  c.observe(arrow(UUID.randomUUID(),4,PLAYER,4,position,z));check(c.confirmation().arrow().uuid().equals(unknown));check(!c.timedOut(ns(1000)));
  c.cancel();c.attempted(attempt(20,z),.55,.1);c.observe(arrow(unknown,3,PLAYER,21,position,z));check(c.confirmation()==null);check(c.pending());c.cancel();check(!c.pending());
  // Two paced attempts, second observed projectile: no fake success after attempt one.
  c=fresh();var clock=new ShootoutShotClock();int[] draws={0};clock.fired(ns(1),.55,0,.1,()->{draws[0]++;return .5;});c.attempted(attempt(1,z),.55,.1);
  for(int i=2;i<=12;i++){check(c.confirmation()==null);check(c.pending());check(!clock.ready(ns(i)));check(!c.timedOut(ns(i)));}
  check(clock.ready(ns(13)));clock.fired(ns(13),.55,0,.1,()->{draws[0]++;return .5;});c.attempted(attempt(13,z),.55,.1);UUID second=UUID.randomUUID();c.observe(arrow(second,8,PLAYER,16,position,z));check(c.confirmation()!=null);check(c.confirmation().attempt().tick()==13);check(c.confirmation().arrow().uuid().equals(second));check(draws[0]==2);check(!clock.ready(ns(16)));
  // A previously unseen UUID after the new attempt follows the simplified rule.
  c.cancel();c.attempted(attempt(17,z),.55,.1);c.observe(arrow(UUID.randomUUID(),9,PLAYER,18,position,z));check(c.detection()==ShootoutArrowConfirmation.Detection.CONFIRMED);check(c.confirmation()!=null);
  c=fresh();c.attempted(attempt(1,z),.55,.1);check(!c.timedOut(ns(100)));check(c.timedOut(ns(101)));c.observe(arrow(UUID.randomUUID(),1,PLAYER,102,position,z));check(c.confirmation()==null);
  c=fresh();c.attempted(attempt(1,z),.55,.1);c.observe(arrow(UUID.randomUUID(),1,PLAYER,2,new Vec3(0,0,130),z));check(c.confirmation()!=null);
  c=fresh();c.attempted(attempt(1,z),.55,.1);c.observe(arrow(UUID.randomUUID(),2,PLAYER,2,position,new Vec3(1,0,0)));check(c.confirmation()!=null);
  c=fresh();c.attempted(attempt(1,z),.55,.1);var wrongType=new ShootoutArrowConfirmation.Observation(UUID.randomUUID(),3,"minecraft:trident",PLAYER,position,z,2,ns(2));c.observe(wrongType);check(c.confirmation()==null);
  UUID delayed=UUID.randomUUID();c.observe(arrow(delayed,4,null,2,position,Vec3.ZERO));check(c.confirmation()!=null);
  var config=new ZombieShootoutConfig();check(config.get(AIM_TOLERANCE)==.5);check(config.get(SNAP)==15);check(!config.load(JsonParser.parseString("{\"aimPeakSpeedDegPerSecond\":600,\"unknown\":true}").getAsJsonObject()));check(config.get(AIM_SPEED)==600&&config.get(AIM_TOLERANCE)==.5);
  for(double value:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY,31}){try{config.set(AIM_TOLERANCE,value);throw new AssertionError();}catch(IllegalArgumentException expected){check(config.get(AIM_TOLERANCE)==.5);}}
  config.set(AIM_TOLERANCE,2);check(!ShootoutAimCompletion.aligned(0,0,3,0,config.get(AIM_TOLERANCE)));var json=config.toJson(JsonParser.parseString("{\"unknown\":true}").getAsJsonObject());var reload=new ZombieShootoutConfig();check(!reload.load(json));check(reload.get(AIM_TOLERANCE)==2);check(json.get("unknown").getAsBoolean());
  for(var setting:ZombieShootoutConfig.Setting.values())if(setting.window())check(config.get(setting)==.3);
  var directory=java.nio.file.Files.createTempDirectory("ug-tolerance-migration-");java.nio.file.Files.writeString(directory.resolve("zombie_shootout.json"),"{\"schemaVersion\":1,\"aimPeakSpeedDegPerSecond\":600,\"unknown\":true}");var manager=new ConfigManager(directory);check(manager.getZombieShootoutConfig().get(AIM_TOLERANCE)==.5);check(manager.getZombieShootoutConfig().get(AIM_SPEED)==600);var migrated=JsonParser.parseString(java.nio.file.Files.readString(directory.resolve("zombie_shootout.json"))).getAsJsonObject();check(migrated.get("aimCompletionToleranceDeg").getAsDouble()==.5);check(migrated.get("unknown").getAsBoolean());manager.getZombieShootoutConfig().set(AIM_TOLERANCE,2);check(manager.save(ConfigManager.Module.ZOMBIE_SHOOTOUT));check(new ConfigManager(directory).getZombieShootoutConfig().get(AIM_TOLERANCE)==2);
  System.out.println("PASS: "+checks+" actual-angle, paced retry, delayed arrow, owner-independent identity/UUID reload, timeout and tolerance migration checks. Not Verified In-Game.");
 }
}
