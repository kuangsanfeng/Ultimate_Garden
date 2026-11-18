package com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug;

import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import com.kuangsanfeng.ultimategarden.client.platform.input.view.*;
import com.kuangsanfeng.ultimategarden.client.platform.input.virtual.VirtualKeyboard;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.InstantTransmissionConfig;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.log.TransmissionLog;
import com.kuangsanfeng.ultimategarden.client.function.observation.sampling.TimedWindow;
import com.kuangsanfeng.ultimategarden.client.platform.context.ClientContextEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Client-thread only experiment; no direct rotation writes, movement packets or item NBT. */
public final class InstantTransmissionController {
 public static final InstantTransmissionController INSTANCE=new InstantTransmissionController();
 public enum State {STOPPED,WAIT_GUI,START_ALIGN,SLOT,RETURN_ALIGN,WALK,DOWN_ALIGN,RESET_JUMP,RESET_WAIT,TEST_YAW_ALIGN,TEST_ALIGN,SETTLE,TEST_WAIT,Y_JUMP,Y_WAIT}
 private State state=State.STOPPED;private String reason="NONE";
 private final NativeViewInput mouse=new NativeViewInput();private VirtualKeyboard keyboard;
 private final TransmissionResetJump resetJump=new TransmissionResetJump();
 private final com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.log.TransmissionResetDiagnostics resetDiagnostics=
  new com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.log.TransmissionResetDiagnostics(this::resetDiagnosticSnapshot,(type,data)->{
   if(this.log!=null)this.log.event(type,((Number)data.get("observationTick")).longValue(),data);
  });
 public boolean resetDiagnosticsActive(){return resetDiagnostics.active();}
 public void resetNativeInput(Map<String,Object> input){resetDiagnostics.consumed(tick,input);}
 private Map<String,Object> resetDiagnosticSnapshot(){
  var c=Minecraft.getInstance();var values=map("state",state.name(),"controllerTick",tick,"targetYaw",targetYaw,"targetPitch",targetPitch);
  values.put("baseBlock",base==null?null:List.of(base.getX(),base.getY(),base.getZ()));
  values.put("expectedCenter",base==null?null:List.of(base.getX()+.5,baseY,base.getZ()+.5));
  if(c.player==null){values.put("observationAvailable",false);values.put("feet",null);values.put("onGround",null);return values;}
  values.putAll(snapshot().data());values.put("observationAvailable",true);values.put("horizontalSpeed",Math.hypot(c.player.getDeltaMovement().x,c.player.getDeltaMovement().z));
  var block=BlockPos.containing(c.player.position());values.put("blockX",block.getX());values.put("blockZ",block.getZ());
  values.put("deltaX",base==null?null:c.player.getX()-(base.getX()+.5));values.put("deltaZ",base==null?null:c.player.getZ()-(base.getZ()+.5));
  values.put("insideBaseBlock",base==null?null:block.getX()==base.getX()&&block.getZ()==base.getZ());
  values.put("returnPredicate","floor(feetX)==base.blockX && floor(feetZ)==base.blockZ");
  values.put("returnPredicateResult",values.get("insideBaseBlock"));values.put("returnPredicateUsedForControl",false);
  values.put("keyMapping",map("W",c.options.keyUp.isDown(),"A",c.options.keyLeft.isDown(),"S",c.options.keyDown.isDown(),"D",c.options.keyRight.isDown(),"SPACE",c.options.keyJump.isDown(),"SHIFT",c.options.keyShift.isDown()));
  values.put("returnPhase",returnPhase.name());values.put("microAdjustments",microAdjustments);values.put("selectedSlot",c.player.getInventory().getSelectedSlot()+1);values.put("keyboard",keyboard==null?null:keyboard.diagnosticState());return values;
 }
 private void submitResetKey(String key){var before=resetDiagnostics.beforeInput(tick);keyboard.keyDown(key);resetDiagnostics.submitted(tick,key,false,before,"EXISTING_KEY_DOWN");}
 private void releaseResetKeys(String why){var before=resetDiagnostics.beforeInput(tick);keyboard.releaseAll();resetDiagnostics.submitted(tick,null,true,before,why);}
 private void beginResetDiagnostics(String previousTrial,String why){resetDiagnostics.begin(tick,previousTrial,state.name(),why);}

