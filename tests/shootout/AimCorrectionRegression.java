
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim.ShootoutAimCompletion;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim.ShootoutAimCorrection;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootoutConfig;

import static com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootoutConfig.Setting.*;
import java.nio.file.*;
public final class AimCorrectionRegression {
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("Correction "+checks);}
 public static void main(String[] args)throws Exception{
  var c=new ShootoutAimCorrection();int[] draws={0};java.util.function.DoubleSupplier random=()->{draws[0]++;return .8;};
  for(double d:new double[]{.5,1,1.9,2})check(c.ready(d,2,.5,0,random));check(draws[0]==0);
  check(!c.ready(3.2,2,.5,1,random));check(c.active());check(c.threshold()==.8);check(draws[0]==1);
  for(double d:new double[]{3,2.4,2,1.5,1})check(!c.ready(d,2,.5,2,random));check(draws[0]==1);check(c.ready(.6,2,.5,3,random));check(!c.active());check(c.threshold()==0);
  for(double d:new double[]{.5,1.2,1.8,2})check(c.ready(d,2,.5,4,random));check(draws[0]==1);check(!c.ready(2.1,2,.5,5,random));check(draws[0]==2);check(c.ready(.8,2,.5,6,random));
  check(!c.ready(5.,2,.5,7,random));double old=c.threshold();check(!c.ready(1.,2,.1,8,random));check(c.threshold()==old);c.clear();check(!c.active());check(c.threshold()==0);check(!c.ready(3.,2,.25,9,random));check(c.threshold()==.4);
  c.clear();check(!c.ready(3.,2,0,0,random));check(!c.ready(.001,2,0,9_000_000_000L,random));try{c.ready(.001,2,0,10_000_000_000L,random);throw new AssertionError();}catch(IllegalStateException expected){check(expected.getMessage().equals("AIM_CORRECTION_UNREACHABLE"));}c.clear();check(c.ready(0.,2,0,0,random));
  check(ShootoutAimCompletion.error(179,0,-179,0)<2.001);check(ShootoutAimCompletion.error(0,90,180,90)==0);check(!c.ready(null,2,.5,0,random));
  var root=Files.createTempDirectory("ug-correction-");var configs=new ConfigManager(root);var cfg=configs.getZombieShootoutConfig();check(cfg.get(AIM_TOLERANCE)==.5);check(cfg.get(AIM_CORRECTION_RATIO)==.25);cfg.set(AIM_CORRECTION_RATIO,.25);check(configs.save(ConfigManager.Module.ZOMBIE_SHOOTOUT));check(new ConfigManager(root).getZombieShootoutConfig().get(AIM_CORRECTION_RATIO)==.25);
  for(double invalid:new double[]{-.1,.51,Double.NaN,Double.POSITIVE_INFINITY})try{cfg.set(AIM_CORRECTION_RATIO,invalid);throw new AssertionError();}catch(IllegalArgumentException expected){check(cfg.get(AIM_CORRECTION_RATIO)==.25);}
  System.out.println("PASS: "+checks+" outer/inner hysteresis, one draw, no boundary pullback, reset, zero-threshold timeout and ratio persistence checks; Not Verified In-Game.");
 }
}
