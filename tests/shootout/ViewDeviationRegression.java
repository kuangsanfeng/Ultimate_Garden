
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim.ExpectedViewTracker;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim.ViewWarningClock;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootoutConfig;

import java.nio.file.*;
public final class ViewDeviationRegression {
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("View check "+checks);}
 public static void main(String[] args)throws Exception {
  var t=new ExpectedViewTracker();t.begin(0,0);for(double e:new double[]{0,3,4.99})check(t.evaluate(e,5,15)==0);check(t.evaluate(5.,5,15)==1);check(t.evaluate(8.,5,15)==0);check(t.evaluate(14.99,5,15)==0);check(t.evaluate(15.,5,15)==2);check(t.evaluate(20.,5,15)==2);check(t.evaluate(0.,5,15)==0);check(t.evaluate(5.,5,15)==1);
  t.begin(179,0);t.submit(20,0,.5,false,false);check(t.stationary(-178,0)==null);check(t.consumed(-178,0)<.001);check(t.consumed(-178,0)==null);
  for(int i=0;i<20;i++){t.submit(1,0,.5,false,false);check(t.consumed((float)(182+(i+1)*.15),0)<.001);}check(t.stationary(-150,0)>15);
  t.begin(0,89);t.submit(0,100,.5,false,false);check(t.consumed(0,90)<.001);
  t.begin(0,0);t.submit(2,3,.5,true,true);check(t.consumed(-.3f,-.45f)<.001);
  t.begin(0,0);for(int i=1;i<=3;i++){t.submit(0,0,.5,false,false);check(Math.abs(t.consumed(i*2,0)-i*2)<.001);}t.clear();check(!t.active());check(t.stationary(100,0)==null);
  var clock=new ViewWarningClock();clock.begin(0,5);check(clock.pulse(0)==1);check(clock.pulse(1)==0);check(clock.pulse(400_000_000)==1);check(clock.pulse(4_999_999_999L)==1);check(clock.pulse(5_000_000_000L)==-1);check(!clock.active());clock.begin(10,7);clock.clear();check(clock.pulse(400_000_010)==0);check(!clock.active());
  var dir=Files.createTempDirectory("ug-view-check-");var m=new ConfigManager(dir);var c=m.getViewDeviationConfig();check(!c.farm().enabled&&!c.zombie().enabled);c.farm().enabled=true;c.farm().warning(8);c.farm().stop(20);check(c.zombie().warning()==5&&c.zombie().stop()==15);try{c.zombie().warning(15);throw new AssertionError();}catch(IllegalArgumentException expected){check(c.zombie().warning()==5);}try{c.zombie().stop(5);throw new AssertionError();}catch(IllegalArgumentException expected){check(c.zombie().stop()==15);}check(m.save(ConfigManager.Module.ANTI_CHECK));var reload=new ConfigManager(dir);check(reload.getViewDeviationConfig().farm().enabled);check(reload.getViewDeviationConfig().farm().warning()==8);check(!reload.getViewDeviationConfig().zombie().enabled);check(reload.getZombieShootoutConfig().get(ZombieShootoutConfig.Setting.AIM_TOLERANCE)==.5);
  System.out.println("PASS: "+checks+" time-aligned cumulative view, warning latch, wrap/clamp, lifecycle and independent account config checks; Not Verified In-Game.");
 }
}
