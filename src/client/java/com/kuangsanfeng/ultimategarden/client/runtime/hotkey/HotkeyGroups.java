package com.kuangsanfeng.ultimategarden.client.runtime.hotkey;
import java.util.*;
/** Stable ownership tree shared by the menu and effective input/list/conflict state. */
public final class HotkeyGroups {
 public enum Group {
  FARM("farm",null), CARNIVAL("carnival",null), ZOMBIE("carnival.zombie_shootout",CARNIVAL),
  DEVELOPER("developer",null), DEVELOPER_MAPPING("developer.mapping",DEVELOPER), TELEPORT("developer.instant_teleport",DEVELOPER),
  HUD("hud",null), PREFERENCES("preferences",null);
  public final String id;public final Group parent;Group(String id,Group parent){this.id=id;this.parent=parent;}
  public String key(){return "group:"+id;}public String label(){return "ui.ultimategarden.hotkey.group."+id;}
 }
 public static Group owner(String id){return switch(id){
  case "toggle_zombie_shootout"->Group.ZOMBIE;
  case "mapping_initialize","mapping_toggle","mapping_redraw"->Group.DEVELOPER_MAPPING;
  case "toggle_instant_transmission","toggle_instant_transmission_y_rounding"->Group.TELEPORT;
  case "toggle_hud"->Group.HUD;
  case "window_focus","open_menu","start_pathfinding"->Group.PREFERENCES;
  case "macro_toggle","macro_reset","next_line","previous_line","spawn","warp","pest_loadout","pest_timer_cancel","loadout","crop_change"->Group.FARM;
  default->null;
 };}
 private HotkeyGroups(){}
}
