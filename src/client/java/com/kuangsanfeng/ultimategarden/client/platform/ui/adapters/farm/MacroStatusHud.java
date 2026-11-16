package com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.farm;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.HudConfig;
import com.kuangsanfeng.ultimategarden.client.platform.ui.hud.HudAdjustmentController;
import com.kuangsanfeng.ultimategarden.client.platform.ui.hud.HudPanel;




import com.kuangsanfeng.ultimategarden.client.macro.farm.FarmController;
import com.kuangsanfeng.ultimategarden.client.macro.farm.crop.CropType;
import com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageManager;
import com.kuangsanfeng.ultimategarden.client.platform.ui.render.RoundedRect;
import com.kuangsanfeng.ultimategarden.client.platform.ui.screen.UltimateGardenScreen;
import com.kuangsanfeng.ultimategarden.client.platform.ui.theme.ThemePalette;
import com.kuangsanfeng.ultimategarden.client.platform.window.WindowsState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Read-only HUD. All counters and workflow flags remain owned by the farm. */
public class MacroStatusHud {
    private record Row(HudConfig.Item item, String label, String value) { }
    private final ConfigManager configs;
    private final FarmController farm;
    private final WindowsState window;
    private final LanguageManager language;
    public MacroStatusHud(ConfigManager configs, FarmController farm, WindowsState window) {
        this.configs = configs; this.farm = farm; this.window = window; language = new LanguageManager(configs);
    }
    private String text(String name) { return language.text("ui.ultimategarden.hud." + name); }
    public void render(GuiGraphicsExtractor g) {
        Minecraft client = Minecraft.getInstance();
        boolean editing = HudAdjustmentController.isActive();
        if (client.player == null || client.level == null || (!editing && client.options.hideGui) || client.screen instanceof UltimateGardenScreen) return;
        var state = farm.getState(); var hud = configs.getHudConfig();
        if (!editing && !hud.showFarm(state.isRunning())) return;
        var crop = state.getCropType() == 0 ? null : configs.getCropConfig().get(state.getCropType());
        String status = text(state.isRunning() ? "running" : "paused");
        int enabledColor = ThemePalette.from(configs.getUIConfig()).accent();
        int disabledColor = 0xffbc3945;
        int accent = state.isRunning() ? enabledColor : disabledColor;
        boolean showStatus = hud.visible(HudConfig.Item.MACRO_STATUS);
        int ticks = farm.getCommand().getPestLoadoutRemainingTicks();
        List<Row> rows = new ArrayList<>();
        for (HudConfig.Item item : HudConfig.Item.values()) {
            if (!hud.visible(item) || item == HudConfig.Item.MACRO_STATUS
                    || item == HudConfig.Item.FARM_MODE && showStatus) continue;
            String value = switch (item) {
                case MACRO_STATUS -> status;
                case FARM_MODE -> language.text("ui.ultimategarden.mode." + (state.isPestsMode() ? "PESTS" : "FARM"));
                case CROP -> crop == null ? language.text("ui.ultimategarden.none") : language.cropName(CropType.fromValue(state.getCropType()).name(), crop.getName());
                case LINE -> crop == null || crop.getType().isCustom() ? "0 / 0" : Math.min(state.getEventCount() + 1, crop.getLines()) + " / " + crop.getLines();
                case TOTAL_TICK -> state.getTotalTicks() + " tick";
                case LINE_TICK -> crop == null || crop.getType().isCustom() ? state.getTick() + " tick" : state.getTick() + " / " + crop.getTickLimit() + " tick";
                case PEST_LOADOUT -> String.format(Locale.ROOT, "%d tick (%.1f s)", ticks, ticks / 20.0);
                case FOCUS_STATE -> text(client.screen != null ? "gui_cursor" : window.isMouseReleased() ? "released" : "captured");
                case INPUT_INTERCEPTION -> text(window.isInputIntercepting() ? "on" : "off");
                case LINE_CHANGE -> crop != null && crop.lineChangeMovement().enabled() ? crop.lineChangeMovement().durationTicks() + " tick" : text("disable");
                case MOUSEMAT -> crop != null && crop.lineChangeYawPitch().enabled()
                        ? text("slot1") + " " + crop.lineChangeYawPitch().slot1() + " / " + text("slot2") + " " + crop.lineChangeYawPitch().slot2() : text("disable");
                case CURRENT_DIRECTION -> crop == null ? text("none") : state.isEventSelect() ? "A" : "B";
                case LINE_CHANGE_STATUS -> text("phase." + (state.getLineChangePhase().name().equals("IDLE") && state.isRunning() && !state.isCommandRunning() ? "WORKING" : state.getLineChangePhase().name()));
                case STOP_REASON -> text("reason." + farm.getHudStopReason());
                case CURRENT_WORKFLOW -> text("workflow." + farm.getCommand().getWorkflow().name());
            };
            rows.add(new Row(item, text("item." + item.name()), value));
        }
        int width = 240;
        for (Row row : rows) width = Math.max(width, Math.min(288, client.font.width(row.label) + client.font.width(row.value) + 32));
        int height = showStatus ? 47 : 30;
        for (Row row : rows) height += rowHeight(client, row, width);
        height += 12;
        // Fit the card without dropping optional rows on small GUI scales.
        double sw=client.getWindow().getGuiScaledWidth(), sh=client.getWindow().getGuiScaledHeight();
        double fit=Math.min(1,Math.min((sw-16)/width,(sh-16)/height));
        double bw=hud.width()>0 ? hud.width()*sw : width*fit;
        double bh=hud.height()>0 ? hud.height()*sh : height*fit;
        bw=Math.clamp(bw,Math.min(sw,width*.65),sw);
        bh=Math.clamp(bh,Math.min(sh,height*.65),sh);
        double x=hud.width()>0 ? hud.x()*sw : 8, y=hud.height()>0 ? hud.y()*sh : 8;
        x=Math.clamp(x,0,sw-bw); y=Math.clamp(y,0,sh-bh);
        float scale=(float)Math.min(bw/width,bh/height);
        if (scale<=0) return;
        if (editing) {
            g.fill(0,0,(int)sw,(int)sh,0x99000000);
            HudAdjustmentController.layout(x,y,bw,bh,Math.min(sw,width*.65),Math.min(sh,height*.65));

        }
        width=(int)Math.round(bw/scale); height=(int)Math.round(bh/scale);
        g.pose().pushMatrix();
        g.pose().translate((float)x,(float)y);
        g.pose().scale(scale);
        int borderColor = state.isRunning() ? enabledColor : 0xffed6669;
        HudPanel.background(g, width, height, borderColor);
        HudPanel.title(g, text("title"));
        if (showStatus) {
            RoundedRect.fill(g, 11, 29, 6, 6, 3, accent);
            g.text(client.font, Component.literal(status), 23, 28, accent, false);
            if (hud.visible(HudConfig.Item.FARM_MODE)) {
                String mode = language.text("ui.ultimategarden.mode." + (state.isPestsMode() ? "PESTS" : "FARM"));
                g.text(client.font, mode, width - client.font.width(mode) - 11, 28, 0xffffffff, false);
            }
        }
        int rowY = showStatus ? 47 : 30;
        for (Row row : rows) {
            int rowHeight = rowHeight(client, row, width);
            int valueColor = switch (row.item) {
                case MACRO_STATUS -> accent;
                case FOCUS_STATE -> client.screen != null ? 0xffffffff : window.isMouseReleased() ? enabledColor : disabledColor;
                case INPUT_INTERCEPTION -> window.isInputIntercepting() ? enabledColor : disabledColor;
                case LINE_CHANGE -> crop != null && crop.lineChangeMovement().enabled() ? 0xffffffff : disabledColor;
                case MOUSEMAT -> crop != null && crop.lineChangeYawPitch().enabled() ? 0xffffffff : disabledColor;
                case LINE_CHANGE_STATUS -> {
                    boolean active = state.getLineChangePhase() != com.kuangsanfeng.ultimategarden.client.macro.farm.FarmState.LineChangePhase.IDLE
                            || state.isRunning() && !state.isCommandRunning();
                    yield active ? state.isRunning() ? enabledColor : disabledColor : 0xffffffff;
                }
                case STOP_REASON -> switch (farm.getHudStopReason()) {
                    case "NONE", "START_REQUESTED" -> 0xffffffff;
                    default -> disabledColor;
                };
                case CURRENT_WORKFLOW -> farm.getCommand().getWorkflow() == com.kuangsanfeng.ultimategarden.client.macro.farm.action.FarmCommandController.Workflow.IDLE
                        ? 0xffffffff : farm.getCommand().getScheduler().isPaused() ? disabledColor : enabledColor;
                default -> 0xffffffff;
            };
            HudPanel.row(g, row.label, row.value, width, rowY, rowHeight, valueColor);
            rowY += rowHeight;
        }
        float progress = crop == null || crop.getType().isCustom() ? 0 : Math.max(0, Math.min(1, state.getTick() / (float)crop.getTickLimit()));
        RoundedRect.fill(g, 11, height - 9, width - 22, 3, 1, 0xffe3e8e4);
        RoundedRect.fill(g, 11, height - 9, Math.round((width - 22) * progress), 3, 1, accent);
        g.pose().popMatrix();
    }
    private int rowHeight(Minecraft client, Row row, int width) {
        return HudPanel.rowHeight(row.label, row.value, width);
    }
}
