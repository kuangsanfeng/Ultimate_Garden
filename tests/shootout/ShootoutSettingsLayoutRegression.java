import com.kuangsanfeng.ultimategarden.client.config.*;
import static com.kuangsanfeng.ultimategarden.client.config.ZombieShootoutConfig.Setting.*;
import com.kuangsanfeng.ultimategarden.client.ui.widget.CollapsibleSection;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

public final class ShootoutSettingsLayoutRegression {
 static int checks;static void check(boolean ok){checks++;if(!ok)throw new AssertionError("Shootout settings layout "+checks);}
 static JsonObject saved(){return JsonParser.parseString("{\"autoAimEnabled\":false,\"autoShootEnabled\":true,\"manualShotRestrictionEnabled\":false,\"aimPeakSpeedDegPerSecond\":777,\"aimCurveCurvatureDeg\":42.5,\"future\":true}").getAsJsonObject();}
 public static void main(String[] args)throws Exception {
  var fresh=new ZombieShootoutConfig();check(fresh.isAutoAimEnabled());check(!fresh.isAutoShootEnabled());check(fresh.isManualShotRestrictionEnabled());check(fresh.get(AIM_SPEED)==300);check(fresh.get(AIM_CURVATURE)==15);
  check(fresh.manualShotRestrictionEffective()&&fresh.manualShotRestrictionEditable());
  var preserved=new ZombieShootoutConfig();check(!preserved.load(saved()));check(!preserved.isAutoAimEnabled()&&preserved.isAutoShootEnabled()&&!preserved.isManualShotRestrictionEnabled());check(preserved.get(AIM_SPEED)==777&&preserved.get(AIM_CURVATURE)==42.5);
  check(preserved.toJson(saved()).get("future").getAsBoolean());
  for(String key:List.of("autoAimEnabled","autoShootEnabled","manualShotRestrictionEnabled","aimPeakSpeedDegPerSecond","aimCurveCurvatureDeg")){
   var partial=saved();partial.remove(key);var cfg=new ZombieShootoutConfig();check(!cfg.load(partial));
   var result=cfg.toJson(partial);check(result.get(key).equals(fresh.toJson(new JsonObject()).get(key)));
   for(var entry:partial.entrySet())check(result.get(entry.getKey()).equals(entry.getValue()));
  }
  var dir=Files.createTempDirectory("ug-shootout-defaults-");Files.writeString(dir.resolve("zombie_shootout.json"),saved().toString());
  var configs=new ConfigManager(dir);var user=configs.getZombieShootoutConfig();check(user.get(AIM_SPEED)==777&&user.get(AIM_CURVATURE)==42.5);check(!user.isAutoAimEnabled()&&user.isAutoShootEnabled()&&!user.isManualShotRestrictionEnabled());
  check(configs.save(ConfigManager.Module.ZOMBIE_SHOOTOUT));var reload=new ConfigManager(dir).getZombieShootoutConfig();check(reload.get(AIM_SPEED)==777&&reload.get(AIM_CURVATURE)==42.5);
  fresh.setAutoShootEnabled(true);check(fresh.isManualShotRestrictionEnabled()&&!fresh.manualShotRestrictionEditable()&&!fresh.manualShotRestrictionEffective());fresh.setAutoShootEnabled(false);check(fresh.isManualShotRestrictionEnabled()&&fresh.manualShotRestrictionEffective());
  int windows=0;for(var setting:ZombieShootoutConfig.Setting.values())if(setting.window()){windows++;check(fresh.get(setting)==.3);}check(windows==8);
  var source=Files.readString(Path.of("src/client/java/com/kuangsanfeng/ultimategarden/client/ui/screen/UltimateGardenScreen.java"));
  var menu=source.substring(source.indexOf("private void shootoutSettings()"),source.indexOf("private void shootoutInput("));
  String[] order={"shootout.status","Setting.AIM_SPEED,0","Setting.AIM_CURVATURE,0","Setting.AIM_TOLERANCE,0","Setting.AIM_CORRECTION_RATIO,CollapsibleSection.NESTED_INDENT","Setting.SHOOT_RATIO,CollapsibleSection.NESTED_INDENT","Setting.COOLDOWN,0","Setting.JITTER_MIN,CollapsibleSection.NESTED_INDENT","Setting.JITTER_MAX,CollapsibleSection.NESTED_INDENT","shootout.speed_sampling","if(setting.window())shootoutInput(setting,0)","sampling.finishContent(cursor)","Setting.BABY_OFFSET,0","Setting.PING_INTERVAL,0","shootout.ping","shootout.target"};
  int last=-1;for(String marker:order){int i=menu.indexOf(marker);check(i>last);last=i;}
  check(menu.split("divider\\(\\);",-1).length-1==4);
  String[] after={"Setting.AIM_UPDATE_RATE,0","Setting.SHOOT_RATIO,CollapsibleSection.NESTED_INDENT","Setting.JITTER_MAX,CollapsibleSection.NESTED_INDENT","Setting.BABY_OFFSET,0"};
  for(String marker:after){int index=menu.indexOf(marker);int close=menu.indexOf(';',index);check(menu.substring(close+1).stripLeading().startsWith("divider();"));}
  check(source.contains("dividers.clear()"));check(source.contains("sectionVisible(divider.section())"));check(source.contains("displayedY(divider.y())"));check(source.contains("ThemePalette.blend(p.background(),0xff000000,.18f)"));
  check(source.contains("sectionX(currentSection)+6+inset"));check(source.contains("sectionWidth(currentSection)-12-inset"));check(source.contains("new Card(cursor,28,currentSection,inset,decoration)"));
  // Same section-state tree: collapsed by default, ancestors and measured heights remain authoritative.
  var carnival=new CollapsibleSection.State();var shootout=new CollapsibleSection.State(carnival);var sampling=new CollapsibleSection.State(shootout);
  carnival.toggle();shootout.toggle();check(!sampling.isExpanded()&&!sampling.isContentVisible());sampling.measure(200,29,900);check(sampling.hiddenHeight()==671);check(sampling.hiddenHeightBefore(150)==0&&sampling.hiddenHeightBefore(1000)==671);
  sampling.toggle();check(sampling.isContentVisible()&&sampling.hiddenHeight()==0);shootout.toggle();check(!sampling.isContentVisible());
  for(int width:new int[]{80,90,120,400,700}){
   int headerX=-CollapsibleSection.NESTED_INDENT+sampling.inset(width+CollapsibleSection.NESTED_INDENT);
   check(headerX==shootout.inset(width));check(width+CollapsibleSection.NESTED_INDENT-sampling.inset(width+CollapsibleSection.NESTED_INDENT)==width-shootout.inset(width));
   if(width>=120)check(sampling.inset(width)-shootout.inset(width)==CollapsibleSection.NESTED_INDENT);
  }
  System.out.println("PASS: "+checks+" new/missing/saved defaults, parent gating, four scrolling dividers, ordered independent inputs and collapsed sampling layout checks. Not Verified In-Game.");
 }
}
