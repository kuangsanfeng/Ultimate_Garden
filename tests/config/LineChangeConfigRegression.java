
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.farm.CropConfig;
import com.google.gson.*;

import com.kuangsanfeng.ultimategarden.client.macro.farm.crop.CropType;
import java.nio.file.*;
import java.util.*;

public final class LineChangeConfigRegression {
    private static int checks;
    private static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("Line config check " + checks); }
    public static void main(String[] args) throws Exception {
        var config = new CropConfig();
        for (var type : CropType.values()) {
            var crop = config.getCrop(type);
            check(crop.lineChangeMovement().enabled() == (type == CropType.CACTUS));
            check(crop.lineChangeMovement().durationTicks() == 10);
            check(crop.lineChangeYawPitch().enabled() == (type == CropType.SUGARCANE || type == CropType.MUSHROOM));
            check(crop.lineChangeYawPitch().slot1() == 3 && crop.lineChangeYawPitch().slot2() == 4);
            if (type.isCustom()) check(!crop.isExecutable());
        }
        JsonObject old = config.toJson();
        for (var entry : old.getAsJsonObject("crops").entrySet()) {
            entry.getValue().getAsJsonObject().remove("lineChangeMovement");
            entry.getValue().getAsJsonObject().remove("lineChangeYawPitch");
        }
        var wheat = old.getAsJsonObject("crops").getAsJsonObject("WHEAT");
        wheat.addProperty("tickLimit", 321);
        wheat.addProperty("alternateSlot", true);
        wheat.addProperty("userExtra", "keep");
        config.load(old);
        check(config.getCrop(CropType.WHEAT).lineChangeYawPitch().enabled());
        check(config.getCrop(CropType.WHEAT).getTickLimit() == 321);
        check(config.toJson().getAsJsonObject("crops").getAsJsonObject("WHEAT").get("userExtra").getAsString().equals("keep"));
        JsonObject partial = config.toJson();
        wheat = partial.getAsJsonObject("crops").getAsJsonObject("WHEAT");
        wheat.getAsJsonObject("lineChangeYawPitch").addProperty("enabled", false);
        wheat.getAsJsonObject("lineChangeYawPitch").addProperty("slot1", 8);
        wheat.getAsJsonObject("lineChangeYawPitch").remove("slot2");
        wheat.getAsJsonObject("lineChangeMovement").addProperty("durationTicks", 22);
        config.load(partial);
        check(!config.getCrop(CropType.WHEAT).lineChangeYawPitch().enabled());
        check(config.getCrop(CropType.WHEAT).lineChangeYawPitch().slot1() == 8);
        check(config.getCrop(CropType.WHEAT).lineChangeYawPitch().slot2() == 4);
        check(config.getCrop(CropType.WHEAT).lineChangeMovement().durationTicks() == 22);
        for (String invalid : List.of("0", "-1", "1.5", "2147483648", "\"12\"", "null")) {
            JsonObject candidate = config.toJson();
            candidate.getAsJsonObject("crops").getAsJsonObject("WHEAT").getAsJsonObject("lineChangeMovement")
                    .add("durationTicks", JsonParser.parseString(invalid));
            config.load(candidate);
            check(config.wasRepaired());
            check(config.getCrop(CropType.WHEAT).lineChangeMovement().durationTicks() == 10);
            check(config.getCrop(CropType.WHEAT).getTickLimit() == 321);
            check(config.getCrop(CropType.WHEAT).lineChangeYawPitch().slot1() == 8);
        }
        var before = config.toJson();
        for (var change : List.of(Map.of("lineChangeMovement.durationTicks", "0"),
                Map.of("lineChangeYawPitch.slot1", "10"), Map.of("lineChangeYawPitch.slot2", "1.1"),
                Map.of("lineChangeYawPitch.enabled", "yes"))) {
            try { config.updateCrop(CropType.WHEAT, change); throw new AssertionError("Accepted invalid fields"); }
            catch (IllegalArgumentException expected) { check(config.toJson().equals(before)); }
        }
        for (var custom : List.of(CropType.CUSTOM1, CropType.CUSTOM2, CropType.CUSTOM3)) {
            check(config.editableValues(custom).size() == 5);
            config.updateCrop(custom, Map.of("lineChangeMovement.enabled", "true", "lineChangeMovement.durationTicks", "7",
                    "lineChangeYawPitch.enabled", "true", "lineChangeYawPitch.slot1", "9", "lineChangeYawPitch.slot2", "9"));
            check(config.getCrop(custom).lineChangeMovement().enabled());
            check(config.getCrop(custom).lineChangeMovement().durationTicks() == 7);
            check(config.getCrop(custom).lineChangeYawPitch().slot1() == 9);
            check(!config.getCrop(custom).isExecutable());
            try { config.updateCrop(custom, Map.of("enabled", "true")); throw new AssertionError("Custom route unlocked"); }
            catch (IllegalArgumentException expected) { check(!config.getCrop(custom).isExecutable()); }
        }
        Path dir = Files.createTempDirectory("ug-line-config-");
        var manager = new ConfigManager(dir);
        manager.getCropConfig().load(config.toJson());
        check(manager.save(ConfigManager.Module.CROPS));
        var loaded = new ConfigManager(dir);
        check(loaded.getCropConfig().toJson().equals(config.toJson()));
        System.out.println("PASS: " + checks + " line-change configuration migration/validation/persistence checks.");
    }
}
