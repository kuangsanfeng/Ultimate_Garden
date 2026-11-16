
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim.ShootoutAimCompletion;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim.ShootoutTanhAim;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.model.ShootoutModel;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.shot.ShootoutArrowConfirmation;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootLogCategory;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootoutConfig;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat.ShootoutAimDiagnostics;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat.ZombieShootSnapshot;


import com.google.gson.*;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public final class AimShotDiagnosticsRegression {
 static int checks;static void check(boolean value){checks++;if(!value)throw new AssertionError("Aim/shot diagnostics "+checks);}
 static Map<String,Object> settings(int hz){return ZombieShootSnapshot.map("testFixture",true,"aimUpdateRateHz",hz,"aimPeakSpeedDegPerSecond",300,"aimCurveCurvatureDeg",15,"aimCompletionToleranceDeg",.5,"aimCorrectionRandomRatio",.25,"shootToleranceRatio",1.5,"shootToleranceDeg",.75);}
 static List<JsonObject> read(Path root)throws Exception {return Files.readAllLines(root.resolve(ShootoutAimDiagnostics.FILE)).stream().map(v->JsonParser.parseString(v).getAsJsonObject()).toList();}
 static List<JsonObject> of(List<JsonObject> rows,String event){return rows.stream().filter(v->v.get("event").getAsString().equals(event)).toList();}
 public static void main(String[] args)throws Exception {
  check(!new ZombieShootoutConfig().logEnabled(ZombieShootLogCategory.AIM_SHOT_DIAGNOSTICS));check(!ZombieShootLogCategory.AIM_SHOT_DIAGNOSTICS.legacy());
  var cfg=new ZombieShootoutConfig();cfg.setLogEnabled(ZombieShootLogCategory.AIM_SHOT_DIAGNOSTICS,true);var reload=new ZombieShootoutConfig();reload.load(cfg.toJson(new JsonObject()));check(reload.logEnabled(ZombieShootLogCategory.AIM_SHOT_DIAGNOSTICS));
  for(int rate:new int[]{20,60}){
   var root=Files.createTempDirectory("ug-aim-shot-fixture-"+rate+"-");var time=new AtomicLong(1_000_000_000L);var trace=new ShootoutAimDiagnostics(time::get,()->"TEST-ABSOLUTE-TIME");
   trace.sync(false,null,0,Map.of(),"DISABLED");check(!Files.exists(root.resolve(ShootoutAimDiagnostics.FILE)));
   trace.sync(true,root,1,settings(rate),"unused");check(trace.active());for(int i=0;i<100;i++)trace.sync(true,root,1,settings(rate),"unused");
   var regular=new ShootoutModel.Solution(3,30,0,new Vec3(2,1,10));trace.solution("00000001",regular,"REGULAR",1);check(trace.solutionId("00000001",regular)==1L);
   trace.inputContext("00000001",regular,1);var rotation=ShootoutTanhAim.rotation(0,0,30,0,300,15,1./rate);
   long input=trace.queued(rotation,1./rate,2,0,.5,false,false,"SUBMITTED");trace.applied(input,0,0,3,0);
   trace.gate("00000001",regular,"AUTO",3,0,1.,.75,true,.1,false,"CORRECTION_INCOMPLETE",true,false,true);
   for(int i=1;i<=100;i++){time.set(1_000_000_000L+i*1_000_000L);trace.gate("00000001",regular,"AUTO",3,0,1.,.75,true,.1,false,"CORRECTION_INCOMPLETE",true,false,true);}
   time.set(1_101_000_000L);trace.gate("00000001",regular,"AUTO",3,0,.6,.75,true,.1,false,"CORRECTION_INCOMPLETE",true,false,true); // First within shoot tolerance, but correction still active.
   time.incrementAndGet();trace.gate("00000001",regular,"AUTO",3,0,.6,.75,false,null,false,"SHOT_COOLDOWN",true,false,true);
   time.incrementAndGet();var fire=new ShootoutModel.Solution(3,30.1,.1,new Vec3(2,1,10));trace.solution("00000001",fire,"FIRE_RECHECK",2);
   trace.gate("00000001",fire,"AUTO",3,0,.6,.75,false,null,true,"ALLOWED",true,false,true);trace.gate("00000001",fire,"AUTO",3,0,.6,.75,false,null,true,"ALLOWED",true,false,true);
   long attemptId=trace.nextAttempt();var attempt=new ShootoutArrowConfirmation.Attempt(2,time.get(),Vec3.ZERO,new Vec3(0,0,1),3,50);trace.linkAttempt(attempt.nanos(),attemptId);
   trace.dispatch(attemptId,"AUTO",true,"NATIVE_PRESS_RELEASE_COMPLETED","00000001");trace.confirmation("START","AUTO",attempt,"00000001",null,null,"AUTO_ATTEMPT_REGISTERED");
   trace.confirmation("SUCCESS","AUTO",attempt,"00000001",UUID.randomUUID(),3L,"NEW_ARROW_OBSERVED");
   trace.inputContext("00000001",fire,3);trace.queued(rotation,1./rate,0,0,.5,false,false,"ZERO_QUANTIZED"); // Computed move, no native input applied.
   trace.end(4,"MANUAL_STOP");trace.end(4,"DUPLICATE_STOP");check(!trace.active());trace.solution("off",regular,"REGULAR",4);trace.dispatch(99,"AUTO",true,"off","off");
   // A second batch appends to the same file and cannot interleave START/END with the first batch.
   time.addAndGet(1_000_000_000L);trace.sync(true,root,5,settings(rate),"unused");trace.solution("00000002",null,"FIRE_RECHECK",5);check(trace.solutionId("00000002",null)==1L);
   trace.gate("00000002",null,"MANUAL",0,0,null,.75,false,null,false,"NO_INTERCEPT_SOLUTION",true,false,false);
   long manual=trace.nextAttempt();trace.dispatch(manual,"MANUAL",true,"NATIVE_BUTTON_RETURN","00000002");
   trace.sync(false,null,6,Map.of(),"CATEGORY_DISABLED");trace.shutdown();var rows=read(root);
   check(of(rows,"START").size()==2&&of(rows,"END").size()==2);check(of(rows,"AIM_SOLUTION").size()==3);
   check(of(rows,"SHOT_GATE").size()==5);check(of(rows,"AIM_INPUT_QUEUED").size()==2&&of(rows,"AIM_INPUT_APPLIED").size()==1);
   check(of(rows,"SHOT_DISPATCH").size()==2&&of(rows,"ARROW_CONFIRMATION").size()==2);
   boolean open=false;double previous=0;for(var row:rows){String event=row.get("event").getAsString();
    if(event.equals("START")){check(!open);open=true;previous=0;check(row.get("aimUpdateRateHz").getAsInt()==rate&&row.get("shootToleranceDeg").getAsDouble()==.75);}else {check(open);check(row.get("elapsedMs").getAsDouble()>=previous);previous=row.get("elapsedMs").getAsDouble();}
    if(event.equals("END")){open=false;check(row.has("timestamp"));}else if(!event.equals("START"))check(!row.has("timestamp")&&!row.has("sessionId")&&!row.has("batchId"));check(row.has("clientTick"));
   }check(!open);
   var queued=of(rows,"AIM_INPUT_QUEUED").getFirst();var applied=of(rows,"AIM_INPUT_APPLIED").getFirst();check(Math.abs(queued.get("dtMs").getAsDouble()-1000./rate)<1e-8);
   check(queued.get("solutionSequence").getAsLong()==1);check(applied.get("inputSequenceFrom").equals(queued.get("inputSequence"))&&applied.get("inputSequenceTo").equals(queued.get("inputSequence")));
   var gate=of(rows,"SHOT_GATE").getFirst();check(gate.get("lastAppliedInputSequence").equals(queued.get("inputSequence")));
   check(of(rows,"SHOT_GATE").get(3).get("solutionSequence").getAsLong()==2);check(of(rows,"ARROW_CONFIRMATION").getFirst().get("attemptSequence").equals(of(rows,"SHOT_DISPATCH").getFirst().get("attemptSequence")));
   check(of(rows,"END").getFirst().get("reason").getAsString().equals("MANUAL_STOP"));check(of(rows,"END").getLast().get("reason").getAsString().equals("CATEGORY_DISABLED"));
   long count=Files.size(root.resolve(ShootoutAimDiagnostics.FILE));trace.dispatch(100,"AUTO",true,"off","off");trace.shutdown();check(Files.size(root.resolve(ShootoutAimDiagnostics.FILE))==count);
  }
  // A queue-budget failure still gets one reserved END after accepted rows, without touching any macro state.
  var root=Files.createTempDirectory("ug-aim-shot-backlog-");var trace=new ShootoutAimDiagnostics();trace.sync(true,root,0,settings(60),"unused");
  var field=com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.ShootoutAimDiagnostics.class.getDeclaredField("writer");field.setAccessible(true);var writer=(com.kuangsanfeng.ultimategarden.client.platform.persistence.log.JsonlWriter)field.get(trace);
  check(!writer.offer(ShootoutAimDiagnostics.FILE,Map.of("oversize",Collections.nCopies(500001,1))));trace.sync(true,root,1,settings(60),"unused");check(!trace.active());trace.shutdown();var rows=read(root);check(of(rows,"START").size()==1&&of(rows,"END").size()==1);check(of(rows,"END").getFirst().get("writeError").getAsString().equals("QUEUE_BACKLOG_LIMIT"));
  var base=Path.of("src/client/java/com/kuangsanfeng/ultimategarden/client");String controller=Files.readString(base.resolve("macro/zombie_shoot/ZombieShootoutController.java"));
  check(controller.split("model\\.solve\\(",-1).length-1==2);check(controller.contains("aimDiagnostics.solution(t.tag,solution,\"REGULAR\",tick)"));check(controller.contains("aimDiagnostics.solution(t.tag,fire,\"FIRE_RECHECK\",tick)"));
  check(controller.contains("captureGateError(solution,error,yaw,pitch)"));check(controller.contains("diagnosticManualAttempt=aimDiagnostics.nextAttempt()"));check(controller.contains("diagnosticCollisionReason=\"OBSTACLE_HIT\""));
  String mouse=Files.readString(base.resolve("platform/input/view/NativeViewInput.java"));check(mouse.indexOf("m.shootout$turn(dt)")<mouse.indexOf("diagnostics.applied(sequence"));check(mouse.indexOf("diagnostics.applied(sequence")<mouse.indexOf("observer.consumed(c.player"));check(mouse.contains("ZERO_QUANTIZED"));
  String diagnostic=Files.readString(base.resolve("platform/persistence/log/zombie_shoot/ShootoutAimDiagnostics.java"));for(String forbidden:List.of(".solve(","ShootoutAimCompletion.error(","mouse.aim(","mouse.click("))check(!diagnostic.contains(forbidden));
  System.out.println("PASS: "+checks+" synthetic 20/60 Hz trace linkage, zero submissions, gate sampling, append batches, close/backlog, configuration and unchanged solve-count checks. Not Verified In-Game; no reproduction of the reported 60 Hz failure is claimed.");
 }
}
