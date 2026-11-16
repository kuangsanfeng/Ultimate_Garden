
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.GeneralConfig;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;

import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.GeneralConfig.Hint;
import com.kuangsanfeng.ultimategarden.client.platform.hint.HintManager;
import com.kuangsanfeng.ultimategarden.client.macro.farm.hint.FarmHints;
import com.kuangsanfeng.ultimategarden.client.macro.farm.crop.CropType;
import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import net.minecraft.client.Minecraft;
public final class HintRegression {
 static int checks;
 static void check(boolean v){checks++;if(!v)throw new AssertionError("Hint check "+checks);}
 static void emit(String event){
  Minecraft.messages.clear();
  switch(event){
   case "CropInfo" -> FarmHints.printHint(event,CropType.WHEAT);
   case "LineFinished","TimeTesterCount" -> FarmHints.printHint(event,17);
   case "Hotkey","Helper","WrongCommand","FocusRelease","FocusRestore" -> HintManager.printHint(event);
   default -> FarmHints.printHint(event);
  }
 }
 public static void main(String[] args)throws Exception {
  Path path=Files.createTempDirectory("ug-hints-");var configs=new ConfigManager(path);Ultimate_GardenClient.configs=configs;var general=configs.getGeneralConfig();
  String[] events={"MacroStart","ProgramPause","ProgramReset","InitializeDone","LineFinished","TimeTesterCount","PestLoadoutTimerStart","PestLoadoutTimerReset","PestTimerDone","PestLoadoutChange","FocusRelease","FocusRestore","CropInfo","Hotkey","WrongCommand"};
  for(int i=0;i<Hint.values().length;i++){
   Hint hint=Hint.values()[i];check(general.hintEnabled(hint));
   for(boolean master:new boolean[]{true,false})for(boolean child:new boolean[]{true,false}){
    general.setShowModChatHints(master);general.setHintEnabled(hint,child);
    check(HintManager.isEnabled(events[i])==(master&&child));
    if(i!=8&&i!=9){emit(events[i]);check(Minecraft.messages.isEmpty()!= (master&&child));}
    check(general.hintEnabled(hint)==child);
   }
   general.setShowModChatHints(true);general.setHintEnabled(hint,true);
  }
  for(boolean done:new boolean[]{true,false})for(boolean change:new boolean[]{true,false}){
   general.setHintEnabled(Hint.pestTimerDone,done);general.setHintEnabled(Hint.pestLoadoutChange,change);emit("PestLoadoutTimeUp");
   check(Minecraft.messages.stream().anyMatch(s->s.contains("Ticker Done"))==done);
   check(Minecraft.messages.stream().anyMatch(s->s.contains("Loadout Change Macro"))==change);
   if(!done&&!change)check(Minecraft.messages.isEmpty());
  }
  general.setHintEnabled(Hint.cropSelectionInitialization,false);
  for(String e:List.of("CropSelect","ModeSelect","SimulateCropSelect","CropInfo")){emit(e);check(Minecraft.messages.isEmpty());}
  Minecraft.messages.clear();HintManager.printFor("CropSelect","Not implemented / Coming soon");check(Minecraft.messages.isEmpty());
  general.setHintEnabled(Hint.hotkeyHelp,true);emit("Hotkey");check(Minecraft.messages.stream().anyMatch(s->s.contains("REBOUND-toggle")));
  general.setHintEnabled(Hint.commandHelpErrors,false);emit("Helper");check(Minecraft.messages.isEmpty());emit("WrongCommand");check(Minecraft.messages.isEmpty());
  general.setShowModChatHints(false);check(configs.save(ConfigManager.Module.GENERAL));
  var loaded=new ConfigManager(path).getGeneralConfig();check(!loaded.isShowModChatHints());check(!loaded.hintEnabled(Hint.cropSelectionInitialization));check(!loaded.hintEnabled(Hint.commandHelpErrors));
  JsonObject old=JsonParser.parseString(Files.readString(path.resolve("general.json"))).getAsJsonObject();old.remove("hint");old.addProperty("extra",9);Files.writeString(path.resolve("general.json"),old.toString());
  configs=new ConfigManager(path);check(!configs.getGeneralConfig().isShowModChatHints());for(Hint h:Hint.values())check(configs.getGeneralConfig().hintEnabled(h));
  var doc=JsonParser.parseString(Files.readString(path.resolve("general.json"))).getAsJsonObject();check(doc.get("extra").getAsInt()==9);
  doc.getAsJsonObject("hint").addProperty("future",false);doc.getAsJsonObject("hint").addProperty("macroStart",false);Files.writeString(path.resolve("general.json"),doc.toString());
  configs=new ConfigManager(path);check(!configs.getGeneralConfig().hintEnabled(Hint.macroStart));check(!JsonParser.parseString(Files.readString(path.resolve("general.json"))).getAsJsonObject().getAsJsonObject("hint").get("future").getAsBoolean());
  doc=JsonParser.parseString(Files.readString(path.resolve("general.json"))).getAsJsonObject();
  doc.getAsJsonObject("hint").addProperty("macroReset","invalid");
  Files.writeString(path.resolve("general.json"),doc.toString());
  configs=new ConfigManager(path);check(configs.getGeneralConfig().hintEnabled(Hint.macroReset));
  check(!configs.getGeneralConfig().hintEnabled(Hint.macroStart));
  try(var files=Files.list(path)){check(files.anyMatch(p->p.getFileName().toString().startsWith("general.json.broken-")));}
  System.out.println("PASS: "+checks+" real hint dispatch, independent messages, blank-line suppression, live bindings and configuration compatibility checks; Minecraft chat is stubbed.");
 }
}
