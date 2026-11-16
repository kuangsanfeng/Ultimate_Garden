
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.model.ShootoutModel;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.shot.ShootoutLampCollision;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.targeting.ShootoutCandidateSelection;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.targeting.ShootoutTargetLoss;

import net.minecraft.world.phys.*;
import java.util.*;
import java.nio.file.*;

public final class TargetRotationRegression {
 static int checks;
 static void check(boolean value){checks++;if(!value)throw new AssertionError("Target rotation "+checks);}
 record Candidate(String id,boolean valid,boolean solution){}
 static boolean legacyLamp(ShootoutModel model,Vec3 eye,ShootoutModel.Solution solution,AABB lamp,List<AABB> obstacles){
  Vec3 previous=eye;boolean hit=false;
  for(int i=1;i<=Math.ceil(solution.ticks()+1);i++){
   var next=model.arrow(eye,solution.yaw(),solution.pitch(),Math.min(i,solution.ticks()+.5));
   for(var box:obstacles)if(box.inflate(.125).clip(previous,next).isPresent())return false;
   if(lamp.inflate(.125).clip(previous,next).isPresent())hit=true;
   previous=next;
  }
  return hit;
 }
 public static void main(String[] args)throws Exception {
  var model=ShootoutModel.load();var eye=new Vec3(.5,1.62,0);var point=new Vec3(.5,1.6,10.005);
  var lamp=new AABB(0,1,10,1,2,11);var backing=new AABB(0,1,11,1,2,12);
  var solution=model.solve(eye,t->point);check(solution!=null);
  check(!legacyLamp(model,eye,solution,lamp,List.of(backing)));
  check(ShootoutLampCollision.hits(model,eye,solution.yaw(),solution.pitch(),solution.ticks(),.125,List.of(lamp),List.of(backing)));
  check(ShootoutLampCollision.hits(model,eye,solution.yaw(),solution.pitch(),solution.ticks(),.125,List.of(lamp),List.of()));
  var front=new AABB(0,1,9,1,2,10);
  check(!ShootoutLampCollision.hits(model,eye,solution.yaw(),solution.pitch(),solution.ticks(),.125,List.of(lamp),List.of(front)));
  check(!ShootoutLampCollision.hits(model,eye,solution.yaw(),solution.pitch(),solution.ticks(),.125,List.of(),List.of()));
  check(!ShootoutLampCollision.hits(model,eye,solution.yaw()+90,solution.pitch(),solution.ticks(),.125,List.of(lamp),List.of()));
  // Different presentation/order of target geometry cannot change earliest-contact ordering.
  var left=new AABB(0,1,10,.5,2,11);var right=new AABB(.5,1,10,1,2,11);
  check(ShootoutLampCollision.hits(model,eye,solution.yaw(),solution.pitch(),solution.ticks(),.125,List.of(right,left),List.of(backing)));
  for(double ping:new double[]{0,50,100,250}){
   var fixed=model.solve(eye,t->point);check(fixed.target().equals(point));
   check(model.arrow(eye,fixed.yaw(),fixed.pitch(),fixed.ticks()).distanceTo(point)<1e-6);
   check(ShootoutLampCollision.hits(model,eye,fixed.yaw(),fixed.pitch(),fixed.ticks(),.125,List.of(lamp),List.of(backing)));
  }
  var gone=new Candidate("gone",false,true);var noSolution=new Candidate("no-solution",true,false);var handled=new Candidate("handled",false,true);
  var ready=new Candidate("ready",true,true);var later=new Candidate("later",true,true);
  List<Candidate> checked=new ArrayList<>(),solved=new ArrayList<>();
  var ordered=List.of(gone,noSolution,noSolution,handled,ready,later);
  var result=ShootoutCandidateSelection.first(ordered,t->{checked.add(t);return t.valid();},t->{solved.add(t);return t.solution();});
  check(result==ready);check(checked.equals(List.of(gone,noSolution,handled,ready)));check(solved.equals(List.of(noSolution,ready)));
  // An unsolvable first candidate cannot starve later valid candidates on subsequent ticks.
  for(int i=0;i<20;i++)check(ShootoutCandidateSelection.first(ordered,Candidate::valid,Candidate::solution)==ready);
  check(ShootoutCandidateSelection.first(List.of(gone,noSolution,handled),Candidate::valid,Candidate::solution)==null);
  check(ShootoutCandidateSelection.first(List.of(ready,later),Candidate::valid,Candidate::solution)==ready);
  check(ShootoutCandidateSelection.first(List.of(later,ready),Candidate::valid,Candidate::solution)==later);
  var loss=new ShootoutTargetLoss();check(!loss.expired(false,0));check(!loss.expired(false,99_999_999));check(loss.expired(false,100_000_000));
  loss.clear();check(!loss.expired(false,1_000_000_000));check(!loss.expired(true,1_099_999_999));check(!loss.expired(false,1_100_000_000));check(!loss.expired(false,1_199_999_999));check(loss.expired(false,1_200_000_000));
  // Model-free lost-target handling still progresses while no RTT is available.
  String source=Files.readString(Path.of("src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java"));
  String tick=source.substring(source.indexOf(" private void tick()"));
  check(tick.indexOf("holdingInvalid=holdInvalidTarget(now)")<tick.indexOf("if(ping<0)"));
  check(tick.indexOf("holdingInvalid=holdInvalidTarget(now)")<tick.indexOf("if(c.screen!=null"));
  check(tick.indexOf("observe();")<tick.indexOf("finishDispatchedShot()"));
  check(source.contains("current=null;resetAimCycle();current=select();state=State.SELECTING_TARGET"));
  check(source.contains("ShootoutCandidateSelection.first(ordered"));check(source.contains("rejectedThisTick.add(t)"));
  check(source.contains("selectionPlan.tick()==tick"));check(source.contains("ShootoutLampCollision.hits("));
  check(!source.contains("box.clip(eye,solution.target()).isPresent()"));
  System.out.println("PASS: "+checks+" lamp-first collision versus backing/front blocks, ordered candidate fallback, lock grace/recovery and pre-RTT lifecycle checks. Not Verified In-Game.");
 }
}
