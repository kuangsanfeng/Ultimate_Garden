package com.kuangsanfeng.ultimategarden.client.config;

import com.google.gson.*;
import com.kuangsanfeng.ultimategarden.client.macro.farm.crop.CropType;
import java.util.*;

/** Validated immutable in-memory view of one JSON definition. */
public final class CropDefinition {
    private final CropType type;
    private final JsonObject data;
    CropDefinition(CropType type, JsonObject source) {
        this.type = type;
        data = source.deepCopy();
        text("name"); bool("enabled");
        validateLineChange(data);
        if (type.isCustom()) return;
        positive("tickLimit"); positive("totalLines");
        keys(true); keys(false); bool("alternateSlot");
        for (String key : List.of("yawA", "pitchA", "yawB", "pitchB")) angle(key);
        for (String key : List.of("hintYawA", "hintPitchA", "hintYawB", "hintPitchB")) if (data.has(key)) angle(key);
    }
    private String text(String key) {
        if (!data.get(key).isJsonPrimitive() || !data.getAsJsonPrimitive(key).isString() || data.get(key).getAsString().isBlank()) throw new IllegalArgumentException(key);
        return data.get(key).getAsString();
    }
    private boolean bool(String key) {
        if (!data.get(key).isJsonPrimitive() || !data.getAsJsonPrimitive(key).isBoolean()) throw new IllegalArgumentException(key);
        return data.get(key).getAsBoolean();
    }
    private int positive(String key) {
        if (!data.getAsJsonPrimitive(key).isNumber()) throw new IllegalArgumentException(key);
        int value = data.get(key).getAsBigDecimal().intValueExact();
        if (value <= 0) throw new IllegalArgumentException(key);
        return value;
    }
    private double angle(String key) {
        if (!data.getAsJsonPrimitive(key).isNumber()) throw new IllegalArgumentException(key);
        double value = data.get(key).getAsDouble();
        if (!Double.isFinite(value) || Math.abs(value) > (key.toLowerCase(Locale.ROOT).contains("pitch") ? 90 : 180)) throw new IllegalArgumentException(key);
        return value;
    }
    public List<String> keys(boolean a) {
        List<String> keys = new ArrayList<>();
        for (JsonElement key : data.getAsJsonArray(a ? "movementA" : "movementB")) {
            if (!key.isJsonPrimitive() || !key.getAsJsonPrimitive().isString() || !List.of("W", "S", "A", "D").contains(key.getAsString()) || keys.contains(key.getAsString())) throw new IllegalArgumentException("Invalid movement key");
            keys.add(key.getAsString());
        }
        if (keys.isEmpty()) throw new IllegalArgumentException("Empty movement");
        return List.copyOf(keys);
    }
    public String getName() { return text("name"); }
    public CropType getType() { return type; }
    public boolean isExecutable() { return !type.isCustom() && bool("enabled"); }
    public int getTickLimit() { return positive("tickLimit"); }
    public int getLines() { return positive("totalLines"); }
    public record LineChangeMovement(boolean enabled, int durationTicks) { }
    public record LineChangeYawPitch(boolean enabled, int slot1, int slot2) { }
    static void validateLineChange(JsonObject value) {
        for (String group : List.of("lineChangeMovement", "lineChangeYawPitch")) {
            JsonObject config = value.getAsJsonObject(group);
            for (String key : group.equals("lineChangeMovement") ? List.of("enabled", "durationTicks") : List.of("enabled", "slot1", "slot2")) {
                if (!config.has(key)) throw new IllegalArgumentException(group + "." + key);
                validateLineChangeField(group, key, config.get(key));
            }
        }
    }
    static void validateLineChangeField(String group, String key, JsonElement value) {
        String path = group + "." + key;
        if (key.equals("enabled")) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(path);
        } else {
            try {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(path);
                int number = value.getAsBigDecimal().intValueExact();
                if (number < 1 || (!key.equals("durationTicks") && number > 9)) throw new IllegalArgumentException(path);
            } catch (RuntimeException e) { throw new IllegalArgumentException(path, e); }
        }
    }
    public LineChangeMovement lineChangeMovement() {
        JsonObject config = data.getAsJsonObject("lineChangeMovement");
        return new LineChangeMovement(config.get("enabled").getAsBoolean(), config.get("durationTicks").getAsInt());
    }
    public LineChangeYawPitch lineChangeYawPitch() {
        JsonObject config = data.getAsJsonObject("lineChangeYawPitch");
        return new LineChangeYawPitch(config.get("enabled").getAsBoolean(), config.get("slot1").getAsInt(), config.get("slot2").getAsInt());
    }
    public boolean useAlternateSlot() { return lineChangeYawPitch().enabled(); }
    public double getYaw(boolean a) { return angle(a ? "yawA" : "yawB"); }
    public double getPitch(boolean a) { return angle(a ? "pitchA" : "pitchB"); }
    public double getHintYaw(boolean a) { String key = a ? "hintYawA" : "hintYawB"; return data.has(key) ? angle(key) : getYaw(a); }
    public double getHintPitch(boolean a) { String key = a ? "hintPitchA" : "hintPitchB"; return data.has(key) ? angle(key) : getPitch(a); }
    public boolean[] getMovementKeys(boolean a) {
        var keys = keys(a);
        return new boolean[]{keys.contains("W"), keys.contains("S"), keys.contains("A"), keys.contains("D")};
    }
}
