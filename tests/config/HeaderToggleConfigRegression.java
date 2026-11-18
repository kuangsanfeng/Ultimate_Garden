import com.google.gson.JsonObject;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.*;
import java.nio.file.Files;
public final class HeaderToggleConfigRegression {
 static int checks; static void check(boolean v){checks++;if(!v)throw new AssertionError("header config "+checks);}
 public static void main(String[] args)throws Exception{
  var root=Files.createTempDirectory("ug-header-config-");var m=new ConfigManager(root);
  var g=m.getGeneralConfig();
  for(String group:new String[]{"macro","pest","focus"})check(g.hintGroupEnabled(group));
  for(var hint:GeneralConfig.Hint.values())check(g.hintSelected(hint));
  g.setHintEnabled(GeneralConfig.Hint.macroPause,false);g.setHintGroupEnabled("macro",false);
  check(!g.hintEnabled(GeneralConfig.Hint.macroStart)&&g.hintSelected(GeneralConfig.Hint.macroStart));
  check(g.hintEnabled(GeneralConfig.Hint.focusReleased));check(m.save(ConfigManager.Module.GENERAL));
  var a=m.getViewDeviationConfig();var d=m.getRandomDelayConfig();
  check(a.enabled&&a.viewChecksEnabled&&d.enabled);
  a.zombie().enabled=true;d.set(RandomDelayConfig.Key.WARP,7,7);
  for(var key:RandomDelayConfig.Key.values())check(d.ownEnabled(key));
  check(a.effective(a.zombie())&&d.sample(RandomDelayConfig.Key.WARP)==7);
  a.enabled=false;check(!a.effective(a.zombie())&&a.zombie().enabled);check(d.sample(RandomDelayConfig.Key.WARP)==0);
  a.enabled=true;a.viewChecksEnabled=false;check(!a.effective(a.zombie()));check(d.sample(RandomDelayConfig.Key.WARP)==7);
  a.viewChecksEnabled=true;d.setEnabled(RandomDelayConfig.Key.WARP,false);d.enabled=false;
  check(m.save(ConfigManager.Module.ANTI_CHECK)&&m.save(ConfigManager.Module.RANDOM_DELAY));
  var loaded=new ConfigManager(root);var lg=loaded.getGeneralConfig();var ld=loaded.getRandomDelayConfig();
  check(!lg.hintGroupEnabled("macro")&&lg.hintSelected(GeneralConfig.Hint.macroStart)&&!lg.hintSelected(GeneralConfig.Hint.macroPause));
  lg.setHintGroupEnabled("macro",true);check(lg.hintEnabled(GeneralConfig.Hint.macroStart)&&!lg.hintEnabled(GeneralConfig.Hint.macroPause));
  check(!ld.enabled&&!ld.ownEnabled(RandomDelayConfig.Key.WARP));ld.enabled=true;check(ld.sample(RandomDelayConfig.Key.WARP)==0);
  ld.setEnabled(RandomDelayConfig.Key.WARP,true);check(ld.sample(RandomDelayConfig.Key.WARP)==7);
  check(loaded.getViewDeviationConfig().effective(loaded.getViewDeviationConfig().zombie()));
  check(!new RandomDelayConfig().load(new JsonObject()));check(!new ViewDeviationConfig().load(new JsonObject()));
  System.out.println("PASS: "+checks+" header switches: legacy defaults, parent gates, child values, live delay/check behavior and JSON reload.");
 }
}
