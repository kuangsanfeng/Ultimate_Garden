
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ShootoutAutoStart;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootoutConfig;

import static com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootoutConfig.Setting.*;
import java.nio.file.*;import com.google.gson.*;
public final class AutoStartRegression {
 static int checks;static void check(boolean v){checks++;if(!v)throw new AssertionError("Auto start "+checks);}
 public static void main(String[] args)throws Exception {
  String target="[NPC] Carnival Cowboy: Good luck, pal!";
  check(ShootoutAutoStart.matches(target));check(ShootoutAutoStart.matches("\u00a7e[NPC]  Carnival Cowboy: Good luck, pal!  "));
  for(String text:new String[]{"[NPC] Carnival Cowboy: I won't keep ya, then.","Good luck, pal!","[NPC] Other: Good luck, pal!","Player: "+target,"[VIP] Me: "+target,"Carnival Cowboy: Good luck, pal!",target+" more","[20:02:36] "+target})check(!ShootoutAutoStart.matches(text));
  var context=new ShootoutAutoStart.Context(new Object(),new Object(),new Object(),1);var auto=new ShootoutAutoStart();long t=10_000_000_000L;
  auto.receive(target,t,false,false,context);check(!auto.pending());auto.receive(target,t,true,true,context);check(!auto.pending());
  auto.receive(target,t,true,false,context);check(auto.pending());check(!auto.due(t+199_999_999,true,false,context));auto.receive(target,t+100_000_000,true,false,context);check(auto.due(t+200_000_000,true,false,context));check(!auto.due(t+300_000_000,true,false,context));
  auto.receive(target,t+300_000_000,true,false,context);check(!auto.pending());auto.receive(target,t+3_000_000_000L,true,false,context);check(auto.pending());auto.cancel();check(!auto.due(t+4_000_000_000L,true,false,context));
  for(int scenario=0;scenario<6;scenario++){var a=new ShootoutAutoStart();a.receive(target,t,true,false,context);var changed=switch(scenario){case 2->new ShootoutAutoStart.Context(new Object(),context.world(),context.player(),1);case 3->new ShootoutAutoStart.Context(context.connection(),new Object(),context.player(),1);case 4->new ShootoutAutoStart.Context(context.connection(),context.world(),new Object(),1);case 5->new ShootoutAutoStart.Context(context.connection(),context.world(),context.player(),2);default->context;};check(!a.due(t+200_000_000,scenario!=0,scenario==1,changed));check(!a.pending());}
  var c=new ZombieShootoutConfig();check(!c.isAutoStartZombieShootout());int[] cancel={0};c.setAutoStartCancellation(()->cancel[0]++);c.setAutoStartZombieShootout(true);c.setAutoStartZombieShootout(false);check(cancel[0]==1);
  Path dir=Files.createTempDirectory("ug-autostart-");Files.writeString(dir.resolve("zombie_shootout.json"),"{\"aimCompletionToleranceDeg\":2.0,\"extra\":true}");var configs=new ConfigManager(dir);check(configs.getZombieShootoutConfig().get(AIM_TOLERANCE)==2);check(!configs.getZombieShootoutConfig().isAutoStartZombieShootout());configs.getZombieShootoutConfig().setAutoStartZombieShootout(true);check(configs.save(ConfigManager.Module.ZOMBIE_SHOOTOUT));var reload=new ConfigManager(dir);check(reload.getZombieShootoutConfig().isAutoStartZombieShootout());check(reload.getZombieShootoutConfig().get(AIM_TOLERANCE)==2);check(JsonParser.parseString(Files.readString(dir.resolve("zombie_shootout.json"))).getAsJsonObject().get("extra").getAsBoolean());
  System.out.println("PASS: "+checks+" NPC matching, monotonic 200ms, deduplication, cancellation/context and persisted tolerance=2 checks; Not Verified In-Game.");
 }
}