 private Object world,connection;private long sessionGeneration;private long tick,stateTick,stateNanos,lastFrame,trialStartNanos;private double targetYaw,targetPitch,baseY;
 private BlockPos base;private InstantTransmissionConfig.Plan plan;private int yawIndex,pitchIndex,group=1;private boolean baseConfirmed;
 private TransmissionLog log;private TransmissionTrial trial;private String trialId;private Vec3 lastWalk;private long walkProgress;
 private enum ReturnPhase {APPROACH,COAST,PULSE,WAIT_LANDING}
 private ReturnPhase returnPhase=ReturnPhase.APPROACH;private boolean correctiveReturn;
 private int stableReturnTicks,microAdjustments;private long returnStartedNanos,coastDecisionTick;
 private TransmissionCoastPrediction.Leg returnLeg;
 private Vec3 lastCoast,coastVelocity;private TransmissionCoastPrediction.Stop coastPrediction;
 private record Snapshot(long tick,long nanos,Map<String,Object> data){}
 private final TimedWindow<Snapshot> pre=new TimedWindow<>(2,Snapshot::tick);
 private final List<TransmissionLog> draining=new ArrayList<>();
 private TransmissionHudSnapshot finalHud,lastTrialHud;
 private String hudLastResult;
 public TransmissionHudSnapshot hudSnapshot(){return running()||finalHud==null?captureHud():finalHud;}
 private TransmissionHudSnapshot captureHud(){
  var player=Minecraft.getInstance().player;boolean available=plan!=null;
  int total=available?plan.yaws().size()*plan.pitches().size()*3:0;
  boolean finished=available&&yawIndex>=plan.yaws().size();
  int index=available?Math.min(total,(yawIndex*plan.pitches().size()+pitchIndex)*3+group):0;
  int y=available?Math.min(yawIndex,plan.yaws().size()-1):0;
  int p=available?(finished?plan.pitches().size()-1:pitchIndex):0;
  Long relative=trial==null?null:tick-trial.firstTick;
  String phase=TransmissionHudSnapshot.phase(state.name(),relative,trial==null?0:trial.planned,
   trial!=null&&trial.endpoint!=null,trial!=null&&(trial.lastResponse<trial.lastClick||trial.actual<trial.planned||trial.updates<trial.actual));
  return new TransmissionHudSnapshot(available,running(),phase,reason,hudLastResult,index,total,finished?3:group,
   available?plan.yaws().get(y):null,available?plan.pitches().get(p):null,
   player==null?null:(double)player.getYRot(),player==null?null:(double)player.getXRot(),resetCycle,
   available?targetYaw:null,available?targetPitch:null,player==null?null:resetJump.relativeHeight(player.getY()),
   pre.size(),trial==null?0:trial.actual,trial==null?0:trial.planned,trial==null?0:trial.updates,relative);
 }
 public boolean running(){return state!=State.STOPPED;}
 public String status(){return (running()?activeTestName()+" / ":"")+state+" / "+(finalHud!=null&&finalHud.terminationStage()!=null?finalHud.terminationStage()+" / ":"")+reason;}
 public static boolean ownsInput(){return INSTANCE.running()&&INSTANCE.state!=State.WAIT_GUI&&Minecraft.getInstance().screen==null&&!NativeViewInput.dispatching();}
 public enum StartSource {API,MENU,HOTKEY}
 private StartSource entrySource=StartSource.API;
 public record FailureDiagnostic(String source,String stage,String phase,String code,Map<String,Object> observed){}
 private FailureDiagnostic lastDiagnostic;public FailureDiagnostic lastDiagnostic(){return lastDiagnostic;}
 public void toggle(){toggle(StartSource.API);}
 public void toggle(StartSource source){if(running()){if(yRounding){notifyOtherTest();return;}stop("MANUAL_STOP");}else start(source);}
 private record Inspection(TransmissionInputCheck values,Map<String,Object> observed){}
 private Inspection inspect(Minecraft c){
  var farm=Ultimate_GardenClient.getFarmController();var owner=farm==null?null:farm.inputOwnership();
  var v=new TransmissionInputCheck(c.player!=null,c.level!=null,c.getConnection()!=null,c.player!=null&&c.player.onGround(),
   world!=null&&c.level!=world,connection!=null&&c.getConnection()!=connection,
   owner!=null&&owner.running(),owner!=null&&owner.heldInput(),owner!=null&&owner.actionPending(),
   com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController.INSTANCE.enabled(),
   com.kuangsanfeng.ultimategarden.client.platform.ui.hud.HudAdjustmentController.isActive(),c.player!=null&&c.player.isAlive(),
   c.screen!=null,c.getOverlay()!=null,c.isPaused(),c.isWindowActive(),c.mouseHandler.isMouseGrabbed());
  var facts=map("playerAvailable",v.player(),"worldAvailable",v.world(),"connectionAvailable",v.connection(),"onGround",c.player==null?null:v.grounded(),
   "worldChanged",v.worldChanged(),"connectionChanged",v.connectionChanged(),"screen",c.screen==null?null:c.screen.getClass().getName(),
   "farmRunning",v.farmRunning(),"farmHeldInput",v.farmHeld(),"farmActionPending",v.farmAction(),"shootout",v.shootout(),"hudEditor",v.hudEditor(),
   "owner",v.ownerFailure(),"alive",v.alive(),"overlay",v.overlay(),"paused",v.paused(),"focused",v.focused(),"mouseGrabbed",v.grabbed());
  return new Inspection(v,Collections.unmodifiableMap(facts));
 }
 private void diagnose(String why,String stage,String phase,Inspection inspection){
  var facts=inspection.observed();lastDiagnostic=new FailureDiagnostic(entrySource.name(),stage,phase,why,facts);
  var data=map("source",entrySource.name(),"stage",stage,"phase",phase,"code",why,"observed",facts);
  try{if(log!=null)log.event("FAILURE_DIAGNOSTIC",tick,resetDiagnostics.decorate(data));
   else com.kuangsanfeng.ultimategarden.Ultimate_Garden.LOGGER.warn("Instant Transmission failure: {}",data);
  }catch(RuntimeException ignored){/* Diagnostics must never prevent this module's input cleanup. */}
 }
 private void reject(String why,Inspection inspection){
  var c=Minecraft.getInstance();reason=why;state=State.STOPPED;
  finalHud=TransmissionHudSnapshot.rejected(why,c.player==null?null:(double)c.player.getYRot(),c.player==null?null:(double)c.player.getXRot());
  diagnose(why,"START_REJECTED","START_VALIDATION",inspection);plan=null;lastTrialHud=null;hudLastResult=null;
 }
 public void start(){start(StartSource.API);}
 public void start(StartSource source){
  if(running())return;entrySource=Objects.requireNonNull(source);lastDiagnostic=null;world=connection=null;
  var c=Minecraft.getInstance();var check=inspect(c);
  if(c.screen==null){var failure=check.values().startFailure();if(failure!=null){reject(failure,check);return;}}
  try{plan=yRounding?new InstantTransmissionConfig.Plan(List.of(fixedYaw),InstantTransmissionConfig.axis(-90,90,15)):Ultimate_GardenClient.getConfigManager().getGeneralConfig().instantTransmission().plan();}catch(RuntimeException e){reject("INVALID_PARAMETERS",check);return;}
  if(draining.stream().anyMatch(l->!l.finished())){reject("LOG_DRAINING",check);return;}draining.clear();
  if(c.screen!=null){
   world=c.level;connection=c.getConnection();tick=0;yawIndex=pitchIndex=0;group=1;resetCycle=true;trial=null;trialId=null;
   finalHud=lastTrialHud=null;hudLastResult=null;reason="RUNNING";targetYaw=targetPitch=0;enter(State.WAIT_GUI);return;
  }
  activate(c);
 }
 private void activate(Minecraft c){
  long generation=++sessionGeneration;keyboard=new VirtualKeyboard(c);keyboard.setInputAllowed(()->running()&&sessionGeneration==generation);
  base=BlockPos.containing(c.player.position());baseY=c.player.getY();world=c.level;connection=c.getConnection();yawIndex=pitchIndex=0;group=1;baseConfirmed=false;tick=0;
  if(yRounding){coverage=new TransmissionTickPlan();}
  log=new TransmissionLog(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("logs/ultimategarden/instant transmission"),yRounding?"instant-transmission-y-rounding.jsonl":"instant-transmission.jsonl");
  emit("PARAMETERS","yaws",plan.yaws(),"pitches",plan.pitches(),"parameters",Ultimate_GardenClient.getConfigManager().getGeneralConfig().instantTransmission().toJson().toString(),"baseBlock",List.of(base.getX(),base.getY(),base.getZ()),"expectedBaseY",baseY,"world",c.level.dimension().toString(),"server",c.getCurrentServer()==null?"local":c.getCurrentServer().ip,"distanceBonusNote",Ultimate_GardenClient.getConfigManager().getGeneralConfig().instantTransmission().distanceBonusNote(),"start",xyz(c.player.position()));
  resetJump.clear();clearReturn();finalHud=lastTrialHud=null;hudLastResult=null;positionOrder=0;lastActualClick=-1;resetCycle=true;trial=null;trialId=null;reason="RUNNING";enter(State.START_ALIGN);targetYaw=yRounding?fixedYaw:0;targetPitch=yRounding?-90:0;beginResetDiagnostics(null,"INITIAL_RESET");
 }

