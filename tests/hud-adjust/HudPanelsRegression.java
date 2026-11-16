import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.*;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.HudConfig;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.HudConfig.*;
import com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.mapping.MappingHud;
import com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.instant_transmission.*;
import com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.zombie_shoot.ZombieShootoutHud;
import com.kuangsanfeng.ultimategarden.client.platform.ui.hud.HudAdjustmentController;
import com.kuangsanfeng.ultimategarden.client.platform.ui.screen.UltimateGardenScreen;
import com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageManager;
import com.kuangsanfeng.ultimategarden.client.function.world.*;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.*;
import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import net.minecraft.client.Minecraft;import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.nio.file.*;import java.util.*;import com.google.gson.*;
public final class HudPanelsRegression {
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("HUD panels "+checks);}
 static void clear(HudConfig h,HudId id){for(String field:HudConfig.fields(id)){h.content(id,field,true,false);h.content(id,field,false,false);}}
 public static void main(String[] args)throws Exception{
  var root=Files.createTempDirectory("ug-panels-");var config=new ConfigManager(root);Ultimate_GardenClient.configs=config;var h=config.getHudConfig();var c=Minecraft.getInstance();var language=new LanguageManager(config);
  h.setBounds(.1,.2,.3,.4);h.setMode(Mode.DETAIL);h.setCompact(Item.CROP,false);h.setDetail(Item.CROP,true);var farmBounds=h.bounds(HudId.FARM_MACRO);
  for(var id:List.of(HudId.ZOMBIE_SHOOTOUT,HudId.INSTANT_TRANSMISSION,HudId.MAPPING)){
   for(String field:HudConfig.fields(id)){check(h.visible(id,field));h.content(id,field,true,false);check(!h.visible(id,field));h.content(id,field,false,true);check(!h.visible(id,field));h.mode(id,Mode.DETAIL);check(h.visible(id,field));h.content(id,field,true,true);check(h.visible(id,field));h.mode(id,Mode.COMPACT);}
   h.onlyRunning(id,true);h.enabled(id,true);check(!h.show(id,false,false)&&h.show(id,true,false));h.enabled(id,false);check(h.onlyRunning(id));check(h.show(id,false,true));h.onlyRunning(id,false);h.enabled(id,true);
  }
  check(h.getMode()==Mode.DETAIL&&!h.compact(Item.CROP)&&h.detail(Item.CROP));check(h.bounds(HudId.FARM_MACRO).equals(farmBounds));
  var render=new InstantTransmissionHud(config);var controller=InstantTransmissionController.INSTANCE;
  h.onlyRunning(HudId.INSTANT_TRANSMISSION,true);var g=new GuiGraphicsExtractor();render.render(g);check(g.backgrounds==0);
  controller.value=new TransmissionHudSnapshot(true,true,"RESET_JUMP","RUNNING",null,1,570,1,0.,-5.,0.,90.,true,0.,90.,2.,1,1,1,1,0L);
  g=new GuiGraphicsExtractor();render.render(g);check(g.backgrounds==1);
  var all=TransmissionHudContent.rows(controller.value,language::text);for(var row:all){var one=TransmissionHudContent.rows(controller.value,language::text,key->key.equals(row.key()));check(one.size()==1&&one.getFirst().key().equals(row.key()));}
  clear(h,HudId.INSTANT_TRANSMISSION);h.content(HudId.INSTANT_TRANSMISSION,"progress",true,true);g=new GuiGraphicsExtractor();render.render(g);check(g.values.contains(language.text("ui.ultimategarden.transmission_hud.progress")));check(!g.values.contains(language.text("ui.ultimategarden.transmission_hud.actual_angles")));
  controller.value=controller.value.stopped("MANUAL_STOP");g=new GuiGraphicsExtractor();render.render(g);check(g.backgrounds==0);h.onlyRunning(HudId.INSTANT_TRANSMISSION,false);g=new GuiGraphicsExtractor();render.render(g);check(g.backgrounds==1);
  var zombie=new ZombieShootoutHud(config);clear(h,HudId.ZOMBIE_SHOOTOUT);h.content(HudId.ZOMBIE_SHOOTOUT,"ping",true,true);g=new GuiGraphicsExtractor();zombie.render(g);check(g.values.contains(language.text("ui.ultimategarden.shootout_hud.ping")));check(!g.values.contains(language.text("ui.ultimategarden.shootout_hud.target")));
  h.mode(HudId.ZOMBIE_SHOOTOUT,Mode.DETAIL);h.content(HudId.ZOMBIE_SHOOTOUT,"target",false,true);g=new GuiGraphicsExtractor();zombie.render(g);check(g.values.contains(language.text("ui.ultimategarden.shootout_hud.target")));check(Collections.frequency(g.values,language.text("ui.ultimategarden.shootout_hud.ping"))==1);
  for(String field:HudConfig.fields(HudId.ZOMBIE_SHOOTOUT))if(!field.equals("status")){
   clear(h,HudId.ZOMBIE_SHOOTOUT);h.mode(HudId.ZOMBIE_SHOOTOUT,Mode.COMPACT);h.content(HudId.ZOMBIE_SHOOTOUT,field,true,true);g=new GuiGraphicsExtractor();zombie.render(g);
   check(Collections.frequency(g.values,language.text("ui.ultimategarden.shootout_hud."+field))==1);
   for(String other:HudConfig.fields(HudId.ZOMBIE_SHOOTOUT))if(!other.equals(field)&&!other.equals("status"))check(!g.values.contains(language.text("ui.ultimategarden.shootout_hud."+other)));
  }
  for(var lang:com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageType.values())for(var id:HudId.values()){
   String panel="ui.ultimategarden.hud.panel."+id.name();check(!com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageManager.translate(lang,panel).equals(panel));
   for(String field:HudConfig.fields(id)){String key=switch(id){case FARM_MACRO->"ui.ultimategarden.hud.item."+field;case ZOMBIE_SHOOTOUT,INSTANT_TRANSMISSION->"ui.ultimategarden.hud.field."+id.name()+"."+field;case MAPPING->"ui.ultimategarden.mapping_hud."+field;};check(!com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageManager.translate(lang,key).equals(key));}
  }
  var mapping=new MappingHud(config);clear(h,HudId.MAPPING);h.enabled(HudId.MAPPING,true);
  for(String field:HudConfig.fields(HudId.MAPPING)){h.content(HudId.MAPPING,field,true,true);g=new GuiGraphicsExtractor();mapping.render(g);check(g.backgrounds==1);check(g.values.contains(language.text("ui.ultimategarden.mapping_hud."+field)));check(Collections.frequency(g.values,field.startsWith("recording_")?"\u2014":language.text("ui.ultimategarden.mapping_hud.unknown"))==1);h.content(HudId.MAPPING,field,true,false);}
  h.onlyRunning(HudId.MAPPING,true);g=new GuiGraphicsExtractor();mapping.render(g);check(g.backgrounds==0&&!MappingHud.taskRunning());com.kuangsanfeng.ultimategarden.client.function.mapping.MappingController.INSTANCE.recording=true;g=new GuiGraphicsExtractor();mapping.render(g);check(g.backgrounds==1&&MappingHud.taskRunning());com.kuangsanfeng.ultimategarden.client.function.mapping.MappingController.INSTANCE.recording=false;h.enabled(HudId.MAPPING,false);
  c.screen=new UltimateGardenScreen();HudAdjustmentController.begin((UltimateGardenScreen)c.screen);g=new GuiGraphicsExtractor();mapping.render(g);check(g.backgrounds==1&&!h.enabled(HudId.MAPPING));
  var before=new EnumMap<HudId,Bounds>(HudId.class);for(var id:HudId.values())before.put(id,h.bounds(id));
  HudAdjustmentController.layout(HudId.MAPPING,600,400,250,150,125,75);HudAdjustmentController.move(700,460);HudAdjustmentController.button(0,1);check(HudAdjustmentController.selected()==HudId.MAPPING);HudAdjustmentController.move(720,480);HudAdjustmentController.button(0,0);
  var moved=h.bounds(HudId.MAPPING);check(moved.x()==.62);HudAdjustmentController.move(870,570);HudAdjustmentController.button(0,1);HudAdjustmentController.move(890,590);HudAdjustmentController.button(0,0);check(h.bounds(HudId.MAPPING).width()>.25);
  for(var id:HudId.values())if(id!=HudId.MAPPING)check(h.bounds(id).equals(before.get(id)));HudAdjustmentController.finish(true);
  var saved=new ConfigManager(root).getHudConfig();for(var id:HudId.values()){check(saved.bounds(id).equals(h.bounds(id)));check(saved.mode(id)==h.mode(id));check(saved.enabled(id)==h.enabled(id));check(saved.onlyRunning(id)==h.onlyRunning(id));for(String key:HudConfig.fields(id))check(saved.visible(id,key)==h.visible(id,key));}
  var legacy=new HudConfig();check(!legacy.load(new JsonObject()));check(!legacy.onlyRunning(HudId.INSTANT_TRANSMISSION)&&!legacy.onlyRunning(HudId.MAPPING));for(var id:List.of(HudId.ZOMBIE_SHOOTOUT,HudId.INSTANT_TRANSMISSION))for(String field:HudConfig.fields(id))check(legacy.visible(id,field));
  var context=new WorldInfoState();Object world=new Object(),connection=new Object();context.update(world,connection,"server-one","dimension-one");check(context.read(world,connection).server().equals("server-one"));check(context.read(new Object(),connection).server()==null);check(context.read(world,new Object()).dimension()==null);context.clear();check(context.read(world,connection).server()==null);context.update(world,connection,"server-two","dimension-two");check(context.read(world,connection).value("island")==null&&context.read(world,connection).value("instance")==null);context.update(null,connection,"old","old");check(context.read(world,connection)==WorldInfoState.UNKNOWN);
  System.out.println("PASS: "+checks+" independent panel content/modes, production HUD filtering, running visibility, Mapping preview/drag/resize/reload and context invalidation checks; native boundaries stubbed.");
 }
}
