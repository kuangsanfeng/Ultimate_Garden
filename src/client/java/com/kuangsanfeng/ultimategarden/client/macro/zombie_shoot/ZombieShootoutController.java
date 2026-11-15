package com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot;
import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import com.kuangsanfeng.ultimategarden.client.config.*;
import static com.kuangsanfeng.ultimategarden.client.config.ZombieShootoutConfig.Setting.*;
import net.minecraft.client.Minecraft;import net.minecraft.core.*;import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;import java.util.*;
import org.lwjgl.glfw.GLFW;
/** Independent tick state machine. Models are read-only; no farm actions or custom packets. */
public final class ZombieShootoutController {
 public static final ZombieShootoutController INSTANCE=new ZombieShootoutController();
 public enum State {STOPPED,WAITING_FOR_WORLD,SELECTING_TARGET,AIMING,READY_TO_FIRE,WAITING_FOR_COOLDOWN,FIRING,WAITING_FOR_ARROW_CONFIRMATION,STOPPING,ERROR}
 public record InterceptDebug(Vec3 point,String classification,UUID targetUuid,int entityId,long tick){}
 private volatile InterceptDebug interceptDebug;
 // HUD-only copy of the latest existing solve; never used by control decisions.
 private ShootoutModel.Solution hudSolution;
 public record HudSnapshot(boolean running,State state,String classification,String tag,Double measuredSpeed,
   Double predictionSpeed,Double targetYaw,Double targetPitch,Double aimError,boolean aimReady,
   double tolerance,Double correctionThreshold,String aimMode,double cooldownSeconds,double remainingSeconds,
   boolean confirmationEnabled,String confirmationState,String attemptState,Double ping,Double latencyTicks){}
 public HudSnapshot hudSnapshot(){
  var c=Minecraft.getInstance();var cfg=config();var t=current;
  boolean live=running&&world==c.level&&t!=null&&c.player!=null&&valid(t);
  var solution=live?hudSolution:null;
  Double error=solution==null?null:ShootoutAimCompletion.error(c.player.getYRot(),c.player.getXRot(),solution.yaw(),solution.pitch());
  boolean ready=error!=null&&!aimCorrection.active()&&error<=cfg.get(AIM_TOLERANCE)+1e-9;
  return new HudSnapshot(running,state,live?(t.lamp==null?t.classification:"LAMP"):null,live?t.tag:null,
    live&&t.lamp==null?t.speed.measuredSpeed():null,live&&t.estimate!=null?Math.abs(t.estimate.signedSpeed()):null,
    solution==null?null:solution.yaw(),solution==null?null:solution.pitch(),error,ready,cfg.get(AIM_TOLERANCE),
    live&&aimCorrection.active()?aimCorrection.threshold():null,solution==null?null:mouse.aimMode(),
    cfg.get(COOLDOWN),running?shotClock.remainingTicks(System.nanoTime())/20:0,
    cfg.isArrowSpawnConfirmationEnabled(),!running?"IDLE":arrowConfirmation.pending()?"WAITING":reason.equals("ARROW_CONFIRMED")?"CONFIRMED":"IDLE",
    running&&lastDispatchedAttempt!=null?"DISPATCHED":state==State.FIRING?"FIRING":"IDLE",
    running&&ping>=0?ping:null,running&&ping>=0?ping/50.:null);
 }
 public InterceptDebug interceptDebug(Object level){var value=interceptDebug;var t=current;return running&&world==level&&value!=null&&value.tick()==tick&&t!=null&&t.lamp==null&&t.entity.getId()==value.entityId()&&t.uuid.equals(value.targetUuid())&&valid(t)?value:null;}
 private void publishIntercept(ShootoutModel.Solution value){hudSolution=value;var t=current;interceptDebug=value!=null&&t!=null&&t.lamp==null&&ShootoutModel.finite(value.target())?new InterceptDebug(value.target(),t.classification,t.uuid,t.entity.getId(),tick):null;}
 private State state=State.STOPPED;private String reason="NONE";private ShootoutModel model;private Object world;private Vec3 origin;
 private long tick,revision,order,shotId,lastRescanTick=-1;private double ping=-1;private int scanCursor;private boolean running,sceneMatched;
 private final com.kuangsanfeng.ultimategarden.client.anti_check.ViewDeviationCheckManager viewCheck=new com.kuangsanfeng.ultimategarden.client.anti_check.ViewDeviationCheckManager(()->{diagnosticGate("AUTO","VIEW_DEVIATION_EXCEEDED",hudSolution,false,true);stop("VIEW_DEVIATION_EXCEEDED");});
 private final ShootoutRtt rtt=new ShootoutRtt();
 public String pingStatus(){return rtt.status().name();}
 public boolean onRttPong(Object listener,long token){
  if(!rtt.owns(token))return false;long received=System.nanoTime();var c=Minecraft.getInstance();
  c.execute(()->{if(running&&c.getConnection()==listener&&rtt.pong(listener,token,received))ping=rtt.milliseconds();});return true;
 }
 private void measureRtt(){var connection=Minecraft.getInstance().getConnection();rtt.tick(connection,System.nanoTime(),config().get(PING_INTERVAL),token->{if(connection!=null)connection.send(new net.minecraft.network.protocol.ping.ServerboundPingRequestPacket(token));});var value=rtt.milliseconds();ping=value==null?-1:value;}
 private record AimPlan(Target target,long tick,Vec3 eye,Vec3 initial,double latency,ShootoutModel.Solution solution){}
 private AimPlan selectionPlan;
 private final ShootoutCandidateSelection.Relock<Target> relockRound=new ShootoutCandidateSelection.Relock<>();
 private final Set<Target> rejectedThisTick=Collections.newSetFromMap(new IdentityHashMap<>());
 private final ShootoutTargetLoss targetLoss=new ShootoutTargetLoss();
 private final ShootoutAimCorrection aimCorrection=new ShootoutAimCorrection();
 private final ShootoutAimDiagnostics aimDiagnostics=new ShootoutAimDiagnostics();
 private ShootoutModel.Solution gateErrorSolution;private Double gateError;private float gateYaw,gatePitch;private long gateErrorTick=-1,diagnosticManualAttempt;
 private String diagnosticCollisionReason;private long diagnosticManualBatch;
 private void captureGateError(ShootoutModel.Solution solution,Double error,float yaw,float pitch){if(aimDiagnostics.active()){gateErrorSolution=solution;gateError=error;gateYaw=yaw;gatePitch=pitch;gateErrorTick=tick;}}
 private void diagnosticGate(String source,String reason,ShootoutModel.Solution used,boolean allowed,boolean toleranceEnforced){
  if(!aimDiagnostics.active())return;var c=Minecraft.getInstance();
  float yaw=c.player==null?Float.NaN:c.player.getYRot(),pitch=c.player==null?Float.NaN:c.player.getXRot();
  boolean captured=used!=null&&gateErrorSolution==used&&gateErrorTick==tick&&gateYaw==yaw&&gatePitch==pitch;
  aimDiagnostics.gate(current==null?null:current.tag,used,source,yaw,pitch,captured?gateError:null,config().get(AIM_TOLERANCE)*config().get(SHOOT_RATIO),aimCorrection.active(),aimCorrection.active()?aimCorrection.threshold():null,allowed,reason,toleranceEnforced,arrowConfirmation.pending(),captured);
 }
 private void diagnosticConfirmation(String stage,String why){aimDiagnostics.confirmation(stage,shotSource.name(),lastDispatchedAttempt,current==null?null:current.tag,null,null,why);}
 private void syncAimDiagnostics(){
  try{
   boolean master=ZombieShootCollector.INSTANCE.logsEnabled(),selected=config().logEnabled(ZombieShootLogCategory.AIM_SHOT_DIAGNOSTICS),wanted=running&&master&&selected;
   var settings=wanted&&!aimDiagnostics.active()?ZombieShootSnapshot.map("aimUpdateRateHz",config().get(AIM_UPDATE_RATE),"aimPeakSpeedDegPerSecond",config().get(AIM_SPEED),"aimCurveCurvatureDeg",config().get(AIM_CURVATURE),"aimCompletionToleranceDeg",config().get(AIM_TOLERANCE),"aimCorrectionRandomRatio",config().get(AIM_CORRECTION_RATIO),"shootToleranceRatio",config().get(SHOOT_RATIO),"shootToleranceDeg",config().get(AIM_TOLERANCE)*config().get(SHOOT_RATIO),"currentTargetTag",current==null?null:current.tag,"arrowConfirmationPending",arrowConfirmation.pending(),"dtMsDefinition","effective step interval supplied to Tanh/native input, including the existing 50 ms cap","quantizationResidualDefinition","requested minus native-representable delta; not accumulated or fed back"):Map.<String,Object>of();
   aimDiagnostics.sync(wanted,wanted?net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("logs/ultimategarden/zombie shoot"):null,tick,settings,!running?"MACRO_NOT_RUNNING":!master?"LOGS_DISABLED":"CATEGORY_DISABLED");
  }catch(RuntimeException unavailable){aimDiagnostics.end(tick,"DIAGNOSTICS_UNAVAILABLE");}
 }
 private boolean aimReady(Minecraft c,ShootoutModel.Solution s,long now){float yaw=c.player.getYRot(),pitch=c.player.getXRot();Double error=ShootoutAimCompletion.error(yaw,pitch,s.yaw(),s.pitch());captureGateError(s,error,yaw,pitch);return aimCorrection.ready(error,config().get(AIM_TOLERANCE),config().get(AIM_CORRECTION_RATIO),now,Math::random);}
 private void resetAimCycle(){clearFrameAim();targetLoss.clear();interceptDebug=null;hudSolution=null;aimCorrection.clear();viewCheck.clear();mouse.resetAim();}
 private final ShootoutMouseInput mouse=new ShootoutMouseInput();
 private final ShootoutAimClock aimClock=new ShootoutAimClock();
 private ShootoutModel.Solution frameAimSolution;private Target frameAimTarget;
 private void clearFrameAim(){frameAimSolution=null;frameAimTarget=null;aimClock.reset();}
 private void requestFrameAim(ShootoutModel.Solution solution){frameAimTarget=current;frameAimSolution=solution;}
 /** Read only the most recent Tick solve; never solve, select, shoot or send packets from a frame. */
 public void aimFrame(){
  var c=Minecraft.getInstance();if(!c.isSameThread())return;
  try{
   var solution=frameAimSolution;var target=frameAimTarget;
   if(!running||!config().isAutoAimEnabled()||solution==null||target==null||target!=current||c.player==null||!c.player.isAlive()||c.level!=world||c.screen!=null||c.getOverlay()!=null||c.isPaused()||!c.isWindowActive()||!c.mouseHandler.isMouseGrabbed()||c.player.position().distanceToSqr(origin)>1||!valid(target)){
    clearFrameAim();return;
   }
   Double error=ShootoutAimCompletion.error(c.player.getYRot(),c.player.getXRot(),solution.yaw(),solution.pitch());
   double threshold=aimCorrection.active()?aimCorrection.threshold():config().get(AIM_TOLERANCE);
   boolean needsMotion=error!=null&&(error>threshold+1e-9||config().autoShootEffective()&&error>config().get(AIM_TOLERANCE)*config().get(SHOOT_RATIO)+1e-9);
   if(!needsMotion){aimClock.reset();return;}
   long aimNow=System.nanoTime();int updateRate=(int)config().get(AIM_UPDATE_RATE);double dt=aimClock.step(aimNow,updateRate);
   if(dt>0){if(aimDiagnostics.active())aimDiagnostics.inputContext(target.tag,solution,tick,updateRate);mouse.aim(c,solution.yaw(),solution.pitch(),config().get(AIM_SPEED),config().get(AIM_CURVATURE),dt);}
  }catch(Exception e){stop("ERROR: "+e.getMessage());state=State.ERROR;com.kuangsanfeng.ultimategarden.Ultimate_Garden.LOGGER.error("Shootout frame aim stopped",e);}
 }

