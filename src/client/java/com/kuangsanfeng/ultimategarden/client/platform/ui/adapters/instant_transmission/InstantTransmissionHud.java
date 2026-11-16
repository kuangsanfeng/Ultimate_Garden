package com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.instant_transmission;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.InstantTransmissionController;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.HudConfig.HudId;
import com.kuangsanfeng.ultimategarden.client.platform.ui.hud.*;
import com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageManager;
import com.kuangsanfeng.ultimategarden.client.platform.ui.screen.UltimateGardenScreen;
import com.kuangsanfeng.ultimategarden.client.platform.ui.theme.ThemePalette;
import net.minecraft.client.Minecraft;import net.minecraft.client.gui.GuiGraphicsExtractor;
/** Read-only HUD adapter; independent visibility preference, final result lasts until next start. */
public final class InstantTransmissionHud {
 private final ConfigManager configs;private final LanguageManager language;
 public InstantTransmissionHud(ConfigManager configs){this.configs=configs;language=new LanguageManager(configs);}
 public void render(GuiGraphicsExtractor g){
  var c=Minecraft.getInstance();boolean editing=HudAdjustmentController.isActive();
  if(c.player==null||c.level==null||!editing&&c.options.hideGui||c.screen instanceof UltimateGardenScreen)return;
  var s=InstantTransmissionController.INSTANCE.hudSnapshot();
  var hud=configs.getHudConfig();var id=HudId.INSTANT_TRANSMISSION;
  if(!hud.show(id,s.running(),editing))return;
  var rows=TransmissionHudContent.rows(s,language::text,key->hud.visible(id,key));String status=TransmissionHudContent.status(s,language::text);
  int accent=s.running()?ThemePalette.from(configs.getUIConfig()).accent():s.reason().equals("COMPLETED")?0xff63cd91:0xffed6669;
  boolean showStatus=hud.visible(id,"status");
  int width=Math.max(310,c.font.width(status)+22),height=showStatus?59:40;
  for(var row:rows){width=Math.max(width,Math.max(c.font.width(row.label()),c.font.width(row.value()))+22);}
  for(var row:rows)height+=HudPanel.rowHeight(row.label(),row.value(),width);
  int sw=c.getWindow().getGuiScaledWidth(),sh=c.getWindow().getGuiScaledHeight();var b=configs.getHudConfig().bounds(HudId.INSTANT_TRANSMISSION);
  double fit=Math.min(1,Math.min((sw-16.)/width,(sh-16.)/height));
  double minW=Math.min(sw,width*Math.min(.65,fit)),minH=Math.min(sh,height*Math.min(.65,fit));
  double bw=Math.clamp(b.width()>0?b.width()*sw:width*fit,minW,sw),bh=Math.clamp(b.height()>0?b.height()*sh:height*fit,minH,sh);
  double x=Math.clamp(b.width()>0?b.x()*sw:(sw-bw)/2,0,sw-bw),y=Math.clamp(b.height()>0?b.y()*sh:sh-bh-24,0,sh-bh);
  float scale=(float)Math.min(bw/width,bh/height);if(scale<=0)return;
  if(editing)HudAdjustmentController.layout(HudId.INSTANT_TRANSMISSION,x,y,bw,bh,minW,minH);
  width=(int)Math.round(bw/scale);height=(int)Math.round(bh/scale);
  g.pose().pushMatrix();g.pose().translate((float)x,(float)y);g.pose().scale(scale);
  HudPanel.background(g,width,height,accent);HudPanel.title(g,"Instant Transmission");
  if(showStatus)g.text(c.font,status,11,28,accent,false);int rowY=showStatus?47:28;
  for(var row:rows){int rh=HudPanel.rowHeight(row.label(),row.value(),width);HudPanel.row(g,row.label(),row.value(),width,rowY,rh,0xffffffff);rowY+=rh;}
  g.pose().popMatrix();if(editing)HudPanel.editorOverlay(g,configs);
 }
}
