
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.model.ShootoutSpeed;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.targeting.ShootoutCandidateSelection;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.targeting.ShootoutPolicy;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.targeting.ShootoutTargetRound;

import java.util.*;
import java.nio.file.*;

public final class RelockRegression {
 static int checks;static void check(boolean value){checks++;if(!value)throw new AssertionError("Relock round "+checks);}
 static final class Target {
  final String type;final long first;final UUID uuid=UUID.randomUUID();final ShootoutTargetRound round=new ShootoutTargetRound();
  final ShootoutSpeed speed=new ShootoutSpeed();boolean valid=true,solution=true;
  Target(String type,long first){this.type=type;this.first=first;}
 }
 static List<Target> ordered(List<Target> targets){return targets.stream().sorted(Comparator.comparingInt((Target t)->ShootoutPolicy.priority(t.type)).thenComparingLong(t->t.first)).toList();}
 static ShootoutTargetRound.Attempt missBoth(Target t){
  var first=t.round.dispatched();check(first.retry()==0&&!t.round.relockEligible());
  check(t.round.verify(first,true,true));check(t.round.retry()&&t.round.regularEligible());
  check(!t.round.verify(first,true,true)); // A duplicate result cannot queue a second retry.
  var retry=t.round.dispatched();check(retry.retry()==1&&!t.round.retry());check(!t.round.relockEligible());
  check(!t.round.verify(retry,true,true));check(!t.round.regularEligible()&&t.round.relockEligible());return retry;
 }
 static Target select(ShootoutCandidateSelection.Relock<Target> queue,List<Target> targets,boolean rescan){
  var regular=ShootoutCandidateSelection.first(ordered(targets),t->t.round.regularEligible()&&t.valid,t->t.solution);
  if(regular!=null)return regular;
  return queue.select(rescan,()->ordered(targets).stream().filter(t->t.round.relockEligible()).toList(),
    t->t.round.relockEligible()&&t.valid,t->t.solution,t->t.round.beginRelock());
 }
 public static void main(String[] args)throws Exception {
  // Each of the eight classes alone survives its first shot and its single retry.
  for(String type:ShootoutPolicy.ORDER.subList(1,9)){
   var t=new Target(type,1);var records=new ArrayList<Target>();records.add(t);var queue=new ShootoutCandidateSelection.Relock<Target>();
   var identity=t.uuid;var speed=t.speed;t.speed.observe(0,"PATH_A",1,.2,6);t.speed.observe(1,"PATH_A",1.2,.2,6);
   check(select(queue,records,true)==t);var old=missBoth(t);
   check(select(queue,records,true)==t);check(t.uuid==identity&&t.speed==speed&&t.speed.measuredSpeed()!=null);check(!t.round.handled()&&!t.round.retry());
   var next=t.round.dispatched();check(next.round()==old.round()+1&&next.retry()==0);check(!t.round.relockEligible());
   check(!t.round.verify(old,true,true)); // Late previous-round verification must not touch this pending attempt.
   check(!t.round.relockEligible());check(t.round.verify(next,true,true));check(t.round.retry());
   var retry=t.round.dispatched();check(retry.retry()==1);check(!t.round.verify(retry,true,true));
   check(select(queue,records,true)==t);check(!t.round.handled());
  }
  // All equipment/age classes rotate in priority order, preserving first-seen ordering for equal types.
  var targets=new ArrayList<Target>();long order=1;for(String type:ShootoutPolicy.ORDER.subList(1,9))targets.add(new Target(type,order++));
  targets.add(new Target("DIAMOND_BABY",order++));Collections.reverse(targets);for(var t:targets)missBoth(t);
  var expected=ordered(targets);var queue=new ShootoutCandidateSelection.Relock<Target>();
  for(int i=0;i<expected.size();i++){
   var picked=select(queue,targets,true);check(picked==expected.get(i));missBoth(picked);
   // Finishing a high-priority target does not reseed the snapshot ahead of the remaining targets.
  }
  check(select(queue,targets,false)==null);check(select(queue,targets,true)==expected.getFirst());
  // A new regular target preempts the remaining relock candidates, but never discards their order.
  queue.clear();targets.clear();var high=new Target("DIAMOND_BABY",1);var low=new Target("LEATHER_ADULT",2);targets.add(low);targets.add(high);missBoth(low);missBoth(high);
  check(select(queue,targets,true)==high);missBoth(high);var fresh=new Target("GOLD_ADULT",3);targets.add(fresh);
  check(select(queue,targets,true)==fresh);missBoth(fresh);check(select(queue,targets,true)==low);
  // Same-round failures/no solutions are skipped immediately; no seed permit means no new round this Tick.
  queue.clear();targets.clear();var gone=new Target("DIAMOND_ADULT",1);var unsolved=new Target("GOLD_BABY",2);var good=new Target("IRON_BABY",3);
  targets.addAll(List.of(gone,unsolved,good));for(var t:targets)missBoth(t);gone.valid=false;unsolved.solution=false;
  check(select(queue,targets,true)==good);missBoth(good);check(select(queue,targets,false)==null);
  good.valid=false;check(select(queue,targets,true)==null);unsolved.solution=true;check(select(queue,targets,true)==unsolved);
  // Outstanding impact verification cannot be silently reset to suppress its normal single retry.
  var pending=new Target("LEATHER_BABY",1);var attempt=pending.round.dispatched();
  check(!pending.round.relockEligible());try{pending.round.beginRelock();throw new AssertionError();}catch(IllegalStateException expectedFailure){checks++;}
  check(pending.round.verify(attempt,true,true));check(pending.round.retry());
  var removed=new Target("IRON_ADULT",1);var removedShot=removed.round.dispatched();check(!removed.round.verify(removedShot,false,true));check(!removed.round.retry());
  // Source integration: no class exceptions; lifecycle caches/arrow confirmation remain separate.
  String source=Files.readString(Path.of("src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java"));
  String selection=source.substring(source.indexOf(" private Target select()"),source.indexOf(" private AimPlan prediction("));
  for(String type:ShootoutPolicy.ORDER.subList(1,9))check(!selection.contains('"'+type+'"'));
  check(selection.contains("lastRescanTick!=tick"));check(selection.contains("if(rescanned){lastRescanTick=tick;observe();selected=selectObserved()"));
  check(selection.indexOf("selected=selectObserved()")<selection.indexOf("relockRound.select"));check(selection.contains("t.round.beginRelock()"));check(selection.contains("thenComparingLong(t->t.first)"));
  check(!selection.contains("arrowConfirmation.cancel()")&&!selection.contains("speed=new")&&!selection.contains("targets.clear()"));
  check(source.contains("round.verify(shot.roundAttempt"));check(source.contains("target.round.dispatched()"));check(source.contains("!current.round.regularEligible()"));
  System.out.println("PASS: "+checks+" eight last-survivor relocks, one retry per round, ordered rotation/new-target precedence, stale-result isolation and bounded rescan checks. Not Verified In-Game.");
 }
}
