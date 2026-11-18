import com.kuangsanfeng.ultimategarden.client.runtime.hotkey.HotkeySwitches;import java.nio.file.*;
public class HotkeySwitchRegression {
 public static void main(String[] args)throws Exception{
  var root=Files.createTempDirectory("ug-hotkey-switch-");var file=root.resolve("hotkeys.json");var store=new HotkeySwitches.Store(file);
  if(!store.enabled("open_menu")||!store.enabled("future"))throw new AssertionError("default on");
  if(!store.set("open_menu",false)||store.enabled("open_menu"))throw new AssertionError("disable");
  var reloaded=new HotkeySwitches.Store(file);if(reloaded.enabled("open_menu")||!reloaded.enabled("toggle_instant_transmission"))throw new AssertionError("reload/isolation");
  if(!reloaded.set("open_menu",true)||!new HotkeySwitches.Store(file).enabled("open_menu"))throw new AssertionError("re-enable");
  var source=Files.readString(Path.of("src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/hotkey/MacroKeyBindings.java"));if(!source.contains("register(\"open_menu\", GLFW.GLFW_KEY_M)"))throw new AssertionError("preserve M");
  var developer=com.kuangsanfeng.ultimategarden.client.runtime.hotkey.HotkeyGroups.Group.DEVELOPER;
  var mapping=com.kuangsanfeng.ultimategarden.client.runtime.hotkey.HotkeyGroups.Group.DEVELOPER_MAPPING;
  var teleport=com.kuangsanfeng.ultimategarden.client.runtime.hotkey.HotkeyGroups.Group.TELEPORT;
  reloaded.set(mapping.key(),false);if(reloaded.effective("mapping_initialize")||!reloaded.effective("toggle_instant_transmission"))throw new AssertionError("developer mapping isolated from actual regular mappings and teleport");
  reloaded.set("toggle_instant_transmission_y_rounding",false);reloaded.set(developer.key(),false);if(reloaded.effective("toggle_instant_transmission"))throw new AssertionError("ancestor gate");
  reloaded.set(developer.key(),true);if(!reloaded.effective("toggle_instant_transmission")||reloaded.effective("toggle_instant_transmission_y_rounding")||reloaded.enabled(mapping.key()))throw new AssertionError("restore saved child flags");
  reloaded.set(teleport.key(),false);if(reloaded.effective("toggle_instant_transmission"))throw new AssertionError("second-level gate");
  var carnival=com.kuangsanfeng.ultimategarden.client.runtime.hotkey.HotkeyGroups.Group.CARNIVAL;var zombie=com.kuangsanfeng.ultimategarden.client.runtime.hotkey.HotkeyGroups.Group.ZOMBIE;
  reloaded.set(zombie.key(),false);reloaded.set(carnival.key(),false);reloaded.set(carnival.key(),true);if(reloaded.effective("toggle_zombie_shootout"))throw new AssertionError("saved Zombie group");
  reloaded.set(zombie.key(),true);if(!reloaded.effective("toggle_zombie_shootout"))throw new AssertionError("restore Zombie");
  var saved=new HotkeySwitches.Store(file);if(saved.enabled(mapping.key())||saved.effective("toggle_instant_transmission"))throw new AssertionError("group reload");
  var ui=new com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.UIConfig();// palette is checked below without constructing widgets
  for(var theme:com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.UIConfig.Theme.values()){
   ui.setUiThemeMode(theme);var palette=com.kuangsanfeng.ultimategarden.client.platform.ui.theme.ThemePalette.from(ui);
   if(com.kuangsanfeng.ultimategarden.client.platform.ui.widget.CollapsibleSection.headerSurface(palette,false)!=0xffffffff||com.kuangsanfeng.ultimategarden.client.platform.ui.widget.CollapsibleSection.headerSurface(palette,true)!=palette.selected())throw new AssertionError("shared idle/hover colors");
  }
  String menu=Files.readString(Path.of("src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/menu/GardenMenuAdapter.java"));String hotkeys=menu.substring(menu.indexOf("private final class HotkeyPageAdapter"),menu.indexOf("private final HintPageAdapter"));if(hotkeys.contains("configure_binding")||hotkeys.contains("info(()->language.text(title+"))throw new AssertionError("no persistent descriptions or bind buttons");
  var registrations=java.util.regex.Pattern.compile("register\\(\\s*\"([a-z_]+)\"").matcher(source);int registered=0;
  while(registrations.find()){if(com.kuangsanfeng.ultimategarden.client.runtime.hotkey.HotkeyGroups.owner(registrations.group(1))==null)throw new AssertionError("unclassified actual binding "+registrations.group(1));registered++;}
  if(registered!=20)throw new AssertionError("review changed registry size "+registered);
  var preferences=com.kuangsanfeng.ultimategarden.client.runtime.hotkey.HotkeyGroups.Group.PREFERENCES;
  if(!reloaded.effective("start_pathfinding")||!source.contains("register(\"start_pathfinding\", GLFW.GLFW_KEY_UNKNOWN)"))throw new AssertionError("path hotkey defaults");
  reloaded.set(preferences.key(),false);if(reloaded.effective("start_pathfinding"))throw new AssertionError("path hotkey parent gate");
  reloaded.set(preferences.key(),true);reloaded.set("start_pathfinding",false);if(reloaded.effective("start_pathfinding"))throw new AssertionError("path hotkey own gate");
  var roots=java.util.Arrays.stream(com.kuangsanfeng.ultimategarden.client.runtime.hotkey.HotkeyGroups.Group.values()).filter(g->g.parent==null).map(g->g.id).toList();if(!roots.equals(java.util.List.of("farm","carnival","developer","hud","preferences")))throw new AssertionError("root order");
  var legacyFile=root.resolve("legacy.json");Files.writeString(legacyFile,"{\"group:mapping\":false,\"group:developer.mapping\":true,\"mapping_initialize\":true}");var migrated=new HotkeySwitches.Store(legacyFile);if(migrated.effective("mapping_initialize")||!migrated.enabled("mapping_initialize")||!migrated.effective("toggle_instant_transmission"))throw new AssertionError("legacy group migration preserves off and own flags");
  System.out.println("Hotkey instance-wide default/persist/reload/independence/re-enable/unchanged registration checks passed");
 }
}