 private boolean yRounding;private double fixedYaw,takeoffY;private boolean airborne,yClicked,yDispatched;private long takeoffTick,yClickTick;private int yResponses;private Vec3 yLaunch,yAfter;private double yLaunchYaw,yLaunchPitch,yLaunchVelocity;private TransmissionTickPlan coverage;private TransmissionTickPlan.Key yTarget;
 public String activeTestName(){var language=new com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageManager(Ultimate_GardenClient.getConfigManager());return language.text(yRounding?"ui.ultimategarden.instant.y_rounding":"ui.ultimategarden.instant.path_test");}
 private String lastConflictNotice;public String lastConflictNotice(){return lastConflictNotice;}
 private void notifyOtherTest(){var language=new com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageManager(Ultimate_GardenClient.getConfigManager());lastConflictNotice=language.text("ui.ultimategarden.instant.already_running",activeTestName());com.kuangsanfeng.ultimategarden.client.platform.hint.HintManager.printRequired(lastConflictNotice);}
 public boolean yRoundingActive(){return running()&&yRounding;}
 public void toggleYRound(){toggleYRound(StartSource.MENU);}
 public void toggleYRound(StartSource source){if(running()){if(yRounding)stop("MANUAL_STOP");else notifyOtherTest();return;}yRounding=true;fixedYaw=Minecraft.getInstance().player==null?0:Minecraft.getInstance().player.getYRot();start(source);if(!running())yRounding=false;}
 private void prepareY(){yTarget=coverage.next();if(yTarget==null){stop("COMPLETED");return;}pitchIndex=(yTarget.pitch()+90)/15;group=1;aim(State.TEST_ALIGN,fixedYaw,yTarget.pitch());}
 private void beginY(){var p=Minecraft.getInstance().player;if(!p.onGround()||horizontalSpeed(p.getDeltaMovement())>=TransmissionCoastPrediction.STOP_SPEED){pre.clear();stateTick=tick;return;}
  takeoffY=p.getY();takeoffTick=-1;airborne=false;yClicked=false;yResponses=0;yAfter=null;trial=null;trialId=UUID.randomUUID().toString();
  emit("Y_ATTEMPT_START","trialId",trialId,"targetPitch",yTarget.pitch(),"plannedTick",yTarget.tick(),"takeoffY",takeoffY,"feet",xyz(p.position()),"distance",yDistance());
  keyboard.keyDown("SPACE");enter(State.Y_JUMP);
 }
 private void fireY(){if(yClicked)return;yClicked=true;var p=Minecraft.getInstance().player;yLaunch=p.position();yLaunchYaw=p.getYRot();yLaunchPitch=p.getXRot();yLaunchVelocity=p.getDeltaMovement().y;yClickTick=tick;
  trial=new TransmissionTrial(1,tick);boolean sent=mouse.click(Minecraft.getInstance());yDispatched=sent;if(sent)trial.clicked(tick);
  emit("Y_ATTEMPT","trialId",trialId,"targetPitch",yTarget.pitch(),"yaw",p.getYRot(),"pitch",yLaunchPitch,"distance",yDistance(),"plannedTick",yTarget.tick(),"takeoffY",takeoffY,"feet",xyz(yLaunch),"actualHeight",yLaunch.y-takeoffY,"actualCastTick",tick-takeoffTick,"onGround",p.onGround(),"ticksAfterTakeoff",tick-takeoffTick,"verticalVelocity",yLaunchVelocity,"clickId",trialId,"clickTick",tick,"dispatched",sent);
  enter(State.Y_WAIT);
 }
 private double yDistance(){return com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Capability.standard().distance();}
 private void tickY(){var p=Minecraft.getInstance().player;keyboard.keyUp("SPACE");
  if(state==State.Y_JUMP){
   if(!airborne&&!p.onGround()&&p.getY()>takeoffY){airborne=true;takeoffTick=tick;emit("Y_TAKEOFF","trialId",trialId,"feet",xyz(p.position()),"takeoffTick",tick);}
   if(airborne&&tick-takeoffTick>=yTarget.tick()){fireY();return;}
   if(!airborne&&tick-stateTick>40)finishY("NO_TAKEOFF");return;
  }
  emit("Y_DESCENT","trialId",trialId,"feet",xyz(p.position()),"verticalVelocity",p.getDeltaMovement().y,"onGround",p.onGround());
  if(p.onGround()&&(yResponses>0||tick-yClickTick>60)||tick-yClickTick>200)finishY(yResponses==1?"PAIRED":"UNPAIRED_OR_AMBIGUOUS");
 }
 private void finishY(String why){var p=Minecraft.getInstance().player;boolean paired=yClicked&&yDispatched&&yResponses==1&&yAfter!=null;Integer actual=yClicked&&yDispatched?(int)(yClickTick-takeoffTick):null;
  boolean suspect=paired&&yLaunch.distanceTo(yAfter)<yDistance()-2;
  if(yClicked){double yaw=Math.toRadians(yLaunchYaw),pitch=Math.toRadians(yLaunchPitch);var end=yLaunch.add(-Math.cos(pitch)*Math.sin(yaw)*yDistance(),-Math.sin(pitch)*yDistance(),Math.cos(pitch)*Math.cos(yaw)*yDistance());
   var hit=Minecraft.getInstance().level.clip(new net.minecraft.world.level.ClipContext(yLaunch,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty()));
   suspect|=hit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS&&hit.getLocation().distanceTo(yLaunch)<yDistance()-.1;
  }
  if(yClicked&&!yDispatched)why="DISPATCH_FAILED";else if(actual!=null&&actual!=yTarget.tick())why="MISSED_TARGET_TICK";
  coverage.result(yTarget,actual,paired,suspect);
  emit("Y_RESULT","trialId",trialId,"status",why,"targetPitch",yTarget.pitch(),"plannedTick",yTarget.tick(),"actualCastTick",actual,"pairedUpdates",yResponses,"paired",paired,"possibleTerrainTruncation",suspect,"terrainAssessment",paired?"CLIENT_COLLISION_OR_DISPLACEMENT_HEURISTIC_NOT_SERVER_PROOF":"UNKNOWN","landing",p.onGround()?xyz(p.position()):null,"landingY",p.onGround()?p.getY():null,"plannedSamples",520);
  trial=null;yClicked=false;yAfter=null;yResponses=0;if(coverage.next()==null){stop("COMPLETED");return;}
  resetCycle=true;clearReturn();returnStartedNanos=System.nanoTime();beginResetDiagnostics(trialId,"Y_ROUND_RETURN");alignReturn();
 }
 private void enter(State s){var from=state;state=s;stateTick=tick;stateNanos=System.nanoTime();lastFrame=0;pre.clear();resetDiagnostics.state(tick,from.name(),s.name(),"EXISTING_ENTER");}
 private void aim(State s,double yaw,double pitch){targetYaw=yaw;targetPitch=pitch;mouse.resetAim();enter(s);}
 private boolean aligned(){var c=Minecraft.getInstance();return ViewAngles.aligned(c.player.getYRot(),c.player.getXRot(),targetYaw,targetPitch,.05);}
 public void frame(){
  if(!running()||state==State.WAIT_GUI)return;var c=Minecraft.getInstance();
  if(!usable(c))return;
  boolean moving=switch(state){case START_ALIGN,RETURN_ALIGN,DOWN_ALIGN,TEST_YAW_ALIGN,TEST_ALIGN,SETTLE->true;default->false;};
  if(!moving)return;long now=System.nanoTime();double dt=lastFrame==0?0:Math.clamp((now-lastFrame)/1e9,0,.05);lastFrame=now;
  try{mouse.aim(c,targetYaw,targetPitch,300,15,dt);}catch(RuntimeException e){stop("VIEW_INPUT_FAILURE");}
 }
 private boolean usable(Minecraft c){var check=inspect(c);var failure=check.values().runtimeFailure();if(failure!=null){stop(failure,check);return false;}return true;}
 private void waitForMenu(Minecraft c,Inspection check){
  var context=check.values().contextFailure();if(context!=null){stop(context,check);return;}
  if(c.screen!=null){if(tick-stateTick>600)stop("GUI_WAIT_TIMEOUT",check);return;}
  var owner=check.values().ownerFailure();if(owner!=null){stop(owner,check);return;}
  // Observe native/Farm focus restoration; do not grab the mouse or change Focus here.
  if(!check.values().mouseRestored()){if(tick-stateTick>600)stop("GUI_RESTORE_TIMEOUT",check);return;}
  var failure=check.values().startFailure();if(failure!=null){stop(failure,check);return;}
  activate(c);
 }

