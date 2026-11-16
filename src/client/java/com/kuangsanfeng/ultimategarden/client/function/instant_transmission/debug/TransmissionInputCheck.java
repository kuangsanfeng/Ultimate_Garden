package com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug;
/** Values read once at the decision point. No world reads, side effects or menu-edit policy. */
public record TransmissionInputCheck(boolean player,boolean world,boolean connection,boolean grounded,
 boolean worldChanged,boolean connectionChanged,boolean farmRunning,boolean farmHeld,boolean farmAction,
 boolean shootout,boolean hudEditor,boolean alive,boolean screenOpen,boolean overlay,boolean paused,boolean focused,boolean grabbed) {
 public String contextFailure(){
  if(!player)return "PLAYER_UNAVAILABLE";if(!world)return "WORLD_UNAVAILABLE";if(!connection)return "CONNECTION_UNAVAILABLE";
  if(worldChanged)return "WORLD_CHANGED";if(connectionChanged)return "CONNECTION_CHANGED";return null;
 }
 public String ownerFailure(){
  if(farmRunning||farmHeld||farmAction)return "INPUT_CONFLICT_FARM";
  if(shootout)return "INPUT_CONFLICT_SHOOTOUT";if(hudEditor)return "INPUT_CONFLICT_HUD_EDITOR";return null;
 }
 public String startFailure(){var c=contextFailure();if(c!=null)return c;if(!grounded)return "PLAYER_NOT_ON_GROUND";return ownerFailure();}
 public boolean mouseRestored(){return !screenOpen&&!overlay&&!paused&&focused&&grabbed;}
 public String runtimeFailure(){
  var c=contextFailure();if(c!=null)return c;c=ownerFailure();if(c!=null)return c;
  if(!alive)return "PLAYER_NOT_ALIVE";if(screenOpen)return "SCREEN_OPEN";if(overlay)return "OVERLAY_ACTIVE";
  if(paused)return "CLIENT_PAUSED";if(!focused)return "WINDOW_INACTIVE";if(!grabbed)return "MOUSE_NOT_CAPTURED";return null;
 }
}
