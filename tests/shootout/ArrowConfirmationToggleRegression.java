
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.shot.ShootoutArrowConfirmation;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.shot.ShootoutShotClock;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootoutConfig;

import net.minecraft.world.phys.Vec3;import java.util.*;import java.nio.file.*;
public final class ArrowConfirmationToggleRegression {
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("Confirmation toggle "+checks);}
 public static void main(String[] args)throws Exception {
  var root=Files.createTempDirectory("ug-confirmation-toggle-");Files.writeString(root.resolve("zombie_shootout.json"),"{\"aimCompletionToleranceDeg\":2,\"aimCorrectionRandomRatio\":0.5,\"extra\":true}");var m=new ConfigManager(root);var c=m.getZombieShootoutConfig();check(c.isArrowSpawnConfirmationEnabled());c.setArrowSpawnConfirmationEnabled(false);check(m.save(ConfigManager.Module.ZOMBIE_SHOOTOUT));var reload=new ConfigManager(root);check(!reload.getZombieShootoutConfig().isArrowSpawnConfirmationEnabled());check(reload.getZombieShootoutConfig().get(ZombieShootoutConfig.Setting.AIM_TOLERANCE)==2);check(reload.getZombieShootoutConfig().get(ZombieShootoutConfig.Setting.AIM_CORRECTION_RATIO)==.5);check(Files.readString(root.resolve("zombie_shootout.json")).contains("extra"));
  var helper=new ShootoutArrowConfirmation();UUID player=UUID.randomUUID(),old=UUID.randomUUID();helper.reset(player);var direction=new Vec3(0,0,1);var attempt=new ShootoutArrowConfirmation.Attempt(1,50_000_000,Vec3.ZERO,direction,6,0);helper.attempted(attempt,.55,.1);check(helper.pending());check(helper.confirmation()==null);
  var clock=new ShootoutShotClock();clock.fired(50_000_000,.55,0,.1,()->.5);double remaining=clock.remainingTicks(100_000_000);helper.cancel();helper.rememberDispatched(attempt);check(!helper.pending());check(helper.confirmation()==null);check(clock.remainingTicks(100_000_000)==remaining);check(!clock.ready(100_000_000));
  helper.observe(new ShootoutArrowConfirmation.Observation(old,1,"minecraft:arrow",player,new Vec3(0,0,3),direction,10,500_000_000));helper.attempted(new ShootoutArrowConfirmation.Attempt(13,650_000_000,Vec3.ZERO,direction,6,0),.55,.1);helper.observe(new ShootoutArrowConfirmation.Observation(old,1,"minecraft:arrow",player,new Vec3(0,0,3),direction,14,700_000_000));check(helper.confirmation()==null);
  helper.observe(new ShootoutArrowConfirmation.Observation(UUID.randomUUID(),2,"minecraft:arrow",player,new Vec3(0,0,3),direction,14,700_000_000));check(helper.confirmation()!=null);
  var source=Files.readString(Path.of("src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java"));int click=source.indexOf("  if(mouse.click(c))");String block=source.substring(click,source.indexOf(" }catch(Exception e)",click));check(block.contains("if(config().isArrowSpawnConfirmationEnabled())"));check(block.contains("}else finishDispatchedShot();"));check(block.indexOf("shotClock.fired")<block.indexOf("if(config().isArrowSpawnConfirmationEnabled())"));String finish=source.substring(source.indexOf(" private void finishDispatchedShot()"),source.indexOf(" private boolean confirmationExpired"));check(!finish.contains("mouse.click")&&!finish.contains("shotClock.fired"));check(finish.contains("lastDispatchedAttempt==null||current==null"));check(finish.contains("completeShot(attempt,null,null,null,attempt.nanos(),false)"));
  System.out.println("PASS: "+checks+" default ON, persistence, OFF dispatch guards, CD preservation, mode-switch baseline and old-arrow exclusion checks; Not Verified In-Game.");
 }
}
