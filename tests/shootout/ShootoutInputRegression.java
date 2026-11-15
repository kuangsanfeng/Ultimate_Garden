import net.minecraft.client.Minecraft;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ShootoutTanhAim;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ShootoutMouseInput;
public final class ShootoutInputRegression {
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("Input check "+checks);}
 public static void main(String[] args)throws Exception{
  for(boolean invert:new boolean[]{false,true})for(double sensitivity:new double[]{0,.25,.5,1}){
   var c=new Minecraft();c.options.sensitivity.value=sensitivity;c.options.x.value=invert;c.options.y.value=invert;var input=new ShootoutMouseInput();c.mouseHandler.dx=3;c.mouseHandler.dy=4;
   double error=input.aim(c,90,30,500,15);check(c.player.yaw>0&&c.player.pitch>0);check(com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ShootoutAimCompletion.error(0,0,c.player.yaw,c.player.pitch)<=25.0001);check(error<Math.hypot(90,30));check(c.mouseHandler.dx==3&&c.mouseHandler.dy==4);check(c.mouseHandler.sourceSeen);check(!ShootoutMouseInput.dispatching());
  }
  var c=new Minecraft();var input=new ShootoutMouseInput();check(input.click(c));check(c.mouseHandler.presses==1&&c.mouseHandler.releases==1);check(!c.mouseHandler.right&&!c.options.keyUse.down);check(c.options.keyUse.clicks==1);input.cancel(c);check(c.options.keyUse.clicks==0);
  c.options.keyUse.down=true;check(!input.click(c));input.cancel(c);check(c.options.keyUse.down);c.options.keyUse.down=false;
  c.mouseHandler.failRelease=true;try{input.click(c);throw new AssertionError();}catch(IllegalStateException expected){check(!ShootoutMouseInput.dispatching());}
  try{input.cancel(c);}catch(IllegalStateException expected){}check(!c.mouseHandler.right&&!c.options.keyUse.down);check(c.options.keyUse.clicks==0);check(!ShootoutMouseInput.dispatching());
  var observedClient=new Minecraft();var observedInput=new ShootoutMouseInput();var tracker=new com.kuangsanfeng.ultimategarden.client.anti_check.ExpectedViewTracker();double[] deviation={0};
  observedInput.setViewObserver(new ShootoutMouseInput.ViewObserver(){public boolean before(float y,float p){if(!tracker.active())tracker.begin(y,p);return true;}public void submitted(double dx,double dy,double sensitivity,boolean ix,boolean iy){tracker.submit(dx,dy,sensitivity,ix,iy);check(tracker.stationary(observedClient.player.yaw,observedClient.player.pitch)==null);}public void consumed(float y,float p){deviation[0]=tracker.consumed(y,p);}});
  observedInput.aim(observedClient,90,30,500,15);check(deviation[0]<.001);observedClient.player.yaw+=6;observedInput.aim(observedClient,100,20,500,15);check(deviation[0]>5);
  // Exercise production native conversion with deterministic 50ms update intervals.
  var clock=ShootoutMouseInput.class.getDeclaredField("lastUpdate");clock.setAccessible(true);
  for(double sensitivity:new double[]{0,.25,.5,1})for(double curvature:new double[]{.01,1,30})for(double[] v:new double[][]{{0,0,30,0},{179,0,-179,0},{0,89,170,88},{45,-89,-130,-88},{0,0,90,30}}){
   var client=new Minecraft();client.options.sensitivity.value=sensitivity;client.player.yaw=(float)v[0];client.player.pitch=(float)v[1];var aim=new ShootoutMouseInput();
   for(int i=0;i<30;i++){
    float beforeYaw=client.player.yaw,beforePitch=client.player.pitch;
    double before=com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ShootoutAimCompletion.error(beforeYaw,beforePitch,v[2],v[3]);
    try{clock.setLong(aim,System.nanoTime()-50_000_000);}catch(IllegalAccessException e){throw new AssertionError(e);}
    double after=aim.aim(client,v[2],v[3],500,curvature);
    check(after<=before+1e-5);check(client.player.pitch>=-90&&client.player.pitch<=90);
    check(com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ShootoutAimCompletion.error(beforeYaw,beforePitch,client.player.yaw,client.player.pitch)<=25.0001);
   }
  }
  var still=new Minecraft();var stillAim=new ShootoutMouseInput();check(stillAim.aim(still,0,0,500,30)==0);check(!still.mouseHandler.sourceSeen);
  var moving=new Minecraft();var movingAim=new ShootoutMouseInput();movingAim.aim(moving,60,0,500,30);
  try{clock.setLong(movingAim,System.nanoTime()-50_000_000);}catch(IllegalAccessException e){throw new AssertionError(e);}
  float previous=moving.player.yaw;movingAim.aim(moving,-60,0,500,30);check(moving.player.yaw<previous);
  // Frame-driven overload: actual interval controls the same native consumer and view-observation sequence.
  double previousStep=Double.POSITIVE_INFINITY;
  for(int hz:new int[]{20,60,120}){
   var client=new Minecraft();var aim=new ShootoutMouseInput();var view=new com.kuangsanfeng.ultimategarden.client.anti_check.ExpectedViewTracker();int[] phase={0};
   aim.setViewObserver(new ShootoutMouseInput.ViewObserver(){public boolean before(float y,float p){check(phase[0]==0);view.begin(y,p);phase[0]=1;return true;}public void submitted(double dx,double dy,double sens,boolean ix,boolean iy){check(phase[0]==1);view.submit(dx,dy,sens,ix,iy);check(view.stationary(client.player.yaw,client.player.pitch)==null);phase[0]=2;}public void consumed(float y,float p){check(phase[0]==2);check(view.consumed(y,p)<.001);phase[0]=3;}});
   double dt=1./hz;aim.aim(client,30,0,500,30,dt);double moved=com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ShootoutAimCompletion.error(0,0,client.player.yaw,client.player.pitch);
   check(phase[0]==3&&moved<=500*dt+1e-5&&moved<=30);check(moved<previousStep);check(Math.abs(moved-ShootoutTanhAim.step(30,500,30,dt))<1e-4);previousStep=moved;
  }
  // New diagnostics observe the real production invoker call and must not change its output.
  for(int hz:new int[]{20,60}){
   var time=new java.util.concurrent.atomic.AtomicLong(1_000_000_000L);var root=java.nio.file.Files.createTempDirectory("ug-native-aim-diag-fixture-");
   var trace=new com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ShootoutAimDiagnostics(time::get,()->"TEST-ABSOLUTE-TIME");
   trace.sync(true,root,1,com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootSnapshot.map("testFixture",true,"aimUpdateRateHz",hz),"unused");
   var client=new Minecraft();var control=new Minecraft();var aim=new ShootoutMouseInput();aim.setDiagnosticsObserver(trace);
   var solution=new com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ShootoutModel.Solution(3,30,0,new net.minecraft.world.phys.Vec3(1,1,10));
   trace.solution("00000001",solution,"REGULAR",1);trace.inputContext("00000001",solution,1,hz);
   double error=aim.aim(client,30,0,300,15,1./hz);new ShootoutMouseInput().aim(control,30,0,300,15,1./hz);
   check(client.player.yaw==control.player.yaw&&client.player.pitch==control.player.pitch);
   float afterYaw=client.player.yaw,afterPitch=client.player.pitch;
   trace.gate("00000001",solution,"AUTO",afterYaw,afterPitch,error,.75,true,.05,false,"CORRECTION_INCOMPLETE",true,false,true);
   // A request smaller than native float resolution still produces QUEUED, but never APPLIED.
   time.addAndGet(50_000_000);client.player.yaw=1000;client.player.pitch=0;client.mouseHandler.sourceSeen=false;
   aim.aim(client,1000.00001,0,300,15,1./hz);check(!client.mouseHandler.sourceSeen);
   trace.end(2,"FIXTURE_END");trace.shutdown();
   var rows=java.nio.file.Files.readAllLines(root.resolve("aim-shot-diagnostics.jsonl")).stream().map(v->com.google.gson.JsonParser.parseString(v).getAsJsonObject()).toList();
   var queued=rows.stream().filter(v->v.get("event").getAsString().equals("AIM_INPUT_QUEUED")).toList();
   var applied=rows.stream().filter(v->v.get("event").getAsString().equals("AIM_INPUT_APPLIED")).toList();
   check(queued.size()==2&&applied.size()==1);check(queued.get(1).get("submission").getAsString().equals("ZERO_QUANTIZED"));
   var consumed=applied.getFirst();check(consumed.get("beforeYaw").getAsFloat()==0&&consumed.get("beforePitch").getAsFloat()==0);check(consumed.get("afterYaw").getAsFloat()==afterYaw&&consumed.get("afterPitch").getAsFloat()==afterPitch);
   check(consumed.get("inputSequenceFrom").equals(queued.getFirst().get("inputSequence"))&&consumed.get("inputSequenceTo").equals(queued.getFirst().get("inputSequence")));
  }
  System.out.println("PASS: "+checks+" production mouse input checks using a simulated native consumer: sensitivity, inversion, combined cap, frame intervals, aligned observers, diagnostics/no-output-change, source scopes, Press/Release, ownership and failure cleanup. Not Verified In-Game.");
 }
}