 private final Map<UUID,Target> targets=new LinkedHashMap<>();private final LinkedHashMap<BlockPos,Target> lamps=new LinkedHashMap<>();private final List<Shot> shots=new ArrayList<>();private Target current;private final ShootoutShotClock shotClock=new ShootoutShotClock();
 
 private static final class Target {
  ShootoutPolicy.Metadata metadata;Zombie entity;UUID uuid;String tag,classification;BlockPos lamp;long first,last;final ShootoutTargetRound round=new ShootoutTargetRound();int matchEvidence;ShootoutBabyStall stall=new ShootoutBabyStall();ShootoutSpeed speed=new ShootoutSpeed();ShootoutModel.Projection projection;ShootoutSpeed.Estimate estimate;
 }
 private final ShootoutArrowConfirmation arrowConfirmation=new ShootoutArrowConfirmation();
 private ShootoutArrowConfirmation.Attempt lastDispatchedAttempt;
 private enum ShotSource { AUTO,MANUAL }
 private ShotSource shotSource=ShotSource.AUTO;private boolean manualRightDown;private long manualAutomaticNotBefore;
 public void onManualShotBlockedByInterceptor(int button,int action){
  if(button==1&&action==1&&running&&aimDiagnostics.active())diagnosticGate("MANUAL","INPUT_INTERCEPTOR",hudSolution,false,config().manualShotRestrictionEffective());
 }
 public boolean blockManualShot(int button,int action){
  if(button!=1||action!=1||!running||!config().manualShotRestrictionEffective())return false;
  var c=Minecraft.getInstance();
  if(c.player==null||c.level==null||c.screen!=null||c.getOverlay()!=null)return false;
  if(c.level!=world){diagnosticGate("MANUAL","WORLD_INVALID",null,false,true);return true;}
  if(current==null){diagnosticGate("MANUAL","NO_TARGET",null,false,true);return true;}
  if(!valid(current)){diagnosticGate("MANUAL","TARGET_INVALID",hudSolution,false,true);return true;}
  if(hudSolution==null){diagnosticGate("MANUAL","NO_INTERCEPT_SOLUTION",null,false,true);return true;}
  boolean admitted=shootAligned(c,hudSolution);diagnosticGate("MANUAL",admitted?"MANUAL_INPUT_ADMITTED":"ANGLE_EXCEEDED",hudSolution,admitted,true);return !admitted;
 }
 public void onManualButton(int button,int action){
  if(button!=1)return;if(action==0){manualRightDown=false;return;}if(action!=1||manualRightDown)return;manualRightDown=true;
  var c=Minecraft.getInstance();if(!c.isSameThread()||!running||config().autoShootEffective()||c.player==null||c.level!=world||c.screen!=null||c.getOverlay()!=null||current==null||!valid(current))return;
  long now=System.nanoTime();if(arrowConfirmation.confirmation()!=null)return;if(arrowConfirmation.timedOut(now)){diagnosticConfirmation("TIMEOUT","MANUAL_SHOT_TIMEOUT");arrowConfirmation.cancel();lastDispatchedAttempt=null;}
  if(!arrowConfirmation.pending())observeArrows(c,now); // baseline before native right-click handling
  if(!config().manualShotRestrictionEffective())diagnosticGate("MANUAL","MANUAL_INPUT_ADMITTED",hudSolution,true,false);
  diagnosticManualAttempt=aimDiagnostics.nextAttempt();diagnosticManualBatch=aimDiagnostics.batchGeneration();shotSource=ShotSource.MANUAL;manualAutomaticNotBefore=now+(long)((config().get(COOLDOWN)+config().get(JITTER_MAX))*1e9);
  var eye=c.player.getEyePosition();
  // No valid solution is required for player input; use the solver's 40-tick upper bound for impact verification if unavailable.
  lastDispatchedAttempt=new ShootoutArrowConfirmation.Attempt(tick,now,eye,model.velocity(c.player.getYRot(),c.player.getXRot()).normalize(),hudSolution==null?40:hudSolution.ticks(),Math.max(0,ping));
  aimDiagnostics.linkAttempt(lastDispatchedAttempt.nanos(),diagnosticManualAttempt);
  if(config().isArrowSpawnConfirmationEnabled()){arrowConfirmation.attemptedManual(lastDispatchedAttempt,config().get(COOLDOWN),config().get(JITTER_MAX));diagnosticConfirmation("START","MANUAL_ATTEMPT_REGISTERED");state=State.WAITING_FOR_ARROW_CONFIRMATION;}
 }
 public void manualInputProcessed(){if(diagnosticManualAttempt!=0){aimDiagnostics.dispatch(diagnosticManualBatch==aimDiagnostics.batchGeneration()?diagnosticManualAttempt:0,"MANUAL",true,"NATIVE_BUTTON_RETURN",current==null?null:current.tag);diagnosticManualAttempt=0;}var c=Minecraft.getInstance();if(c.screen!=null||c.getOverlay()!=null||c.level!=world)return;if(running&&shotSource==ShotSource.MANUAL&&!config().autoShootEffective()&&!config().isArrowSpawnConfirmationEnabled()&&lastDispatchedAttempt!=null)finishDispatchedShot();}

