package com.kuangsanfeng.ultimategarden.client.runtime.hotkey;
import com.google.gson.*;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.storage.ConfigIO;
import java.nio.file.Path;
import net.minecraft.client.KeyMapping;
/** Instance-wide preferences; keys and registration remain owned by Minecraft options. */
public final class HotkeySwitches {
 public static final String PREFIX="key.ultimate_garden.";
 private static final class Holder {static final Store STORE=new Store(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("ultimategarden/hotkeys.json"));}
 private static long revision;
 public static long revision(){return revision;}
 public static boolean enabled(KeyMapping key){return !key.getName().startsWith(PREFIX)||Holder.STORE.effective(key.getName().substring(PREFIX.length()));}
 public static boolean ownEnabled(KeyMapping key){return Holder.STORE.enabled(key.getName().substring(PREFIX.length()));}
 public static boolean groupEnabled(HotkeyGroups.Group group){return Holder.STORE.enabled(group.key());}
 public static boolean setGroupEnabled(HotkeyGroups.Group group,boolean value){if(!Holder.STORE.set(group.key(),value))return false;for(var binding:MacroKeyBindings.getBindings()){var key=binding.mapping();if(!enabled(key)){key.setDown(false);while(key.consumeClick()){}}}revision++;return true;}
 public static KeyMapping[] visible(KeyMapping[] keys){return java.util.Arrays.stream(keys).filter(HotkeySwitches::enabled).toArray(KeyMapping[]::new);}
 public static boolean setEnabled(KeyMapping key,boolean value){if(!key.getName().startsWith(PREFIX))return false;
  if(!Holder.STORE.set(key.getName().substring(PREFIX.length()),value))return false;
  key.setDown(false);while(key.consumeClick()){}revision++;return true;
 }
 public static final class Store {
  private final Path path;private final ConfigIO io=new ConfigIO();private JsonObject json;private boolean writable=true;
  public Store(Path path){this.path=path;try{json=io.read(path);if(json.has("group:mapping")){boolean merged=enabled("group:mapping")&&enabled("group:developer.mapping");var migrated=json.deepCopy();migrated.remove("group:mapping");migrated.addProperty("group:developer.mapping",merged);boolean persisted=io.write(path,migrated);json=migrated;if(!persisted)writable=false;}}catch(Exception e){writable=io.backup(path);json=new JsonObject();}}
  public boolean enabled(String id){var v=json.get(id);return v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isBoolean()||v.getAsBoolean();}
  public boolean effective(String id){if(!enabled(id))return false;for(var group=HotkeyGroups.owner(id);group!=null;group=group.parent)if(!enabled(group.key()))return false;return true;}
  public boolean set(String id,boolean enabled){if(!writable)return false;var next=json.deepCopy();next.addProperty(id,enabled);if(!io.write(path,next))return false;json=next;return true;}
 }
}
