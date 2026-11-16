package com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.shot.ShootoutArrowConfirmation;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.model.ShootoutModel;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim.ShootoutMouseInput;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim.ShootoutTanhAim;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat.ZombieShootLogger;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat.ZombieShootSnapshot;



import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.function.*;
import static com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat.ZombieShootSnapshot.map;

/** Optional observations only. No world access, angle solving, input scheduling or control decisions. */
public class ShootoutAimDiagnostics implements ShootoutMouseInput.DiagnosticsObserver {
 public static final String FILE="aim-shot-diagnostics.jsonl";
 private final LongSupplier nanos;private final Supplier<String> absolute;
 private ZombieShootLogger writer;private boolean active,failed;private long started,tick,solutions,inputs,attempts,lastApplied;
 private String target;private Long inputSolution;private int inputRate;private long batchGeneration;
 public long batchGeneration(){return batchGeneration;}
 private final IdentityHashMap<ShootoutModel.Solution,Long> solutionIds=new IdentityHashMap<>();
 private final ArrayDeque<ShootoutModel.Solution> solutionOrder=new ArrayDeque<>();
 private record LastSolution(long id,ShootoutModel.Solution solution){}
 private final LinkedHashMap<String,LastSolution> lastSolutions=new LinkedHashMap<>();
 private final LinkedHashMap<Long,Long> attemptIds=new LinkedHashMap<>();
 private record Gate(String target,String reason,boolean allowed,boolean within,long written){}
 private final Map<String,Gate> gates=new HashMap<>();
 public ShootoutAimDiagnostics(){this(System::nanoTime,()->Instant.now().toString());}
 public ShootoutAimDiagnostics(LongSupplier nanos,Supplier<String> absolute){this.nanos=nanos;this.absolute=absolute;}
 public boolean active(){return active;}
 public void sync(boolean wanted,Path root,long tick,Map<String,Object> settings,String stopReason){
  this.tick=tick;
  if(!wanted){end(tick,stopReason);failed=false;return;}
  if(active&&writer.error!=null){end(tick,"WRITE_ERROR");failed=true;}
  if(active||failed)return;
  try{
   if(writer==null||writer.finished())writer=new ZombieShootLogger(root,"aim-shot-diagnostics",true);
   if(writer.error!=null)return;
   started=nanos.getAsLong();inputRate=((Number)settings.getOrDefault("aimUpdateRateHz",20)).intValue();solutions=inputs=attempts=lastApplied=0;solutionIds.clear();solutionOrder.clear();lastSolutions.clear();attemptIds.clear();gates.clear();
   var start=new LinkedHashMap<String,Object>(settings);start.putAll(map("event","START","timestamp",absolute.get(),"elapsedMs",0.,"clientTick",tick));
   active=writer.offer(FILE,start);if(active)batchGeneration++;if(!active){failed=true;writer.close();}
  }catch(RuntimeException error){active=false;failed=true;}
 }
 public void tick(long value){tick=value;}
 public void end(long tick,String reason){
  if(!active)return;active=false;
  try{writer.endBatch(FILE,map("event","END","timestamp",absolute.get(),"elapsedMs",elapsed(),"clientTick",tick,"reason",reason,"droppedSamples",writer.dropped.get(),"writeError",writer.error));}
  catch(RuntimeException error){failed=true;writer.close();}
  solutionIds.clear();solutionOrder.clear();lastSolutions.clear();attemptIds.clear();gates.clear();target=null;inputSolution=null;
 }
 public void shutdown(){end(tick,"CLIENT_STOPPING");if(writer!=null){writer.close();try{writer.awaitClose(3000);}catch(InterruptedException error){Thread.currentThread().interrupt();}}}
 private double elapsed(){return Math.max(0,(nanos.getAsLong()-started)/1e6);}
 private void event(String event,Map<String,Object> fields){
  if(!active)return;
  try{var data=new LinkedHashMap<String,Object>(fields);data.put("event",event);data.put("elapsedMs",elapsed());data.put("clientTick",tick);
   if(!writer.offer(FILE,data)){end(tick,"WRITE_BACKLOG_OR_ERROR");failed=true;}
  }catch(RuntimeException error){end(tick,"DIAGNOSTIC_ERROR");failed=true;}
 }
 public void solution(String target,ShootoutModel.Solution solution,String purpose,long tick){
  if(!active)return;this.tick=tick;long id=++solutions;
  if(solution!=null){solutionIds.put(solution,id);solutionOrder.addLast(solution);while(solutionOrder.size()>128)solutionIds.remove(solutionOrder.removeFirst());}
  if(target!=null){lastSolutions.put(target,new LastSolution(id,solution));while(lastSolutions.size()>128)lastSolutions.remove(lastSolutions.keySet().iterator().next());}
  event("AIM_SOLUTION",map("targetTag",target,"solutionSequence",id,"purpose",purpose,"targetYaw",solution==null?null:solution.yaw(),"targetPitch",solution==null?null:solution.pitch(),"valid",solution!=null));
 }
 public Long solutionId(String target,ShootoutModel.Solution solution){
  if(solution!=null)return solutionIds.get(solution);var last=lastSolutions.get(target);return last!=null&&last.solution()==null?last.id():null;
 }
 public void inputContext(String target,ShootoutModel.Solution solution,long tick,int rate){inputRate=rate;inputContext(target,solution,tick);}
 public void inputContext(String target,ShootoutModel.Solution solution,long tick){this.target=target;this.inputSolution=solutionId(target,solution);this.tick=tick;}
 @Override public long queued(ShootoutTanhAim.Rotation rotation,double dt,double dx,double dy,double sensitivity,boolean invertX,boolean invertY,String submission){
  if(!active)return 0;long id=++inputs;
  // The same float conversion used by native Entity.turn; residuals are measured in degrees and never fed back.
  double ss=sensitivity*.6F+.2F,factor=ss*ss*ss*8;
  double representedYaw=(float)(dx*factor*(invertX?-1:1))*.15F,representedPitch=(float)(dy*factor*(invertY?-1:1))*.15F;
  event("AIM_INPUT_QUEUED",map("targetTag",target,"inputSequence",id,"solutionSequence",inputSolution,"dtMs",dt*1000,"aimUpdateRateHz",inputRate,
    "requestedStepDeg",rotation.step(),"requestedYawDelta",rotation.yawDelta(),"requestedPitchDelta",rotation.pitchDelta(),"submittedMouseDx",dx,"submittedMouseDy",dy,
    "quantizationResidualYawDeg",submission.equals("VIEW_OBSERVER_REJECTED")?null:rotation.yawDelta()-representedYaw,"quantizationResidualPitchDeg",submission.equals("VIEW_OBSERVER_REJECTED")?null:rotation.pitchDelta()-representedPitch,"submission",submission));return id;
 }
 @Override public void applied(long id,float beforeYaw,float beforePitch,float afterYaw,float afterPitch){
  if(!active||id==0)return;lastApplied=id;
  event("AIM_INPUT_APPLIED",map("inputSequenceFrom",id,"inputSequenceTo",id,"solutionSequence",inputSolution,"targetTag",target,
    "beforeYaw",beforeYaw,"beforePitch",beforePitch,"afterYaw",afterYaw,"afterPitch",afterPitch));
 }
 public void gate(String target,ShootoutModel.Solution solution,String source,float yaw,float pitch,Double error,double tolerance,boolean correctionActive,Double correctionThreshold,boolean allowed,String reason,boolean toleranceEnforced,boolean waitingForArrow,boolean errorEvaluated){
  if(!active)return;long now=nanos.getAsLong();boolean within=error!=null&&error<=tolerance+1e-9;var old=gates.get(source);
  boolean immediate=old==null||!Objects.equals(old.target(),target)||!old.reason().equals(reason)||old.allowed()!=allowed||!allowed&&within&&!old.within();
  if(immediate||now-old.written()>=250_000_000L){
   event("SHOT_GATE",map("targetTag",target,"solutionSequence",solutionId(target,solution),"lastAppliedInputSequence",lastApplied==0?null:lastApplied,"source",source,
    "actualYaw",yaw,"actualPitch",pitch,"aimErrorDeg",error,"errorEvaluatedAtGate",errorEvaluated,"shootToleranceDeg",tolerance,"shootToleranceEnforced",toleranceEnforced,
    "correctionActive",correctionActive,"correctionThresholdDeg",correctionThreshold,"allowed",allowed,"reason",reason,"waitingForArrow",waitingForArrow));
   gates.put(source,new Gate(target,reason,allowed,within,now));
  }else gates.put(source,new Gate(target,reason,allowed,within,old.written()));
 }
 public long nextAttempt(){return active?++attempts:0;}
 public void linkAttempt(long nanos,long sequence){if(active&&sequence!=0){attemptIds.put(nanos,sequence);while(attemptIds.size()>256)attemptIds.remove(attemptIds.keySet().iterator().next());}}
 public void dispatch(long sequence,String source,boolean executed,String reason,String target){event("SHOT_DISPATCH",map("attemptSequence",sequence==0?null:sequence,"source",source,"executed",executed,"reason",reason,"targetTag",target));}
 public void confirmation(String stage,String source,ShootoutArrowConfirmation.Attempt attempt,String target,UUID arrow,Long confirmationTick,String reason){
  if(!active)return;event("ARROW_CONFIRMATION",map("stage",stage,"source",source,"attemptSequence",attempt==null?null:attemptIds.get(attempt.nanos()),"attemptTick",attempt==null?null:attempt.tick(),
   "targetTag",target,"arrowUuid",arrow==null?null:arrow.toString(),"confirmationTick",confirmationTick,"reason",reason));
 }
}