 private record Shot(long id,Target target,long due,ShootoutTargetRound.Attempt roundAttempt,double health,double ping,double flight,UUID arrowUuid,Integer arrowEntityId,Long confirmationTick,long attemptTick,long attemptNanos,boolean arrowConfirmed){}
 private void observeArrows(Minecraft c,long now){for(var e:c.level.entitiesForRendering()){
  if(!(e instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow) )continue;
  arrowConfirmation.observe(new ShootoutArrowConfirmation.Observation(e.getUUID(),e.getId(),BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString(),null,e.position(),e.getDeltaMovement(),tick,now));
 }}
 private void finishConfirmedShot(){var confirmed=arrowConfirmation.confirmation();if(confirmed==null||current==null)return;
  aimDiagnostics.confirmation("SUCCESS",shotSource.name(),confirmed.attempt(),current.tag,confirmed.arrow().uuid(),confirmed.confirmationTick(),"NEW_ARROW_OBSERVED");
  completeShot(confirmed.attempt(),confirmed.arrow().uuid(),confirmed.arrow().entityId(),confirmed.confirmationTick(),confirmed.confirmationNanos(),true);
 }
 private void finishDispatchedShot(){if(lastDispatchedAttempt==null||current==null)return;var attempt=lastDispatchedAttempt;diagnosticConfirmation("SKIPPED","CONFIRMATION_DISABLED");arrowConfirmation.cancel();arrowConfirmation.rememberDispatched(attempt);completeShot(attempt,null,null,null,attempt.nanos(),false);}
 private void completeShot(ShootoutArrowConfirmation.Attempt attempt,UUID arrowUuid,Integer arrowEntityId,Long confirmedTick,long referenceTime,boolean confirmed){
  var target=current;var roundAttempt=target.round.dispatched();
  long due=referenceTime+(long)((attempt.flightTicks()+attempt.ping()/50.+(confirmed?4:5))*50_000_000);
  shots.add(new Shot(++shotId,target,due,roundAttempt,target.lamp==null?target.entity.getHealth():0,attempt.ping(),attempt.flightTicks(),arrowUuid,arrowEntityId,confirmedTick,attempt.tick(),attempt.nanos(),confirmed));
  lastDispatchedAttempt=null;arrowConfirmation.cancel();current=null;resetAimCycle();current=select();state=State.SELECTING_TARGET;reason=confirmed?"ARROW_CONFIRMED":"SHOT_DISPATCHED_UNVERIFIED";
 }
 private boolean confirmationExpired(long now){if(!arrowConfirmation.timedOut(now))return false;diagnosticConfirmation("TIMEOUT",shotSource==ShotSource.MANUAL?"MANUAL_SHOT_TIMEOUT":"SHOT_CONFIRMATION_TIMEOUT");if(shotSource==ShotSource.MANUAL){arrowConfirmation.cancel();lastDispatchedAttempt=null;state=State.AIMING;reason="MANUAL_SHOT_TIMEOUT";return false;}com.kuangsanfeng.ultimategarden.Ultimate_Garden.LOGGER.warn("Shootout arrow confirmation timed out: {}",arrowConfirmation.detection());stop("SHOT_CONFIRMATION_TIMEOUT");state=State.ERROR;return true;}

