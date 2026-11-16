
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim.ShootoutAimCompletion;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim.ShootoutAimCorrection;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.model.ShootoutModel;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.shot.ShootoutLampCollision;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootLogCategory;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootoutConfig;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat.ZombieShootLogger;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat.ZombieShootSession;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat.ZombieShootSnapshot;
import com.kuangsanfeng.ultimategarden.client.runtime.world.collection.zombie_shoot.ZombieShootClassifier;
import com.kuangsanfeng.ultimategarden.client.runtime.world.collection.zombie_shoot.ZombieShootCoordinateSystem;
import com.kuangsanfeng.ultimategarden.client.runtime.world.collection.zombie_shoot.compat.ZombieShootCollector;
import com.kuangsanfeng.ultimategarden.client.runtime.world.collection.zombie_shoot.compat.ZombieShootLogGate;
import com.kuangsanfeng.ultimategarden.client.runtime.world.observation.entity.ZombieShootEntityTracker;

import static com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootoutConfig.Setting.*;

import com.kuangsanfeng.ultimategarden.client.platform.ui.widget.CollapsibleSection;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import net.minecraft.world.phys.*;

public final class DeveloperLogsRegression {
 static int checks;static void check(boolean value){checks++;if(!value)throw new AssertionError("Developer logs/defaults/occlusion "+checks);}
 public static void main(String[] args)throws Exception {
  var cfg=new ZombieShootoutConfig();check(cfg.get(AIM_TOLERANCE)==.5&&cfg.get(AIM_CORRECTION_RATIO)==.25&&cfg.get(SHOOT_RATIO)==1.5);
  check(cfg.get(AIM_TOLERANCE)*cfg.get(AIM_CORRECTION_RATIO)==.125);check(cfg.get(AIM_TOLERANCE)*cfg.get(SHOOT_RATIO)==.75);
  var correction=new ShootoutAimCorrection();check(!correction.ready(.6,.5,.25,0,()->.8));check(Math.abs(correction.threshold()-.1)<1e-12);
  check(ShootoutAimCompletion.aligned(0,0,.75,0,.75));check(!ShootoutAimCompletion.aligned(0,0,.751,0,.75));
  check(!cfg.isOcclusionCheckEnabled());check(cfg.logCategories().isEmpty());
  for(String material:List.of("leather","iron","golden","diamond"))for(boolean baby:new boolean[]{false,true}){
   var armor=new ArrayList<String>();for(String slot:List.of("helmet","chestplate","leggings","boots"))armor.add("minecraft:"+material+"_"+slot);
   String classification=ZombieShootClassifier.classify(armor,baby);check(!classification.equals("UNCLASSIFIED"));check(ZombieShootLogCategory.valueOf(classification).zombie());
   check(ZombieShootClassifier.classify(armor.subList(0,2),baby).equals("UNCLASSIFIED"));check(ZombieShootoutController.classify(armor.get(0),armor.get(1),baby).equals(classification));
  }
  var model=ShootoutModel.load();var eye=new Vec3(.5,1.62,0);var target=new AABB(0,1,10,1,2,11);var obstacle=new AABB(0,1,9,1,2,10);var hit=model.solve(eye,t->new Vec3(.5,1.6,10.005));check(hit!=null);
  check(!ShootoutLampCollision.hits(model,eye,hit.yaw(),hit.pitch(),hit.ticks(),.125,List.of(target),List.of(obstacle)));
  check(ShootoutLampCollision.hits(model,eye,hit.yaw(),hit.pitch(),hit.ticks(),.125,List.of(target),List.of()));
  check(!ShootoutLampCollision.hits(model,eye,hit.yaw()+90,hit.pitch(),hit.ticks(),.125,List.of(target),List.of()));
  for(String missing:List.of("aimCompletionToleranceDeg","aimCorrectionRandomRatio","shootToleranceRatio")){
   var old=JsonParser.parseString("{\"aimCompletionToleranceDeg\":2,\"aimCorrectionRandomRatio\":0.5,\"shootToleranceRatio\":1,\"future\":true}").getAsJsonObject();old.remove(missing);
   var restored=new ZombieShootoutConfig();check(!restored.load(old));var saved=restored.toJson(old);check(saved.get(missing).equals(cfg.toJson(new JsonObject()).get(missing)));
   for(var entry:old.entrySet())check(saved.get(entry.getKey()).equals(entry.getValue()));
  }
  for(boolean aim:new boolean[]{false,true})for(boolean shoot:new boolean[]{false,true}){
   cfg.setAutoAimEnabled(aim);cfg.setAutoShootEnabled(shoot);cfg.setOcclusionCheckEnabled(true);check(cfg.autoShootEffective()==(aim&&shoot));check(cfg.isOcclusionCheckEnabled());
   var copy=new ZombieShootoutConfig();copy.load(cfg.toJson(new JsonObject()));check(copy.isOcclusionCheckEnabled());
  }
  var configDir=Files.createTempDirectory("ug-developer-config-");var manager=new ConfigManager(configDir);var user=manager.getZombieShootoutConfig();
  user.set(AIM_TOLERANCE,2);user.set(AIM_CORRECTION_RATIO,.5);user.set(SHOOT_RATIO,1);user.setOcclusionCheckEnabled(true);
  for(var category:ZombieShootLogCategory.values())user.setLogEnabled(category,true);
  var gate=new ZombieShootLogGate();gate.categories(user.logCategories());check(!gate.enabled()&&!gate.collecting(true));gate.setEnabled(true);check(gate.collecting(true)&&!gate.collecting(false));check(gate.enabled());
  check(manager.save(ConfigManager.Module.ZOMBIE_SHOOTOUT));var restarted=new ConfigManager(configDir).getZombieShootoutConfig();var restartedGate=new ZombieShootLogGate();restartedGate.categories(restarted.logCategories());
  check(!restartedGate.enabled()&&!restartedGate.collecting(true));check(restarted.logCategories().size()==ZombieShootLogCategory.values().length&&restarted.isOcclusionCheckEnabled());check(restarted.get(AIM_TOLERANCE)==2&&restarted.get(AIM_CORRECTION_RATIO)==.5&&restarted.get(SHOOT_RATIO)==1);
  check(!JsonParser.parseString(Files.readString(configDir.resolve("zombie_shootout.json"))).getAsJsonObject().has("logsEnabled"));
  gate.setEnabled(false);check(!gate.collecting(true)&&gate.categories().size()==ZombieShootLogCategory.values().length);gate.setEnabled(true);check(gate.collecting(true));gate.categories(Set.of());check(!gate.collecting(true));
  var root=Files.createTempDirectory("ug-developer-log-");Files.writeString(root.resolve("history.jsonl"),"historic");
  var origin=new ZombieShootCoordinateSystem(-10,70,20,-10,71.62,20,45,5);
  for(var category:Arrays.stream(ZombieShootLogCategory.values()).filter(ZombieShootLogCategory::legacy).toList()){
   var folder=root.resolve(category.name());var session=new ZombieShootSession(folder,origin,"test:world","test",Set.of(category));session.registerTag("track","00000001");
   for(var other:Arrays.stream(ZombieShootLogCategory.values()).filter(ZombieShootLogCategory::legacy).toList())check(session.emit(other.folder,ZombieShootSnapshot.map("clientTick",1,"entityTrackId","track","worldX",1,"relativeX",11,"invalid",Double.NaN))==(other==category));
   check(!session.emit("unknown",Map.of()));session.select(Set.of());check(!session.emit(category.folder,Map.of("clientTick",2)));session.stop(3);
   check(!session.emit(category.folder,Map.of("clientTick",4)));var late=new java.util.concurrent.atomic.AtomicBoolean(true);var analysisResult=new Thread(()->late.set(session.emit(category.folder,Map.of("clientTick",5))));analysisResult.start();analysisResult.join(2000);check(!late.get());session.logger.awaitClose(5000);check(session.logger.finished()&&session.logger.error==null);
   var file=folder.resolve(category.folder).resolve((category==ZombieShootLogCategory.TAGS?"session_":"")+session.id+".jsonl");check(Files.exists(file));
   var lines=Files.readAllLines(file);check(lines.size()==1);var record=JsonParser.parseString(lines.getFirst()).getAsJsonObject();check(record.get("sessionId").getAsString().equals(session.id));check(record.get("tag").getAsString().equals("00000001"));check(record.get("invalid").isJsonNull());
   for(var other:ZombieShootLogCategory.values())if(other!=category)check(!Files.exists(folder.resolve(other.folder).resolve(session.id+".jsonl")));
   check(Files.exists(folder.resolve("sessions/"+session.id+"-summary.json")));
  }
  check(Files.readString(root.resolve("history.jsonl")).equals("historic"));
  // Current logger handles both filesystem errors and bounded-queue overload without any game objects.
  var blocked=root.resolve("file-not-directory");Files.writeString(blocked,"x");var broken=new ZombieShootLogger(blocked,"broken");broken.awaitClose(3000);check(broken.finished()&&broken.error!=null&&broken.errors.get()>0);check(!broken.offer("arrows/a.jsonl",Map.of()));
  var overload=new ZombieShootLogger(root.resolve("overload"),"overload");check(!overload.offer("arrows/data.jsonl",Map.of("oversize",Collections.nCopies(500001,1))));check(overload.error.equals("QUEUE_BACKLOG_LIMIT"));overload.awaitClose(5000);check(overload.finished()&&overload.dropped.get()>0);
  var queueField=com.kuangsanfeng.ultimategarden.client.platform.persistence.log.JsonlWriter.class.getDeclaredField("queue");queueField.setAccessible(true);var queue=(ArrayBlockingQueue<?>)queueField.get(overload);check(queue.size()+queue.remainingCapacity()==4096);
  var tracker=new ZombieShootEntityTracker();var uuid=UUID.randomUUID();var entity=new Object();var one=tracker.identify("s",1,uuid,"zombie",entity,1,"LEATHER_ADULT");tracker.observe(1,uuid,1,0,0,0,4);
  var two=tracker.identify("s",1,uuid,"zombie",new Object(),2,"LEATHER_ADULT");check(!one.tag().equals(two.tag()));check(tracker.observe(1,uuid,2,0,0,0,4).previous()==null);
  var section=new CollapsibleSection.State();check(!section.isExpanded());section.toggle();check(section.isContentVisible());check(!restartedGate.enabled());
  var base=Path.of("src/client/java/com/kuangsanfeng/ultimategarden/client");String controller=Files.readString(base.resolve("macro/zombie_shoot/ZombieShootoutController.java"));
  check(controller.contains("if(config().isOcclusionCheckEnabled())for(int x=-3"));check(controller.contains("if(!collisionCheck(t,fire"));check(controller.contains("var targetBox=bounds(t,delay+step).inflate(arrowRadius)"));check(controller.contains("ZombieShootCollector.INSTANCE.update(running)"));check(controller.contains("aimDiagnostics.end(tick,why);diagnosticManualAttempt=0;ZombieShootCollector.INSTANCE.stop()"));
  String select=controller.substring(controller.indexOf(" private Target select()"),controller.indexOf(" private Vec3 feet("));check(!select.contains("collisionCheck(")&&!select.contains("OcclusionAnalyzer"));
  String manual=controller.substring(controller.indexOf(" public boolean blockManualShot("),controller.indexOf(" private record Shot("));check(!manual.contains("OcclusionCheck"));
  String collector=Files.readString(base.resolve("runtime/world/collection/zombie_shoot/ZombieShootCollector.java"));check(!collector.contains("END_CLIENT_TICK.register"));check(collector.contains("if(!gate.collecting(macroRunning)){stop();return;}"));check(collector.contains("if(!enable")||collector.contains("if(failed)return"));
  String menu=Files.readString(base.resolve("platform/ui/adapters/menu/GardenMenuAdapter.java"));check(menu.contains("automation.autoShootEffective()&&!configs.isPendingRestart")&&menu.contains("2*CollapsibleSection.NESTED_INDENT"));
  for(String key:List.of("developer_options","developer.carnival","developer.shootout","developer.shootout.logs"))check(menu.contains("\"ui.ultimategarden."+key+"\""));
  check(menu.contains("collector.logsEnabled()&&!configs.isPendingRestart"));check(menu.contains("config.logEnabled(category)"));
  System.out.println("PASS: "+checks+" new/missing/saved defaults, 0.75-degree tolerance, occlusion gating, 17 independent log categories, runtime master, async close/overload and lifecycle checks. Not Verified In-Game.");
 }
}
