package com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model;

import com.google.gson.*;
import java.util.*;
import java.util.random.RandomGenerator;
import java.util.concurrent.ThreadLocalRandom;

/** Global tick ranges. Default weights retain the old millisecond rounding distribution. */
public final class RandomDelayConfig {
    public enum Key {
        FARM_ROW_JITTER(0, 9),
        VIEW_ATTACK(100, 150, true), VIEW_RELEASE(100, 150, true),
        VIEW_TOOL_SLOT(100, 150, true), VIEW_RESUME_ATTACK(100, 150, true),
        WARP(800, 800, true), SPAWN_POINT(300, 150, true), SPAWN_MUSHROOM(150, 150, true),
        PEST_OPEN_WAIT(50, 50, true), PEST_SELECT(150, 200, true), PEST_SETTLE(100, 50, true),
        SPAWN_LOADOUT_OPEN_WAIT(50, 50, true), SPAWN_LOADOUT_SELECT(150, 200, true),
        SPAWN_LOADOUT_SETTLE(100, 50, true), SPAWN_INVENTORY_KEY(150, 250, true);
        private final Range defaults;
        private final int firstWeight, totalWeight;
        Key(int min, int max) { defaults = new Range(min, max); firstWeight = 0; totalWeight = 0; }
        Key(int baseMs, int spreadMs, boolean legacy) {
            int min = (baseMs + 49) / 50;
            defaults = new Range(min, (baseMs + spreadMs - 1 + 49) / 50);
            firstWeight = Math.min(spreadMs, min * 50 - baseMs + 1);
            totalWeight = spreadMs;
        }
        public Range defaults() { return defaults; }
    }
    public record Range(int minTicks, int maxTicks) {
        public Range {
            if (minTicks < 0 || maxTicks < minTicks) throw new IllegalArgumentException("ui.ultimategarden.random_delay.invalid_range");
        }
    }
    private final EnumMap<Key, Range> ranges = new EnumMap<>(Key.class);
    public RandomDelayConfig() { resetToDefault(); }
    public void resetToDefault() { for (Key key : Key.values()) ranges.put(key, key.defaults); }
    public Range get(Key key) { return ranges.get(Objects.requireNonNull(key)); }
    public void set(Key key, int minTicks, int maxTicks) { ranges.put(key, new Range(minTicks, maxTicks)); }
    public int sample(Key key) { return sample(key, ThreadLocalRandom.current()); }
    public int sample(Key key, RandomGenerator random) {
        Range range = get(key);
        if (range.minTicks == range.maxTicks) return range.minTicks;
        if (key.totalWeight > 0 && range.equals(key.defaults)) {
            long ticket = random.nextLong(key.totalWeight);
            return ticket < key.firstWeight ? range.minTicks : range.minTicks + 1 + (int)((ticket - key.firstWeight) / 50);
        }
        return (int)random.nextLong(range.minTicks, (long)range.maxTicks + 1);
    }
    private static int integer(JsonObject value, String key, int fallback) {
        if (!value.has(key)) return fallback;
        JsonElement item = value.get(key);
        if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(key);
        return item.getAsBigDecimal().intValueExact();
    }
    /** Returns true only for invalid values, not missing fields. */
    public boolean load(JsonObject source) {
        resetToDefault();
        boolean repaired = false;
        for (Key key : Key.values()) {
            if (!source.has(key.name())) continue;
            try {
                JsonObject value = source.getAsJsonObject(key.name());
                Range fallback = key.defaults;
                set(key, integer(value, "minTicks", fallback.minTicks), integer(value, "maxTicks", fallback.maxTicks));
            } catch (RuntimeException error) { repaired = true; }
        }
        return repaired;
    }
    public JsonObject toJson(JsonObject source) {
        JsonObject result = source.deepCopy();
        result.addProperty("schemaVersion", 1);
        for (Key key : Key.values()) {
            JsonObject value = result.has(key.name()) && result.get(key.name()).isJsonObject()
                    ? result.getAsJsonObject(key.name()) : new JsonObject();
            value.addProperty("minTicks", get(key).minTicks);
            value.addProperty("maxTicks", get(key).maxTicks);
            result.add(key.name(), value);
        }
        return result;
    }
}
