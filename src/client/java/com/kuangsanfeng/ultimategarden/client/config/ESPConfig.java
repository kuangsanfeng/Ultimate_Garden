package com.kuangsanfeng.ultimategarden.client.config;

import com.google.gson.*;
import com.kuangsanfeng.ultimategarden.Ultimate_Garden;
import com.kuangsanfeng.ultimategarden.client.macro.esp.style.*;
import java.util.*;
import java.util.function.Consumer;

/** Shared render parameters and independently owned target styles. */
public final class ESPConfig {
    public static final int SCHEMA_VERSION = 2;
    public static final String SILVERFISH = "SILVERFISH";
    public static final float MIN_LINE_WIDTH = 0.5f, MAX_LINE_WIDTH = 16;
    private boolean throughWalls = true;
    private int cycleDurationMs = 3000;
    private final Map<String, TargetConfig> targets = new LinkedHashMap<>();
    private boolean repaired;
    public ESPConfig() { registerTarget(SILVERFISH); }
    public void clearForAccount() { targets.clear(); resetToDefault(); registerTarget(SILVERFISH); }
    public boolean hasTarget(String id) { return targets.containsKey(id); }
    public TargetConfig registerTarget(String id) {
        if (id == null || !id.matches("[A-Z][A-Z0-9_]*")) throw new IllegalArgumentException("Invalid ESP target identifier");
        return targets.computeIfAbsent(id, ignored -> new TargetConfig());
    }
    public TargetConfig getTarget(String id) {
        TargetConfig target = targets.get(id);
        if (target == null) throw new IllegalArgumentException("Unregistered ESP target: " + id);
        return target;
    }

