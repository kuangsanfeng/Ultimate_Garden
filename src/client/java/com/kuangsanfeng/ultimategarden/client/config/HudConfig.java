package com.kuangsanfeng.ultimategarden.client.config;

import com.google.gson.*;
import java.util.*;

/** HUD preferences only; never drives macro execution. */
public final class HudConfig {
    public enum Mode { COMPACT, DETAIL }
    public enum Item { MACRO_STATUS, FARM_MODE, CROP, LINE, TOTAL_TICK, LINE_TICK, PEST_LOADOUT, FOCUS_STATE, INPUT_INTERCEPTION, LINE_CHANGE, MOUSEMAT, CURRENT_DIRECTION, LINE_CHANGE_STATUS, STOP_REASON, CURRENT_WORKFLOW }
    private double x, y, width, height;
    public double x() { return x; }
    public double y() { return y; }
    public double width() { return width; }
    public double height() { return height; }
    public void setBounds(double x, double y, double width, double height) {
        for (double v : new double[]{x,y,width,height}) if (!Double.isFinite(v) || v < 0 || v > 1) throw new IllegalArgumentException("Invalid HUD bounds");
        this.x=x; this.y=y; this.width=width; this.height=height;
    }
    private Mode mode;
    private final EnumSet<Item> compact = EnumSet.noneOf(Item.class), detail = EnumSet.noneOf(Item.class);
    public HudConfig() { resetToDefault(); }
    public Mode getMode() { return mode; }
    public void setMode(Mode value) { mode = Objects.requireNonNull(value); }
    public boolean compact(Item item) { return compact.contains(item); }
    public boolean detail(Item item) { return detail.contains(item); }
    public boolean visible(Item item) { return mode == Mode.COMPACT ? compact(item) : detail(item); }
    public void setCompact(Item item, boolean enabled) {
        if (enabled) { compact.add(item); detail.add(item); }
        else { compact.remove(item); detail.remove(item); }
    }
    public void setDetail(Item item, boolean enabled) {
        if (compact(item)) { detail.add(item); return; }
        if (enabled) detail.add(item); else detail.remove(item);
    }
    public void resetToDefault() {
        x=0; y=0; width=0; height=0;
        mode = Mode.COMPACT; compact.clear(); detail.clear();
        compact.addAll(EnumSet.range(Item.MACRO_STATUS, Item.INPUT_INTERCEPTION));
        detail.addAll(EnumSet.range(Item.MACRO_STATUS, Item.MOUSEMAT));
        compact.remove(Item.TOTAL_TICK);
        detail.remove(Item.TOTAL_TICK);
    }
    public boolean load(JsonObject source) {
        resetToDefault(); boolean repaired = false;
        if (source.has("displayMode")) {
            try {
                if (!source.get("displayMode").isJsonPrimitive() || !source.getAsJsonPrimitive("displayMode").isString()) throw new IllegalArgumentException();
                mode = Mode.valueOf(source.get("displayMode").getAsString());
            } catch (RuntimeException e) { repaired = true; }
        }
        if (source.has("bounds")) {
            try {
                JsonObject b = source.getAsJsonObject("bounds");
                setBounds(b.get("x").getAsDouble(), b.get("y").getAsDouble(), b.get("width").getAsDouble(), b.get("height").getAsDouble());
            } catch (RuntimeException e) { repaired=true; }
        }
        for (String group : List.of("compact", "detail")) {
            if (!source.has(group)) continue;
            if (!source.get(group).isJsonObject()) { repaired = true; continue; }
            JsonObject values = source.getAsJsonObject(group);
            var target = group.equals("compact") ? compact : detail;
            for (Item item : Item.values()) {
                if (!values.has(item.name())) continue;
                var value = values.get(item.name());
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) { repaired = true; continue; }
                if (value.getAsBoolean()) target.add(item); else target.remove(item);
            }
        }
        detail.addAll(compact);
        return repaired;
    }
    public JsonObject toJson(JsonObject original) {
        JsonObject result = original.deepCopy(); result.addProperty("schemaVersion",1); result.addProperty("displayMode", mode.name());
        JsonObject bounds = result.has("bounds") && result.get("bounds").isJsonObject() ? result.getAsJsonObject("bounds") : new JsonObject();
        bounds.addProperty("x",x); bounds.addProperty("y",y); bounds.addProperty("width",width); bounds.addProperty("height",height); result.add("bounds",bounds);
        for (String group : List.of("compact","detail")) {
            JsonObject values = result.has(group) && result.get(group).isJsonObject() ? result.getAsJsonObject(group) : new JsonObject();
            for (Item item : Item.values()) values.addProperty(item.name(), group.equals("compact") ? compact(item) : detail(item));
            result.add(group,values);
        }
        return result;
    }
}
