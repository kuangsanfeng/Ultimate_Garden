import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ShootoutRtt;
import com.kuangsanfeng.ultimategarden.client.config.*;
import static com.kuangsanfeng.ultimategarden.client.config.ZombieShootoutConfig.Setting.*;
import java.util.*;
import java.nio.file.*;
import com.google.gson.*;

public final class RttRegression {
 static int checks;static void check(boolean ok){checks++;if(!ok)throw new AssertionError("RTT "+checks);}
 public static void main(String[] args)throws Exception {
  var rtt=new ShootoutRtt();var connection=new Object();var other=new Object();List<Long> sent=new ArrayList<>();
  check(rtt.milliseconds()==null);rtt.tick(connection,0,15,sent::add);check(sent.isEmpty());
  rtt.start(connection,0,sent::add);check(sent.size()==1);check(rtt.pending()&&rtt.status()==ShootoutRtt.Status.MEASURING);
  long first=sent.getFirst();check(rtt.owns(first));check(!rtt.owns(12345));
  rtt.tick(connection,100_000_000L,.5,sent::add);check(sent.size()==1);
  check(!rtt.pong(other,first,120_000_000L));check(!rtt.pong(connection,first+1,120_000_000L));check(!rtt.pong(connection,first,-1));
  check(rtt.pong(connection,first,183_250_000L));check(rtt.milliseconds()==183.25);check(rtt.milliseconds()/50.==3.665);check(!rtt.pending());
  check(!rtt.pong(connection,first,200_000_000L));check(rtt.milliseconds()==183.25);
  rtt.tick(connection,14_999_999_999L,15,sent::add);check(sent.size()==1);
  rtt.tick(connection,15_000_000_000L,15,sent::add);check(sent.size()==2);check(rtt.milliseconds()==183.25);
  rtt.tick(connection,15_500_000_000L,.5,sent::add);check(sent.size()==2);
  check(rtt.pong(connection,sent.getLast(),15_600_125_000L));check(rtt.milliseconds()==600.125);
  // New interval is evaluated against the actual last send, without an extra immediate burst.
  rtt.tick(connection,15_600_125_000L,1,sent::add);check(sent.size()==2);
  rtt.tick(connection,16_000_000_000L,1,sent::add);check(sent.size()==3);
  rtt.tick(connection,20_999_999_999L,.5,sent::add);check(sent.size()==3&&rtt.pending());
  long timed=sent.getLast();rtt.tick(connection,21_000_000_000L,.5,sent::add);check(!rtt.pending());check(rtt.status()==ShootoutRtt.Status.UNAVAILABLE&&rtt.milliseconds()==null);
  check(!rtt.pong(connection,timed,21_000_000_001L));rtt.tick(connection,21_050_000_000L,.5,sent::add);check(sent.size()==4);
  long old=sent.getLast();rtt.stop();check(!rtt.pending()&&rtt.milliseconds()==null);check(!rtt.pong(connection,old,21_100_000_000L));
  rtt.tick(connection,100_000_000_000L,.5,sent::add);check(sent.size()==4);
  rtt.start(connection,101_000_000_000L,sent::add);check(sent.size()==5);check(sent.getLast()!=old);check(!rtt.pong(connection,old,101_100_000_000L));
  check(rtt.pong(connection,sent.getLast(),101_250_750_000L));check(rtt.milliseconds()==250.75);
  rtt.tick(other,102_000_000_000L,.5,sent::add);check(rtt.status()==ShootoutRtt.Status.STOPPED&&rtt.milliseconds()==null);
  rtt.start(null,0,sent::add);check(sent.size()==5&&rtt.status()==ShootoutRtt.Status.UNAVAILABLE);
  rtt.start(connection,0,id->{throw new IllegalStateException("send failed");});check(!rtt.pending()&&rtt.milliseconds()==null&&rtt.status()==ShootoutRtt.Status.UNAVAILABLE);
  var cfg=new ZombieShootoutConfig();check(cfg.get(PING_INTERVAL)==15);cfg.set(PING_INTERVAL,.5);check(cfg.get(PING_INTERVAL)==.5);cfg.set(PING_INTERVAL,2.75);check(cfg.get(PING_INTERVAL)==2.75);
  for(double invalid:new double[]{0,.499,-1,Double.NaN,Double.POSITIVE_INFINITY}){try{cfg.set(PING_INTERVAL,invalid);throw new AssertionError();}catch(IllegalArgumentException expected){check(cfg.get(PING_INTERVAL)==2.75);}}
  var copy=new ZombieShootoutConfig();check(!copy.load(cfg.toJson(new JsonObject())));check(copy.get(PING_INTERVAL)==2.75&&copy.get(AIM_TOLERANCE)==.5);
  var root=Files.createTempDirectory("ug-rtt-");var configs=new ConfigManager(root);configs.getZombieShootoutConfig().set(PING_INTERVAL,.5);check(configs.save(ConfigManager.Module.ZOMBIE_SHOOTOUT));check(new ConfigManager(root).getZombieShootoutConfig().get(PING_INTERVAL)==.5);
  var base=Path.of("src/client/java/com/kuangsanfeng/ultimategarden/client");String controller=Files.readString(base.resolve("macro/zombie_shoot/ZombieShootoutController.java"));
  check(!controller.contains("getLatency()")&&!controller.contains("getPlayerInfo("));check(controller.contains("latency=ping/50."));check(controller.contains("rtt.start(connection"));check(controller.contains("rtt.stop();ping=-1;"));check(controller.contains("c.execute(()->"));check(controller.contains("ping=rtt.milliseconds()"));
  String mixin=Files.readString(base.resolve("mixin/ShootoutPongMixin.java"));check(mixin.contains("handlePongResponse"));check(mixin.contains("packet.time()"));check(mixin.contains("onRttPong(this,packet.time()))ci.cancel()"));
  System.out.println("PASS: "+checks+" single-flight RTT, monotonic scheduling, pairing/session isolation, timeout, fractional precision, config and native packet wiring checks. Not Verified In-Game.");
 }
}
