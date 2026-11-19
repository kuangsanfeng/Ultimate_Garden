package com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.path_finding;

import com.kuangsanfeng.ultimategarden.client.function.path_finding.execution.ClientPathFinding;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.execution.PathService;
import com.kuangsanfeng.ultimategarden.client.function.world.WorldInfoService;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.HudConfig.HudId;
import com.kuangsanfeng.ultimategarden.client.platform.ui.hud.HudAdjustmentController;
import com.kuangsanfeng.ultimategarden.client.platform.ui.hud.HudPanel;
import com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageManager;
import com.kuangsanfeng.ultimategarden.client.platform.ui.render.RoundedRect;
import com.kuangsanfeng.ultimategarden.client.platform.ui.screen.UltimateGardenScreen;
import com.kuangsanfeng.ultimategarden.client.platform.ui.theme.ThemePalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayList;
import java.util.Locale;

public final class PathFindingHud {
    private static final HudId ID=HudId.PATH_FINDING;
    private static final String PREFIX="ui.ultimategarden.path_finding.";
    private final ConfigManager configs;
    private final LanguageManager language;
    public PathFindingHud(ConfigManager configs) {this.configs=configs;language=new LanguageManager(configs);}

    public void render(GuiGraphicsExtractor g) {
        var c=Minecraft.getInstance();
        boolean editing=HudAdjustmentController.isActive();
        if (c.player==null || c.level==null || c.screen instanceof UltimateGardenScreen || !editing && c.options.hideGui) return;
        var controller=ClientPathFinding.INSTANCE;
        var candidate=controller.confirmation();
        var config=configs.getHudConfig();
        boolean waiting=candidate!=null;
        if (!config.show(ID,waiting||controller.service().stage()!=PathService.Stage.IDLE,editing)
                || !editing && !config.worldAllows(ID,WorldInfoService.snapshot().island())) return;
        var rows=new ArrayList<String[]>();
        if(config.visible(ID,"status")) rows.add(new String[]{language.text(PREFIX+"status"),waiting?language.text(PREFIX+"pending"):controller.status()});
        if((waiting||editing) && config.visible(ID,"candidate")) {
            var point=waiting?candidate.point():new com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point(-12,70,34);
            rows.add(new String[]{language.text(PREFIX+"candidate"),point.x()+" / "+point.y()+" / "+point.z()});
        }
        var details=controller.hudFields();
        if(!waiting && config.visible(ID,"status") && details.containsKey("status"))
            rows.set(0,new String[]{language.text(PREFIX+"status"),details.get("status")});
        for(String field:com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.HudConfig.fields(ID)){
            if(field.equals("status")||!config.visible(ID,field)||!details.containsKey(field))continue;
            rows.add(new String[]{language.text(PREFIX+field),details.get(field)});
        }
        if(config.visible(ID,"latency") && (editing||controller.service().stage()!=PathService.Stage.IDLE)){
            Double latency=controller.latencyMilliseconds();
            String value=latency==null?language.text(PREFIX+(controller.latencyStatus().equals("UNAVAILABLE")?
                    "latency_unavailable":"latency_pending")):String.format(Locale.ROOT,"%.2f ms",latency);
            rows.add(new String[]{language.text(PREFIX+"latency"),value});
        }
        boolean progress=(waiting||editing) && config.visible(ID,"remaining");
        if(progress)rows.add(new String[]{language.text(PREFIX+"remaining"),String.format(Locale.ROOT,"%.2f s",waiting?candidate.remainingMs()/1000.0:5.0)});
        int width=235,height=progress?41:28;
        for(var row:rows)height+=HudPanel.rowHeight(row[0],row[1],width);
        int sw=c.getWindow().getGuiScaledWidth(),sh=c.getWindow().getGuiScaledHeight();
        var bounds=config.bounds(ID);
        double fit=Math.min(1,Math.min((sw-16.)/width,(sh-16.)/height));
        if (fit<=0) return;
        double minW=width*Math.min(.65,fit),minH=height*Math.min(.65,fit);
        double bw=Math.clamp(bounds.width()>0?bounds.width()*sw:width*fit,minW,sw);
        double bh=Math.clamp(bounds.height()>0?bounds.height()*sh:height*fit,minH,sh);
        double x=Math.clamp(bounds.width()>0?bounds.x()*sw:sw-bw-8,0,sw-bw);
        double y=Math.clamp(bounds.height()>0?bounds.y()*sh:sh-bh-24,0,sh-bh);
        float scale=(float)Math.min(bw/width,bh/height);
        if(editing)HudAdjustmentController.layout(ID,x,y,bw,bh,minW,minH);
        width=(int)Math.round(bw/scale);height=(int)Math.round(bh/scale);
        int accent=ThemePalette.from(configs.getUIConfig()).accent();
        g.pose().pushMatrix();g.pose().translate((float)x,(float)y);g.pose().scale(scale);
        HudPanel.background(g,width,height,accent);HudPanel.title(g,language.text(PREFIX+"hud_title"));
        int rowY=26;
        for(var row:rows){int h=HudPanel.rowHeight(row[0],row[1],width);HudPanel.row(g,row[0],row[1],width,rowY,h,0xffffffff);rowY+=h;}
        if(progress){
            RoundedRect.fill(g,11,height-9,width-22,3,1,0xff626b66);
            int barWidth=(int)Math.round((width-22)*(waiting?candidate.fraction():1));
            if(barWidth>0)RoundedRect.fill(g,11,height-9,barWidth,3,1,accent);
        }
        g.pose().popMatrix();
        if(editing)HudPanel.editorOverlay(g,configs);
    }
}
