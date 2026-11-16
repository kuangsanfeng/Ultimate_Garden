import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.*;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.log.TransmissionLog;
import com.kuangsanfeng.ultimategarden.client.platform.input.view.NativeViewInput;
import net.minecraft.client.Minecraft;import java.nio.file.*;import java.util.*;
public final class ReturnRegression {
 static int checks;static final InstantTransmissionController it=InstantTransmissionController.INSTANCE;
 static void check(boolean b){checks++;if(!b)throw new AssertionError("Return "+checks+" "+it.status());}
 static void near(double a,double b){check(Math.abs(a-b)<1e-10);}
 static void tick(){StartRegression.advance();}
 static void phase(String s){check(it.hudSnapshot().phase().equals(s));}
 static Map<String,Object> last(String type){return TransmissionLog.events.stream().filter(e->type.equals(e.get("type"))).toList().getLast();}
 static boolean movement(){var o=Minecraft.getInstance().options;return o.keyUp.down||o.keyDown.down||o.keyLeft.down||o.keyRight.down;}
 static long jumps(){return NativeViewInput.trace.stream().filter("down:SPACE"::equals).count();}
 static void initial(double z){StartRegression.reset();TransmissionLog.capture=true;TransmissionLog.events.clear();NativeViewInput.trace.clear();var p=Minecraft.getInstance().player;p.x=17.5;p.y=30;p.z=z;it.start();}
 static void toWalk(){
  initial(17.5);var p=Minecraft.getInstance().player;tick();tick();p.yaw=0;p.yaw=0;p.pitch=90;tick();tick();tick();tick();phase("RESET_JUMP");
  p.ground=false;p.y=34;tick();phase("RESET_WAIT");StartRegression.packet(17.5,30,17.5);p.ground=true;tick();tick();tick();phase("TEST_YAW_ALIGN");
  p.pitch=0;tick();tick();tick();tick();tick();phase("WAIT_POSITION");StartRegression.packet(17.5,30,20.5);tick();tick();tick();phase("RETURN_ALIGN");tick();phase("WALK");
 }
 static void predictor(){
  var t78=TransmissionCoastPrediction.predict(17.5,18.450080871,0,-.471337605);near(t78.z(),17.416369550092842);check(t78.ticks()==9);check(t78.inside(17,17));
  var t79=TransmissionCoastPrediction.predict(17.5,17.586743253,0,-.471382394);near(t79.z(),16.552933703358576);check(!t79.inside(17,17));check(TransmissionCoastPrediction.leg(17.5,20.5,17,17).overshot(t79));
  var zero=TransmissionCoastPrediction.predict(-.25,2.5,0,.00299);check(zero.ticks()==0);near(zero.z(),2.5);
  var edge=TransmissionCoastPrediction.predict(0,0,.003,0);check(edge.ticks()==1);near(edge.x(),.003);
  var diagonal=TransmissionCoastPrediction.predict(1,2,.0025,-.0025);check(diagonal.ticks()==1);near(diagonal.x(),1.0025);near(diagonal.z(),1.9975);
  for(double vx:new double[]{-.471,.02,0,.471})for(double vz:new double[]{-.471,-.0025,0,.0025,.471}){
   var result=TransmissionCoastPrediction.predict(3,4,vx,vz);int n=result.ticks();double scale=(1-Math.pow(.546,n))/(1-.546);
   near(result.x(),3+vx*scale);near(result.z(),4+vz*scale);check(Math.pow(.546,n)*Math.hypot(vx,vz)<.003);if(n>0)check(Math.pow(.546,n-1)*Math.hypot(vx,vz)>=.003);
  }
 }
 static void replayAndRecover(){
  toWalk();var c=Minecraft.getInstance();var p=c.player;long jumpCount=jumps();int clicks=NativeViewInput.clicks;
  p.z=19;p.vz=-.2;tick();check(c.options.keyDown.down);
  p.z=18.450080871;p.vz=-.471337605;tick();check(!movement());phase("WALK");check(last("RESET_WALK_DECISION").get("decision").equals("RELEASE_PREDICTED_INSIDE"));near(((Number)((List<?>)last("RESET_COAST_DECISION").get("predictedStop")).get(1)).doubleValue(),17.416369550092842);
  p.z=17.6;p.vz=-.2;tick();check(!movement());check(jumps()==jumpCount&&NativeViewInput.clicks==clicks);
  // Prediction error: stop in the adjacent block. No automatic jump or use is allowed there.
  p.z=16.8;p.vz=0;tick();tick();tick();phase("RETURN_ALIGN");check(!movement());check(jumps()==jumpCount&&NativeViewInput.clicks==clicks);
  var measured=last("RESET_ACTUAL_STOP");near(((Number)((List<?>)measured.get("predictionError")).get(1)).doubleValue(),16.8-17.416369550092842);
  tick();phase("WALK");check(c.options.keyDown.down);check(((Number)last("RESET_MICRO_ADJUST").get("microAdjustments")).intValue()==1);
  p.z=16.9;p.vz=.06;tick();check(!movement());check(jumps()==jumpCount&&NativeViewInput.clicks==clicks);
  // A second one-Tick correction is permitted only after the first pulse has stopped.
  p.z=16.95;p.vz=0;tick();tick();tick();phase("RETURN_ALIGN");tick();check(c.options.keyDown.down);
  p.z=17.05;p.vz=.08;tick();check(!movement());p.z=17.2;p.vz=0;tick();tick();tick();phase("DOWN_ALIGN");check(((Number)last("RESET_ACTUAL_STOP").get("microAdjustments")).intValue()==2);
  p.yaw=0;p.yaw=0;p.pitch=90;tick();tick();tick();tick();phase("RESET_JUMP");check(jumps()==jumpCount+1);
  // Drifting outside while airborne must cancel the use, wait for landing and recover.
  p.ground=false;p.y=34;p.z=16.99;tick();phase("WALK");check(NativeViewInput.clicks==clicks&&!movement()&&!c.options.keyJump.down);
  p.y=35;tick();check(!movement());p.ground=true;p.y=30;p.vz=0;tick();tick();tick();phase("RETURN_ALIGN");
  it.stop("MANUAL_STOP");int count=NativeViewInput.clicks;tick();check(!movement()&&!c.options.keyJump.down&&NativeViewInput.clicks==count);
 }
 static void overshootAndPreJump(){
  toWalk();var p=Minecraft.getInstance().player;long jumpCount=jumps();int clicks=NativeViewInput.clicks;
  p.z=17.586743253;p.vz=-.471382394;tick();check(last("RESET_WALK_DECISION").get("decision").equals("RELEASE_OVERSHOT"));check(!movement());
  p.z=17.5;p.vz=0;tick();tick();tick();phase("DOWN_ALIGN");p.yaw=0;p.pitch=90;tick();tick();tick();
  // Just before the jump decision, a server correction puts us outside.
  p.z=18.001;tick();phase("WALK");check(jumps()==jumpCount&&NativeViewInput.clicks==clicks);
  it.stop("MANUAL_STOP");
 }
 static void successfulReset(){
  toWalk();var p=Minecraft.getInstance().player;int clicks=NativeViewInput.clicks;
  p.z=18.450080871;p.vz=-.471337605;tick();p.z=17.416369550092842;p.vz=0;tick();tick();tick();phase("DOWN_ALIGN");
  p.yaw=0;p.yaw=0;p.pitch=90;tick();tick();tick();tick();phase("RESET_JUMP");p.ground=false;p.y=33.99;tick();check(NativeViewInput.clicks==clicks);p.y=34.01;tick();phase("RESET_WAIT");check(NativeViewInput.clicks==clicks+1);
  StartRegression.packet(17.5,30,17.5);p.ground=true;tick();tick();tick();phase("TEST_YAW_ALIGN");it.stop("MANUAL_STOP");
 }
 static void xAxisPulseCancel(){
  toWalk();var c=Minecraft.getInstance();var p=c.player;int clicks=NativeViewInput.clicks;long jumpCount=jumps();
  p.x=17.6;p.z=16.9;p.vx=.471;tick();check(last("RESET_WALK_DECISION").get("decision").equals("RELEASE_OVERSHOT"));check(!movement());
  p.x=18.05;p.z=17.1;p.vx=0;tick();tick();tick();phase("RETURN_ALIGN");tick();
  check(c.options.keyDown.down&&!c.options.keyUp.down&&!c.options.keyRight.down&&!c.options.keyLeft.down);
  it.stop("MANUAL_STOP");check(!movement()&&!c.options.keyJump.down);for(int i=0;i<5;i++){tick();it.frame();}check(NativeViewInput.clicks==clicks&&jumps()==jumpCount);
 }
 static void alignmentCorrection(){
  toWalk();var p=Minecraft.getInstance().player;p.z=17.58;p.vz=-.471;tick();p.z=16.8;p.vz=0;tick();tick();tick();phase("RETURN_ALIGN");
  long pulses=TransmissionLog.events.stream().filter(e->"RESET_MICRO_ADJUST".equals(e.get("type"))).count();
  // A position correction during view alignment must not trigger an unnecessary pulse.
  p.z=17.5;tick();check(!movement());tick();tick();phase("DOWN_ALIGN");check(TransmissionLog.events.stream().filter(e->"RESET_MICRO_ADJUST".equals(e.get("type"))).count()==pulses);it.stop("MANUAL_STOP");
 }
 static void timeoutCleanup()throws Exception{
  toWalk();int clicks=NativeViewInput.clicks;var f=InstantTransmissionController.class.getDeclaredField("returnStartedNanos");f.setAccessible(true);f.setLong(it,System.nanoTime()-30_000_000_001L);
  tick();check(!it.running());check(it.hudSnapshot().reason().equals("STATE_TIMEOUT_WALK"));check(!movement()&&!Minecraft.getInstance().options.keyJump.down&&NativeViewInput.clicks==clicks);
 }
 static void backwardLegChecks(){
  var leg=TransmissionCoastPrediction.leg(-18.5,16.5,-18,4);near(leg.yaw(),Math.toDegrees(Math.atan2(1,12)));
  check(Math.sin(Math.toRadians(leg.yaw()))>0&&-Math.cos(Math.toRadians(leg.yaw()))<0);
  check(!leg.overshot(new TransmissionCoastPrediction.Stop(-17.4,8,0)));check(leg.overshot(new TransmissionCoastPrediction.Stop(-17.4,3,0)));
  near(TransmissionCoastPrediction.gap(17.5,16.5,17,17),.5);near(TransmissionCoastPrediction.gap(17.5,16.6,17,17),.4);
  for(double z:new double[]{16.5,16.4}){
   toWalk();var p=Minecraft.getInstance().player;p.z=17.58;p.vz=-.471;tick();p.z=z;p.vz=0;tick();tick();tick();phase("RETURN_ALIGN");tick();tick();check(Minecraft.getInstance().options.keyDown.down);tick();check(Minecraft.getInstance().options.keyDown.down);
   check(TransmissionLog.events.stream().noneMatch(e->"RESET_MICRO_ADJUST".equals(e.get("type"))));it.stop("MANUAL_STOP");
  }
 }
 public static void main(String[] args)throws Exception{
  Ultimate_GardenClient.configs=new ConfigManager(Files.createTempDirectory("ug-return-"));predictor();backwardLegChecks();replayAndRecover();overshootAndPreJump();successfulReset();xAxisPulseCancel();alignmentCorrection();timeoutCleanup();StartRegression.resetTraceChecks();
  System.out.println("PASS: "+checks+" targeted return checks + "+StartRegression.checks+" RESET trace checks; RESET-only prediction, supplied Tick 78/79 replay, release/coast, measured-stop recovery, single-Tick pulses, pre-jump/airborne guard and stop-cleanup checks. Minecraft/input boundaries stubbed; Not Verified In-Game.");
 }
}
