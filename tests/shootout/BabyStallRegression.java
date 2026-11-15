import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.*;
import com.kuangsanfeng.ultimategarden.client.config.ZombieShootoutConfig;
import static com.kuangsanfeng.ultimategarden.client.config.ZombieShootoutConfig.Setting.*;
import com.google.gson.*;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;

public final class BabyStallRegression {
 static int checks;static void check(boolean value){checks++;if(!value)throw new AssertionError("Baby stall/empty selection "+checks);}
 static void sample(ShootoutBabyStall s,long tick,double x,double z){s.observe(tick,tick*50_000_000,x,z,.2,.4,.5);}
 static final class Candidate {boolean handled,retry,retried,valid=true,solution=true;}
 static Candidate choose(List<Candidate> targets){return ShootoutCandidateSelection.first(targets,t->(!t.handled||t.retry)&&t.valid,t->t.solution);}
 public static void main(String[] args)throws Exception{
  var s=new ShootoutBabyStall();
  for(int t=0;t<8;t++){sample(s,t,0,0);check(!s.ready()&&!s.stalled());}
  sample(s,8,0,0);check(s.ready()&&s.stalled()); // Actual zero displacement is valid, regardless of head rotation.
  for(int t=9;t<=10;t++)sample(s,t,(t-8)*.2,0);check(s.stalled());
  for(int t=11;t<=13;t++)sample(s,t,(t-8)*.2,0);check(!s.stalled());
  // A normal reversal returns to its starting point but has nonzero accumulated distance.
  s.clear();for(int t=0;t<=8;t++)sample(s,t,(t<=4?t:8-t)*.2,0);check(s.ready()&&!s.stalled());
  // Z motion and negative coordinates count; every entity has independent memory.
  var other=new ShootoutBabyStall();for(int t=0;t<=8;t++)sample(other,t,-10,-20);check(other.stalled());
  s.clear();for(int t=0;t<=8;t++)sample(s,t,-10,-20+t*.2);check(!s.stalled()&&other.stalled());
  // Exact equality preserves either previous state (binary-exact distances and speed).
  s.clear();for(int t=0;t<=8;t++)s.observe(t,t*50_000_000,0,0,.125,.4,.5);check(s.stalled());
  for(int t=9;t<=16;t++)s.observe(t,t*50_000_000,(t-8)*.0625,0,.125,.4,.5);check(s.stalled());
  s.clear();for(int t=0;t<=8;t++)s.observe(t,t*50_000_000,t*.0625,0,.125,.4,.5);check(s.ready()&&!s.stalled());
  // Missing ticks, teleport, long pauses and nonfinite observations require a fresh complete window.
  for(int anomaly=0;anomaly<4;anomaly++){
   s.clear();for(int t=0;t<=8;t++)sample(s,t,0,0);check(s.stalled());
   long first=anomaly==0?10:9,base=first*50_000_000+(anomaly==2?500_000_000:0);double x=anomaly==1?5:0;
   s.observe(first,base,anomaly==3?Double.NaN:x,0,.2,.4,.5);check(!s.ready()&&!s.stalled());
   for(int i=1;i<=8;i++){s.observe(first+i,base+i*50_000_000,x,0,.2,.4,.5);check(i==8&&anomaly!=3?s.stalled():!s.stalled());}
   s.observe(first+9,base+450_000_000,x,0,.2,.4,.5);check(s.stalled());
  }
  // Same-tick rescans cannot erase continuous history or add fake movement.
  s.clear();for(int t=0;t<=8;t++){sample(s,t,0,0);sample(s,t,.9,.9);}check(s.stalled());
  // Nonuniform monotonic intervals cover a physical .4-second window with boundary interpolation.
  s.clear();for(int t=0;t<=10;t++)s.observe(t,t*47_000_000,-10,0,.2,.4,.5);check(s.ready()&&s.stalled());
  s.clear();for(int t=0;t<=6;t++)s.observe(t,t*70_000_000,t*.1365,0,.2,.4,.5);check(s.ready()&&s.stalled()); // .78 in-window, not .819 over the whole boundary segment.
  // Four material speeds come exclusively from the resource; 0.4s means eight reference ticks.
  var model=ShootoutModel.load();for(String type:List.of("LEATHER_BABY","IRON_BABY","GOLD_BABY","DIAMOND_BABY")){
   double v=model.speeds.get(type);s.clear();for(int t=0;t<=8;t++)s.observe(t,t*50_000_000,t*v,0,v,.4,.5);check(!s.stalled());
   s.clear();for(int t=0;t<=8;t++)s.observe(t,t*50_000_000,1,2,v,.4,.5);check(s.stalled());
  }
  // Static baby target still uses the existing gravity/drag solver.
  var eye=new Vec3(0,1.62,0);var point=new Vec3(4,.85,15);var solution=model.solve(eye,t->point);
  check(solution!=null&&solution.target().equals(point));check(model.arrow(eye,solution.yaw(),solution.pitch(),solution.ticks()).distanceTo(point)<1e-6);
  check(solution.pitch()<-Math.toDegrees(Math.atan2(point.y-eye.y,Math.hypot(point.x,point.z))));
  var cfg=new ZombieShootoutConfig();check(cfg.get(BABY_STALL_WINDOW)==.4&&cfg.get(BABY_STALL_RATIO)==.5);check(!BABY_STALL_WINDOW.window()&&!BABY_STALL_RATIO.window());
  cfg.load(JsonParser.parseString("{\"aimCompletionToleranceDeg\":2,\"babyZombieStallWindowSeconds\":0.6,\"babyZombieStallThresholdRatio\":0.25}").getAsJsonObject());
  var reloaded=new ZombieShootoutConfig();reloaded.load(cfg.toJson(new JsonObject()));check(reloaded.get(BABY_STALL_WINDOW)==.6&&reloaded.get(BABY_STALL_RATIO)==.25&&reloaded.get(AIM_TOLERANCE)==2);
  for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1})for(var setting:List.of(BABY_STALL_WINDOW,BABY_STALL_RATIO)){try{cfg.set(setting,bad);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}}
  // Changing only the window needs fresh coverage; changing ratio reevaluates the same physical observations.
  s.clear();for(int t=0;t<=8;t++)sample(s,t,0,0);s.observe(9,450_000_000,0,0,.2,.6,.5);check(!s.ready());s.clear();check(!s.ready()&&!s.stalled());
  for(int t=0;t<=8;t++)s.observe(t,t*50_000_000,t*.075,0,.2,.4,.5);check(s.stalled());
  s.observe(9,450_000_000,.675,0,.2,.4,.25);check(s.ready()&&!s.stalled());
  for(int t=10;t<10000;t++)s.observe(t,t*50_000_000,.675,0,.2,.4,.25);
  var field=ShootoutBabyStall.class.getDeclaredField("samples");field.setAccessible(true);check(((Collection<?>)field.get(s)).size()<=256);
  // Last retry misses, but its still-alive handled record cannot block a newly scanned candidate.
  var exhausted=new Candidate();exhausted.handled=true;exhausted.retried=true;var records=new ArrayList<Candidate>();records.add(exhausted);
  var next=new Candidate();int[] scans={0};
  var chosen=ShootoutCandidateSelection.firstOrRescan(()->choose(records),()->{scans[0]++;records.add(next);});check(chosen==next&&scans[0]==1);check(exhausted.handled&&exhausted.retried&&!exhausted.retry);
  int[] unused={0};check(ShootoutCandidateSelection.firstOrRescan(()->choose(records),()->unused[0]++)==next&&unused[0]==0);
  next.valid=false;check(ShootoutCandidateSelection.firstOrRescan(()->choose(records),()->scans[0]++)==null);
  var noSolution=new Candidate();noSolution.solution=false;records.add(noSolution);var later=new Candidate();records.add(later);check(choose(records)==later);
  exhausted.retry=true;check(choose(records)==exhausted);exhausted.retry=false;check(choose(records)==later);
  check(!ShootoutPolicy.retryAllowed(1,true,true,true));check(ShootoutPolicy.retryAllowed(0,false,true,true));
  String source=Files.readString(Path.of("src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java"));
  check(source.contains("relockRound.select(rescanned"));check(source.contains("if(target.last==tick)continue"));check(source.contains("!current.round.regularEligible()"));
  check(source.contains("if(t.stall.stalled())return t.entity.position()"));check(source.contains("if(z.isBaby())target.stall.observe"));check(source.contains("model.speeds.get(type),config().get(BABY_STALL_WINDOW)"));
  check(source.contains("flight->t.lamp!=null?initial:aimPoint(t,wait+latency+flight)"));check(source.contains("flight->firingTarget.lamp!=null?initial:aimPoint(firingTarget,1+latency+flight)"));
  String menu=Files.readString(Path.of("src/client/java/com/kuangsanfeng/ultimategarden/client/ui/screen/UltimateGardenScreen.java"));
  check(menu.indexOf("if(setting.window())shootoutInput(setting,0)")<menu.indexOf("Setting.BABY_STALL_WINDOW,0"));check(menu.indexOf("Setting.BABY_STALL_RATIO,0")<menu.indexOf("sampling.finishContent(cursor)"));
  System.out.println("PASS: "+checks+" continuous X/Z stall, reversal, gaps/recovery, stationary ballistics, configuration and empty-queue rescan checks. Not Verified In-Game.");
 }
}
