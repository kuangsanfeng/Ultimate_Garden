import com.google.gson.*;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.storage.*;
import java.nio.file.*;import java.nio.file.attribute.FileTime;import java.util.*;
public final class StartupCorrectionRegression {
 static int checks;static void check(boolean v){checks++;if(!v)throw new AssertionError("startup "+checks);}
 static JsonObject read(Path p)throws Exception{return JsonParser.parseString(Files.readString(p)).getAsJsonObject();}
 static List<String> keys(JsonObject o){return new ArrayList<>(o.keySet());}
 public static void main(String[] args)throws Exception{
  Path game=Files.createTempDirectory("ug-moved-settings-"),mod=game.resolve("config/ultimategarden"),root=mod.resolve("user-setting");
  var a=new AccountIdentity(UUID.randomUUID(),"CurrentAccount");var b=new AccountIdentity(UUID.randomUUID(),"OtherAccount");
  var m=new ConfigManager(root,a);new ConfigManager(root,b);
  Path dir=root.resolve(a.name()),other=root.resolve(b.name());
  Files.writeString(other.resolve("general.json"),"{\"obsoleteOtherAccount\":true}");
  var otherTimes=new HashMap<Path,FileTime>();var otherBytes=new HashMap<Path,byte[]>();
  try(var paths=Files.list(other)){for(var p:paths.toList()){otherTimes.put(p,Files.getLastModifiedTime(p));otherBytes.put(p,Files.readAllBytes(p));}}
  Files.writeString(dir.resolve("general.json"),"{\"showModChatHints\":false,\"oldDeveloperProbe\":true,\"hint\":{\"obsolete\":true,\"macroStart\":false}}");
  Files.writeString(dir.resolve("ui.json"),"{\"accentColor\":\"#123456\",\"language\":\"ENGLISH\",\"obsolete\":1}");
  var crops=read(dir.resolve("crops.json"));crops.addProperty("obsolete",true);var wheat=crops.getAsJsonObject("crops").getAsJsonObject("WHEAT");wheat.addProperty("obsoleteCrop",true);wheat.add("movementA",JsonParser.parseString("[\"D\",\"W\"]"));
  Files.writeString(dir.resolve("crops.json"),crops.toString());
  var esp=read(dir.resolve("esp.json"));var custom=new JsonObject();custom.addProperty("obsolete",true);var box=new JsonObject();box.addProperty("color","#aabbcc");custom.add("box",box);esp.getAsJsonObject("targets").add("CUSTOM_TARGET",custom);Files.writeString(dir.resolve("esp.json"),esp.toString());
  var hud=read(dir.resolve("hud.json"));hud.getAsJsonObject("compact").addProperty("MACRO_STATUS",true);hud.getAsJsonObject("detail").addProperty("MACRO_STATUS",false);hud.getAsJsonObject("worldFilters").getAsJsonObject("FARM_MACRO").add("worlds",JsonParser.parseString("[\"garden\",\"hub\"]"));Files.writeString(dir.resolve("hud.json"),hud.toString());
  Files.writeString(mod.resolve("hotkeys.json"),"{\"customDynamicKey\":false}");Files.createDirectories(mod.resolve("map/hub"));Files.writeString(mod.resolve("map/hub/map.json"),"{\"worldName\":\"Hub\"}");
  m=new ConfigManager(root,a);
  var g=read(dir.resolve("general.json"));check(!g.has("oldDeveloperProbe")&&!g.getAsJsonObject("hint").has("obsolete"));check(!g.get("showModChatHints").getAsBoolean());check(!g.getAsJsonObject("hint").get("macroStart").getAsBoolean());check(g.has("autoPauseFarmWhenOpeningMenu"));check(g.getAsJsonObject("hint").getAsJsonObject("groups").get("macro").getAsBoolean());
  check(keys(g).equals(List.of("schemaVersion","hint","instantTransmission","pathFinding","showModChatHints","autoPauseFarmWhenOpeningMenu","autoResumeFarmAfterGuiClose")));
  var ui=read(dir.resolve("ui.json"));check(!ui.has("obsolete"));check(ui.get("accentColor").getAsString().equals("#123456"));check(keys(ui).equals(List.of("schemaVersion","mappingReplayShowChunkBorders","menuTransparency","language","uiThemeMode","accentColor")));
  wheat=read(dir.resolve("crops.json")).getAsJsonObject("crops").getAsJsonObject("WHEAT");check(!wheat.has("obsoleteCrop"));check(wheat.getAsJsonArray("movementA").toString().equals("[\"D\",\"W\"]"));
  custom=read(dir.resolve("esp.json")).getAsJsonObject("targets").getAsJsonObject("CUSTOM_TARGET");check(!custom.has("obsolete")&&custom.has("box"));check(custom.getAsJsonObject("box").get("color").getAsString().equals("#aabbcc"));check(!read(dir.resolve("hud.json")).getAsJsonObject("detail").get("MACRO_STATUS").getAsBoolean());
  check(read(dir.resolve("hud.json")).getAsJsonObject("worldFilters").getAsJsonObject("FARM_MACRO").getAsJsonArray("worlds").toString().equals("[\"garden\",\"hub\"]"));
  for(var p:otherTimes.keySet()){check(Files.getLastModifiedTime(p).equals(otherTimes.get(p)));check(Arrays.equals(Files.readAllBytes(p),otherBytes.get(p)));}
  check(Files.isRegularFile(root.resolve(".account-storage.lock"))&&!Files.exists(mod.resolve(".account-storage.lock")));
  check(Files.readString(mod.resolve("hotkeys.json")).equals("{\"customDynamicKey\":false}"));check(Files.readString(mod.resolve("map/hub/map.json")).equals("{\"worldName\":\"Hub\"}"));
  var expected=new HashMap<Path,String>();var times=new HashMap<Path,FileTime>();
  for(var module:ConfigManager.Module.values()){Path p=dir.resolve(module.name().toLowerCase(Locale.ROOT)+".json");Files.setLastModifiedTime(p,FileTime.fromMillis(946684800000L+module.ordinal()*1000));expected.put(p,Files.readString(p));times.put(p,Files.getLastModifiedTime(p));}
  new ConfigManager(root,a);
  for(var p:expected.keySet()){check(Files.readString(p).equals(expected.get(p)));check(Files.getLastModifiedTime(p).equals(times.get(p)));}
  // A non-startup account activation retains the established ordinary load/save path.
  var ordinary=read(dir.resolve("ui.json"));ordinary.addProperty("addedAfterStartup",true);Files.writeString(dir.resolve("ui.json"),ordinary.toString());check(m.activateAccount(a));check(read(dir.resolve("ui.json")).has("addedAfterStartup"));
  Path future=dir.resolve("pest.json");Files.writeString(future,"{\"schemaVersion\":99,\"futureField\":true}");String futureText=Files.readString(future);var next=new ConfigManager(root,a);check(Files.readString(future).equals(futureText));check(!next.save(ConfigManager.Module.PEST));
  System.out.println("PASS: "+checks+" startup correction/current-account isolation/new root lock, fixed order, dynamic ESP, ordered arrays, legal values and no second-start writes.");
 }
}