 public void tick(){
  if(!running())return;tick++;var c=Minecraft.getInstance();
  resetDiagnostics.tick(tick);
  var check=inspect(c);
  if(state==State.WAIT_GUI){waitForMenu(c,check);return;}
  var context=check.values().contextFailure();if(context!=null){stop(context,check);return;}
  if(!log.healthy()){stop("LOG_WRITE_FAILURE",check);return;}
  if(!usable(c))return;
  mouse.consumed();
  if(returnStartedNanos!=0&&System.nanoTime()-returnStartedNanos>30_000_000_000L){stop("STATE_TIMEOUT_WALK");return;}
  if(System.nanoTime()-stateNanos>30_000_000_000L){stop("STATE_TIMEOUT_"+state);return;}
  if(c.player.getInventory().getSelectedSlot()!=1&&state!=State.START_ALIGN&&state!=State.SLOT){stop("SLOT_INTERFERENCE");return;}
  switch(state){
   case Y_JUMP,Y_WAIT->tickY();
   case START_ALIGN->{if(aligned()){keyboard.slot(2);enter(State.SLOT);}}
   case SLOT->{if(tick>stateTick&&c.player.getInventory().getSelectedSlot()==1)aim(State.DOWN_ALIGN,yRounding?fixedYaw:0,90);else if(tick-stateTick>40)stop("SLOT_NOT_CONFIRMED");}
   case RETURN_ALIGN->{if(aligned()){
    lastWalk=c.player.position();walkProgress=tick;enter(State.WALK);
    if(correctiveReturn){
     correctiveReturn=false;var p=c.player;var velocity=p.getDeltaMovement();
     var predicted=TransmissionCoastPrediction.predict(p.getX(),p.getZ(),velocity.x,velocity.z);
     if(insideBase(p.position())||!p.onGround()||horizontalSpeed(velocity)>=TransmissionCoastPrediction.STOP_SPEED)beginCoast("MICRO_START_RECHECK",predicted);
     else{microAdjustments++;returnPhase=ReturnPhase.PULSE;releaseResetKeys("BEFORE_MICRO_PULSE");submitResetKey("S");
      resetDiagnostics.event("RESET_MICRO_ADJUST",tick,map("microAdjustments",microAdjustments,"pulseTicks",1,"predictedStop",List.of(predicted.x(),predicted.z())),"END_CLIENT_TICK_TEST_ACTION");}
    }
    else returnPhase=ReturnPhase.APPROACH;
   }}
   case WALK->returnWalk();
   case TEST_YAW_ALIGN->{if(aligned())aim(State.TEST_ALIGN,plan.yaws().get(yawIndex),plan.pitches().get(pitchIndex));}
   case DOWN_ALIGN,TEST_ALIGN->{if(aligned()){enter(State.SETTLE);}}
   case SETTLE->{
    if(!aligned()){pre.clear();stateTick=tick;break;}
    var snap=snapshot();
    if(tick-stateTick>=3&&pre.size()==2){
     if(c.mouseHandler.isRightPressed()||c.options.keyUse.isDown()){pre.add(snap);break;}
     if(resetCycle){
      if(!insideBase(c.player.position())||!c.player.onGround()||horizontalSpeed(c.player.getDeltaMovement())>=TransmissionCoastPrediction.STOP_SPEED){
       recoverReturn("PRE_JUMP_POSITION_OR_MOTION",false);break;
      }
      if(!resetJump.begin(c.player.onGround(),c.player.getY(),tick,System.nanoTime())){stop("RESET_JUMP_NOT_GROUNDED");break;}
      releaseResetKeys("BEFORE_RESET_JUMP");submitResetKey("SPACE");
      // Keep the existing pre-sample ring across the one-Tick jump pulse.
      var from=state;state=State.RESET_JUMP;stateTick=tick;stateNanos=System.nanoTime();lastFrame=0;resetDiagnostics.state(tick,from.name(),state.name(),"JUMP_SUBMITTED");
      pre.add(snap);emit("RESET_JUMP","kind","RESET","takeoffY",c.player.getY());resetDiagnostics.jump(tick,resetJump.takeoffY());
     }else if(yRounding)beginY();else beginTrial(group);
    }
    else pre.add(snap);
   }
   case RESET_JUMP->{
    // The key was held across exactly one native client Tick; never hold it until landing.
    var beforeJumpRelease=resetDiagnostics.beforeInput(tick);keyboard.keyUp("SPACE");resetDiagnostics.released(tick,"SPACE",beforeJumpRelease);
    var action=resetJump.update(c.player.onGround(),c.player.getY(),tick,System.nanoTime());
    resetDiagnostics.event("RESET_HEIGHT_GATE",tick,map("action",action.name(),"requiredHeight",4.0,"predicate","!onGround && currentY-startY>=4.0"),"RESET_HEIGHT_DECISION");
    if(!c.player.onGround()&&!insideBase(c.player.position())){recoverReturn("AIRBORNE_OUTSIDE_BASE",true);break;}
    if(action==TransmissionResetJump.Action.CLICK){
     if(!aligned()||c.mouseHandler.isRightPressed()||c.options.keyUse.isDown()){stop("RESET_AIRBORNE_WINDOW_MISSED");break;}
     beginTrial(1);
    }else if(action==TransmissionResetJump.Action.FAILED){stop("RESET_TAKEOFF_FAILED_OR_WINDOW_MISSED");}
    else pre.add(snapshot());
   }
   case RESET_WAIT,TEST_WAIT->observe();
   default->{}
  }
 }
 // END_CLIENT_TICK: native player movement for this Tick has completed; keys below affect the next Tick.
 private static double horizontalSpeed(Vec3 velocity){return Math.hypot(velocity.x,velocity.z);}
 private boolean insideBase(Vec3 position){return Math.floor(position.x)==base.getX()&&Math.floor(position.z)==base.getZ();}
 private void clearReturn(){returnPhase=ReturnPhase.APPROACH;correctiveReturn=false;stableReturnTicks=microAdjustments=0;returnStartedNanos=0;lastCoast=coastVelocity=null;returnLeg=null;coastPrediction=null;coastDecisionTick=0;}
 private void alignReturn(){
  var p=Minecraft.getInstance().player;releaseResetKeys("RETURN_ALIGNMENT");
  if(insideBase(p.position())&&p.onGround()&&horizontalSpeed(p.getDeltaMovement())<TransmissionCoastPrediction.STOP_SPEED){aim(State.DOWN_ALIGN,yRounding?fixedYaw:0,90);return;}
  if(insideBase(p.position())){enter(State.WALK);beginCoast("RETURN_ALREADY_INSIDE",TransmissionCoastPrediction.predict(p.getX(),p.getZ(),p.getDeltaMovement().x,p.getDeltaMovement().z));return;}
  returnLeg=TransmissionCoastPrediction.leg(p.getX(),p.getZ(),base.getX(),base.getZ());
  correctiveReturn=TransmissionCoastPrediction.gap(p.getX(),p.getZ(),base.getX(),base.getZ())<.5;
  aim(State.RETURN_ALIGN,returnLeg.yaw(),0);
  resetDiagnostics.event("RESET_RETURN_LEG",tick,map("start",List.of(returnLeg.startX(),returnLeg.startZ()),"direction",List.of(returnLeg.ux(),returnLeg.uz()),"entryDistance",returnLeg.entry(),"exitDistance",returnLeg.exit(),"gap",TransmissionCoastPrediction.gap(p.getX(),p.getZ(),base.getX(),base.getZ()),"micro",correctiveReturn),"RETURN_ALIGNMENT");
 }
 private void beginCoast(String why,TransmissionCoastPrediction.Stop predicted){
  var p=Minecraft.getInstance().player;coastPrediction=predicted;coastVelocity=p.getDeltaMovement();coastDecisionTick=tick;
  stableReturnTicks=0;lastCoast=p.position();returnPhase=ReturnPhase.COAST;releaseResetKeys(why);
  resetDiagnostics.event("RESET_COAST_DECISION",tick,map("decisionTick",tick,"initialHorizontalVelocity",List.of(coastVelocity.x,coastVelocity.z),"predictedStop",List.of(predicted.x(),predicted.z()),"coastTicks",predicted.ticks(),"retention",TransmissionCoastPrediction.RETENTION,"stopSpeed",TransmissionCoastPrediction.STOP_SPEED,"reason",why,"microAdjustments",microAdjustments),"END_CLIENT_TICK_RELEASE_DECISION");
 }
 private void recoverReturn(String why,boolean airborne){
  if(returnStartedNanos==0)returnStartedNanos=System.nanoTime();resetJump.clear();enter(State.WALK);
  var p=Minecraft.getInstance().player;beginCoast(why,TransmissionCoastPrediction.predict(p.getX(),p.getZ(),p.getDeltaMovement().x,p.getDeltaMovement().z));
  if(airborne)returnPhase=ReturnPhase.WAIT_LANDING;
 }
 private void returnWalk(){
  var p=Minecraft.getInstance().player;var pos=p.position();var velocity=p.getDeltaMovement();
  if(returnStartedNanos==0)returnStartedNanos=System.nanoTime();
  if(returnPhase==ReturnPhase.WAIT_LANDING){
   if(p.onGround())beginCoast("RECOVERY_LANDED",TransmissionCoastPrediction.predict(pos.x,pos.z,velocity.x,velocity.z));return;
  }
  if(returnPhase==ReturnPhase.COAST){
   boolean still=p.onGround()&&horizontalSpeed(velocity)<TransmissionCoastPrediction.STOP_SPEED&&lastCoast!=null&&Math.hypot(pos.x-lastCoast.x,pos.z-lastCoast.z)<=.01;
   stableReturnTicks=still?stableReturnTicks+1:0;lastCoast=pos;
   if(stableReturnTicks<2)return;
   resetDiagnostics.event("RESET_ACTUAL_STOP",tick,map("decisionTick",coastDecisionTick,"initialHorizontalVelocity",List.of(coastVelocity.x,coastVelocity.z),"predictedStop",List.of(coastPrediction.x(),coastPrediction.z()),"actualStop",List.of(pos.x,pos.z),"predictionError",List.of(pos.x-coastPrediction.x(),pos.z-coastPrediction.z()),"stableTicks",stableReturnTicks,"microAdjustments",microAdjustments,"insideBaseBlock",insideBase(pos)),"END_CLIENT_TICK_STOP_CONFIRMED");
   if(insideBase(pos)){aim(State.DOWN_ALIGN,yRounding?fixedYaw:0,90);return;}
   alignReturn();return;
  }
  var predicted=TransmissionCoastPrediction.predict(pos.x,pos.z,velocity.x,velocity.z);
  if(returnPhase==ReturnPhase.PULSE){beginCoast("MICRO_PULSE_COMPLETE",predicted);return;}
  boolean stopInside=predicted.inside(base.getX(),base.getZ());boolean overshot=returnLeg!=null&&returnLeg.overshot(predicted);
  resetDiagnostics.event("RESET_WALK_DECISION",tick,map("initialHorizontalVelocity",List.of(velocity.x,velocity.z),"predictedStop",List.of(predicted.x(),predicted.z()),"coastTicks",predicted.ticks(),"predicate","floor(predictedX)==base.blockX && floor(predictedZ)==base.blockZ","result",stopInside,"overshot",overshot,"returnPredicateUsedForControl",true,"decision",stopInside?"RELEASE_PREDICTED_INSIDE":overshot?"RELEASE_OVERSHOT":"CONTINUE_WALKING","microAdjustments",microAdjustments),"END_CLIENT_TICK_BEFORE_NEXT_INPUT");
  if(stopInside||overshot||!p.onGround()){beginCoast(stopInside?"PREDICTED_INSIDE":overshot?"PREDICTED_OVERSHOT":"WAIT_GROUND",predicted);return;}
  if(pos.distanceToSqr(lastWalk)>.01){lastWalk=pos;walkProgress=tick;}if(tick-walkProgress>60){stop("RETURN_STUCK");return;}
  submitResetKey("S");
 }
 private boolean resetCycle=true;
 private Map<String,Object> predictionData(com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.FlightResult value){return map("status",value.status().name(),"yaw",value.yaw(),"pitch",value.pitch(),"clicks",value.clicks(),"points",value.points(),"end",value.end(),"error",value.error(),"confidence",value.confidence().stream().map(Enum::name).sorted().toList(),"budgetExhausted",value.budgetExhausted(),"reason",value.reason());}
 private void recordPrediction(int clicks){
  var p=Minecraft.getInstance().player;var start=new com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point(p.getX(),p.getY(),p.getZ());
  var capability=com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Capability.standard();
  var model=new com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.FlightModel();
  var delta=model.displacement(targetYaw,targetPitch,capability.distance());var goal=new com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point(start.x()+delta.x()*clicks,start.y()+delta.y()*clicks,start.z()+delta.z()*clicks);
  var prediction=model.predict(start,goal,targetYaw,targetPitch,clicks,capability);
  var strategy=Ultimate_GardenClient.getConfigManager().getGeneralConfig().instantTransmission().strategy();
  var route=new com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing.RoutePlanner().plan(start,goal,capability,strategy,DebugTerrain.current());
  emit("PREDICTION","trialId",trialId,"kind",resetCycle?"RESET":"TEST","plannedYaw",targetYaw,"plannedPitch",targetPitch,"plannedClicks",clicks,"flight",predictionData(prediction),"route",map("strategy",route.strategy().name(),"status",route.status().name(),"segments",route.segments().stream().map(this::predictionData).toList(),"points",route.points(),"lastTransmission",route.lastTransmission(),"landing",route.landing(),"error",route.error(),"budgetExhausted",route.budgetExhausted(),"reason",route.reason()),"observation","PREDICTION_ONLY_NOT_PACKET_DATA");
 }
 private void beginTrial(int clicks){
  // Reset is explicit, independent of a test angle also being 0/90.
  boolean reset=resetCycle;trialId=UUID.randomUUID().toString();trialStartNanos=System.nanoTime();trial=new TransmissionTrial(clicks,tick);
  try{recordPrediction(clicks);}catch(RuntimeException e){emit("PREDICTION_UNAVAILABLE","trialId",trialId,"reason",e.getClass().getSimpleName());}
  emit("TRIAL_START","kind",reset?"RESET":"TEST","trialId",trialId,"targetYaw",targetYaw,"targetPitch",targetPitch,"plannedClicks",clicks,"plannedTicks",java.util.stream.IntStream.range(0,clicks).map(i->2*i).boxed().toList(),"start",xyz(Minecraft.getInstance().player.position()),"baseBlock",List.of(base.getX(),base.getY(),base.getZ()));
  var before=pre.snapshot();enter(reset?State.RESET_WAIT:State.TEST_WAIT);
  for(var s:before)emitSnapshot(s);
  dispatch(0);observe();
 }

