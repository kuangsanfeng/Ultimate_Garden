import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.HudConfig;
import com.kuangsanfeng.ultimategarden.client.function.world.WorldIds;
import com.google.gson.JsonObject;
public class WorldFilterRegression {
 public static void main(String[] args){var hud=new HudConfig();var farm=HudConfig.HudId.FARM_MACRO;var zombie=HudConfig.HudId.ZOMBIE_SHOOTOUT;
  if(!WorldIds.id("Private Island").equals(com.kuangsanfeng.ultimategarden.client.function.mapping.MappingNames.key("Private Island")))throw new AssertionError("existing directory key correspondence");
  if(WorldIds.all().size()!=21||!WorldIds.id(" Private Island ").equals("private-island")||!WorldIds.id("PRIVATE-ISLAND").equals("private-island")||!WorldIds.id("dynamic").equals("private-island"))throw new AssertionError("private island keys");
  if(!WorldIds.id("dungeon").equals("dungeon")||WorldIds.id("Dungeon Hub").equals(WorldIds.id("Dungeon"))||WorldIds.id("Glacite Tunnels")!=null||!WorldIds.id("Glacite Mineshaft").equals("glacite-mineshaft"))throw new AssertionError("distinct worlds");
  for(var id:HudConfig.HudId.values()){if(hud.worldFilter(id)||!hud.worldAllows(id,null))throw new AssertionError("default off");hud.worldFilter(id,true);if(!hud.worldAllows(id,"Hub")||hud.worldAllows(id,null))throw new AssertionError("all/unknown");}
  hud.allWorlds(farm,false);hud.worldSelected(farm,"hub",true);hud.allWorlds(zombie,false);hud.worldSelected(zombie,"garden",true);
  if(!hud.worldAllows(farm,"Hub")||hud.worldAllows(farm,"Garden")||!hud.worldAllows(zombie,"Garden")||hud.worldAllows(zombie,"Hub"))throw new AssertionError("independence");
  hud.worldFilter(farm,false);if(!hud.worldAllows(farm,null)||!hud.worldAllows(farm,"Garden"))throw new AssertionError("off ignores world");hud.worldFilter(farm,true);
  var loaded=new HudConfig();loaded.load(hud.toJson(new JsonObject()));if(!loaded.worldAllows(farm,"Hub")||loaded.worldAllows(farm,"Garden")||!loaded.worldAllows(zombie,"Garden"))throw new AssertionError("persist restore");
  loaded.allWorlds(farm,false);if(loaded.worldAllows(farm,"Hub"))throw new AssertionError("none");System.out.println("World filters: 21 static worlds, aliases, per-HUD selection, unknown hiding, off/restore, all/none and JSON reload passed");
 }
}
