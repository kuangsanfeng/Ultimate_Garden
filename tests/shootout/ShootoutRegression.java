
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim.ShootoutTanhAim;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.model.ShootoutModel;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.model.ShootoutSpeed;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.shot.ShootoutShotClock;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.targeting.ShootoutPolicy;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootoutConfig;


import static com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootoutConfig.Setting.*;
import com.google.gson.*;import net.minecraft.world.phys.Vec3;import java.nio.file.*;import java.util.*;
public final class ShootoutRegression {
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("Shootout check "+checks);}static void close(double a,double b){check(Math.abs(a-b)<1e-6);}
 public static void main(String[] args)throws Exception {
  var m=ShootoutModel.load();check(m.paths.get(0).points.length==141);check(m.paths.get(1).points.length==151);check(m.speeds.size()==8);
  for(var path:m.paths){for(int i=0;i<=100;i++){double s=path.length*i/100;var point=path.at(s);var projection=path.project(point);close(projection.residual(),0);close(projection.s(),s);check(path.tangent(s).length()>.99);close(ShootoutModel.reflect(s,path.length),s);close(ShootoutModel.reflect(2*path.length-s,path.length),s);}close(ShootoutModel.reflect(-1,path.length),1);close(ShootoutModel.reflect(path.length+1,path.length),path.length-1);}
  check(m.match(new Vec3(100,100,100))==null);check(m.match(m.paths.get(0).at(15))!=null);
  for(double yaw:new double[]{-180,-90,-45,0,45,90,179})for(double pitch:new double[]{-35,0,25}){var eye=new Vec3(0,1.62,0);var pos=eye;var velocity=m.velocity(yaw,pitch);for(int i=1;i<25;i++){pos=pos.add(velocity);velocity=velocity.scale(m.drag).add(0,-m.gravity,0);check(pos.distanceTo(m.arrow(eye,yaw,pitch,i))<1e-8);}}
  check(m.velocity(0,0).z>0);check(m.velocity(90,0).x<0);check(m.velocity(0,30).y<0);close(ShootoutModel.wrap(180),-180);close(ShootoutModel.wrap(-181),179);
  for(var path:m.paths)for(double speed:m.speeds.values())for(double sign:new double[]{-1,0,1})for(double ping:new double[]{0,50,100,200})for(double wait:new double[]{0,5,13}){var eye=new Vec3(0,1.62,0);double delay=wait+ping/50;java.util.function.DoubleFunction<Vec3> target=t->path.at(ShootoutModel.reflect(path.length*.55+sign*speed*(delay+t),path.length)).add(0,.8,0);var solution=m.solve(eye,target);check(solution!=null);check(m.arrow(eye,solution.yaw(),solution.pitch(),solution.ticks()).distanceTo(target.apply(solution.ticks()))<1e-6);}
  check(m.solve(Vec3.ZERO,t->new Vec3(10000,10000,10000))==null);
  var speed=new ShootoutSpeed();var estimate=speed.observe(0,"A",0,.2,6);check(!estimate.reliable());close(estimate.speedMagnitude(),.2);check(estimate.direction()==ShootoutSpeed.Direction.UNKNOWN);for(int i=1;i<=6;i++){estimate=speed.observe(i,"A",i*.1,.2,6);close(estimate.signedSpeed(),i<6?(i*.1+.2)/(i+1):.1);}for(int i=7;i<=12;i++)estimate=speed.observe(i,"A",.6,.2,6);close(estimate.signedSpeed(),0);check(estimate.reliable());var gap=speed.observe(14,"A",20,.2,6);check(!gap.reliable());close(gap.speedMagnitude(),0);check(!speed.observe(15,"B",20,.2,6).reliable());
  var reversal=new ShootoutSpeed();reversal.observe(0,"A",0,.2,6);reversal.observe(1,"A",.1,.2,6);var reverse=reversal.observe(2,"A",0,.2,6);close(reverse.speedMagnitude(),(.2+.1+.1)/3);check(reverse.direction()==ShootoutSpeed.Direction.BACKWARD);check(reverse.signedSpeed()<0);
  var one=new ShootoutSpeed();one.observe(0,"A",0,.2,1);var oneTick=one.observe(1,"A",.1,.2,1);check(oneTick.reliable());close(oneTick.speedMagnitude(),.1);var expanded=one.observe(2,"A",.2,.2,60);close(expanded.speedMagnitude(),.1);check(!expanded.reliable());
  for(String material:List.of("leather","iron","golden","diamond"))for(boolean baby:new boolean[]{true,false}){var type=ZombieShootoutController.classify("minecraft:"+material+"_helmet","minecraft:"+material+"_chestplate",baby);check(!type.equals("UNKNOWN"));check(m.speeds.containsKey(type));}
  check(ZombieShootoutController.classify("minecraft:leather_helmet","minecraft:iron_chestplate",false).equals("UNKNOWN"));
  check(ZombieShootoutController.classify("minecraft:air","minecraft:iron_chestplate",false).equals("UNKNOWN"));
  check(ZombieShootoutController.classify("minecraft:iron_helmet","minecraft:air",false).equals("UNKNOWN"));
  for(double error:new double[]{.01,5,15,16,90,180})for(double peak:new double[]{10,500,1000})for(double curvature:new double[]{.1,15,30,100}){
   double step=ShootoutTanhAim.step(error,peak,curvature,.05);check(step<=peak*.05+1e-10&&step<=error+1e-10&&step>=0);
  }
  for(int i=0;i<ShootoutPolicy.ORDER.size();i++)check(ShootoutPolicy.priority(ShootoutPolicy.ORDER.get(i))==i);check(ShootoutPolicy.metadata("DIAMOND_BABY").points()==240);check(ShootoutPolicy.metadata("LAMP").lifetimeSeconds()==6);
  var clock=new ShootoutShotClock();int[] draws={0};clock.fired(1_000_000_000L,.55,0,.1,()->{draws[0]++;return .5;});check(draws[0]==1);check(!clock.ready(1_599_999_999L));check(clock.ready(1_600_000_000L));close(clock.remainingTicks(1_100_000_000L),10);try{clock.fired(1_200_000_000L,.55,0,.1,()->{draws[0]++;return 0;});throw new AssertionError();}catch(IllegalStateException expected){check(draws[0]==1);}
  for(double dt:new double[]{.00001,.001,.01,.05})for(double error:new double[]{2,20,90}){double amount=ShootoutTanhAim.step(error,500,30,dt);check(amount>0);check(amount<=500*dt+1e-9);}
  for(int retry=0;retry<3;retry++)for(boolean queued:new boolean[]{true,false})for(boolean same:new boolean[]{true,false})for(boolean alive:new boolean[]{true,false})check(ShootoutPolicy.retryAllowed(retry,queued,same,alive)==(retry==0&&!queued&&same&&alive));
  Path root=Files.createTempDirectory("ug-shootout-config-");var configs=new ConfigManager(root);var c=configs.getZombieShootoutConfig();close(c.get(COOLDOWN),.55);close(c.get(JITTER_MAX),.1);for(var s:ZombieShootoutConfig.Setting.values())if(s.window())close(c.get(s),.3);
  c.set(LEATHER_ADULT,.45);c.set(AIM_SPEED,600);check(configs.save(ConfigManager.Module.ZOMBIE_SHOOTOUT));var loaded=new ConfigManager(root).getZombieShootoutConfig();close(loaded.get(AIM_SPEED),600);close(loaded.get(LEATHER_ADULT),.45);close(loaded.get(IRON_BABY),.3);
  try{c.set(JITTER_MIN,.5);throw new AssertionError();}catch(IllegalArgumentException expected){close(c.get(JITTER_MIN),0);}try{c.set(AIM_SPEED,Double.NaN);throw new AssertionError();}catch(IllegalArgumentException expected){close(c.get(AIM_SPEED),600);}
  var json=JsonParser.parseString(Files.readString(root.resolve("zombie_shootout.json"))).getAsJsonObject();check(!json.has("enabled"));json.addProperty("unknown",true);json.addProperty("enabled",true);Files.writeString(root.resolve("zombie_shootout.json"),json.toString());new ConfigManager(root);var saved=JsonParser.parseString(Files.readString(root.resolve("zombie_shootout.json"))).getAsJsonObject();check(saved.get("unknown").getAsBoolean());check(!saved.has("enabled"));
  System.out.println("PASS: "+checks+" resource, projection, ballistic back-substitution, speed, classification and configuration checks. Not Verified In-Game.");
 }
}
