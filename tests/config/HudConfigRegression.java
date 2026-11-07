import com.google.gson.*;
import com.kuangsanfeng.ultimategarden.client.config.*;
import com.kuangsanfeng.ultimategarden.client.config.HudConfig.*;
import com.kuangsanfeng.ultimategarden.client.macro.farm.FarmState;
import java.nio.file.*;

public final class HudConfigRegression {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("HUD check " + checks); }
    public static void main(String[] args) throws Exception {
        var hud = new HudConfig();
        check(hud.isShowHud());
        hud.setBounds(.1,.2,.3,.4); hud.setMode(Mode.DETAIL); hud.setDetail(Item.CURRENT_DIRECTION,true);
        hud.setShowHud(false);
        var hidden = new HudConfig(); check(!hidden.load(hud.toJson(new JsonObject())));
        check(!hidden.isShowHud()); check(hidden.getMode()==Mode.DETAIL && hidden.detail(Item.CURRENT_DIRECTION));
        check(hidden.x()==.1 && hidden.y()==.2 && hidden.width()==.3 && hidden.height()==.4);
        hidden.setShowHud(true); check(hidden.getMode()==Mode.DETAIL && hidden.detail(Item.CURRENT_DIRECTION));
        check(!hidden.load(new JsonObject()) && hidden.isShowHud());
        JsonObject badVisibility=new JsonObject(); badVisibility.addProperty("showHud","false");
        check(hidden.load(badVisibility) && hidden.isShowHud());
        hud.resetToDefault();
        check(hud.getMode() == Mode.COMPACT);
        for (Item item : Item.values()) {
            boolean compactDefault = item != Item.TOTAL_TICK && item.ordinal() <= Item.INPUT_INTERCEPTION.ordinal();
            check(hud.compact(item) == compactDefault);
            check(hud.detail(item) == (item != Item.TOTAL_TICK && item.ordinal() <= Item.MOUSEMAT.ordinal()));
            check(hud.visible(item) == compactDefault);
            hud.setCompact(item,true); check(hud.detail(item));
            hud.setDetail(item,false); check(hud.detail(item));
            hud.setCompact(item,false); check(!hud.detail(item));
            hud.setDetail(item,true); check(hud.detail(item) && !hud.compact(item));
            hud.setCompact(item,true); hud.setCompact(item,false); check(!hud.detail(item));
        }
        hud.setMode(Mode.DETAIL); hud.setDetail(Item.LINE_CHANGE,true); check(hud.visible(Item.LINE_CHANGE));
        JsonObject partial = new JsonObject(), compact = new JsonObject(), detail = new JsonObject();
        compact.addProperty("MACRO_STATUS",true); detail.addProperty("MACRO_STATUS",false);
        detail.addProperty("LINE_CHANGE",false); detail.addProperty("futureItem",true);
        partial.add("compact",compact); partial.add("detail",detail); partial.addProperty("extra","keep");
        check(!hud.load(partial)); check(hud.detail(Item.MACRO_STATUS)); check(!hud.detail(Item.LINE_CHANGE));
        check(hud.toJson(partial).getAsJsonObject("detail").get("futureItem").getAsBoolean());
        partial.addProperty("displayMode","INVALID"); check(hud.load(partial)); check(hud.getMode()==Mode.COMPACT);
        compact.addProperty("CROP","yes"); check(hud.load(partial)); check(hud.compact(Item.CROP));
        Path root = Files.createTempDirectory("ug-hud-"); var configs = new ConfigManager(root);
        check(Files.exists(root.resolve("hud.json")));
        configs.getHudConfig().setCompact(Item.CROP,false);
        configs.getHudConfig().setDetail(Item.CROP,true);
        configs.getHudConfig().setMode(Mode.DETAIL);
        check(configs.save(ConfigManager.Module.HUD));
        var loaded = new ConfigManager(root);
        check(loaded.getHudConfig().getMode()==Mode.DETAIL);
        check(!loaded.getHudConfig().compact(Item.CROP)); check(loaded.getHudConfig().detail(Item.CROP));
        // A single mode write/save must immediately change visible items in both directions.
        var live = configs.getHudConfig();
        live.setCompact(Item.LINE_CHANGE,false); live.setDetail(Item.LINE_CHANGE,true);
        for (int i = 0; i < 10; i++) {
            for (Mode mode : new Mode[]{Mode.COMPACT,Mode.DETAIL}) {
                live.setMode(mode);
                check(live.getMode() == mode);
                check(live.visible(Item.LINE_CHANGE) == (mode == Mode.DETAIL));
                check(configs.save(ConfigManager.Module.HUD));
                check(configs.getHudConfig() == live);
                check(new ConfigManager(root).getHudConfig().getMode() == mode);
                check(live.visible(Item.LINE_CHANGE) == (mode == Mode.DETAIL));
            }
        }
        check(configs.reset(ConfigManager.Module.HUD)); check(configs.getHudConfig().compact(Item.CROP));
        Files.writeString(root.resolve("hud.json"),"{\"schemaVersion\":99}");
        loaded = new ConfigManager(root); check(!loaded.save(ConfigManager.Module.HUD));
        check(Files.readString(root.resolve("hud.json")).equals("{\"schemaVersion\":99}"));
        var bounds = new HudConfig();
        check(!bounds.load(new JsonObject())); check(bounds.width()==0 && bounds.height()==0);
        bounds.setBounds(.2,.3,.4,.5);
        var restored = new HudConfig(); check(!restored.load(bounds.toJson(new JsonObject())));
        check(restored.x()==.2 && restored.y()==.3 && restored.width()==.4 && restored.height()==.5);
        for (double invalid : new double[]{-1,2,Double.NaN,Double.POSITIVE_INFINITY}) {
            try { bounds.setBounds(invalid,.3,.4,.5); throw new AssertionError("Invalid HUD bounds accepted"); }
            catch (IllegalArgumentException expected) { check(bounds.x()==.2); }
        }
        JsonObject broken = bounds.toJson(new JsonObject()); broken.getAsJsonObject("bounds").addProperty("width",-1);
        check(restored.load(broken)); check(restored.width()==0);
        FarmState state = new FarmState();
        state.incrementTick(); state.incrementTick(); check(state.getTotalTicks()==2);
        state.resetTick(); check(state.getTotalTicks()==2);
        state.resetRoute(); check(state.getTotalTicks()==2);
        state.setRunning(false); check(state.getTotalTicks()==2);
        state.reset(); check(state.getTotalTicks()==0);
        System.out.println("PASS: " + checks + " HUD defaults, linkage, JSON persistence and total-Tick reset checks.");
    }
}
