import com.kuangsanfeng.ultimategarden.client.config.*;
import java.nio.file.*;

/** Standalone regression checks; uses only existing runtime dependencies. */
public final class ConfigRegression {
    private static int checks;
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("Check " + checks);
    }
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("ultimate-garden-config-test-");
        ConfigManager manager = new ConfigManager(dir);
        check(Files.exists(dir.resolve("crops.json")));
        check(!manager.getGeneralConfig().isAutoResumeFarmAfterGuiClose());
        manager.getGeneralConfig().setAutoResumeFarmAfterGuiClose(true);
        check(manager.save(ConfigManager.Module.GENERAL));
        manager = new ConfigManager(dir);
        check(manager.getGeneralConfig().isAutoResumeFarmAfterGuiClose());
        var edited = new java.util.LinkedHashMap<>(manager.getCropConfig().editableValues(com.kuangsanfeng.ultimategarden.client.macro.farm.crop.CropType.CARROT));
        edited.put("hintPitchA", "");
        manager.getCropConfig().updateCrop(com.kuangsanfeng.ultimategarden.client.macro.farm.crop.CropType.CARROT, edited);
        check(manager.getCropConfig().get(9).getHintPitch(true) == manager.getCropConfig().get(9).getPitch(true));
        edited.put("tickLimit", "-1");
        boolean invalidEdit = false;
        try { manager.getCropConfig().updateCrop(com.kuangsanfeng.ultimategarden.client.macro.farm.crop.CropType.CARROT, edited); }
        catch (IllegalArgumentException e) { invalidEdit = true; }
        check(invalidEdit);
        check(manager.getCropConfig().get(9).getTickLimit() == 806);
        manager.getCropConfig().resetToDefault();
        check(manager.getCropConfig().toJson().getAsJsonObject("crops").size() == 15);
        check(manager.getCropConfig().get(1).getPitch(true) == -3.0);
        check(manager.getCropConfig().get(9).getHintPitch(true) == 3.0);
        check(manager.getCropConfig().get(9).getPitch(true) == -3.0);
        check(!manager.getCropConfig().get(13).isExecutable());
        var customDoc = manager.getCropConfig().toJson();
        customDoc.getAsJsonObject("crops").getAsJsonObject("CUSTOM1").addProperty("enabled", true);
        manager.getCropConfig().load(customDoc);
        check(!manager.getCropConfig().get(13).isExecutable());
        customDoc.getAsJsonObject("crops").getAsJsonObject("FLOWER").addProperty("tickLimit", 600);
        customDoc.getAsJsonObject("crops").getAsJsonObject("FLOWER").addProperty("yawB", 45.5);
        customDoc.getAsJsonObject("crops").remove("COCOA");
        customDoc.getAsJsonObject("crops").getAsJsonObject("CACTUS").add("movementA", new com.google.gson.JsonArray());
        Files.writeString(dir.resolve("crops.json"), customDoc.toString());
        manager = new ConfigManager(dir);
        check(manager.getCropConfig().get(2).getTickLimit() == 600);
        check(manager.getCropConfig().get(2).getHintYaw(false) == 45.5);
        check(manager.getCropConfig().get(3).getTickLimit() == 797);
        check(manager.getCropConfig().get(8).getTickLimit() == 272);
        try (var files = Files.list(dir)) { check(files.anyMatch(p -> p.getFileName().toString().startsWith("crops.json.broken-"))); }
        manager = new ConfigManager(dir);
        check(manager.getCropConfig().get(2).getTickLimit() == 600);
        for (String name : new String[]{"general", "farm", "ui"}) check(Files.exists(dir.resolve(name + ".json")));
        check(manager.getFarmConfig().getSelectedCropTypeId() == 0);
        manager.getFarmConfig().setSelectedCropType("TIMETESTER");
        manager.getFarmConfig().setSelectedFarmMode(FarmConfig.FarmMode.PESTS);
        manager.getFarmConfig().setSelectedSimulatedCropType("MUSHROOM");
        check(manager.saveFarmConfig());
        manager.getUIConfig().setUiThemeMode(UIConfig.Theme.DARK);
        check(manager.saveUIConfig());
        manager = new ConfigManager(dir);
        check(manager.getFarmConfig().getSelectedCropTypeId() == 12);
        check(manager.getFarmConfig().getSelectedSimulatedCropTypeId() == 7);
        check(manager.getFarmConfig().getSelectedFarmModeId() == 1);
        check(manager.getUIConfig().getUiThemeMode() == UIConfig.Theme.DARK);
        check(!Files.readString(dir.resolve("farm.json")).contains("running"));
        Files.delete(dir.resolve("ui.json"));
        manager = new ConfigManager(dir);
        check(manager.getUIConfig().getUiThemeMode() == UIConfig.Theme.SYSTEM);
        check(Files.exists(dir.resolve("ui.json")));
        Files.writeString(dir.resolve("farm.json"), "{\"extra\":true}");
        manager = new ConfigManager(dir);
        check(manager.getFarmConfig().getSelectedCropTypeId() == 0);
        check(Files.readString(dir.resolve("farm.json")).contains("extra"));
        for (String invalid : new String[]{"{", "[]", "null", "{\"cropType\":99}", "{\"mode\":3}", "{\"cropType\":\"1\"}", "{\"cropType\":1.5}", "{\"schemaVersion\":0}"}) {
            Files.writeString(dir.resolve("farm.json"), invalid);
            manager = new ConfigManager(dir);
            check(manager.getFarmConfig().getSelectedCropTypeId() == 0);
            try (var files = Files.list(dir)) {
                check(files.filter(p -> p.getFileName().toString().startsWith("farm.json.broken-")).anyMatch(p -> {
                    try { return Files.readString(p).equals(invalid); }
                    catch (Exception e) { throw new RuntimeException(e); }
                }));
            }
        }
        Files.writeString(dir.resolve("ui.json"), "{\"theme\":\"INVALID\"}");
        manager = new ConfigManager(dir);
        check(manager.getUIConfig().getUiThemeMode() == UIConfig.Theme.SYSTEM);
        String future = "{\"schemaVersion\":99,\"futureData\":42}";
        Files.writeString(dir.resolve("farm.json"), future);
        manager = new ConfigManager(dir);
        manager.getFarmConfig().setSelectedCropType("FLOWER");
        check(!manager.saveFarmConfig());
        check(!manager.reset(ConfigManager.Module.FARM));
        check(Files.readString(dir.resolve("farm.json")).equals(future));
        Files.writeString(dir.resolve("farm.json"), "{\"cropType\":6,\"mode\":1,\"simulateCropType\":9}");
        manager = new ConfigManager(dir);
        check(manager.getFarmConfig().getSelectedCropType().equals("NONE"));
        check(manager.getFarmConfig().getSelectedFarmMode() == FarmConfig.FarmMode.PESTS);
        check(manager.getFarmConfig().getSelectedSimulatedCropType().equals("WHEAT"));
        String migrated = Files.readString(dir.resolve("farm.json"));
        check(migrated.contains("selectedCropType") && !migrated.contains("\"cropType\""));
        Files.writeString(dir.resolve("farm.json"), "{\"selectedCropType\":\"FLOWER\",\"cropType\":6,\"selectedFarmMode\":\"BROKEN\",\"mode\":1}");
        manager = new ConfigManager(dir);
        check(manager.getFarmConfig().getSelectedCropType().equals("FLOWER"));
        check(manager.getFarmConfig().getSelectedFarmMode() == FarmConfig.FarmMode.PESTS);
        Files.writeString(dir.resolve("farm.json"), "{\"selectedCropType\":\"MUSHROOM\",\"mode\":9}");
        manager = new ConfigManager(dir);
        check(manager.getFarmConfig().getSelectedCropType().equals("MUSHROOM"));
        check(manager.getFarmConfig().getSelectedFarmMode() == FarmConfig.FarmMode.FARM);
        Files.writeString(dir.resolve("ui.json"), "{\"uiThemeMode\":\"LIGHT\",\"theme\":\"DARK\",\"accentColor\":\"bad\"}");
        manager = new ConfigManager(dir);
        check(manager.getUIConfig().getUiThemeMode() == UIConfig.Theme.LIGHT);
        check(!Files.readString(dir.resolve("ui.json")).contains("\"theme\""));
        Files.writeString(dir.resolve("pest.json"), "{\"autoPestLoadout\":false,\"pestLoadoutDelayTicks\":17}");
        Files.writeString(dir.resolve("general.json"), "{\"showModChatHints\":false}");
        manager = new ConfigManager(dir);
        check(!manager.getPestConfig().isAutoPestLoadout());
        check(manager.getPestConfig().getPestLoadoutDelayTicks() == 17);
        check(!manager.getGeneralConfig().isShowModChatHints());
        Files.writeString(dir.resolve("pest.json"), "{\"autoPestLoadout\":false,\"pestLoadoutDelayTicks\":0}");
        manager = new ConfigManager(dir);
        check(!manager.getPestConfig().isAutoPestLoadout());
        check(manager.getPestConfig().getPestLoadoutDelayTicks() == 2500);
        String[] expected = {"WHEAT", "FLOWER", "COCOA", "MELON", "PUMPKIN", "SUGARCANE", "MUSHROOM", "CACTUS", "CARROT", "POTATO", "NETHER_WART", "TIMETESTER", "CUSTOM1", "CUSTOM2", "CUSTOM3"};
        check(com.kuangsanfeng.ultimategarden.client.macro.farm.crop.CropType.values().length == 15);
        for (int id = 1; id <= 15; id++) {
            var type = com.kuangsanfeng.ultimategarden.client.macro.farm.crop.CropType.fromValue(id);
            check(type.name().equals(expected[id - 1]) && type.getValue() == id);
            if (id <= 11) {
                manager.getFarmConfig().setSelectedSimulatedCropType(type.name());
                check(manager.getFarmConfig().getSelectedSimulatedCropTypeId() == id);
            } else {
                boolean rejected = false;
                try { manager.getFarmConfig().setSelectedSimulatedCropType(type.name()); } catch (IllegalArgumentException e) { rejected = true; }
                check(rejected);
            }
            if (id >= 13) check(!manager.getCropConfig().get(id).isExecutable());
        }
        var splitSource = new com.google.gson.JsonObject();
        var definitions = new com.google.gson.JsonObject();
        var combined = manager.getCropConfig().toJson().getAsJsonObject("crops").getAsJsonObject("CARROT").deepCopy();
        combined.addProperty("tickLimit", 900);
        definitions.add("CARROT_POTATO", combined);
        var override = new com.google.gson.JsonObject(); override.addProperty("tickLimit", 901);
        definitions.add("CARROT", override);
        splitSource.add("crops", definitions);
        Files.writeString(dir.resolve("crops.json"), splitSource.toString());
        Files.writeString(dir.resolve("farm.json"), "{\"selectedCropType\":\"CARROT_POTATO\",\"selectedFarmMode\":\"PESTS\",\"selectedSimulatedCropType\":\"MELON_PUMPKIN\"}");
        manager = new ConfigManager(dir);
        check(manager.getCropConfig().get(9).getTickLimit() == 901);
        check(manager.getCropConfig().get(10).getTickLimit() == 900);
        check(manager.getCropConfig().get(9).getPitch(true) == -3.0);
        check(manager.getCropConfig().get(10).getHintPitch(true) == 3.0);
        check(!manager.getCropConfig().toJson().getAsJsonObject("crops").has("CARROT_POTATO"));
        check(manager.getFarmConfig().getSelectedCropType().equals("NONE"));
        check(manager.getFarmConfig().getSelectedSimulatedCropType().equals("WHEAT"));
        check(manager.getFarmConfig().getSelectedFarmMode() == FarmConfig.FarmMode.PESTS);
        try (var files = Files.list(dir)) {
            check(files.filter(f -> f.getFileName().toString().startsWith("crops.json.broken-")).anyMatch(f -> {
                try { return Files.readString(f).equals(splitSource.toString()); } catch (Exception e) { throw new RuntimeException(e); }
            }));
        }
        Path blocked = Files.createTempFile("config-not-a-directory", ".tmp");
        manager = new ConfigManager(blocked);
        check(manager.getFarmConfig().getSelectedCropTypeId() == 0);
        check(!manager.saveAll());
        try (var files = Files.list(dir)) { check(files.noneMatch(p -> p.toString().endsWith(".tmp"))); }
        System.out.println("PASS: " + checks + " configuration checks; isolated files at " + dir);
    }
}
