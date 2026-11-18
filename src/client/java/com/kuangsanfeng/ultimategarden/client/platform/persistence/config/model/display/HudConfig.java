package com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display;

import com.google.gson.*;
import java.util.*;

/** HUD preferences only; never drives macro execution. */
public final class HudConfig {
    private final Map<HudId,Boolean> worldFilterFlags=new EnumMap<>(HudId.class);
    private final Map<HudId,Set<String>> worldSelections=new EnumMap<>(HudId.class);
    public boolean worldFilter(HudId id){return worldFilterFlags.getOrDefault(id,false);}
    public void worldFilter(HudId id,boolean enabled){worldFilterFlags.put(id,enabled);}
    public boolean worldSelected(HudId id,String key){return worldSelections.get(id).contains(key);}
    public void worldSelected(HudId id,String key,boolean enabled){if(enabled)worldSelections.get(id).add(key);else worldSelections.get(id).remove(key);}
    public void allWorlds(HudId id,boolean selected){worldSelections.get(id).clear();if(selected)worldSelections.get(id).addAll(com.kuangsanfeng.ultimategarden.client.function.world.WorldIds.all());}
    public boolean worldAllows(HudId id,String raw){if(!worldFilter(id))return true;String key=com.kuangsanfeng.ultimategarden.client.function.world.WorldIds.id(raw);return key!=null&&worldSelected(id,key);}
    public enum Mode { COMPACT, DETAIL }
    public enum Item { MACRO_STATUS, FARM_MODE, CROP, LINE, TOTAL_TICK, LINE_TICK, PEST_LOADOUT, FOCUS_STATE, INPUT_INTERCEPTION, LINE_CHANGE, MOUSEMAT, CURRENT_DIRECTION, LINE_CHANGE_STATUS, STOP_REASON, CURRENT_WORKFLOW }
    public enum HudId { FARM_MACRO, ZOMBIE_SHOOTOUT, INSTANT_TRANSMISSION, MAPPING }
    public record Bounds(double x,double y,double width,double height) { }
    private Bounds mappingBounds=new Bounds(0,0,0,0);
    private Bounds transmissionBounds=new Bounds(0,0,0,0);
    private Bounds zombieBounds=new Bounds(0,0,0,0);
    public Bounds bounds(HudId id) { return switch(id){case FARM_MACRO->new Bounds(x,y,width,height);case ZOMBIE_SHOOTOUT->zombieBounds;case INSTANT_TRANSMISSION->transmissionBounds;case MAPPING->mappingBounds;}; }
    public void setBounds(HudId id,double x,double y,double width,double height) {
        if(id==HudId.FARM_MACRO){setBounds(x,y,width,height);return;}
        for(double value:new double[]{x,y,width,height})if(!Double.isFinite(value)||value<0||value>1)throw new IllegalArgumentException("Invalid HUD bounds");
        if(id==HudId.MAPPING)mappingBounds=new Bounds(x,y,width,height);else if(id==HudId.INSTANT_TRANSMISSION)transmissionBounds=new Bounds(x,y,width,height);else zombieBounds=new Bounds(x,y,width,height);
    }
    public static List<String> fields(HudId id){return switch(id){
        case FARM_MACRO->Arrays.stream(Item.values()).map(Enum::name).toList();
        case ZOMBIE_SHOOTOUT->List.of("status","target","measured_speed","prediction_speed","current_angles","target_angles","aim_error","aim_ready","tolerance","correction","aim_mode","cooldown","confirmation","attempt","ping","latency");
        case INSTANT_TRANSMISSION->List.of("status","progress","test_angles","actual_angles","reset_alignment","height","samples","clicks","since_first","updates","failure_stage","reason","last_result");
        case MAPPING->List.of("server","game_type","island","sub_area","dimension","instance","recording_origin","recording_chunk","recording_layer","recording_status","recording_current_state","recording_saved");};}
    private static final class Panel {
        Mode mode=Mode.COMPACT;boolean onlyRunning,show;final Set<String> compact=new LinkedHashSet<>(),detail=new LinkedHashSet<>();
        Panel(HudId id){compact.addAll(fields(id));}
    }
    private final Map<HudId,Panel> panels=new EnumMap<>(HudId.class);
    public Mode mode(HudId id){return id==HudId.FARM_MACRO?getMode():panels.get(id).mode;}
    public void mode(HudId id,Mode mode){if(id==HudId.FARM_MACRO)setMode(mode);else panels.get(id).mode=Objects.requireNonNull(mode);}
    public boolean compact(HudId id,String key){return id==HudId.FARM_MACRO?compact(Item.valueOf(key)):panels.get(id).compact.contains(key);}
    public boolean detail(HudId id,String key){return id==HudId.FARM_MACRO?detail(Item.valueOf(key)):panels.get(id).detail.contains(key);}
    public boolean visible(HudId id,String key){return compact(id,key)||mode(id)==Mode.DETAIL&&detail(id,key);}
    public void content(HudId id,String key,boolean compact,boolean enabled){
        if(!fields(id).contains(key))throw new IllegalArgumentException("Unknown HUD field");
        if(id==HudId.FARM_MACRO){if(compact)setCompact(Item.valueOf(key),enabled);else setDetail(Item.valueOf(key),enabled);return;}
        var p=panels.get(id);var set=compact?p.compact:p.detail;if(enabled)set.add(key);else set.remove(key);
    }
    public boolean enabled(HudId id){return switch(id){case FARM_MACRO->showHud;case ZOMBIE_SHOOTOUT->showZombieShootoutHud;case INSTANT_TRANSMISSION->showInstantTransmissionHud;case MAPPING->panels.get(id).show;};}
    public void enabled(HudId id,boolean value){switch(id){case FARM_MACRO->showHud=value;case ZOMBIE_SHOOTOUT->showZombieShootoutHud=value;case INSTANT_TRANSMISSION->showInstantTransmissionHud=value;case MAPPING->panels.get(id).show=value;}}
    public boolean onlyRunning(HudId id){return switch(id){case FARM_MACRO->farmOnlyWhileRunning;case ZOMBIE_SHOOTOUT->zombieOnlyWhileRunning;default->panels.get(id).onlyRunning;};}
    public void onlyRunning(HudId id,boolean value){switch(id){case FARM_MACRO->farmOnlyWhileRunning=value;case ZOMBIE_SHOOTOUT->zombieOnlyWhileRunning=value;default->panels.get(id).onlyRunning=value;}}
    public boolean show(HudId id,boolean running,boolean editing){return editing||enabled(id)&&(!onlyRunning(id)||running);}
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
    private boolean showInstantTransmissionHud = true;
    public boolean isShowInstantTransmissionHud() { return showInstantTransmissionHud; }
    public void setShowInstantTransmissionHud(boolean value) { showInstantTransmissionHud=value; }
    public boolean showTransmission(boolean editing) { return show(HudId.INSTANT_TRANSMISSION,false,editing); }
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
        worldFilterFlags.clear();worldSelections.clear();for(var id:HudId.values()){worldFilterFlags.put(id,false);worldSelections.put(id,new LinkedHashSet<>(com.kuangsanfeng.ultimategarden.client.function.world.WorldIds.all()));}
        showHud=true; farmOnlyWhileRunning=false; showZombieShootoutHud=true; zombieOnlyWhileRunning=false; showInstantTransmissionHud=true;
        x=0; y=0; width=0; height=0; zombieBounds=new Bounds(0,0,0,0);transmissionBounds=new Bounds(0,0,0,0);
        panels.clear();for(var id:HudId.values())if(id!=HudId.FARM_MACRO)panels.put(id,new Panel(id));mappingBounds=new Bounds(0,0,0,0);
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
        for (String key : List.of("farmOnlyWhileRunning", "showZombieShootoutHud", "zombieOnlyWhileRunning", "showInstantTransmissionHud")) {
            if (!source.has(key)) continue;
            var value=source.get(key);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) { repaired=true; continue; }
            switch (key) {
                case "farmOnlyWhileRunning" -> farmOnlyWhileRunning=value.getAsBoolean();
                case "showZombieShootoutHud" -> showZombieShootoutHud=value.getAsBoolean();
                case "zombieOnlyWhileRunning" -> zombieOnlyWhileRunning=value.getAsBoolean();
                case "showInstantTransmissionHud" -> showInstantTransmissionHud=value.getAsBoolean();
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
        if(source.has("instantTransmissionBounds")) {
            try {var b=source.getAsJsonObject("instantTransmissionBounds");setBounds(HudId.INSTANT_TRANSMISSION,b.get("x").getAsDouble(),b.get("y").getAsDouble(),b.get("width").getAsDouble(),b.get("height").getAsDouble());}
            catch(RuntimeException e){repaired=true;}
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
        if(source.has("mappingBounds"))try{var b=source.getAsJsonObject("mappingBounds");setBounds(HudId.MAPPING,b.get("x").getAsDouble(),b.get("y").getAsDouble(),b.get("width").getAsDouble(),b.get("height").getAsDouble());}catch(RuntimeException e){repaired=true;}
        if(source.has("panels")){
            if(!source.get("panels").isJsonObject())repaired=true;
            else for(var id:panels.keySet()){var all=source.getAsJsonObject("panels");if(!all.has(id.name()))continue;
                if(!all.get(id.name()).isJsonObject()){repaired=true;continue;}var o=all.getAsJsonObject(id.name());var panel=panels.get(id);
                if(o.has("displayMode"))try{if(!o.get("displayMode").isJsonPrimitive()||!o.get("displayMode").getAsJsonPrimitive().isString())throw new IllegalArgumentException();panel.mode=Mode.valueOf(o.get("displayMode").getAsString());}catch(RuntimeException e){repaired=true;}
                for(String flag:List.of("onlyRunning","show"))if(o.has(flag)){var v=o.get(flag);if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isBoolean())repaired=true;else if(flag.equals("onlyRunning"))panel.onlyRunning=v.getAsBoolean();else panel.show=v.getAsBoolean();}
                for(String group:List.of("compact","detail"))if(o.has(group)){if(!o.get(group).isJsonObject()){repaired=true;continue;}var values=o.getAsJsonObject(group);var set=group.equals("compact")?panel.compact:panel.detail;
                    for(String field:fields(id))if(values.has(field)){var v=values.get(field);if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isBoolean())repaired=true;else if(v.getAsBoolean())set.add(field);else set.remove(field);}
                }
            }
        }
        if(source.has("worldFilters"))try{var filters=source.getAsJsonObject("worldFilters");for(var id:HudId.values())if(filters.has(id.name())){
            var f=filters.getAsJsonObject(id.name());if(f.has("enabled")){var v=f.get("enabled");if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException();worldFilter(id,v.getAsBoolean());}
            if(f.has("worlds")){var ids=new LinkedHashSet<String>();for(var v:f.getAsJsonArray("worlds")){if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw new IllegalArgumentException();String key=com.kuangsanfeng.ultimategarden.client.function.world.WorldIds.id(v.getAsString());if(key!=null)ids.add(key);}worldSelections.put(id,ids);}
        }}catch(RuntimeException e){repaired=true;}
        detail.addAll(compact);
        return repaired;
    }
    public JsonObject toJson(JsonObject original) {
        JsonObject result = original.deepCopy();var filters=new JsonObject();for(var id:HudId.values()){var f=new JsonObject();f.addProperty("enabled",worldFilter(id));var ids=new JsonArray();worldSelections.get(id).forEach(ids::add);f.add("worlds",ids);filters.add(id.name(),f);}result.add("worldFilters",filters); result.addProperty("schemaVersion",1); result.addProperty("displayMode", mode.name());
        result.addProperty("showHud",showHud);
        result.addProperty("farmOnlyWhileRunning",farmOnlyWhileRunning);
        result.addProperty("showZombieShootoutHud",showZombieShootoutHud);
        result.addProperty("zombieOnlyWhileRunning",zombieOnlyWhileRunning);
        result.addProperty("showInstantTransmissionHud",showInstantTransmissionHud);
        JsonObject bounds = result.has("bounds") && result.get("bounds").isJsonObject() ? result.getAsJsonObject("bounds") : new JsonObject();
        bounds.addProperty("x",x); bounds.addProperty("y",y); bounds.addProperty("width",width); bounds.addProperty("height",height); result.add("bounds",bounds);
        JsonObject zombie=result.has("zombieShootoutBounds")&&result.get("zombieShootoutBounds").isJsonObject()?result.getAsJsonObject("zombieShootoutBounds"):new JsonObject();
        zombie.addProperty("x",zombieBounds.x());zombie.addProperty("y",zombieBounds.y());zombie.addProperty("width",zombieBounds.width());zombie.addProperty("height",zombieBounds.height());result.add("zombieShootoutBounds",zombie);
        JsonObject transmission=result.has("instantTransmissionBounds")&&result.get("instantTransmissionBounds").isJsonObject()?result.getAsJsonObject("instantTransmissionBounds"):new JsonObject();
        transmission.addProperty("x",transmissionBounds.x());transmission.addProperty("y",transmissionBounds.y());transmission.addProperty("width",transmissionBounds.width());transmission.addProperty("height",transmissionBounds.height());result.add("instantTransmissionBounds",transmission);
        for (String group : List.of("compact","detail")) {
            JsonObject values = result.has(group) && result.get(group).isJsonObject() ? result.getAsJsonObject(group) : new JsonObject();
            for (Item item : Item.values()) values.addProperty(item.name(), group.equals("compact") ? compact(item) : detail(item));
            result.add(group,values);
        }
        var mapping=new JsonObject();mapping.addProperty("x",mappingBounds.x());mapping.addProperty("y",mappingBounds.y());mapping.addProperty("width",mappingBounds.width());mapping.addProperty("height",mappingBounds.height());result.add("mappingBounds",mapping);
        var all=result.has("panels")&&result.get("panels").isJsonObject()?result.getAsJsonObject("panels"):new JsonObject();
        for(var id:panels.keySet()){var p=panels.get(id);var o=all.has(id.name())&&all.get(id.name()).isJsonObject()?all.getAsJsonObject(id.name()):new JsonObject();o.addProperty("displayMode",p.mode.name());if(id==HudId.INSTANT_TRANSMISSION||id==HudId.MAPPING)o.addProperty("onlyRunning",p.onlyRunning);if(id==HudId.MAPPING)o.addProperty("show",p.show);
            for(String group:List.of("compact","detail")){var v=new JsonObject();for(String key:fields(id))v.addProperty(key,(group.equals("compact")?p.compact:p.detail).contains(key));o.add(group,v);}all.add(id.name(),o);
        }result.add("panels",all);
        return result;
    }
}
