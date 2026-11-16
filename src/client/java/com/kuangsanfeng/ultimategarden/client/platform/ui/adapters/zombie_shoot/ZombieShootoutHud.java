package com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.zombie_shoot;
import com.kuangsanfeng.ultimategarden.client.platform.ui.hud.HudAdjustmentController;
import com.kuangsanfeng.ultimategarden.client.platform.ui.hud.HudPanel;



import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController;
import com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageManager;
import com.kuangsanfeng.ultimategarden.client.platform.ui.render.RoundedRect;
import com.kuangsanfeng.ultimategarden.client.platform.ui.screen.UltimateGardenScreen;
import com.kuangsanfeng.ultimategarden.client.platform.ui.theme.ThemePalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-only debug view. Never solves, aims, clicks, or advances a macro state. */
public class ZombieShootoutHud {
    private record Row(String label, String value, int color) { }
    private final ConfigManager configs;
    private final LanguageManager language;
    public ZombieShootoutHud(ConfigManager configs) {
        this.configs = configs;
        language = new LanguageManager(configs);
    }
    private String text(String key) { return language.text("ui.ultimategarden.shootout_hud." + key); }
    private String number(Double value, String unit) {
        return value == null || !Double.isFinite(value) ? text("none") : String.format(Locale.ROOT, "%.2f%s", value, unit);
    }
    private String angles(Double yaw, Double pitch) { return number(yaw, "°") + " / " + number(pitch, "°"); }
    private void add(List<Row> rows, String key, String value) { rows.add(new Row(text(key), value, 0xffffffff)); }
    public void render(GuiGraphicsExtractor g) {
        var client = Minecraft.getInstance();
        boolean editing=HudAdjustmentController.isActive();
        if (client.player == null || client.level == null || !editing&&client.options.hideGui
                || client.screen instanceof UltimateGardenScreen) return;
        var controller = ZombieShootoutController.INSTANCE;
        if (!editing&&!configs.getHudConfig().showZombie(controller.enabled())) return;
        var s = controller.hudSnapshot();
        int enabled = ThemePalette.from(configs.getUIConfig()).accent(), disabled = 0xffbc3945;
        int accent = s.running() ? enabled : disabled;
        List<Row> rows = new ArrayList<>();
        String target = s.classification() == null ? text("none") : text("target." + s.classification());
        if (s.tag() != null) target += " [" + s.tag() + "]";
        add(rows, "target", target);
        add(rows, "measured_speed", number(s.measuredSpeed(), " block/tick"));
        add(rows, "prediction_speed", number(s.predictionSpeed(), " block/tick"));
        add(rows, "current_angles", angles((double)client.player.getYRot(), (double)client.player.getXRot()));
        add(rows, "target_angles", angles(s.targetYaw(), s.targetPitch()));
        add(rows, "aim_error", number(s.aimError(), "°"));
        rows.add(new Row(text("aim_ready"), text(s.aimReady() ? "on" : "off"), s.aimReady() ? enabled : disabled));
        add(rows, "tolerance", number(s.tolerance(), "°"));
        add(rows, "correction", number(s.correctionThreshold(), "°"));
        add(rows, "aim_mode", s.aimMode() == null ? text("none") : text("mode." + s.aimMode()));
        add(rows, "cooldown", number(s.cooldownSeconds(), " s") + " / " + number(s.remainingSeconds(), " s"));
        String confirmation = text(s.confirmationEnabled() ? "on" : "off");
        if (s.confirmationEnabled() && !s.confirmationState().equals("IDLE")) confirmation += " / " + text("confirmation." + s.confirmationState());
        rows.add(new Row(text("confirmation"), confirmation, s.confirmationEnabled() ? enabled : disabled));
        rows.add(new Row(text("attempt"), text("attempt." + s.attemptState()), s.attemptState().equals("IDLE") ? 0xffffffff : enabled));
        add(rows, "ping", s.ping() == null ? text(controller.pingStatus().equals("MEASURING") ? "measuring" : "unavailable") : number(s.ping(), " ms"));
        add(rows, "latency", number(s.latencyTicks(), " tick"));
        int width = 288, height = 59;
        for (var row : rows) {
            width = Math.max(width, client.font.width(row.label()) + 22);
            width = Math.max(width, client.font.width(row.value()) + 22);
        }
        for (var row : rows) height += HudPanel.rowHeight(row.label(), row.value(), width);
        int sw = client.getWindow().getGuiScaledWidth(), sh = client.getWindow().getGuiScaledHeight();
        var bounds=configs.getHudConfig().bounds(com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.HudConfig.HudId.ZOMBIE_SHOOTOUT);
        double fit=Math.min(1,Math.min((sw-16.0)/width,(sh-16.0)/height));
        double bw=bounds.width()>0?bounds.width()*sw:width*fit,bh=bounds.height()>0?bounds.height()*sh:height*fit;
        bw=Math.clamp(bw,Math.min(sw,width*Math.min(.65,fit)),sw);bh=Math.clamp(bh,Math.min(sh,height*Math.min(.65,fit)),sh);
        double x=bounds.width()>0?bounds.x()*sw:sw-bw-8,y=bounds.height()>0?bounds.y()*sh:8;
        x=Math.clamp(x,0,sw-bw);y=Math.clamp(y,0,sh-bh);
        float scale=(float)Math.min(bw/width,bh/height);
        if(scale<=0)return;
        if(editing)HudAdjustmentController.layout(com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.HudConfig.HudId.ZOMBIE_SHOOTOUT,x,y,bw,bh,Math.min(sw,width*Math.min(.65,fit)),Math.min(sh,height*Math.min(.65,fit)));
        width=(int)Math.round(bw/scale);height=(int)Math.round(bh/scale);
        g.pose().pushMatrix();
        g.pose().translate((float)x,(float)y);
        g.pose().scale(scale);
        HudPanel.background(g, width, height, s.running() ? enabled : 0xffed6669);
        HudPanel.title(g, "Zombie Shootout");
        RoundedRect.fill(g, 11, 29, 6, 6, 3, accent);
        g.text(client.font, text("state." + s.state().name()), 23, 28, accent, false);
        int rowY = 47;
        for (var row : rows) {
            int rh = HudPanel.rowHeight(row.label(), row.value(), width);
            HudPanel.row(g, row.label(), row.value(), width, rowY, rh, row.color());
            rowY += rh;
        }
        g.pose().popMatrix();
        if(editing)HudPanel.editorOverlay(g,configs);
    }
}
