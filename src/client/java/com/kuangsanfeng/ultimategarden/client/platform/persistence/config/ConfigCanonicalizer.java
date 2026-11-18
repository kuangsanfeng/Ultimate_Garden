package com.kuangsanfeng.ultimategarden.client.platform.persistence.config;

import com.google.gson.*;

/** Startup-only projection of validated model values; never visits other account files. */
final class ConfigCanonicalizer {
    private ConfigCanonicalizer() { }

    /** Crops retain their user-maintained arrays but accept only the bundled field definitions. */
    static JsonObject knownFields(JsonObject values, JsonObject definition) {
        var result = new JsonObject();
        for (var field : definition.entrySet()) {
            var value = values.get(field.getKey());
            if (value == null) continue; // Optional inherited hints keep the model's existing semantics.
            result.add(field.getKey(),value.isJsonObject() && field.getValue().isJsonObject()
                    ? knownFields(value.getAsJsonObject(),field.getValue().getAsJsonObject()) : value.deepCopy());
        }
        return result;
    }

    /** Serializer insertion order defines fixed fields. Lists retain their legal original ordering. */
    static JsonObject ordered(JsonObject encoded, JsonObject previous) {
        var result = new JsonObject();
        if (encoded.has("schemaVersion")) result.add("schemaVersion",encoded.get("schemaVersion"));
        for (var field : encoded.entrySet()) {
            if (field.getKey().equals("schemaVersion")) continue;
            var value=field.getValue();var before=previous.get(field.getKey());
            if (value.isJsonObject()) {
                value=ordered(value.getAsJsonObject(),before!=null&&before.isJsonObject()?before.getAsJsonObject():new JsonObject());
                // Farm HUD derives effective detail from compact at load time. Keep its legal
                // stored selections rather than persisting that derived union during correction.
                if(field.getKey().equals("detail")&&before!=null&&before.isJsonObject())
                    for(var item:value.getAsJsonObject().entrySet()) {
                        var saved=before.getAsJsonObject().get(item.getKey());
                        if(saved!=null&&saved.isJsonPrimitive()&&saved.getAsJsonPrimitive().isBoolean())item.setValue(saved.deepCopy());
                    }
            }
            else if (value.isJsonArray() && before!=null && before.isJsonArray()) {
                // Preserve a valid permutation (e.g. selected worlds). Never restore rejected elements.
                var remaining=value.getAsJsonArray().deepCopy();var array=new JsonArray();
                for(var item:before.getAsJsonArray()) {
                    if(field.getKey().equals("worlds")&&item.isJsonPrimitive()&&item.getAsJsonPrimitive().isString()) {
                        String id=com.kuangsanfeng.ultimategarden.client.function.world.WorldIds.id(item.getAsString());
                        if(id!=null)item=new JsonPrimitive(id);
                    }
                    if(remaining.contains(item)) {array.add(item.deepCopy());remaining.remove(item);}
                }
                for(var item:remaining)array.add(item);
                value=array;
            }
            else if(before!=null&&before.isJsonPrimitive()&&value.isJsonPrimitive()) {
                var a=before.getAsJsonPrimitive();var b=value.getAsJsonPrimitive();
                if(a.isNumber()&&b.isNumber()&&a.getAsBigDecimal().compareTo(b.getAsBigDecimal())==0)value=before.deepCopy();
                else if(field.getKey().equals("color")&&a.isString()&&b.isString()&&a.getAsString().equalsIgnoreCase(b.getAsString()))value=before.deepCopy();
            }
            result.add(field.getKey(),value);
        }
        return result;
    }
}
