package com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.log;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.log.JsonlWriter;
import com.kuangsanfeng.ultimategarden.client.function.collection.CollectionSession;
import java.nio.file.Path;import java.util.*;
public final class TransmissionLog {
 private final JsonlWriter writer;private final CollectionSession<String> session=new CollectionSession<>(Set.of("TEST"));
 private final long start=System.nanoTime();private boolean closed;private long eventSequence;
 public TransmissionLog(Path root){writer=new JsonlWriter(root,session.id(),true,w->{if(w.dropped.get()>0||w.errors.get()>0)com.kuangsanfeng.ultimategarden.Ultimate_Garden.LOGGER.warn("Instant Transmission writer summary: dropped={}, errors={}, error={}",w.dropped.get(),w.errors.get(),w.error);});event("START",0,Map.of("timestamp",java.time.Instant.now().toString()));}
 public boolean finished(){return writer.finished();}
 public void awaitClose(long ms){try{writer.awaitClose(ms);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
 public String id(){return session.id();}
 public boolean healthy(){return writer.error==null;}
 public void event(String type,long tick,Map<String,Object> data){if(closed)return;var m=relativeTimes(data);m.put("type",type);m.put("batchId",id());m.put("tick",tick);m.put("elapsedMs",(System.nanoTime()-start)/1e6);m.put("eventSequence",++eventSequence);session.submit("TEST",false,"instant-transmission.jsonl",m,writer::offer);}
 private Map<String,Object> relativeTimes(Map<?,?> data){var out=new LinkedHashMap<String,Object>();for(var entry:data.entrySet()){String key=(String)entry.getKey();Object value=entry.getValue();if(key.equals("captureNanos")&&value instanceof Number n){out.put("sampleElapsedMs",(n.longValue()-start)/1e6);continue;}if((key.equals("yaw")||key.equals("pitch"))&&value instanceof Number n)value=wrapLoggedAngle(n.doubleValue());out.put(key,relativeValue(value));}return out;}
 // Logging uses (-180, 180]; ViewAngles.wrap uses [-180, 180) for control instead.
 private static double wrapLoggedAngle(double degrees){double angle=degrees%360.0;if(angle>180.0)angle-=360.0;else if(angle<=-180.0)angle+=360.0;return angle;}
 private Object relativeValue(Object value){if(value instanceof Map<?,?> map)return relativeTimes(map);if(value instanceof List<?> list)return list.stream().map(this::relativeValue).toList();return value;}
 public void close(long tick,String why){if(closed)return;var end=new LinkedHashMap<String,Object>();end.put("type","END");end.put("batchId",id());end.put("tick",tick);end.put("elapsedMs",(System.nanoTime()-start)/1e6);end.put("timestamp",java.time.Instant.now().toString());end.put("reason",why);end.put("eventSequence",++eventSequence);end.put("droppedAtClose",writer.dropped.get());end.put("writtenAtClose",writer.written.get());end.put("errorsAtClose",writer.errors.get());end.put("writerErrorAtClose",writer.error);session.stop();closed=true;writer.endBatch("instant-transmission.jsonl",end);writer.close();}
}
