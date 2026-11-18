import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.*;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.log.TransmissionLog;
import com.kuangsanfeng.ultimategarden.client.platform.input.view.NativeViewInput;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import net.minecraft.client.Minecraft;import net.minecraft.world.phys.Vec3;import java.nio.file.*;
public class YControllerRegression {
 static final InstantTransmissionController it=InstantTransmissionController.INSTANCE;
 static void set(String n,Object v)throws Exception{var f=it.getClass().getDeclaredField(n);f.setAccessible(true);f.set(it,v);}
 static Object get(String n)throws Exception{var f=it.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(it);}
 static void check(boolean b){if(!b)throw new AssertionError("Y controller");}
 public static void main(String[] args)throws Exception{
  Ultimate_GardenClient.configs=new ConfigManager(Files.createTempDirectory("ug-y-controller"));var c=Minecraft.getInstance();c.player.yaw=37;TransmissionLog.capture=true;it.toggleYRound();check(it.running());check((double)get("fixedYaw")==37);it.toggle(InstantTransmissionController.StartSource.HOTKEY);check(it.yRoundingActive());check(it.lastConflictNotice().contains(it.activeTestName()));
  var coverage=new TransmissionTickPlan();set("coverage",coverage);set("resetCycle",false);set("yTarget",coverage.next());
  var begin=it.getClass().getDeclaredMethod("beginY");begin.setAccessible(true);begin.invoke(it);c.player.ground=false;c.player.y=71.1;c.player.vy=.05;int before=NativeViewInput.clicks;it.tick();check(NativeViewInput.clicks==before+1);it.tick();check(NativeViewInput.clicks==before+1);
  it.positionUpdate(new Vec3(.5,99,.5),new Vec3(.5,100,.5));c.player.y=100;c.player.ground=true;it.tick();check(coverage.next().tick()==1);
  var packet=TransmissionLog.events.stream().filter(e->e.get("type").equals("Y_POSITION_PAIR")).findFirst().orElseThrow();check(((Number)packet.get("actualY")).doubleValue()==100);check(((java.util.List<?>)packet.get("before")).get(1).equals(99.0));
  it.stop("MANUAL_STOP");check(!it.running());check(NativeViewInput.clicks==before+1);
  for(int planned:new int[]{1,20,39}){c.player.y=71;c.player.ground=true;it.toggleYRound(InstantTransmissionController.StartSource.HOTKEY);set("coverage",new TransmissionTickPlan());set("resetCycle",false);set("yTarget",new TransmissionTickPlan.Key(-90,planned));begin.invoke(it);int clicks=NativeViewInput.clicks;c.player.ground=false;c.player.y=71.1;it.tick();check(NativeViewInput.clicks==clicks);for(int t=1;t<=planned;t++){c.player.ground=t>=10;it.tick();check(NativeViewInput.clicks==clicks+(t==planned?1:0));}check((long)get("yClickTick")-(long)get("takeoffTick")==planned);it.stop("MANUAL_STOP");}
  it.toggle(InstantTransmissionController.StartSource.HOTKEY);check(it.running()&&!it.yRoundingActive());it.toggleYRound(InstantTransmissionController.StartSource.HOTKEY);check(it.running()&&!it.yRoundingActive());check(it.lastConflictNotice().contains(it.activeTestName()));it.toggle(InstantTransmissionController.StartSource.HOTKEY);check(!it.running());
  it.toggleYRound(InstantTransmissionController.StartSource.HOTKEY);check(it.yRoundingActive());check(get("entrySource")==InstantTransmissionController.StartSource.HOTKEY);it.toggleYRound(InstantTransmissionController.StartSource.HOTKEY);check(!it.running());
  String keys=Files.readString(Path.of("src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/hotkey/MacroKeyBindings.java"));check(keys.contains("register(\"toggle_instant_transmission\", GLFW.GLFW_KEY_UNKNOWN)"));check(keys.contains("register(\"toggle_instant_transmission_y_rounding\", GLFW.GLFW_KEY_UNKNOWN)"));check(keys.contains("TRANSMISSION_Y_EDGE.rising(isPressed(TRANSMISSION_Y_KEY))"));
  System.out.println("Y controller single-use/applied-packet and distinct hotkey entries, same-mode toggle, cross-mode notice and preserved binding IDs passed");
 }
}