 private void dispatch(int index){
  var c=Minecraft.getInstance();var resetBeforeClick=resetDiagnostics.site(tick,"BEFORE_RIGHT_CLICK");boolean sent=mouse.click(c);if(sent)trial.clicked(tick);else trial.anomaly=true;
  emit("RIGHT_CLICK","trialId",trialId,"kind",state==State.RESET_WAIT?"RESET":"TEST","clickIndex",index+1,"plannedTick",2*index,"actualTick",tick-trial.firstTick,"relativeMs",(System.nanoTime()-trialStartNanos)/1e6,"intervalTicks",lastActualClick<0?null:tick-lastActualClick,"dispatched",sent,"feet",xyz(c.player.position()),"yaw",c.player.getYRot(),"pitch",c.player.getXRot(),"slot",c.player.getInventory().getSelectedSlot()+1);
  if(state==State.RESET_WAIT)resetDiagnostics.event("RESET_RIGHT_CLICK",tick,map("before",resetBeforeClick,"plannedTick",trial.firstTick+2*index,"actualTick",tick,"dispatched",sent,"slot",c.player.getInventory().getSelectedSlot()+1),"AFTER_RIGHT_CLICK_SUBMISSION");
  if(sent)lastActualClick=tick;
 }
 private long lastActualClick=-1;
 private Snapshot snapshot(){var c=Minecraft.getInstance();var p=c.player;return new Snapshot(tick,System.nanoTime(),map("feet",xyz(p.position()),"velocity",xyz(p.getDeltaMovement()),"yaw",p.getYRot(),"pitch",p.getXRot(),"onGround",p.onGround(),"forward",c.options.keyUp.isDown(),"back",c.options.keyDown.isDown(),"left",c.options.keyLeft.isDown(),"right",c.options.keyRight.isDown(),"jump",c.options.keyJump.isDown(),"sneak",c.options.keyShift.isDown()));}
 private void emitSnapshot(Snapshot s){var data=new LinkedHashMap<String,Object>(s.data());data.put("trialId",trialId);data.put("relativeTick",s.tick()-trial.firstTick);data.put("relativeMs",(s.nanos()-trialStartNanos)/1e6);log.event("SNAPSHOT",s.tick(),resetDiagnostics.decorate(data));}
 private void observe(){
  int due=trial.due(tick);if(due>0)dispatch(due);
  var s=snapshot();emitSnapshot(s);var p=Minecraft.getInstance().player;
  trial.observe(new TransmissionTrial.Sample(tick,s.nanos(),p.getX(),p.getY(),p.getZ(),p.getDeltaMovement().y,p.onGround()));
  if(state==State.TEST_WAIT&&trial.complete()&&(group<3||pitchIndex+1<plan.pitches().size()||yawIndex+1<plan.yaws().size()))beginResetDiagnostics(trialId,"FORMAL_LANDING_OBSERVED");
  if(trial.complete()&&!trial.timedOut(tick)&&(trial.lastResponse<trial.lastClick||trial.actual<trial.planned||state==State.TEST_WAIT&&trial.updates<trial.actual))return;
  if(!trial.complete()&&!trial.timedOut(tick))return;
  boolean reset=state==State.RESET_WAIT;String result=trial.complete()&&trial.updates>=trial.actual?"LANDED_ENDPOINT_PROVISIONAL":trial.updates==0?"NO_IDENTIFIABLE_RESPONSE":trial.updates<trial.actual?"LAST_USE_UNCONFIRMED":"ENDPOINT_OR_LANDING_TIMEOUT";
  emit("RESULT","trialId",trialId,"kind",reset?"RESET":"TEST","plannedClicks",trial.planned,"actualClicks",trial.actual,"identifiableUpdates",trial.updates,"positionPackets",trial.packets,"successfulUses","UNCONFIRMED","pairingReliable",trial.planned==1&&trial.actual==1&&trial.updates==1,"endpoint",sample(trial.endpoint),"landing",sample(trial.landing),"status",result,"schedulingAnomaly",trial.anomaly,"possibleTerrainTruncation","UNKNOWN","endpointCertain",false,"possibleInputInterference",!aligned());
  hudLastResult=result;lastTrialHud=captureHud();
  if(reset){
   boolean resetFailed=!trial.complete()||Math.abs(p.getX()-(base.getX()+.5))>.01||Math.abs(p.getZ()-(base.getZ()+.5))>.01||Math.abs(p.getY()-baseY)>.01;
   resetDiagnostics.event("RESET_POSITION_CHECK",tick,map("landingObserved",trial.complete(),"errorX",p.getX()-(base.getX()+.5),"errorY",p.getY()-baseY,"errorZ",p.getZ()-(base.getZ()+.5),"tolerance",.01,"toleranceX",.01,"toleranceY",.01,"toleranceZ",.01,"landingSample",trial.landing==null?null:map("feet",List.of(trial.landing.x(),trial.landing.y(),trial.landing.z()),"tick",trial.landing.tick(),"captureNanos",trial.landing.nanos()),
    "xzCentered",!(Math.abs(p.getX()-(base.getX()+.5))>.01||Math.abs(p.getZ()-(base.getZ()+.5))>.01),"yMatches",!(Math.abs(p.getY()-baseY)>.01),"actualFailurePredicateResult",resetFailed,
    "failurePredicate","!trial.complete() || abs(errorX)>.01 || abs(errorZ)>.01 || abs(errorY)>.01","code",resetFailed?"RESET_POSITION_OR_HEIGHT_FAILED":"BASE_CONFIRMED"),"RESET_LANDING_CHECK");
   if(resetFailed){stop("RESET_POSITION_OR_HEIGHT_FAILED");return;}
   baseConfirmed=true;resetCycle=false;clearReturn();emit("BASE_CONFIRMED","feet",xyz(p.position()));resetDiagnostics.end(tick,"BASE_CONFIRMED");trial=null;lastActualClick=-1;if(yRounding)prepareY();else aim(State.TEST_YAW_ALIGN,plan.yaws().get(yawIndex),0);
  }else{
   trial=null;lastActualClick=-1;
   if(++group>3){group=1;if(++pitchIndex>=plan.pitches().size()){pitchIndex=0;yawIndex++;}}
   if(yawIndex>=plan.yaws().size()){stop("COMPLETED");return;}
   if(!p.onGround()){stop("LANDING_TIMEOUT");return;}resetCycle=true;clearReturn();returnStartedNanos=System.nanoTime();beginResetDiagnostics(trialId,"RETURN_BEGIN_AFTER_TRIAL");alignReturn();
  }
 }
 /** Called around vanilla's on-client-thread position application, not packet arrival on Netty. */
 public void positionUpdate(Vec3 before,Vec3 after){
  if(!running()||trial==null&&!resetDiagnostics.active())return;double jump=before.distanceTo(after);boolean identifiable=jump>.05;
  if(trial!=null&&(identifiable||state==State.RESET_WAIT))trial.response(tick,identifiable);
  if(yRounding&&state==State.Y_WAIT&&yClicked){if(identifiable){yResponses++;if(yResponses==1)yAfter=after;}double nominal=yLaunch.y-yDistance()*Math.sin(Math.toRadians(yLaunchPitch));emit("Y_POSITION_PAIR","trialId",trialId,"clickId",trialId,"clickTick",yClickTick,"updateTick",tick,"order",positionOrder+1,"pairingCandidate",identifiable,"before",xyz(before),"after",xyz(after),"launchFeet",xyz(yLaunch),"launchVerticalVelocity",yLaunchVelocity,"nominalY",nominal,"actualY",after.y,"nominalError",after.y-nominal,"integerY",Math.abs(after.y-Math.rint(after.y))<1e-9,"responseNanos",System.nanoTime());}
  var site=resetDiagnostics.site(tick,"PLAYER_POSITION_PACKET_APPLIED");
  emit("POSITION_UPDATE","trialId",trial==null?null:trialId,"relativeTick",trial==null?null:tick-trial.firstTick,"relativeMs",trial==null?null:(System.nanoTime()-trialStartNanos)/1e6,"eventOrder",++positionOrder,"before",xyz(before),"after",xyz(after),"identifiable",identifiable,"clickPairing","UNCONFIRMED","state",state.name(),"site",site);
 }
 private long positionOrder;
 public void stop(String why){if(running())stop(why,inspect(Minecraft.getInstance()));}
 private void stop(String why,Inspection inspection){
  if(!running())return;
  String stage=why.equals("COMPLETED")?"COMPLETED":why.equals("MANUAL_STOP")?"MANUAL_STOP":"RUNNING_STOP";
  finalHud=(why.equals("COMPLETED")&&lastTrialHud!=null?lastTrialHud:captureHud()).stopped(why,stage);
  if(!why.equals("COMPLETED")&&!why.equals("MANUAL_STOP"))diagnose(why,stage,state.name(),inspection);
  if(yRounding&&coverage!=null)emit("Y_SUMMARY","pitches",coverage.summary(),"mismatchedOrUnpaired",coverage.mismatches(),"blocked",coverage.blocked());
  var stopFrom=state;resetJump.clear();clearReturn();sessionGeneration++;reason=why;emit("CANCEL_OR_FINISH","trialId",trialId,"reason",why,"partialActualClicks",trial==null?0:trial.actual);state=State.STOPPED;resetDiagnostics.state(tick,stopFrom.name(),state.name(),why);
  try{mouse.cancel(Minecraft.getInstance());}finally{if(keyboard!=null)releaseResetKeys("TEST_STOP_CLEANUP");resetDiagnostics.end(tick,why);if(log!=null){log.close(tick,why);draining.add(log);}log=null;keyboard=null;world=connection=null;plan=null;trial=null;trialId=null;base=null;pre.clear();lastFrame=0;lastActualClick=-1;resetCycle=true;yRounding=false;}
 }
 public void register(){
  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c->{try{tick();}catch(RuntimeException e){stop("TEST_EXCEPTION_"+e.getClass().getSimpleName());}});
  ClientContextEvents.onDisconnect((h,c)->stop("DISCONNECTED"));ClientContextEvents.onChange(e->{if(e.change()!=ClientContextEvents.Change.ACCOUNT_AFTER_LOAD)stop(e.change()==ClientContextEvents.Change.WORLD_CHANGED?"WORLD_CHANGED":"ACCOUNT_CHANGED");});
  ClientContextEvents.onStopping(c->{stop("CLIENT_STOPPING");for(var l:draining)l.awaitClose(2000);});
 }
 private static List<Double> xyz(Vec3 v){return List.of(v.x,v.y,v.z);}
 private static Object sample(TransmissionTrial.Sample s){return s==null?null:map("feet",List.of(s.x(),s.y(),s.z()),"tick",s.tick(),"nanos",s.nanos());}
 private void emit(String type,Object... pairs){if(log!=null)log.event(type,tick,resetDiagnostics.decorate(map(pairs)));}
 private static Map<String,Object> map(Object... pairs){var m=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
}
