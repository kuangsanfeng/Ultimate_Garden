package com.kuangsanfeng.ultimategarden.client.runtime.feature;
import java.util.*;import java.util.function.*;
/** Registration and queries only: this registry never drives tick, frame, timers or pause state. */
public final class FeatureRegistry {
 public record State(boolean running,String status,String reason,Map<String,Object> fields){public State{fields=Collections.unmodifiableMap(new LinkedHashMap<>(fields));}}
 private record Entry(Runnable start,Consumer<String> stop,Supplier<?> snapshot){}
 private final Map<String,Entry> entries=new LinkedHashMap<>();
 public <T> void register(String id,Runnable start,Consumer<String> stop,Supplier<T> snapshot){
  Objects.requireNonNull(id);Objects.requireNonNull(snapshot);
  if(entries.putIfAbsent(id,new Entry(start,stop,snapshot))!=null)throw new IllegalArgumentException("Duplicate feature: "+id);
 }
 private Entry entry(String id){var e=entries.get(id);if(e==null)throw new IllegalArgumentException("Unknown feature: "+id);return e;}
 public void start(String id){var action=entry(id).start();if(action==null)throw new UnsupportedOperationException("Feature requires contextual start");action.run();}
 public void stop(String id,String reason){var action=entry(id).stop();if(action!=null)action.accept(reason);}
 public <T> T snapshot(String id,Class<T> type){return type.cast(entry(id).snapshot().get());}
 public Map<String,Object> snapshots(){var result=new LinkedHashMap<String,Object>();entries.forEach((id,e)->result.put(id,e.snapshot().get()));return Collections.unmodifiableMap(result);}
 public Set<String> ids(){return Collections.unmodifiableSet(new LinkedHashSet<>(entries.keySet()));}
}