 private final ShootoutAutoStart autoStart=new ShootoutAutoStart();
 public void cancelAutoStart(){autoStart.cancel();}
 private ShootoutAutoStart.Context autoStartContext(Minecraft c){if(c.player==null||!c.player.isAlive()||c.player.isRemoved()||c.level==null||c.getConnection()==null)return null;return new ShootoutAutoStart.Context(c.getConnection(),c.level,c.player,Ultimate_GardenClient.getConfigManager().accountRevision());}
 public void onStartMessage(String text){var c=Minecraft.getInstance();if(!c.isSameThread())return;autoStart.receive(text,System.nanoTime(),config().isAutoStartZombieShootout(),running,autoStartContext(c));}
 private void tickAutoStart(){var c=Minecraft.getInstance();if(autoStart.due(System.nanoTime(),config().isAutoStartZombieShootout(),running,autoStartContext(c)))start();}
 public boolean enabled(){return running;}public State state(){return state;}public String status(){return state+" / "+reason+" / Arrow Confirmation: "+(config().isArrowSpawnConfirmationEnabled()?"ON":"OFF");}public String pingText(){return ping<0?rtt.status()==ShootoutRtt.Status.MEASURING?"Measuring...":"Unavailable":String.format(Locale.ROOT,"%.2f ms",ping);}public String targetText(){return current==null?"None":current.lamp!=null?"Active Redstone Lamp":current.classification+" ["+current.tag+"]";}
 public static boolean intercepting(){var s=INSTANCE;var c=Minecraft.getInstance();return s.running&&c.screen==null&&!ShootoutMouseInput.dispatching();}
 public static boolean interceptingAim(){return intercepting()&&INSTANCE.config().isAutoAimEnabled();}
 public static boolean interceptingShoot(){return intercepting()&&INSTANCE.config().autoShootEffective();}
 private void automationChanged(){clearFrameAim();if(!running)return;if(shotSource==ShotSource.AUTO||config().autoShootEffective()){if(arrowConfirmation.pending())diagnosticConfirmation("CANCELLED","AUTOMATION_CHANGED");arrowConfirmation.cancel();lastDispatchedAttempt=null;}mouse.cancel(Minecraft.getInstance());if(config().isAutoAimEnabled())mouse.clearPendingMotion(Minecraft.getInstance());if(!config().isAutoAimEnabled()){aimCorrection.clear();viewCheck.clear();}}
 private boolean shootAligned(Minecraft c,ShootoutModel.Solution solution){float yaw=c.player.getYRot(),pitch=c.player.getXRot();return ShootoutAimCompletion.aligned(yaw,pitch,solution.yaw(),solution.pitch(),config().get(AIM_TOLERANCE)*config().get(SHOOT_RATIO),aimDiagnostics.active()?error->captureGateError(solution,error,yaw,pitch):null);}
 private boolean holdInvalidTarget(long now){
  diagnosticGate("AUTO","TARGET_INVALID",hudSolution,false,true);
  if(targetLoss.expired(false,now)){if(arrowConfirmation.pending())diagnosticConfirmation("CANCELLED","TARGET_LOST");arrowConfirmation.cancel();lastDispatchedAttempt=null;selectionPlan=null;current=null;resetAimCycle();state=State.SELECTING_TARGET;reason="TARGET_LOST";return false;}
  hudSolution=null;interceptDebug=null;state=State.SELECTING_TARGET;reason="TARGET_LOST_WAIT";return true;
 }
 private ZombieShootoutConfig config(){return Ultimate_GardenClient.getConfigManager().getZombieShootoutConfig();}
 public void toggle(){if(running)stop("MANUAL_STOP");else start();}
 public void start(){cancelAutoStart();if(running)return;var c=Minecraft.getInstance();try{
  if(Ultimate_GardenClient.getConfigManager().isPendingRestart(ConfigManager.Module.ZOMBIE_SHOOTOUT))throw new IllegalStateException("CONFIG_RESTART_REQUIRED");
  var farm=Ultimate_GardenClient.getFarmController();if(farm.shouldPauseByHotkey()||farm.getCommand().hasPendingWork())throw new IllegalStateException("FARM_BUSY");
  if(c.player==null||c.level==null)throw new IllegalStateException("WORLD_UNAVAILABLE");
  if(c.options.keyUp.isDown()||c.options.keyDown.isDown()||c.options.keyLeft.isDown()||c.options.keyRight.isDown()||c.options.keyJump.isDown()||c.options.keyShift.isDown()||c.options.keySprint.isDown())throw new IllegalStateException("RELEASE_MOVEMENT_KEYS_BEFORE_START");
  if(c.options.keyAttack.isDown()||c.mouseHandler.isLeftPressed())throw new IllegalStateException("ATTACK_INPUT_BUSY");
  if(c.player.isUsingItem()||c.options.keyUse.isDown()||c.mouseHandler.isRightPressed())throw new IllegalStateException("USE_INPUT_BUSY");
  var use=((com.kuangsanfeng.ultimategarden.client.mixin.KeyMappingAccessor)c.options.keyUse).getKey();if(!use.getName().equals("key.mouse.right"))throw new IllegalStateException("USE_KEY_MUST_BE_RIGHT_MOUSE");
  if(com.kuangsanfeng.ultimategarden.client.ui.hud.HudAdjustmentController.isActive())throw new IllegalStateException("HUD_ADJUSTMENT_ACTIVE");
  if(model==null)model=ShootoutModel.load();world=c.level;origin=c.player.position();revision=Ultimate_GardenClient.getConfigManager().accountRevision();tick=0;lastRescanTick=-1;relockRound.clear();lastDispatchedAttempt=null;sceneMatched=false;order=0;shotId=0;shotClock.reset();ping=-1;scanCursor=0;arrowConfirmation.reset(c.player.getUUID());shotSource=ShotSource.AUTO;manualRightDown=false;manualAutomaticNotBefore=0;observeArrows(c,System.nanoTime());mouse.clearPendingMotion(c);running=true;reason="NONE";state=State.WAITING_FOR_WORLD;var connection=c.getConnection();rtt.start(connection,System.nanoTime(),token->connection.send(new net.minecraft.network.protocol.ping.ServerboundPingRequestPacket(token)));syncAimDiagnostics();
 }catch(Exception e){running=false;state=State.ERROR;reason=e.getMessage();com.kuangsanfeng.ultimategarden.Ultimate_Garden.LOGGER.warn("Shootout start refused: {}",reason);}}
 public void stop(String why){if(arrowConfirmation.pending())diagnosticConfirmation("CANCELLED",why);aimDiagnostics.end(tick,why);diagnosticManualAttempt=0;ZombieShootCollector.INSTANCE.stop();relockRound.clear();clearFrameAim();lastDispatchedAttempt=null;interceptDebug=null;selectionPlan=null;rejectedThisTick.clear();rtt.stop();ping=-1;hudSolution=null;targetLoss.clear();aimCorrection.clear();viewCheck.clear();cancelAutoStart();state=State.STOPPING;arrowConfirmation.cancel();try{arrowConfirmation.clearSession();manualRightDown=false;manualAutomaticNotBefore=0;mouse.cancel(Minecraft.getInstance());}catch(RuntimeException e){com.kuangsanfeng.ultimategarden.Ultimate_Garden.LOGGER.error("Shootout input cleanup failed",e);}running=false;current=null;targets.clear();lamps.clear();shots.clear();world=null;state=State.STOPPED;reason=why;}
 public void register(){mouse.setDiagnosticsObserver(aimDiagnostics);ZombieShootCollector.INSTANCE.setGateChanged(this::syncAimDiagnostics);config().setLogOptionsChanged(()->{ZombieShootCollector.INSTANCE.optionsChanged(config().logCategories());syncAimDiagnostics();});config().setAutomationChangeListener(this::automationChanged);mouse.setViewObserver(viewCheck);config().setAutoStartCancellation(this::cancelAutoStart);net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c->{tick();ZombieShootCollector.INSTANCE.update(running);});net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h,c)->stop("DISCONNECTED"));net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(c->{stop("CLIENT_STOPPING");ZombieShootCollector.INSTANCE.shutdown();aimDiagnostics.shutdown();});}
 private boolean valid(Target t){var c=Minecraft.getInstance();return t.lamp!=null?c.level.hasChunkAt(t.lamp)&&c.level.getBlockState(t.lamp).is(Blocks.REDSTONE_LAMP)&&c.level.getBlockState(t.lamp).getValue(BlockStateProperties.LIT):t.last==tick&&t.entity.isAlive()&&!t.entity.isRemoved()&&t.entity.getUUID().equals(t.uuid)&&t.projection!=null&&t.estimate!=null&&t.estimate.reliable();}
 public static String classify(String helmet,String chest,boolean baby){for(String material:List.of("leather","iron","golden","diamond"))if(helmet.equals("minecraft:"+material+"_helmet")&&chest.equals("minecraft:"+material+"_chestplate"))return (material.equals("golden")?"GOLD":material.toUpperCase(Locale.ROOT))+(baby?"_BABY":"_ADULT");return "UNKNOWN";}
 private String classify(Zombie z){return classify(BuiltInRegistries.ITEM.getKey(z.getItemBySlot(EquipmentSlot.HEAD).getItem()).toString(),BuiltInRegistries.ITEM.getKey(z.getItemBySlot(EquipmentSlot.CHEST).getItem()).toString(),z.isBaby());}
 private void observe(){var c=Minecraft.getInstance();long sampledAt=System.nanoTime();for(var e:c.level.entitiesForRendering()){if(!(e instanceof Zombie z)||!z.isAlive()||z.position().distanceToSqr(origin)>900)continue;String type=classify(z);if(type.equals("UNKNOWN"))continue;var target=targets.get(z.getUUID());if(target==null||target.entity!=z){if(targets.size()>=512)continue;target=new Target();target.entity=z;target.uuid=z.getUUID();target.tag=String.format(Locale.ROOT,"%08X",++order);target.first=order;targets.put(z.getUUID(),target);}if(target.last==tick)continue;target.classification=type;if(z.isBaby())target.stall.observe(tick,sampledAt,z.getX(),z.getZ(),model.speeds.get(type),config().get(BABY_STALL_WINDOW),config().get(BABY_STALL_RATIO));else target.stall.clear();target.metadata=ShootoutPolicy.metadata(type);long previousTick=target.last;target.last=tick;target.projection=model.match(z.position().subtract(origin));if(target.projection!=null){int window=(int)Math.ceil(config().get(ZombieShootoutConfig.Setting.valueOf(type))*20);target.estimate=target.speed.observe(tick,target.projection.path().id,target.projection.s(),model.speeds.get(type),window);target.matchEvidence=target.estimate.reliable()?(previousTick==tick-1?Math.min(3,target.matchEvidence+1):1):0;if(target.matchEvidence>=3)sceneMatched=true;}else target.matchEvidence=0;}
  targets.entrySet().removeIf(e->e.getValue().last<tick-200);
  // Incremental loaded-only discovery, known lamps are checked on every selection.
  for(int n=0;n<4096;n++){int k=scanCursor++%(61*61*61);var p=BlockPos.containing(origin).offset(k/3721-30,k/61%61-30,k%61-30);if(new Vec3(p.getX()+.5,p.getY()+.5,p.getZ()+.5).distanceToSqr(origin)>900||!c.level.hasChunkAt(p))continue;var bs=c.level.getBlockState(p);if(bs.is(Blocks.REDSTONE_LAMP)&&bs.getValue(BlockStateProperties.LIT)&&!lamps.containsKey(p)&&lamps.size()<256){var t=new Target();t.metadata=ShootoutPolicy.metadata("LAMP");t.lamp=p.immutable();t.first=++order;lamps.put(t.lamp,t);}}
  lamps.entrySet().removeIf(e->{var p=e.getKey();if(!c.level.hasChunkAt(p))return false;var state=c.level.getBlockState(p);return !state.is(Blocks.REDSTONE_LAMP)||!state.getValue(BlockStateProperties.LIT);});
 }
 private Target select(){
  var selected=selectObserved();if(selected!=null)return selected;
  boolean rescanned=lastRescanTick!=tick;
  if(rescanned){lastRescanTick=tick;observe();selected=selectObserved();if(selected!=null)return selected;}
  // New/queued regular targets always win. Only a permitted empty-queue rescan may seed a fresh snapshot.
  return relockRound.select(rescanned,()->targets.values().stream().filter(t->t.round.relockEligible()).sorted(zombieOrder()).toList(),
    t->targets.get(t.uuid)==t&&t.round.relockEligible()&&!rejectedThisTick.contains(t)&&valid(t),
    t->{var plan=prediction(t,System.nanoTime());if(plan==null){rejectedThisTick.add(t);return false;}selectionPlan=plan;return true;},
    t->t.round.beginRelock());
 }
 private Comparator<Target> zombieOrder(){return Comparator.comparingInt((Target t)->ShootoutPolicy.priority(t.classification)).thenComparingLong(t->t.first);}
 private Target selectObserved(){
  selectionPlan=null;var ordered=new ArrayList<Target>();
  for(var t:lamps.values())if(!t.round.handled())ordered.add(t);
  for(var t:targets.values())if(t.round.retry())ordered.add(t);
  targets.values().stream().filter(t->!t.round.handled()).sorted(zombieOrder()).forEach(ordered::add);
  return ShootoutCandidateSelection.first(ordered,t->!rejectedThisTick.contains(t)&&valid(t),t->{
   var plan=prediction(t,System.nanoTime());if(plan==null){rejectedThisTick.add(t);return false;}selectionPlan=plan;return true;
  });
 }
 private AimPlan prediction(Target t,long now){
  var c=Minecraft.getInstance();if(ping<0||c.player==null||c.level!=world)return null;
  double cd=Math.max(shotClock.remainingTicks(now),Math.max(0,(manualAutomaticNotBefore-now)/50_000_000.));var initial=aimPoint(t,cd+ping/50.);if(initial==null)return null;
  var eye=c.player.getEyePosition();double initialYaw=Math.toDegrees(Math.atan2(-(initial.x-eye.x),initial.z-eye.z)),initialPitch=-Math.toDegrees(Math.atan2(initial.y-eye.y,Math.hypot(initial.x-eye.x,initial.z-eye.z)));
  double aimTicks=Math.hypot(ShootoutModel.wrap(initialYaw-c.player.getYRot()),initialPitch-c.player.getXRot())/config().get(AIM_SPEED)*20;
  // One RTT covers observed-state age plus command delivery; never add another RTT/2.
  double wait=Math.max(cd,aimTicks)+1,latency=ping/50.;var solution=model.solve(eye,flight->t.lamp!=null?initial:aimPoint(t,wait+latency+flight));
  aimDiagnostics.solution(t.tag,solution,"REGULAR",tick);
  return solution==null?null:new AimPlan(t,tick,eye,initial,latency,solution);
 }
 private Vec3 feet(Target t,double time){if(t.stall.stalled())return t.entity.position();var p=t.projection;double s=ShootoutModel.reflect(p.s()+t.estimate.signedSpeed()*time,p.path().length);return origin.add(p.path().at(s)).add(p.offset());}
 private AABB bounds(Target t,double time){if(t.lamp!=null)return new AABB(t.lamp);var displacement=feet(t,time).subtract(t.entity.position());return t.entity.getBoundingBox().move(displacement);}
 private Vec3 aimPoint(Target t,double time){var box=bounds(t,time);if(t.lamp!=null){var c=Minecraft.getInstance();var shape=c.level.getBlockState(t.lamp).getCollisionShape(c.level,t.lamp);if(shape.isEmpty())return null;var eye=c.player.getEyePosition();var hit=shape.clip(eye,box.getCenter(),t.lamp);return hit==null?null:hit.getLocation().lerp(box.getCenter(),.01);}
  double height=box.maxY-box.minY,offset=t.entity.isBaby()?config().get(BABY_OFFSET):height*.18;double y=box.maxY-Math.clamp(offset,.001,height-.001);var direction=feet(t,time+.05).subtract(feet(t,Math.max(0,time-.05)));double horizontal=Math.hypot(direction.x,direction.z);double x=(box.minX+box.maxX)/2,z=(box.minZ+box.maxZ)/2;if(horizontal>1e-8){double dx=direction.x/horizontal,dz=direction.z/horizontal;double width=Math.min(Math.abs(dx)<1e-8?Double.POSITIVE_INFINITY:(box.maxX-box.minX)/Math.abs(dx),Math.abs(dz)<1e-8?Double.POSITIVE_INFINITY:(box.maxZ-box.minZ)/Math.abs(dz));x+=.2*width*dx;z+=.2*width*dz;}return new Vec3(Math.clamp(x,box.minX+.001,box.maxX-.001),y,Math.clamp(z,box.minZ+.001,box.maxZ-.001));}
 private void verify(long now){for(var it=shots.iterator();it.hasNext();){var shot=it.next();if(now<shot.due)continue;var t=shot.target;if(t.lamp==null&&t.round.verify(shot.roundAttempt,targets.get(t.uuid)==t&&t.entity.getUUID().equals(t.uuid),t.last==tick&&t.entity.isAlive())){com.kuangsanfeng.ultimategarden.Ultimate_Garden.LOGGER.debug("Shootout shot {} target {} still observed alive after impact allowance; queue one retry",shot.id,t.tag);}it.remove();}}
 private void tick(){gateErrorSolution=null;gateError=null;gateErrorTick=-1;frameAimSolution=null;frameAimTarget=null;rejectedThisTick.clear();selectionPlan=null;hudSolution=null;interceptDebug=null;tickAutoStart();if(!running)return;var c=Minecraft.getInstance();try{tick++;syncAimDiagnostics();aimDiagnostics.tick(tick);if(c.player==null||!c.player.isAlive()||c.player.isRemoved()||c.level==null||c.level!=world||revision!=Ultimate_GardenClient.getConfigManager().accountRevision()){diagnosticGate("AUTO","WORLD_OR_ACCOUNT_CHANGED",null,false,true);stop("WORLD_OR_ACCOUNT_CHANGED");return;}if(c.player.position().distanceToSqr(origin)>1){diagnosticGate("AUTO","PLAYER_POSITION_CHANGED",null,false,true);stop("PLAYER_POSITION_CHANGED");return;}if(Ultimate_GardenClient.getFarmController().getState().isRunning()||Ultimate_GardenClient.getFarmController().getCommand().hasPendingWork()){diagnosticGate("AUTO","FARM_BUSY",null,false,true);stop("FARM_BUSY");return;}
  if(com.kuangsanfeng.ultimategarden.client.ui.hud.HudAdjustmentController.isActive()){diagnosticGate("AUTO","HUD_ADJUSTMENT_ACTIVE",null,false,true);stop("HUD_ADJUSTMENT_ACTIVE");return;}
  measureRtt();observe();long now=System.nanoTime();verify(now);
  long arrowNow=System.nanoTime();if(!config().isArrowSpawnConfirmationEnabled()&&arrowConfirmation.pending())finishDispatchedShot();if(config().isArrowSpawnConfirmationEnabled()){if(confirmationExpired(arrowNow))return;observeArrows(c,arrowNow);}if(arrowConfirmation.confirmation()!=null)viewCheck.clear();
  if(arrowConfirmation.confirmation()!=null)finishConfirmedShot();
  boolean holdingInvalid=false;
  // Completed/exhausted records remain tracked, but can never keep a current lock alive.
  if(current!=null&&!current.round.regularEligible()){diagnosticGate("AUTO","TARGET_CYCLE_COMPLETED",null,false,true);if(arrowConfirmation.pending())diagnosticConfirmation("CANCELLED","TARGET_CYCLE_COMPLETED");arrowConfirmation.cancel();lastDispatchedAttempt=null;current=null;resetAimCycle();}
  if(current!=null){boolean usable=valid(current)&&(current.lamp==null||aimPoint(current,0)!=null);if(!usable)holdingInvalid=holdInvalidTarget(now);else targetLoss.clear();}
  if(current==null){current=select();resetAimCycle();state=State.SELECTING_TARGET;}
  if(c.screen!=null||c.getOverlay()!=null){aimCorrection.clear();viewCheck.clear();mouse.cancel(c);state=State.WAITING_FOR_WORLD;reason="GUI_OPEN";diagnosticGate("AUTO","GUI_OPEN",null,false,true);return;}
  if(GLFW.glfwGetWindowAttrib(c.getWindow().handle(),GLFW.GLFW_FOCUSED)==0){diagnosticGate("AUTO","WINDOW_UNFOCUSED",null,false,true);stop("WINDOW_UNFOCUSED");return;}
  if(c.player.isUsingItem()&&config().autoShootEffective()||c.options.smoothCamera||c.player.isScoping()){diagnosticGate("AUTO","UNSUPPORTED_ITEM_OR_CAMERA_MODE",null,false,true);stop("UNSUPPORTED_ITEM_OR_CAMERA_MODE");return;}
  if(holdingInvalid){clearFrameAim();return;}
  viewCheck.tick();if(config().isAutoAimEnabled()&&current!=null&&!viewCheck.poll(c.player.getYRot(),c.player.getXRot()))return;
  mouse.consumed();
  if(ping<0){if(current==null)state=State.SELECTING_TARGET;reason="PING_UNAVAILABLE";diagnosticGate("AUTO","PING_UNAVAILABLE",null,false,true);return;}
  if(!sceneMatched){state=State.SELECTING_TARGET;reason="MODEL_SCENE_NOT_MATCHED";diagnosticGate("AUTO","MODEL_SCENE_NOT_MATCHED",null,false,true);return;}if(current==null){current=select();resetAimCycle();}if(current==null){state=State.SELECTING_TARGET;reason="NO_RELIABLE_TARGET";diagnosticGate("AUTO","NO_RELIABLE_TARGET",null,false,true);return;}
  var t=current;var plan=selectionPlan!=null&&selectionPlan.target()==t&&selectionPlan.tick()==tick?selectionPlan:prediction(t,now);
  boolean awaiting=arrowConfirmation.pending();
  if(plan==null&&!awaiting){
   diagnosticGate("AUTO","NO_INTERCEPT_SOLUTION",null,false,true);
   rejectedThisTick.add(t);current=null;resetAimCycle();current=select();resetAimCycle();
   if(current==null){state=State.SELECTING_TARGET;reason="NO_INTERCEPT_SOLUTION";diagnosticGate("AUTO",arrowConfirmation.pending()?"WAITING_ARROW_NO_SOLUTION":"NO_INTERCEPT_SOLUTION",null,false,true);return;}
   t=current;plan=selectionPlan;
  }
  if(plan==null){state=State.WAITING_FOR_ARROW_CONFIRMATION;reason="NO_INTERCEPT_SOLUTION";diagnosticGate("AUTO",arrowConfirmation.pending()?"WAITING_ARROW_NO_SOLUTION":"NO_INTERCEPT_SOLUTION",null,false,true);return;}
  var eye=plan.eye();var initial=plan.initial();double latency=plan.latency();var solution=plan.solution();publishIntercept(solution);
  state=awaiting?State.WAITING_FOR_ARROW_CONFIRMATION:State.AIMING;reason=awaiting?"ARROW_"+arrowConfirmation.detection():"NONE";
  float validatedYaw=c.player.getYRot(),validatedPitch=c.player.getXRot();Double validatedError=ShootoutAimCompletion.error(validatedYaw,validatedPitch,solution.yaw(),solution.pitch());captureGateError(solution,validatedError,validatedYaw,validatedPitch);
  if(validatedError==null){reason="INVALID_AIM_DATA";diagnosticGate("AUTO","INVALID_AIM_DATA",solution,false,true);return;}
  if(!config().isAutoAimEnabled()){diagnosticGate("AUTO","AUTO_AIM_DISABLED",solution,false,true);return;}
  boolean ready=aimReady(c,solution,now);
  if(!ready||config().autoShootEffective()&&!shootAligned(c,solution)){
   requestFrameAim(solution);
   if(!running)return;
   ready=aimReady(c,solution,System.nanoTime());
  }
  if(!ready){diagnosticGate("AUTO",gateError==null?"INVALID_AIM_DATA":"CORRECTION_INCOMPLETE",solution,false,true);return;}
  if(!config().autoShootEffective()){diagnosticGate("AUTO","AUTO_SHOOT_DISABLED",solution,false,true);return;}
  if(shotSource==ShotSource.MANUAL&&arrowConfirmation.pending()){diagnosticGate("AUTO","WAITING_MANUAL_ARROW",solution,false,true);return;}
  if(!shootAligned(c,solution)){diagnosticGate("AUTO","ANGLE_EXCEEDED",solution,false,true);return;}
  boolean cooldownReady=shotClock.ready(now);
  if(!cooldownReady||now<manualAutomaticNotBefore){if(!awaiting)state=State.WAITING_FOR_COOLDOWN;diagnosticGate("AUTO",!cooldownReady?"SHOT_COOLDOWN":"MANUAL_COOLDOWN",solution,false,true);return;}
  state=awaiting?State.WAITING_FOR_ARROW_CONFIRMATION:State.READY_TO_FIRE;
  // Re-solve for firing now, including the next native input-consumption tick.
  var firingTarget=t;var fire=model.solve(eye,flight->firingTarget.lamp!=null?initial:aimPoint(firingTarget,1+latency+flight));aimDiagnostics.solution(t.tag,fire,"FIRE_RECHECK",tick);publishIntercept(fire);
  if(fire==null||!aimReady(c,fire,System.nanoTime())){if(fire!=null)requestFrameAim(fire);if(!awaiting)state=State.AIMING;diagnosticGate("AUTO",fire==null?"NO_INTERCEPT_SOLUTION":"CORRECTION_INCOMPLETE",fire,false,true);return;}
  if(!shootAligned(c,fire)){requestFrameAim(fire);diagnosticGate("AUTO","ANGLE_EXCEEDED",fire,false,true);return;}
  if(!collisionCheck(t,fire,1+latency,c.player.getYRot(),c.player.getXRot())){reason="LOCAL_OCCLUSION_OR_COLLISION_UNKNOWN";diagnosticGate("AUTO",diagnosticCollisionReason,fire,false,true);return;}
  diagnosticGate("AUTO","ALLOWED",fire,true,true);
  state=State.FIRING;long diagnosticAttempt=aimDiagnostics.nextAttempt();
  if(mouse.click(c)){
   shotSource=ShotSource.AUTO;long submitted=System.nanoTime();
   lastDispatchedAttempt=new ShootoutArrowConfirmation.Attempt(tick,submitted,eye,model.velocity(c.player.getYRot(),c.player.getXRot()).normalize(),fire.ticks(),ping);
   shotClock.fired(submitted,config().get(COOLDOWN),config().get(JITTER_MIN),config().get(JITTER_MAX),Math::random);
   aimDiagnostics.linkAttempt(submitted,diagnosticAttempt);aimDiagnostics.dispatch(diagnosticAttempt,"AUTO",true,"NATIVE_PRESS_RELEASE_COMPLETED",t.tag);
   if(config().isArrowSpawnConfirmationEnabled()){
    arrowConfirmation.attempted(lastDispatchedAttempt,config().get(COOLDOWN),config().get(JITTER_MAX));diagnosticConfirmation("START",awaiting?"AUTO_RETRY":"AUTO_ATTEMPT_REGISTERED");
    state=State.WAITING_FOR_ARROW_CONFIRMATION;reason="ARROW_"+arrowConfirmation.detection();
   }else finishDispatchedShot();
  }else{state=awaiting?State.WAITING_FOR_ARROW_CONFIRMATION:State.READY_TO_FIRE;reason="USE_INPUT_BUSY";aimDiagnostics.dispatch(diagnosticAttempt,"AUTO",false,"USE_INPUT_BUSY",t.tag);diagnosticGate("AUTO","USE_INPUT_BUSY",fire,false,true);}

 }catch(Exception e){diagnosticGate("AUTO","EXCEPTION",hudSolution,false,true);stop("ERROR: "+e.getMessage());state=State.ERROR;com.kuangsanfeng.ultimategarden.Ultimate_Garden.LOGGER.error("Shootout stopped",e);}}
 private static net.minecraft.world.level.BlockGetter localView(net.minecraft.client.multiplayer.ClientLevel level,BlockPos center){return new net.minecraft.world.level.BlockGetter(){
  private void known(BlockPos p){if(Math.abs(p.getX()-center.getX())>3||Math.abs(p.getY()-center.getY())>3||Math.abs(p.getZ()-center.getZ())>3||!level.hasChunkAt(p))throw new IllegalStateException("LOCAL_GEOMETRY_UNKNOWN");}
  public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos p){known(p);return level.getBlockState(p);}
  public net.minecraft.world.level.material.FluidState getFluidState(BlockPos p){known(p);return level.getFluidState(p);}
  public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos p){known(p);return level.getBlockEntity(p);}
  public int getHeight(){return level.getHeight();}public int getMinY(){return level.getMinY();}
 };}
 private boolean collisionCheck(Target t,ShootoutModel.Solution solution,double delay,double yaw,double pitch){diagnosticCollisionReason="TARGET_VOLUME_MISS";var c=Minecraft.getInstance();var eye=c.player.getEyePosition();var end=bounds(t,delay+solution.ticks());var center=BlockPos.containing(end.getCenter());var collision=new ArrayList<AABB>();var geometry=localView(c.level,center);double arrowRadius=net.minecraft.world.entity.EntityType.ARROW.getDimensions().width()/2;
  // Disabling obstruction checks does not disable validation against the target's hit volume.
  if(config().isOcclusionCheckEnabled())for(int x=-3;x<=3;x++)for(int y=-3;y<=3;y++)for(int z=-3;z<=3;z++){var p=center.offset(x,y,z);if(!c.level.hasChunkAt(p)){diagnosticCollisionReason="OCCLUSION_UNKNOWN_UNLOADED";return false;}if(t.lamp!=null&&p.equals(t.lamp))continue;try{var shape=geometry.getBlockState(p).getCollisionShape(geometry,p);for(var box:shape.toAabbs())collision.add(box.move(p));}catch(RuntimeException unknown){diagnosticCollisionReason="OCCLUSION_UNKNOWN_GEOMETRY";return false;}}
  if(t.lamp!=null){
   var shape=c.level.getBlockState(t.lamp).getCollisionShape(geometry,t.lamp);if(shape.isEmpty()){diagnosticCollisionReason="TARGET_SHAPE_EMPTY";return false;}
   var targetShape=new ArrayList<AABB>();for(var box:shape.toAabbs())targetShape.add(box.move(t.lamp));
   return ShootoutLampCollision.hits(model,eye,yaw,pitch,solution.ticks(),arrowRadius,targetShape,collision,aimDiagnostics.active()?outcome->diagnosticCollisionReason=outcome:null);
  }
  Vec3 previous=eye;boolean hit=false;for(int i=1;i<=Math.ceil(solution.ticks()+1);i++){double step=Math.min(i,solution.ticks()+.5);var next=model.arrow(eye,yaw,pitch,step);for(var box:collision)if(box.inflate(arrowRadius).clip(previous,next).isPresent()){diagnosticCollisionReason="OBSTACLE_HIT";return false;}var targetBox=bounds(t,delay+step).inflate(arrowRadius);if(targetBox.clip(previous,next).isPresent())hit=true;previous=next;}return hit;
 }
}
