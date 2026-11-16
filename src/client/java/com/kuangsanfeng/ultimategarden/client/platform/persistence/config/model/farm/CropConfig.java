package com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.farm;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.farm.CropDefinition;


import com.google.gson.*;
import com.kuangsanfeng.ultimategarden.client.macro.farm.crop.CropType;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class CropConfig {
    private final JsonObject defaults;
    private JsonObject document;
    private Map<CropType, CropDefinition> crops;
    private boolean repaired;
    public CropConfig() {
        try (InputStream input = Objects.requireNonNull(CropConfig.class.getResourceAsStream("/ultimategarden/default-crops.json"));
             Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            defaults = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) { throw new IllegalStateException("Missing bundled crop defaults", e); }
        load(new JsonObject());
    }
    public void load(JsonObject source) {
        repaired = false;
        JsonObject merged = source.deepCopy();
        if (!merged.has("crops")) merged.add("crops", new JsonObject());
        JsonObject values = merged.getAsJsonObject("crops");
        // Data migration only: never expose obsolete identifiers as selectable types.
        split(values, "CARROT_POTATO", CropType.CARROT, CropType.POTATO);
        split(values, "WHEAT_NETHER_WART", CropType.WHEAT, CropType.NETHER_WART);
        split(values, "MELON_PUMPKIN", CropType.MELON, CropType.PUMPKIN);
        Map<CropType, CropDefinition> parsed = new EnumMap<>(CropType.class);
        for (CropType type : CropType.values()) {
            JsonObject fallback = defaults.getAsJsonObject("crops").getAsJsonObject(type.name());
            if (!values.has(type.name())) values.add(type.name(), fallback.deepCopy());
            try {
                JsonObject value = values.getAsJsonObject(type.name());
                mergeLineChange(value, fallback);
                for (var field : fallback.entrySet()) if (!value.has(field.getKey()) && !field.getKey().startsWith("hint")) value.add(field.getKey(), field.getValue().deepCopy());
                parsed.put(type, new CropDefinition(type, value));
            } catch (RuntimeException e) {
                repaired = true;
                com.kuangsanfeng.ultimategarden.Ultimate_Garden.LOGGER.warn("Invalid crop {}; using its bundled default", type, e);
                values.add(type.name(), fallback.deepCopy());
                parsed.put(type, new CropDefinition(type, fallback));
            }
        }
        merged.addProperty("schemaVersion", 1);
        document = merged;
        crops = Map.copyOf(parsed);
    }
    private void mergeLineChange(JsonObject value, JsonObject fallback) {
        for (String group : List.of("lineChangeMovement", "lineChangeYawPitch")) {
            JsonObject defaults = fallback.getAsJsonObject(group);
            if (value.has(group) && !value.get(group).isJsonObject()) {
                value.add(group, defaults.deepCopy());
                repaired = true;
            }
            if (!value.has(group)) value.add(group, new JsonObject());
            JsonObject config = value.getAsJsonObject(group);
            if (group.equals("lineChangeYawPitch") && !config.has("enabled") && value.has("alternateSlot")
                    && value.get("alternateSlot").isJsonPrimitive() && value.getAsJsonPrimitive("alternateSlot").isBoolean())
                config.add("enabled", value.get("alternateSlot").deepCopy());
            for (var field : defaults.entrySet()) {
                if (!config.has(field.getKey())) config.add(field.getKey(), field.getValue().deepCopy());
                try { CropDefinition.validateLineChangeField(group, field.getKey(), config.get(field.getKey())); }
                catch (RuntimeException e) {
                    config.add(field.getKey(), field.getValue().deepCopy());
                    repaired = true;
                }
            }
        }
    }
    private void split(JsonObject values, String obsolete, CropType... targets) {
        if (!values.has(obsolete)) return;
        repaired = true; // ConfigManager backs up the original before saving.
        JsonElement original = values.get(obsolete);
        for (CropType target : targets) {
            if (original.isJsonObject()) {
                JsonObject inherited = original.getAsJsonObject().deepCopy();
                JsonElement name = inherited.get("name");
                if (name == null || !name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString()
                        || java.util.Set.of("Carrot/Potato", "Wheat/NetherWart", "Melon/Pumpkin", "Carrot/Wheat/Potato/NetherWart").contains(name.getAsString())) {
                    inherited.add("name", defaults.getAsJsonObject("crops").getAsJsonObject(target.name()).get("name").deepCopy());
                }
                if (values.has(target.name()) && values.get(target.name()).isJsonObject()) {
                    for (var entry : values.getAsJsonObject(target.name()).entrySet())
                        inherited.add(entry.getKey(), entry.getValue().deepCopy());
                }
                values.add(target.name(), inherited);
            }
        }
        values.remove(obsolete);
        com.kuangsanfeng.ultimategarden.Ultimate_Garden.LOGGER.info("Split obsolete crop definition {} into independent nodes", obsolete);
    }
    public CropDefinition getCrop(CropType type) { return Objects.requireNonNull(crops.get(type)); }
    public CropDefinition get(int id) { return getCrop(CropType.fromValue(id)); }
    public JsonObject toJson() { return document.deepCopy(); }
    public java.util.Map<String, String> editableValues(CropType type) {
        JsonObject value = document.getAsJsonObject("crops").getAsJsonObject(type.name());
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        for (String key : java.util.List.of("name", "enabled", "tickLimit", "totalLines", "movementA", "movementB", "yawA", "yawB", "pitchA", "pitchB", "hintYawA", "hintYawB", "hintPitchA", "hintPitchB")) {
            if (type.isCustom()) continue;
            JsonElement item = value.get(key);
            if (item == null) fields.put(key, "");
            else if (item.isJsonArray()) {
                java.util.List<String> keys = new java.util.ArrayList<>();
                for (JsonElement entry : item.getAsJsonArray()) keys.add(entry.getAsString());
                fields.put(key, String.join(",", keys));
            } else fields.put(key, item.getAsString());
        }
        for (String group : List.of("lineChangeMovement", "lineChangeYawPitch")) {
            for (String key : group.equals("lineChangeMovement") ? List.of("enabled", "durationTicks") : List.of("enabled", "slot1", "slot2"))
                fields.put(group + "." + key, value.getAsJsonObject(group).get(key).getAsString());
        }
        return fields;
    }
    /** Validate the whole candidate before changing any effective definition. */
    public void updateCrop(CropType type, java.util.Map<String, String> fields) {
        if (type.isCustom() && fields.keySet().stream().anyMatch(key -> !editableValues(type).containsKey(key)))
            throw new IllegalArgumentException("Custom routes are not implemented.");
        JsonObject next = toJson();
        JsonObject value = next.getAsJsonObject("crops").getAsJsonObject(type.name());
        for (var entry : editableValues(type).entrySet()) {
            String key = entry.getKey();
            String input = fields.getOrDefault(key, entry.getValue()).trim();
            if (key.contains(".")) {
                String[] path = key.split("\\.");
                JsonObject target = value.getAsJsonObject(path[0]);
                if (path[1].equals("enabled")) {
                    if (!input.equals("true") && !input.equals("false")) throw new IllegalArgumentException(key);
                    target.addProperty(path[1], Boolean.parseBoolean(input));
                } else {
                    try { target.addProperty(path[1], new java.math.BigDecimal(input)); }
                    catch (NumberFormatException e) { throw new IllegalArgumentException(key, e); }
                }
                continue;
            }
            if (key.startsWith("hint") && input.isEmpty()) { value.remove(key); continue; }
            if (key.equals("name")) value.addProperty(key, input);
            else if (key.startsWith("movement")) {
                JsonArray keys = new JsonArray();
                for (String part : input.split(",", -1)) keys.add(part.trim());
                value.add(key, keys);
            } else if (key.equals("enabled") || key.equals("alternateSlot")) {
                if (!input.equals("true") && !input.equals("false")) throw new IllegalArgumentException(key + ": expected true/false");
                value.addProperty(key, Boolean.parseBoolean(input));
            } else {
                try { value.addProperty(key, new java.math.BigDecimal(input)); }
                catch (NumberFormatException e) { throw new IllegalArgumentException(key + ": invalid number", e); }
            }
        }
        new CropDefinition(type, value);
        load(next);
    }
    public boolean wasRepaired() { return repaired; }
    public void resetToDefault() { load(new JsonObject()); }
}
