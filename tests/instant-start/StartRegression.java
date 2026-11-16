import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.*;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.log.TransmissionLog;
import com.kuangsanfeng.ultimategarden.client.platform.input.view.NativeViewInput;
import com.kuangsanfeng.ultimategarden.client.platform.input.virtual.VirtualKeyboard;
import com.kuangsanfeng.ultimategarden.client.platform.ui.hud.HudAdjustmentController;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController;
import net.minecraft.client.Minecraft;import java.nio.file.*;
public class StartRegression {
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("Startup "+checks);}
 static final InstantTransmissionController it=InstantTransmissionController.INSTANCE;
 static void reset(){it.stop("MANUAL_STOP");var c=Minecraft.getInstance();c.player=new Minecraft.Player();c.level=new Minecraft.Level();c.connection=new Object();c.screen=c.overlay=null;c.paused=false;c.focused=true;c.mouseHandler.grabbed=true;var f=Ultimate_GardenClient.farm;f.running=f.held=f.action=f.countdown=false;ZombieShootoutController.INSTANCE.running=false;HudAdjustmentController.active=false;}
 static void rejected(String code){it.toggle(InstantTransmissionController.StartSource.HOTKEY);check(!it.running());check(it.hudSnapshot().reason().equals(code));check(it.hudSnapshot().terminationStage().equals("START_REJECTED"));check(it.status().contains(code));check(it.lastDiagnostic().code().equals(code));check(it.lastDiagnostic().source().equals("HOTKEY"));}
 public static void main(String[] args)throws Exception{
  Ultimate_GardenClient.configs=new ConfigManager(Files.createTempDirectory("ug-start-"));var c=Minecraft.getInstance();var f=Ultimate_GardenClient.farm;
  reset();it.toggle(InstantTransmissionController.StartSource.HOTKEY);check(it.running());check(it.hudSnapshot().phase().equals("START_ALIGN"));check(it.lastDiagnostic()==null);it.stop("MANUAL_STOP");
  reset();f.countdown=true;it.toggle(InstantTransmissionController.StartSource.HOTKEY);check(it.running());check(f.countdown);f.action=true;int failures=TransmissionLog.failures,releases=VirtualKeyboard.released,cancels=NativeViewInput.cancels,aims=NativeViewInput.aims;it.frame();
  check(!it.running());check(it.hudSnapshot().reason().equals("INPUT_CONFLICT_FARM"));check(it.hudSnapshot().terminationStage().equals("RUNNING_STOP"));check(it.lastDiagnostic().observed().get("farmActionPending").equals(true));check(TransmissionLog.failures==failures+1);check(VirtualKeyboard.released==releases+1&&NativeViewInput.cancels==cancels+1);check(f.action&&f.countdown);check(NativeViewInput.aims==aims);var event=it.lastDiagnostic();it.frame();it.tick();check(it.lastDiagnostic()==event&&TransmissionLog.failures==failures+1);
  reset();c.screen=new Object();c.mouseHandler.grabbed=false;c.player.ground=false;int logs=TransmissionLog.created;it.toggle(InstantTransmissionController.StartSource.MENU);check(it.running()&&it.hudSnapshot().phase().equals("WAIT_GUI"));it.tick();check(it.hudSnapshot().phase().equals("WAIT_GUI"));check(TransmissionLog.created==logs);c.screen=null;it.tick();check(it.hudSnapshot().phase().equals("WAIT_GUI"));c.player.ground=true;c.mouseHandler.grabbed=true;it.tick();check(it.running()&&it.hudSnapshot().phase().equals("START_ALIGN"));check(TransmissionLog.created==logs+1);
  reset();c.player=null;rejected("PLAYER_UNAVAILABLE");reset();c.level=null;rejected("WORLD_UNAVAILABLE");reset();c.connection=null;rejected("CONNECTION_UNAVAILABLE");reset();c.player.ground=false;rejected("PLAYER_NOT_ON_GROUND");
  reset();f.running=true;rejected("INPUT_CONFLICT_FARM");reset();f.held=true;rejected("INPUT_CONFLICT_FARM");reset();f.action=true;rejected("INPUT_CONFLICT_FARM");reset();ZombieShootoutController.INSTANCE.running=true;rejected("INPUT_CONFLICT_SHOOTOUT");reset();HudAdjustmentController.active=true;rejected("INPUT_CONFLICT_HUD_EDITOR");
  // New refused request replaces an older final snapshot and its reason.
  reset();it.start();it.stop("COMPLETED");check(it.hudSnapshot().reason().equals("COMPLETED"));c.player.ground=false;rejected("PLAYER_NOT_ON_GROUND");check(it.hudSnapshot().phase().equals("START_VALIDATION"));check(it.hudSnapshot().groupIndex()==0);
  reset();c.screen=new Object();c.mouseHandler.grabbed=false;it.toggle(InstantTransmissionController.StartSource.MENU);c.screen=null;c.mouseHandler.grabbed=true;c.player.ground=false;it.tick();check(!it.running());check(it.lastDiagnostic().source().equals("MENU"));check(it.lastDiagnostic().phase().equals("WAIT_GUI"));check(it.hudSnapshot().terminationStage().equals("RUNNING_STOP"));check(it.hudSnapshot().reason().equals("PLAYER_NOT_ON_GROUND"));
  reset();it.start();c.player=null;it.tick();check(it.hudSnapshot().reason().equals("PLAYER_UNAVAILABLE"));
  reset();it.start();c.level=new Minecraft.Level();it.frame();check(it.hudSnapshot().reason().equals("WORLD_CHANGED"));
  reset();it.start();c.connection=new Object();it.tick();check(it.hudSnapshot().reason().equals("CONNECTION_CHANGED"));
  reset();it.start();c.mouseHandler.grabbed=false;it.frame();check(it.hudSnapshot().reason().equals("MOUSE_NOT_CAPTURED"));
  reset();c.screen=new Object();it.toggle(InstantTransmissionController.StartSource.MENU);it.stop("MANUAL_STOP");int inputs=NativeViewInput.aims,clicks=NativeViewInput.clicks;c.screen=null;it.tick();it.frame();check(!it.running()&&NativeViewInput.aims==inputs&&NativeViewInput.clicks==clicks);
  resetTraceChecks();
  System.out.println("PASS: "+checks+" production controller startup, GUI recovery, conflict source, context codes, rejection snapshot and diagnostic dedup checks; game/input/log boundaries stubbed.");
 }
 static void advance(){if(it.hudSnapshot().phase().equals("RETURN_ALIGN")){var p=Minecraft.getInstance().player;p.yaw=it.hudSnapshot().alignmentYaw().floatValue();p.pitch=0;}it.tick();}
 static void phase(String expected){check(it.hudSnapshot().phase().equals(expected));}
 static void packet(double x,double y,double z){var p=Minecraft.getInstance().player;var before=p.position();p.x=x;p.y=y;p.z=z;it.positionUpdate(before,p.position());}
 static java.util.List<String> runResetTrace(boolean capture){
  reset();TransmissionLog.capture=capture;TransmissionLog.events.clear();NativeViewInput.trace.clear();var p=Minecraft.getInstance().player;p.x=17.5;p.y=30;p.z=13.5;
  it.start();advance();advance();p.yaw=0;p.pitch=90;advance();advance();advance();advance();phase("RESET_JUMP");
  p.ground=false;p.y=34;advance();phase("RESET_WAIT");packet(17.5,30,13.5);p.ground=true;advance();advance();advance();phase("TEST_YAW_ALIGN");
  p.pitch=0;advance();advance();advance();advance();advance();phase("WAIT_POSITION");packet(17.5,30,16.5);advance();advance();advance();phase("RETURN_ALIGN");
  // Server correction outside a transmission trial must be logged once, with no trial response bookkeeping.
  packet(17.5,30,16.6);advance();phase("WALK");p.vz=-.2;advance();
  it.resetNativeInput(java.util.Map.of("S",true,"W",false,"A",false,"D",false,"SPACE",false));
  p.z=13.55;p.vz=-.07;advance();phase("WALK");p.z=12.5529;advance();phase("WALK");
  p.z=12.5;p.vz=0;advance();advance();advance();phase("RETURN_ALIGN");advance();phase("WALK");
  p.z=13.1;p.vz=.08;advance();p.z=13.3;p.vz=0;advance();advance();advance();phase("DOWN_ALIGN");
  p.yaw=0;p.pitch=90;advance();advance();advance();advance();phase("RESET_JUMP");
  p.ground=false;p.y=34;advance();phase("RESET_WAIT");packet(17.5,30,12.5);p.ground=true;p.vz=0;advance();advance();advance();
  check(!it.running());check(it.hudSnapshot().reason().equals("RESET_POSITION_OR_HEIGHT_FAILED"));return java.util.List.copyOf(NativeViewInput.trace);
 }
 static void resetTraceChecks(){
  var without=runResetTrace(false);var with=runResetTrace(true);check(without.equals(with));
  var events=TransmissionLog.events;
  for(String type:java.util.List.of("RESET_TICK","RESET_BEGIN","RESET_STATE_CHANGE","RESET_BASE_ENTER","RESET_BASE_EXIT","RESET_INPUT_RELEASE","RESET_INPUT_SUBMITTED","RESET_NATIVE_INPUT","RESET_JUMP_ISSUED","RESET_AIRBORNE","RESET_HEIGHT_GATE","RESET_RIGHT_CLICK","RESET_POSITION_CHECK","RESET_END"))check(events.stream().anyMatch(e->type.equals(e.get("type"))));
  check(events.stream().filter(e->"POSITION_UPDATE".equals(e.get("type"))).count()==4);
  var resetEvents=events.stream().filter(e->"RESET_BEGIN".equals(e.get("type"))).toList();check(resetEvents.size()==2);check(resetEvents.getFirst().get("previousTrialId")==null);
  var formal=events.stream().filter(e->"TRIAL_START".equals(e.get("type"))&&"TEST".equals(e.get("kind"))).findFirst().orElseThrow();check(resetEvents.getLast().get("previousTrialId").equals(formal.get("trialId")));
  var right=events.stream().filter(e->"RESET_RIGHT_CLICK".equals(e.get("type"))).toList();check(right.size()==2);
  var before=(java.util.Map<?,?>)right.getLast().get("before");check(((Number)((java.util.List<?>)before.get("feet")).get(2)).doubleValue()==13.3);check(((Number)before.get("blockZ")).intValue()==13);check(((Number)before.get("takeoffY")).doubleValue()==30);check(((Number)before.get("relativeTakeoffHeight")).doubleValue()==4);
  var verify=events.stream().filter(e->"RESET_POSITION_CHECK".equals(e.get("type"))).toList().getLast();check(verify.get("xzCentered").equals(false));check(verify.get("yMatches").equals(true));check(((Number)verify.get("errorZ")).doubleValue()==-1);check(verify.get("code").equals("RESET_POSITION_OR_HEIGHT_FAILED"));
  check(events.stream().filter(e->"RESET_TICK".equals(e.get("type"))).allMatch(e->"END_CLIENT_TICK_BEFORE_TEST".equals(e.get("observationPhase"))));
  var tickCounts=new java.util.HashSet<String>();for(var e:events)if("RESET_TICK".equals(e.get("type")))check(tickCounts.add(e.get("resetId")+"/"+e.get("tick")));
  check(with.stream().filter("down:SPACE"::equals).count()==2);check(with.stream().filter("click"::equals).count()==3);
  int size=events.size();it.tick();it.frame();it.positionUpdate(new net.minecraft.world.phys.Vec3(0,0,0),new net.minecraft.world.phys.Vec3(1,0,0));check(events.size()==size);
  TransmissionLog.capture=false;
 }

}
