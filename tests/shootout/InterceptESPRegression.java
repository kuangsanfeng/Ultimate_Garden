import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutESP;
import com.kuangsanfeng.ultimategarden.client.config.*;import net.minecraft.world.phys.Vec3;import java.nio.file.*;
public final class InterceptESPRegression {
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("Intercept ESP "+checks);}
 public static void main(String[] args)throws Exception {
  int i=0;for(String material:new String[]{"LEATHER","IRON","GOLD","DIAMOND"}){int rgb=new int[]{0xB8734E,0xD4D5D2,0xF8D34E,0x4ED8D4}[i++];check(ZombieShootoutESP.color(material+"_ADULT")==rgb);check(ZombieShootoutESP.color(material+"_BABY")==rgb);}check(ZombieShootoutESP.color("LAMP")==-1);check(ZombieShootoutESP.color("UNKNOWN")==-1);
  for(var p:new Vec3[]{new Vec3(-10.2,71,-8),new Vec3(5,0,3),Vec3.ZERO}){var b=ZombieShootoutESP.bounds(p);check(b.getCenter().distanceTo(p)<1e-10);check(Math.abs(b.maxX-b.minX-.5)<1e-10);check(Math.abs(b.maxY-b.minY-.5)<1e-10);check(Math.abs(b.maxZ-b.minZ-.5)<1e-10);}
  var path=Files.createTempDirectory("ug-intercept-esp-");var manager=new ConfigManager(path);check(!manager.getESPConfig().isShowShootoutInterceptBox());manager.getESPConfig().setShowShootoutInterceptBox(true);check(manager.save(ConfigManager.Module.ESP));check(new ConfigManager(path).getESPConfig().isShowShootoutInterceptBox());
  var source=Files.readString(Path.of("src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutESP.java"));check(!source.contains(".solve("));check(!source.contains("mouse"));check(source.contains("disabled,disabled"));check(source.contains("ESPColorMode.SOLID"));
  check(com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController.INSTANCE.interceptDebug(new Object())==null);
  var controller=Files.readString(Path.of("src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java"));check(controller.split("model\\.solve\\(",-1).length-1==2);check(controller.contains("private volatile InterceptDebug"));check(controller.contains("value.tick()==tick"));check(controller.contains("aimDiagnostics.end(tick,why);diagnosticManualAttempt=0;ZombieShootCollector.INSTANCE.stop();relockRound.clear();clearFrameAim();lastDispatchedAttempt=null;interceptDebug=null;"));check(controller.contains("publishIntercept(solution)"));check(controller.contains("publishIntercept(fire)"));
  System.out.println("PASS: "+checks+" intercept cube geometry, fixed material colors, toggle persistence and no additional solver/input checks; Not Verified In-Game.");
 }
}
