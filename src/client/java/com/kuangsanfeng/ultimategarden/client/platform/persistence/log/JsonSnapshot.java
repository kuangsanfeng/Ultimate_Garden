package com.kuangsanfeng.ultimategarden.client.platform.persistence.log;
import java.util.*;
/** Deep immutable, JSON-safe values only; no game objects may cross threads. */
public record JsonSnapshot(Map<String,Object> data) {
 public JsonSnapshot { data=freezeMap(data); }
 public static Map<String,Object> map(Object... pairs) { var m=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m; }
 private static Map<String,Object> freezeMap(Map<?,?> in){var m=new LinkedHashMap<String,Object>();in.forEach((k,v)->m.put(k.toString(),freeze(v)));return Collections.unmodifiableMap(m);}
 private static Object freeze(Object v){if(v instanceof Double d&&!Double.isFinite(d)||v instanceof Float f&&!Float.isFinite(f))return null;if(v instanceof Map<?,?> m)return freezeMap(m);if(v instanceof Collection<?> l)return Collections.unmodifiableList(l.stream().map(JsonSnapshot::freeze).toList());if(v instanceof Enum<?> e)return e.name();if(v instanceof Record record){var m=new LinkedHashMap<String,Object>();try{for(var c:record.getClass().getRecordComponents())m.put(c.getName(),freeze(c.getAccessor().invoke(record)));}catch(ReflectiveOperationException e){throw new IllegalArgumentException(e);}return Collections.unmodifiableMap(m);}if(v==null||v instanceof String||v instanceof Number||v instanceof Boolean)return v;throw new IllegalArgumentException("Only immutable JSON data may be queued");}
}

