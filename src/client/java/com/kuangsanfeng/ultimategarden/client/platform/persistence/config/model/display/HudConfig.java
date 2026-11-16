package com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display;

import com.google.gson.*;
import java.util.*;

/** HUD preferences only; never drives macro execution. */
public final class HudConfig {
    public enum Mode { COMPACT, DETAIL }
    public enum Item { MACRO_STATUS, FARM_MODE, CROP, LINE, TOTAL_TICK, LINE_TICK, PEST_LOADOUT, FOCUS_STATE, INPUT_INTERCEPTION, LINE_CHANGE, MOUSEMAT, CURRENT_DIRECTION, LINE_CHANGE_STATUS, STOP_REASON, CURRENT_WORKFLOW }
    public enum HudId { FARM_MACRO, ZOMBIE_SHOOTOUT }
    public record Bounds(double x,double y,double width,double height) { }
    private Bounds zombieBounds=new Bounds(0,0,0,0);
    public Bounds bounds(HudId id) { return id==HudId.FARM_MACRO?new Bounds(x,y,width,height):zombieBounds; }
    public void setBounds(HudId id,double x,double y,double width,double height) {
        if(id==HudId.FARM_MACRO){setBounds(x,y,width,height);return;}
        for(double value:new double[]{x,y,width,height})if(!Double.isFinite(value)||value<0||value>1)throw new IllegalArgumentException("Invalid HUD bounds");
        zombieBounds=new Bounds(x,y,width,height);
    }
    private double x, y, width, height;
    public double x() { return x; }
    public double y() { return y; }
    public double width() { return width; }
    public double height() { return height; }
    public void setBounds(double x, double y, double width, double height) {
        for (double v : new double[]{x,y,width,height}) if (!Double.isFinite(v) || v < 0 || v > 1) throw new IllegalArgumentException("Invalid HUD bounds");
        this.x=x; this.y=y; this.width=width; this.height=height;
    }
    private boolean showHud = true;
    private boolean farmOnlyWhileRunning, showZombieShootoutHud = true, zombieOnlyWhileRunning;
    public boolean isFarmOnlyWhileRunning() { return farmOnlyWhileRunning; }
    public void setFarmOnlyWhileRunning(boolean value) { farmOnlyWhileRunning=value; }
    public boolean isShowZombieShootoutHud() { return showZombieShootoutHud; }
    public void setShowZombieShootoutHud(boolean value) { showZombieShootoutHud=value; }
    public boolean isZombieOnlyWhileRunning() { return zombieOnlyWhileRunning; }
    public void setZombieOnlyWhileRunning(boolean value) { zombieOnlyWhileRunning=value; }
    public boolean showFarm(boolean running) { return showHud && (!farmOnlyWhileRunning || running); }
    public boolean showZombie(boolean running) { return showZombieShootoutHud && (!zombieOnlyWhileRunning || running); }
    public boolean isShowHud() { return showHud; }
    public void setShowHud(boolean value) { showHud=value; }
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
        showHud=true; farmOnlyWhileRunning=false; showZombieShootoutHud=true; zombieOnlyWhileRunning=false;
        x=0; y=0; width=0; height=0; zombieBounds=new Bounds(0,0,0,0);
        mode = Mode.COMPACT; compact.clear(); detail.clear();
        compact.addAll(EnumSet.range(Item.MACRO_STATUS, Item.INPUT_INTERCEPTION));
        detail.addAll(EnumSet.range(Item.MACRO_STATUS, Item.MOUSEMAT));
        compact.remove(Item.TOTAL_TICK);
        detail.remove(Item.TOTAL_TICK);
    }
    public boolean load(JsonObject source) {
        resetToDefault(); boolean repaired = false;
        if (source.has("showHud")) {
            var value=source.get("showHud");
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) showHud=value.getAsBoolean();
            else repaired=true;
        }
        for (String key : List.of("farmOnlyWhileRunning", "showZombieShootoutHud", "zombieOnlyWhileRunning")) {
            if (!source.has(key)) continue;
            var value=source.get(key);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) { repaired=true; continue; }
            switch (key) {
                case "farmOnlyWhileRunning" -> farmOnlyWhileRunning=value.getAsBoolean();
                case "showZombieShootoutHud" -> showZombieShootoutHud=value.getAsBoolean();
                case "zombieOnlyWhileRunning" -> zombieOnlyWhileRunning=value.getAsBoolean();
            }
        }
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
        if(source.has("zombieShootoutBounds")) {
            try {
                var b=source.getAsJsonObject("zombieShootoutBounds");
                setBounds(HudId.ZOMBIE_SHOOTOUT,b.get("x").getAsDouble(),b.get("y").getAsDouble(),b.get("width").getAsDouble(),b.get("height").getAsDouble());
            } catch(RuntimeException e) { repaired=true; }
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
        result.addProperty("showHud",showHud);
        result.addProperty("farmOnlyWhileRunning",farmOnlyWhileRunning);
        result.addProperty("showZombieShootoutHud",showZombieShootoutHud);
        result.addProperty("zombieOnlyWhileRunning",zombieOnlyWhileRunning);
        JsonObject bounds = result.has("bounds") && result.get("bounds").isJsonObject() ? result.getAsJsonObject("bounds") : new JsonObject();
        bounds.addProperty("x",x); bounds.addProperty("y",y); bounds.addProperty("width",width); bounds.addProperty("height",height); result.add("bounds",bounds);
        JsonObject zombie=result.has("zombieShootoutBounds")&&result.get("zombieShootoutBounds").isJsonObject()?result.getAsJsonObject("zombieShootoutBounds"):new JsonObject();
        zombie.addProperty("x",zombieBounds.x());zombie.addProperty("y",zombieBounds.y());zombie.addProperty("width",zombieBounds.width());zombie.addProperty("height",zombieBounds.height());result.add("zombieShootoutBounds",zombie);
        for (String group : List.of("compact","detail")) {
            JsonObject values = result.has(group) && result.get(group).isJsonObject() ? result.getAsJsonObject(group) : new JsonObject();
            for (Item item : Item.values()) values.addProperty(item.name(), group.equals("compact") ? compact(item) : detail(item));
            result.add(group,values);
        }
        return result;
    }
}
