package com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.mapping;
import com.kuangsanfeng.ultimategarden.client.function.world.WorldInfoService;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.HudConfig;
import com.kuangsanfeng.ultimategarden.client.platform.ui.hud.*;
import com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageManager;
import com.kuangsanfeng.ultimategarden.client.platform.ui.screen.UltimateGardenScreen;
import com.kuangsanfeng.ultimategarden.client.platform.ui.theme.ThemePalette;
import net.minecraft.client.Minecraft;import net.minecraft.client.gui.GuiGraphicsExtractor;import java.util.*;
/** Read-only world and recorder snapshots. Rendering performs no IO or scheduling. */
public final class MappingHud {
 private final ConfigManager configs;private final LanguageManager language;
 public MappingHud(ConfigManager configs){this.configs=configs;language=new LanguageManager(configs);}
 /** Existing HUD visibility follows actual recording activity. */
 public static boolean taskRunning(){return com.kuangsanfeng.ultimategarden.client.function.mapping.MappingController.INSTANCE.running();}
 public void render(GuiGraphicsExtractor g){
  var c=Minecraft.getInstance();boolean editing=HudAdjustmentController.isActive();
  if(c.player==null||c.level==null||!editing&&c.options.hideGui||c.screen instanceof UltimateGardenScreen)return;
  var recorder=com.kuangsanfeng.ultimategarden.client.function.mapping.MappingController.INSTANCE.recordingSnapshot();
  var id=HudConfig.HudId.MAPPING;var hud=configs.getHudConfig();if(!hud.show(id,recorder.recording(),editing))return;
  if(!editing&&!configs.getHudConfig().worldAllows(com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.HudConfig.HudId.MAPPING,com.kuangsanfeng.ultimategarden.client.function.world.WorldInfoService.snapshot().island()))return;
  var snapshot=WorldInfoService.snapshot();var rows=new ArrayList<String[]>();
  for(String key:HudConfig.fields(id))if(hud.visible(id,key)){String value=key.startsWith("recording_")?MappingRecordingContent.value(key,recorder,language):snapshot.value(key);rows.add(new String[]{language.text("ui.ultimategarden.mapping_hud."+key),value==null||value.isBlank()?language.text("ui.ultimategarden.mapping_hud.unknown"):value,key.startsWith("recording_")?"recording":"world"});}
  int width=288,height=rows.isEmpty()?30:40;for(var row:rows)width=Math.max(width,Math.max(c.font.width(row[0]),c.font.width(row[1]))+22);
  String last="";for(var row:rows){if(!row[2].equals(last)){height+=21;last=row[2];}height+=HudPanel.rowHeight(row[0],row[1],width);}
  int sw=c.getWindow().getGuiScaledWidth(),sh=c.getWindow().getGuiScaledHeight();var b=hud.bounds(id);double fit=Math.min(1,Math.min((sw-16.)/width,(sh-16.)/height));
  double minW=Math.min(sw,width*Math.min(.65,fit)),minH=Math.min(sh,height*Math.min(.65,fit));
  double bw=Math.clamp(b.width()>0?b.width()*sw:width*fit,minW,sw),bh=Math.clamp(b.height()>0?b.height()*sh:height*fit,minH,sh);
  double x=Math.clamp(b.width()>0?b.x()*sw:8,0,sw-bw),y=Math.clamp(b.height()>0?b.y()*sh:sh-bh-24,0,sh-bh);float scale=(float)Math.min(bw/width,bh/height);if(scale<=0)return;
  if(editing)HudAdjustmentController.layout(id,x,y,bw,bh,minW,minH);width=(int)Math.round(bw/scale);height=(int)Math.round(bh/scale);
  g.pose().pushMatrix();g.pose().translate((float)x,(float)y);g.pose().scale(scale);HudPanel.background(g,width,height,ThemePalette.from(configs.getUIConfig()).accent());HudPanel.title(g,"Mapping");
  int rowY=28;last="";for(var row:rows){if(!row[2].equals(last)){g.text(c.font,language.text("ui.ultimategarden.mapping_hud.group."+row[2]),11,rowY,0xffffffff,false);rowY+=21;last=row[2];}int rh=HudPanel.rowHeight(row[0],row[1],width);HudPanel.row(g,row[0],row[1],width,rowY,rh,0xffffffff);rowY+=rh;}
  g.pose().popMatrix();if(editing)HudPanel.editorOverlay(g,configs);
 }
}
