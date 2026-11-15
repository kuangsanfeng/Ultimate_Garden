import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.nio.file.*;
public final class ManualShotRegression {
 static int checks;static void check(boolean ok){checks++;if(!ok)throw new AssertionError("Manual shot "+checks);}
 static ShootoutArrowConfirmation.Attempt attempt(long nanos){return new ShootoutArrowConfirmation.Attempt(4,nanos,Vec3.ZERO,new Vec3(0,0,1),8,100);}
 static ShootoutArrowConfirmation.Observation arrow(UUID uuid,int id,UUID owner,long nanos){return new ShootoutArrowConfirmation.Observation(uuid,id,"minecraft:arrow",owner,new Vec3(500,100,-500),Vec3.ZERO,4,nanos);}
 public static void main(String[] args)throws Exception {
  var helper=new ShootoutArrowConfirmation();helper.reset(UUID.randomUUID());UUID old=UUID.randomUUID(),fresh=UUID.randomUUID();
  helper.observe(arrow(old,1,null,10));helper.attemptedManual(attempt(20),.55,.1);
  check(helper.pending()&&helper.confirmation()==null);helper.observe(arrow(old,1,null,25));check(helper.confirmation()==null);
  helper.attemptedManual(attempt(30),.55,.1);check(helper.pending());helper.observe(arrow(fresh,1,UUID.randomUUID(),35));
  check(helper.confirmation()!=null);check(helper.confirmation().attempt().nanos()==30);check(helper.confirmation().arrow().uuid().equals(fresh));
  helper.observe(arrow(UUID.randomUUID(),3,null,36));check(helper.confirmation().arrow().uuid().equals(fresh));
  helper.cancel();helper.attemptedManual(attempt(40),.55,.1);helper.observe(arrow(fresh,99,null,45));check(helper.confirmation()==null);
  helper.observe(arrow(old,1,null,46));check(helper.confirmation()==null);
  helper.observe(arrow(UUID.randomUUID(),4,null,50));check(helper.confirmation()!=null);
  // A zero velocity, remote, ownerless projectile is eligible; same-Tick ordering uses nanos.
  helper.clearSession();helper.attemptedManual(attempt(100),.55,.1);UUID earlier=UUID.randomUUID();helper.observe(arrow(earlier,5,null,99));check(helper.confirmation()==null);
  helper.observe(arrow(earlier,5,null,101));check(helper.confirmation()==null);helper.observe(arrow(UUID.randomUUID(),6,null,100));check(helper.confirmation()!=null);
  helper.clearSession();helper.attemptedManual(attempt(1_000_000_000),.55,.1);
  for(int i=0;i<500;i++)helper.attemptedManual(attempt(1_000_000_001+i),.55,.1);
  check(helper.confirmation()==null);check(!helper.timedOut(5_999_999_999L));check(helper.timedOut(6_000_000_000L));
  helper.cancel();check(!helper.pending());helper.attemptedManual(attempt(7_000_000_000L),.55,.1);check(!helper.timedOut(7_000_000_000L));
  helper.clearSession();check(!helper.pending()&&helper.confirmation()==null);helper.attemptedManual(attempt(100),.55,.1);helper.observe(new ShootoutArrowConfirmation.Observation(UUID.randomUUID(),9,"minecraft:arrow",null,null,null,4,101));check(helper.confirmation()!=null);
  var base=Path.of("src/client/java/com/kuangsanfeng/ultimategarden/client");
  String controller=Files.readString(base.resolve("macro/zombie_shoot/ZombieShootoutController.java"));
  String manual=controller.substring(controller.indexOf(" public void onManualButton"),controller.indexOf(" private record Shot("));
  check(manual.contains("manualRightDown"));check(manual.contains("!running||config().autoShootEffective()"));check(manual.contains("c.screen!=null||c.getOverlay()!=null"));
  check(manual.contains("!valid(current)"));check(manual.contains("ShotSource.MANUAL"));check(manual.contains("arrowConfirmation.attemptedManual"));
  check(!manual.contains("mouse.click")&&!manual.contains("shotClock.ready")&&!manual.contains("aimReady("));
  check(manual.contains("!config().isArrowSpawnConfirmationEnabled()&&lastDispatchedAttempt!=null)finishDispatchedShot()"));
  String timeout=controller.substring(controller.indexOf(" private boolean confirmationExpired"),controller.indexOf(" private final ShootoutAutoStart"));
  check(timeout.indexOf("ShotSource.MANUAL")<timeout.indexOf("stop(\"SHOT_CONFIRMATION_TIMEOUT\")"));check(timeout.contains("MANUAL_SHOT_TIMEOUT\";return false;"));
  check(controller.contains("arrowConfirmation.clearSession()"));check(controller.contains("manualAutomaticNotBefore"));check(controller.indexOf("shots.add(new Shot")==controller.lastIndexOf("shots.add(new Shot"));
  String mixin=Files.readString(base.resolve("mixin/WindowsMouseMixin.java"));
  check(mixin.indexOf("ShootoutMouseInput.dispatching()) return")<mixin.indexOf(".onManualButton"));
  check(mixin.contains("at=@At(\"RETURN\")"));check(mixin.contains(".manualInputProcessed()"));
  check(mixin.contains("} else if(window=="));
  System.out.println("PASS: "+checks+" shared manual confirmation, repeated clicks, ownerless/remote arrows, same-Tick order, dedup/reload, timeout/rearm and source/flow wiring checks. Not Verified In-Game.");
 }
}