    public boolean isThroughWalls() { return throughWalls; }
    public void setThroughWalls(boolean value) { throughWalls = value; }
    public int getCycleDurationMs() { return cycleDurationMs; }
    public void setCycleDurationMs(int value) {
        if (value < 1) throw new IllegalArgumentException("Rainbow cycle must be positive (milliseconds)");
        cycleDurationMs = value;
    }
    public void resetToDefault() {
        throughWalls = true; cycleDurationMs = 3000;
        targets.values().forEach(TargetConfig::reset);
    }
    public static final class TargetConfig {
        private final ComponentConfig box = new ComponentConfig("#00FFAA", ESPColorMode.SOLID, 1, 2);
        private final ComponentConfig fill = new ComponentConfig("#00BFFF", ESPColorMode.RAINBOW, 0.25f, 1);
        private final ComponentConfig tracer = new ComponentConfig("#FFAA00", ESPColorMode.SOLID, 1, 1.5f);
        private void reset() {
            box.reset("#00FFAA", ESPColorMode.SOLID, 1, 2);
            fill.reset("#00BFFF", ESPColorMode.RAINBOW, 0.25f, 1);
            tracer.reset("#FFAA00", ESPColorMode.SOLID, 1, 1.5f);
        }
        public ComponentConfig getBox() { return box; }
        public ComponentConfig getFill() { return fill; }
        public ComponentConfig getTracer() { return tracer; }
        public ESPTargetStyle snapshot() { return new ESPTargetStyle(box.snapshot(), fill.snapshot(), tracer.snapshot()); }
    }
    public static final class ComponentConfig {
        private boolean enabled = true;
        private ESPColorMode colorMode;
        private String color;
        private float alpha, lineWidth;
        private ComponentConfig(String color, ESPColorMode mode, float alpha, float width) { reset(color, mode, alpha, width); }
        private void reset(String color, ESPColorMode mode, float alpha, float width) {
            enabled = true; this.color = color; colorMode = mode; this.alpha = alpha; lineWidth = width;
        }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }
        public ESPColorMode getColorMode() { return colorMode; }
        public void setColorMode(ESPColorMode value) { colorMode = java.util.Objects.requireNonNull(value); }
        public String getColor() { return color; }
        public void setColor(String value) {
            if (value == null || !value.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Color must be #RRGGBB");
            color = value.toUpperCase(Locale.ROOT);
        }
        public float getAlpha() { return alpha; }
        public void setAlpha(float value) {
            if (!Float.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Alpha must be 0..1");
            alpha = value;
        }
        public float getLineWidth() { return lineWidth; }
        public void setLineWidth(float value) {
            if (!Float.isFinite(value) || value < MIN_LINE_WIDTH || value > MAX_LINE_WIDTH) throw new IllegalArgumentException("Line width must be 0.5..16 framebuffer pixels");
            lineWidth = value;
        }
        public ESPStyle snapshot() { return new ESPStyle(enabled, colorMode, Integer.parseInt(color.substring(1), 16), alpha, lineWidth); }
    }
    private void read(JsonObject object, String key, Consumer<JsonPrimitive> setter) {
        if (!object.has(key)) return;
        try { setter.accept(object.get(key).getAsJsonPrimitive()); }
        catch (RuntimeException e) { repaired = true; Ultimate_Garden.LOGGER.warn("Invalid ESP field {}; keeping default", key, e); }
    }
    private static boolean bool(JsonPrimitive p) { if (!p.isBoolean()) throw new IllegalArgumentException("Expected boolean"); return p.getAsBoolean(); }
    private static String text(JsonPrimitive p) { if (!p.isString()) throw new IllegalArgumentException("Expected string"); return p.getAsString(); }
    private static float number(JsonPrimitive p) { if (!p.isNumber()) throw new IllegalArgumentException("Expected number"); return p.getAsFloat(); }
    private JsonObject section(JsonObject document, String name) {
        if (!document.has(name)) return new JsonObject();
        if (document.get(name).isJsonObject()) return document.getAsJsonObject(name);
        repaired = true; Ultimate_Garden.LOGGER.warn("Invalid ESP section {}; keeping defaults", name); return new JsonObject();
    }
    public boolean load(JsonObject document) {
        repaired = false;
        // Read legacy values first; valid new fields take precedence independently.
        read(document, "throughWalls", p -> setThroughWalls(bool(p)));
        read(section(document, "rainbow"), "cycleDurationMs", p -> {
            if (!p.isNumber()) throw new IllegalArgumentException("Expected integer");
            setCycleDurationMs(p.getAsBigDecimal().intValueExact());
        });
        loadTarget(document, getTarget(SILVERFISH));
        JsonObject general = section(document, "general");
        read(general, "throughWalls", p -> setThroughWalls(bool(p)));
        read(general, "rainbowCycleDurationMs", p -> {
            if (!p.isNumber()) throw new IllegalArgumentException("Expected integer");
            setCycleDurationMs(p.getAsBigDecimal().intValueExact());
        });
        for (var entry : section(document, "targets").entrySet()) {
            try {
                TargetConfig target = registerTarget(entry.getKey());
                if (!entry.getValue().isJsonObject()) throw new IllegalArgumentException("Expected target object");
                loadTarget(entry.getValue().getAsJsonObject(), target);
            } catch (RuntimeException e) {
                repaired = true; Ultimate_Garden.LOGGER.warn("Invalid ESP target {}; preserving defaults", entry.getKey(), e);
            }
        }
        return repaired;
    }
    public boolean requiresMigration(JsonObject document) {
        if (document.isEmpty()) return false;
        return !document.has("schemaVersion") || document.get("schemaVersion").getAsInt() < SCHEMA_VERSION ||
                document.has("throughWalls") || document.has("box") || document.has("fill") || document.has("tracer") ||
                document.has("rainbow") && document.get("rainbow").isJsonObject() && document.getAsJsonObject("rainbow").has("cycleDurationMs");
    }
    private void loadTarget(JsonObject doc, TargetConfig target) {
        loadComponent(section(doc, "box"), target.box, true);
        loadComponent(section(doc, "fill"), target.fill, false);
        loadComponent(section(doc, "tracer"), target.tracer, true);
    }
    private void loadComponent(JsonObject doc, ComponentConfig value, boolean line) {
        read(doc, "enabled", p -> value.setEnabled(bool(p)));
        read(doc, "colorMode", p -> value.setColorMode(ESPColorMode.valueOf(text(p))));
        read(doc, "color", p -> value.setColor(text(p)));
        read(doc, "alpha", p -> value.setAlpha(number(p)));
        if (line) read(doc, "lineWidth", p -> value.setLineWidth(number(p)));
    }
    public JsonObject toJson(JsonObject original) {
        JsonObject doc = original.deepCopy();
        doc.addProperty("schemaVersion", SCHEMA_VERSION);
        JsonObject general = child(doc, "general");
        general.addProperty("throughWalls", throughWalls); general.addProperty("rainbowCycleDurationMs", cycleDurationMs);
        doc.add("general", general);
        JsonObject definitions = child(doc, "targets");
        for (var entry : targets.entrySet()) {
            JsonObject value = child(definitions, entry.getKey());
            if (entry.getKey().equals(SILVERFISH)) {
                for (String component : List.of("box", "fill", "tracer")) {
                    JsonObject merged = child(doc, component);
                    child(value, component).entrySet().forEach(field -> merged.add(field.getKey(), field.getValue().deepCopy()));
                    value.add(component, merged);
                }
            }
            TargetConfig target = entry.getValue();
            writeComponent(value, "box", target.box, true); writeComponent(value, "fill", target.fill, false); writeComponent(value, "tracer", target.tracer, true);
            definitions.add(entry.getKey(), value);
        }
        doc.add("targets", definitions);
        doc.remove("throughWalls"); doc.remove("box"); doc.remove("fill"); doc.remove("tracer");
        JsonObject rainbow = child(doc, "rainbow"); rainbow.remove("cycleDurationMs");
        if (rainbow.isEmpty()) doc.remove("rainbow"); else doc.add("rainbow", rainbow);
        return doc;
    }
    private static JsonObject child(JsonObject doc, String key) { return doc.has(key) && doc.get(key).isJsonObject() ? doc.getAsJsonObject(key).deepCopy() : new JsonObject(); }
    private void writeComponent(JsonObject doc, String key, ComponentConfig value, boolean line) {
        JsonObject part = child(doc, key);
        part.addProperty("enabled", value.enabled); part.addProperty("colorMode", value.colorMode.name());
        part.addProperty("color", value.color); part.addProperty("alpha", value.alpha);
        if (line) part.addProperty("lineWidth", value.lineWidth);
        doc.add(key, part);
    }
}
