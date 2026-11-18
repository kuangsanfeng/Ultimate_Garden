import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.InstantTransmissionConfig;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.TransmissionTrial;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.TransmissionResetJump;
import com.kuangsanfeng.ultimategarden.client.function.observation.sampling.TimedWindow;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.log.TransmissionLog;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.google.gson.*;import java.nio.file.*;import java.util.*;
public class TransmissionRegression {
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static TransmissionTrial.Sample s(long tick,double x,double y,boolean ground){return new TransmissionTrial.Sample(tick,tick*50_000_000,x,y,0,-.1,ground);}
 public static void main(String[] args)throws Exception{
  angleLogChecks();
  resetJumpChecks();
  pitchChecks();
  hudChecks();
  ownershipChecks();
  var cfg=new InstantTransmissionConfig();var plan=cfg.plan();check(plan.yaws().size()==10);check(plan.pitches().size()==19);check(plan.yaws().getLast()==45);check(plan.pitches().getLast()==-90);
  check(InstantTransmissionConfig.axis(0,11,5).equals(List.of(0.,5.,10.,11.)));check(InstantTransmissionConfig.axis(0,10,5).equals(List.of(0.,5.,10.)));check(InstantTransmissionConfig.axis(5,5,2).equals(List.of(5.)));
  for(double bad:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}){try{InstantTransmissionConfig.axis(0,90,bad);throw new AssertionError();}catch(IllegalArgumentException ok){checks++;}}
  int groups=0;double finalYaw=0,finalPitch=0;
  for(double y:plan.yaws())for(double p:plan.pitches())for(int n=1;n<=3;n++){
   var trial=new TransmissionTrial(n,100);var due=new ArrayList<Long>();for(long t=100;t<107;t++)if(trial.due(t)>=0){due.add(t-100);trial.clicked(t);}
   check(due.size()==n);check(trial.actual==n);check(due.getLast()==2L*(n-1));groups++;finalYaw=y;finalPitch=p;
  }check(groups==570);check(finalYaw==45&&finalPitch==-90);
  var trial=new TransmissionTrial(3,0);trial.clicked(0);trial.response(1);trial.observe(s(1,8,10,false));trial.clicked(2);trial.observe(s(3,8,10,false));trial.clicked(4);
  for(int t=5;t<12;t++)trial.observe(s(t,8,10,true));check(!trial.complete());
  trial.response(12);trial.observe(s(12,16,10,false));trial.observe(s(13,16,9,false));trial.observe(s(14,16,8,false));check(trial.endpoint.tick()==12);check(!trial.complete());trial.observe(s(15,16,7,true));check(trial.landing.tick()==15);check(trial.actual==3&&trial.updates==2);
  var vertical=new TransmissionTrial(1,0);vertical.clicked(0);vertical.observe(s(0,0,20,false));vertical.observe(s(1,0,19,false));check(vertical.endpoint==null);vertical.response(2);vertical.observe(s(2,0,12,false));vertical.observe(s(3,0,11,false));vertical.observe(s(4,0,10,false));check(vertical.endpoint.tick()==2);check(vertical.timedOut(200));check(!vertical.timedOut(199));
  var pre=new TimedWindow<Integer>(2,Integer::longValue);pre.add(-3);pre.add(-2);pre.add(-1);check(pre.snapshot().equals(List.of(-2,-1)));pre.clear();check(pre.isEmpty());
  var temp=Files.createTempDirectory("ug-transmission-");var cm=new ConfigManager(temp.resolve("config"));cm.getGeneralConfig().instantTransmission().set(InstantTransmissionConfig.Setting.YAW_END,35);check(cm.save(ConfigManager.Module.GENERAL));var reloaded=new ConfigManager(temp.resolve("config"));check(reloaded.getGeneralConfig().instantTransmission().get(InstantTransmissionConfig.Setting.YAW_END)==35);
  var log=new TransmissionLog(temp.resolve("logs"));log.event("RIGHT_CLICK",0,Map.of("feet",List.of(.123456789123,71.,.5)));log.close(5,"MANUAL_STOP");log.event("SNAPSHOT",6,Map.of());log.close(6,"DUPLICATE");log.awaitClose(3000);var lines=Files.readAllLines(temp.resolve("logs/instant-transmission.jsonl"));check(lines.size()==3);check(JsonParser.parseString(lines.getFirst()).getAsJsonObject().get("type").getAsString().equals("START"));check(JsonParser.parseString(lines.getLast()).getAsJsonObject().get("reason").getAsString().equals("MANUAL_STOP"));check(lines.get(1).contains("0.123456789123"));check(log.finished());
  var root=Path.of("src/client/java/com/kuangsanfeng/ultimategarden/client");String controller=Files.readString(root.resolve("function/instant_transmission/debug/InstantTransmissionController.java"));check(controller.contains("mouse.aim(c,targetYaw,targetPitch,300,15,dt)"));check(!controller.contains("setYRot("));check(!controller.contains("setXRot("));check(controller.contains("keyboard.slot(2)"));check(controller.contains("tick-stateTick>=3&&pre.size()==2"));check(controller.contains("keyboard.releaseAll()"));check(controller.contains("state=State.STOPPED"));
  System.out.println("PASS: "+checks+" transmission range/timing/endpoint/vertical/pre-sample/config/log/cleanup checks. Not Verified In-Game.");
 }
 static void angleLogChecks()throws Exception{
  double[][] angles={{180,180},{180.01,-179.99},{-180,180},{-180.01,179.99},{179.99,179.99},{-179.99,-179.99},
   {540,180},{-540,180},{1080.125,.125},{-1080.125,-.125},{360000.123456789,360000.123456789%360.0},{-360000.123456789,-360000.123456789%360.0}};
  var dir=Files.createTempDirectory("ug-log-angles-");var log=new TransmissionLog(dir);
  var original=new LinkedHashMap<String,Object>();original.put("yaw",540.125);original.put("pitch",90.01);original.put("targetYaw",540.125);original.put("targetPitch",90.01);
  for(int i=0;i<angles.length;i++)for(String type:List.of("SNAPSHOT","RIGHT_CLICK","RESET_TICK"))
   log.event(type,i,Map.of("case",i,"yaw",angles[i][0],"pitch",angles[i][0],"targetYaw",angles[i][0],"targetPitch",angles[i][0],"before",Map.of("site",original),"sites",List.of(original)));
  for(double pitch:new double[]{-90,0,90,-90.01,90.01})log.event("RESET_RIGHT_CLICK",20,Map.of("yaw",0.,"pitch",pitch));
  log.close(21,"ANGLE_TEST");log.awaitClose(3000);check(log.finished());
  var rows=Files.readAllLines(dir.resolve("instant-transmission.jsonl")).stream().map(line->JsonParser.parseString(line).getAsJsonObject()).toList();
  check(rows.size()==angles.length*3+5+2);
  for(var row:rows){
   if(row.has("case")){
    int i=row.get("case").getAsInt();double expected=angles[i][1];
    check(Math.abs(row.get("yaw").getAsDouble()-expected)<1e-11);check(Math.abs(row.get("pitch").getAsDouble()-expected)<1e-11);
    check(row.get("yaw").getAsDouble()>-180&&row.get("yaw").getAsDouble()<=180);
    check(row.get("targetYaw").getAsDouble()==angles[i][0]);check(row.get("targetPitch").getAsDouble()==angles[i][0]);
    var site=row.getAsJsonObject("before").getAsJsonObject("site");check(site.get("yaw").getAsDouble()==-179.875);check(site.get("pitch").getAsDouble()==90.01);
    check(row.getAsJsonArray("sites").get(0).getAsJsonObject().get("yaw").getAsDouble()==-179.875);
   }
  }
  var pitches=rows.stream().filter(r->r.get("type").getAsString().equals("RESET_RIGHT_CLICK")).map(r->r.get("pitch").getAsDouble()).toList();
  check(pitches.equals(List.of(-90.,0.,90.,-90.01,90.01)));
  check(original.get("yaw").equals(540.125));check(original.get("pitch").equals(90.01));check(original.get("targetYaw").equals(540.125));
 }
 static void ownershipChecks()throws Exception{
  for(boolean running:new boolean[]{false,true})for(boolean held:new boolean[]{false,true})for(boolean action:new boolean[]{false,true}){
   var owner=new com.kuangsanfeng.ultimategarden.client.macro.farm.FarmController.InputOwnership(running,held,action);check(owner.occupied()==(running||held||action));
   var check=new com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.TransmissionInputCheck(true,true,true,true,false,false,running,held,action,false,false,true,false,false,false,true,true);
   check(java.util.Objects.equals(check.startFailure(),owner.occupied()?"INPUT_CONFLICT_FARM":null));check(java.util.Objects.equals(check.runtimeFailure(),check.startFailure()));
  }
  var airborne=new com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.TransmissionInputCheck(true,true,true,false,false,false,false,false,false,false,false,true,false,false,false,true,true);
  check(airborne.startFailure().equals("PLAYER_NOT_ON_GROUND"));check(airborne.runtimeFailure()==null);
  var rejected=com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.TransmissionHudSnapshot.rejected("PLAYER_UNAVAILABLE",null,null);
  check(rejected.terminationStage().equals("START_REJECTED"));check(rejected.reason().equals("PLAYER_UNAVAILABLE"));check(rejected.groupIndex()==0);
  var root=Path.of("src/client/java/com/kuangsanfeng/ultimategarden/client");
  var ownerMethod=Files.readString(root.resolve("macro/farm/FarmController.java"));ownerMethod=ownerMethod.substring(ownerMethod.indexOf("public InputOwnership inputOwnership()"),ownerMethod.indexOf("public boolean canEditRouteConfiguration()"));
  check(!ownerMethod.contains("isPestLoadoutTimerRunning")&&!ownerMethod.contains("menuPause")&&!ownerMethod.contains("canEdit"));
  var en=com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageType.ENGLISH;
  java.util.function.Function<String,String> language=k->com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageManager.translate(en,k);
  var rows=com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.instant_transmission.TransmissionHudContent.rows(rejected,language);
  check(rows.stream().anyMatch(r->r.label().equals("Failure Stage")&&r.value().contains("Start Rejected")));
  check(rows.stream().anyMatch(r->r.label().equals("Stop Reason")&&r.value().equals("Local Player Unavailable")));
 }
 static void hudChecks()throws Exception{
  var s=new com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.TransmissionHudSnapshot(true,true,"RESET_JUMP","RUNNING",null,42,570,3,10.,-35.,0.,90.,true,0.,90.,2.84,2,0,0,0,null);
  java.util.function.Function<String,String> en=k->com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageManager.translate(com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageType.ENGLISH,k);
  java.util.function.Function<String,String> zh=k->com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageManager.translate(com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageType.CHINESE,k);
  var rows=com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.instant_transmission.TransmissionHudContent.rows(s,en);
  check(rows.getFirst().value().contains("42 / 570"));check(rows.getFirst().value().contains("3-Click"));
  check(rows.stream().anyMatch(r->r.value().contains("2.84 / 4.00")));
  check(rows.stream().anyMatch(r->r.label().equals("Test Yaw / Pitch")&&r.value().contains("-35.00")));
  check(rows.stream().anyMatch(r->r.label().equals("Test Yaw / Pitch")&&r.value().contains("\u00b0")));
  check(rows.stream().anyMatch(r->r.label().equals("Reset Aim Yaw / Pitch")&&r.value().contains("+90.00")));
  check(com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.instant_transmission.TransmissionHudContent.status(s,en).contains("Waiting For 4-Block Height"));
  check(!com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.instant_transmission.TransmissionHudContent.status(s,zh).contains("ui.ultimategarden"));
  var stopped=s.stopped("RESET_POSITION_OR_HEIGHT_FAILED");check(!stopped.running());check(stopped.groupIndex()==42&&stopped.relativeHeight()==2.84);
  var failedRows=com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.instant_transmission.TransmissionHudContent.rows(stopped,en);
  check(failedRows.stream().anyMatch(r->r.label().equals("Stop Reason")&&r.value().equals("Reset Position Or Height Check Failed")));
  check(com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.instant_transmission.TransmissionHudContent.status(s.stopped("COMPLETED"),en).equals("Completed"));
  check(com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.instant_transmission.TransmissionHudContent.status(s.stopped("MANUAL_STOP"),en).equals("Manually Stopped"));
  check(com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.instant_transmission.TransmissionHudContent.reason(en,"NO_IDENTIFIABLE_RESPONSE").equals("No Identifiable Displacement Observed"));
  check(com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.instant_transmission.TransmissionHudContent.reason(en,"STATE_TIMEOUT_TEST_ALIGN").contains("Aligning Test Angles"));
  for(String phase:List.of("WALK","RESET_JUMP","RESET_WAIT","TEST_ALIGN","SETTLE","CLICK_SEQUENCE","WAIT_POSITION","TEST_LANDING"))check(!en.apply("ui.ultimategarden.transmission_hud.phase."+phase).contains("ui.ultimategarden"));
  check(com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.TransmissionHudSnapshot.phase("TEST_WAIT",0L,3,false,true).equals("CLICK_SEQUENCE"));
  check(com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.TransmissionHudSnapshot.phase("TEST_WAIT",2L,3,false,true).equals("CLICK_SEQUENCE"));
  check(com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.TransmissionHudSnapshot.phase("TEST_WAIT",4L,3,false,true).equals("WAIT_POSITION"));
  check(com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.TransmissionHudSnapshot.phase("TEST_WAIT",8L,3,true,false).equals("TEST_LANDING"));
  check(com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.TransmissionHudSnapshot.phase("TEST_WAIT",8L,3,true,true).equals("WAIT_POSITION"));
  var click=new com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.TransmissionHudSnapshot(true,true,"WAIT_POSITION","RUNNING",null,42,570,3,10.,-35.,10.,-35.,false,10.,-35.,null,0,3,3,2,5L);
  var clickRows=com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.instant_transmission.TransmissionHudContent.rows(click,en);
  check(clickRows.stream().anyMatch(r->r.label().equals("Dispatched / Planned")&&r.value().equals("3 / 3")));
  check(clickRows.stream().anyMatch(r->r.label().equals("Observed Position Updates")&&r.value().equals("2")));
  check(clickRows.stream().anyMatch(r->r.label().equals("Since First Click")&&r.value().equals("5 Tick")));
  try{clickRows.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}
  var snapshot=click.stopped("NO_IDENTIFIABLE_RESPONSE");check(snapshot.dispatched()==3&&snapshot.updates()==2&&snapshot.relativeTick()==5);
  var cfg=new com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.HudConfig();
  cfg.setBounds(.1,.1,.2,.2);cfg.setBounds(com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.HudConfig.HudId.ZOMBIE_SHOOTOUT,.6,.1,.2,.3);
  cfg.setBounds(com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.HudConfig.HudId.INSTANT_TRANSMISSION,.3,.5,.3,.4);
  var copy=new com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.HudConfig();check(!copy.load(cfg.toJson(new JsonObject())));
  for(var id:com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.HudConfig.HudId.values())check(copy.bounds(id).equals(cfg.bounds(id)));
  var root=Path.of("src/client/java/com/kuangsanfeng/ultimategarden/client");var controller=Files.readString(root.resolve("function/instant_transmission/debug/InstantTransmissionController.java"));
  check(controller.indexOf("finalHud=(why.equals")<controller.indexOf("resetJump.clear();clearReturn();sessionGeneration++"));
  var renderer=Files.readString(root.resolve("platform/ui/adapters/instant_transmission/InstantTransmissionHud.java"));
  check(renderer.contains("HudPanel.background(")&&renderer.contains("HudPanel.title(g,\"Instant Transmission\")"));
  check(renderer.contains("HudAdjustmentController.layout(HudId.INSTANT_TRANSMISSION"));
  check(!renderer.contains(".tick(")&&!renderer.contains(".aim(")&&!renderer.contains(".click("));
 }
 static void pitchChecks()throws Exception{
  var cfg=new InstantTransmissionConfig();
  check(cfg.get(InstantTransmissionConfig.Setting.PITCH_START)==0);
  check(cfg.get(InstantTransmissionConfig.Setting.PITCH_END)==-90);
  check(cfg.get(InstantTransmissionConfig.Setting.PITCH_STEP)==-5);
  check(cfg.plan().pitches().equals(java.util.stream.IntStream.rangeClosed(0,18).mapToObj(i->i==0?0.:-5.*i).toList()));
  check(InstantTransmissionConfig.axis(0,-11,-5).equals(List.of(0.,-5.,-10.,-11.)));
  check(InstantTransmissionConfig.axis(0,-10,-5).equals(List.of(0.,-5.,-10.)));
  check(InstantTransmissionConfig.axis(5,5,-2).equals(List.of(5.)));
  check(InstantTransmissionConfig.axis(0,-.3,-.1).size()==4);
  for(double[] a:new double[][]{{0,-90,5},{0,90,-5},{0,-90,0},{0,-90,Double.NaN},{0,-90,Double.NEGATIVE_INFINITY}}){
   try{InstantTransmissionConfig.axis(a[0],a[1],a[2]);throw new AssertionError("Invalid direction accepted");}catch(IllegalArgumentException ok){checks++;}
  }
  try{cfg.set(InstantTransmissionConfig.Setting.PITCH_STEP,0);throw new AssertionError();}catch(IllegalArgumentException ok){checks++;}
  cfg.set(InstantTransmissionConfig.Setting.PITCH_STEP,5);
  try{cfg.plan();throw new AssertionError();}catch(IllegalArgumentException ok){checks++;}
  var old=JsonParser.parseString("{\"instantTransmission\":{\"PITCH_START\":0,\"PITCH_END\":90,\"PITCH_STEP\":5,\"YAW_END\":25,\"distanceBonusNote\":\"bonus\"}}").getAsJsonObject();
  check(cfg.load(old));check(cfg.get(InstantTransmissionConfig.Setting.PITCH_END)==-90);check(cfg.get(InstantTransmissionConfig.Setting.PITCH_STEP)==-5);check(cfg.get(InstantTransmissionConfig.Setting.YAW_END)==25);check(cfg.distanceBonusNote().equals("bonus"));
  check(!cfg.load(new JsonObject()));check(cfg.plan().pitches().getLast()==-90);
  for(double[] custom:new double[][]{{10,90,5},{0,85,5},{0,90,10},{20,-70,-7}}){
   var o=old.deepCopy();var angles=o.getAsJsonObject("instantTransmission");angles.addProperty("PITCH_START",custom[0]);angles.addProperty("PITCH_END",custom[1]);angles.addProperty("PITCH_STEP",custom[2]);
   check(!cfg.load(o));check(cfg.get(InstantTransmissionConfig.Setting.PITCH_START)==custom[0]);check(cfg.get(InstantTransmissionConfig.Setting.PITCH_END)==custom[1]);check(cfg.get(InstantTransmissionConfig.Setting.PITCH_STEP)==custom[2]);check(cfg.plan().pitches().getLast()==custom[1]);
  }
  var dir=Files.createTempDirectory("ug-pitch-migration-");old.addProperty("schemaVersion",1);old.addProperty("unrelated",true);Files.writeString(dir.resolve("general.json"),old.toString());
  var manager=new ConfigManager(dir);check(manager.getGeneralConfig().instantTransmission().get(InstantTransmissionConfig.Setting.PITCH_END)==-90);
  var saved=JsonParser.parseString(Files.readString(dir.resolve("general.json"))).getAsJsonObject();check(saved.getAsJsonObject("instantTransmission").get("PITCH_STEP").getAsDouble()==-5);check(saved.get("unrelated").getAsBoolean());
  try(var files=Files.list(dir)){check(files.anyMatch(f->f.getFileName().toString().startsWith("general.json.broken-")));}
  String controller=Files.readString(Path.of("src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/debug/InstantTransmissionController.java"));
  check(controller.contains("targetYaw=targetPitch=0"));check(controller.contains("aim(State.DOWN_ALIGN,yRounding?fixedYaw:0,90)"));check(controller.contains("aim(State.TEST_ALIGN,plan.yaws().get(yawIndex),plan.pitches().get(pitchIndex))"));
 }
 static void resetJumpChecks()throws Exception{
  var gate=new TransmissionResetJump();
  check(!gate.begin(false,71,0,0));
  check(gate.update(false,72,1,50_000_000)==TransmissionResetJump.Action.IDLE);
  check(gate.begin(true,71,0,0));
  check(gate.update(true,71,0,0)==TransmissionResetJump.Action.WAIT);
  check(gate.update(true,71,1,50_000_000)==TransmissionResetJump.Action.WAIT);
  check(gate.update(true,71.2,2,100_000_000)==TransmissionResetJump.Action.WAIT);
  check(gate.update(false,71.4,3,150_000_000)==TransmissionResetJump.Action.WAIT);
  check(gate.update(false,74.9,4,200_000_000)==TransmissionResetJump.Action.WAIT);
  check(gate.update(false,Math.nextDown(75.),5,250_000_000)==TransmissionResetJump.Action.WAIT);
  check(gate.update(false,75.,6,300_000_000)==TransmissionResetJump.Action.CLICK);
  for(int t=7;t<20;t++)check(gate.update(false,76,t,t*50_000_000L)==TransmissionResetJump.Action.IDLE);
  for(double y:new double[]{71,70.9}){
   check(gate.begin(true,71,0,0));
   check(gate.update(false,y,1,50_000_000)==TransmissionResetJump.Action.WAIT);
   check(gate.update(true,71,2,100_000_000)==TransmissionResetJump.Action.FAILED);
   check(gate.update(false,76,3,150_000_000)==TransmissionResetJump.Action.IDLE);
  }
  // Crossing the height in one Tick still fires; grounded elevation alone does not.
  check(gate.begin(true,71,0,0));
  check(gate.update(true,76,1,50_000_000)==TransmissionResetJump.Action.WAIT);
  check(gate.update(false,76.5,2,100_000_000)==TransmissionResetJump.Action.CLICK);
  check(gate.update(false,77,3,150_000_000)==TransmissionResetJump.Action.IDLE);
  // A complete sub-threshold jump must fail on landing, with no deferred shot.
  check(gate.begin(true,71,0,0));
  for(int t=1;t<=5;t++)check(gate.update(false,71+t*.5,t,t*50_000_000L)==TransmissionResetJump.Action.WAIT);
  check(gate.update(true,71,6,300_000_000)==TransmissionResetJump.Action.FAILED);
  check(gate.update(false,80,7,350_000_000)==TransmissionResetJump.Action.IDLE);
  check(gate.begin(true,71,0,0));
  for(int t=1;t<20;t++)check(gate.update(true,71,t,t*40_000_000L)==TransmissionResetJump.Action.WAIT);
  check(gate.update(true,71,20,800_000_000)==TransmissionResetJump.Action.FAILED);
  check(gate.begin(true,71,0,0));
  check(gate.update(false,71.2,1,1_000_000_000)==TransmissionResetJump.Action.FAILED);
  check(gate.begin(true,71,0,0));
  check(gate.update(false,74.9,1,50_000_000)==TransmissionResetJump.Action.WAIT);
  gate.clear();
  check(gate.update(false,76,2,100_000_000)==TransmissionResetJump.Action.IDLE);
  // A new RESET owns a new one-shot opportunity; old/cancelled waits cannot fire.
  check(gate.begin(true,80,50,2_000_000_000));
  check(gate.update(false,84.,51,2_050_000_000)==TransmissionResetJump.Action.CLICK);
  check(gate.update(false,84.5,51,2_050_000_001)==TransmissionResetJump.Action.IDLE);
  String source=Files.readString(Path.of("src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/debug/InstantTransmissionController.java"));
  check(source.split("submitResetKey\\(\"SPACE\"\\)",-1).length==2);
  int branch=source.indexOf("case RESET_JUMP->");int release=source.indexOf("keyboard.keyUp(\"SPACE\")",branch);
  int decision=source.indexOf("resetJump.update(",branch);int click=source.indexOf("beginTrial(1)",branch);
  check(branch>=0&&release>branch&&decision>release&&click>decision);
  String stop=source.substring(source.indexOf("public void stop("),source.indexOf("public void register("));
  check(stop.contains("resetJump.clear()")&&stop.contains("releaseResetKeys(\"TEST_STOP_CLEANUP\")"));
  check(source.contains("keyboard.releaseAll();resetDiagnostics.submitted("));
  check(source.contains("}else if(yRounding)beginY();else beginTrial(group);"));
 }
}
