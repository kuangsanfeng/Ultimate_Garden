package com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.log;
import java.util.*;import java.nio.file.Path;
public final class TransmissionLog {
 public static int failures,created;public static boolean capture;public static final List<Map<String,Object>> events=new ArrayList<>();
 public TransmissionLog(Path p){created++;}public boolean finished(){return true;}public boolean healthy(){return true;}public void awaitClose(long ms){}
 public void event(String type,long tick,Map<String,Object> v){if(type.equals("FAILURE_DIAGNOSTIC"))failures++;if(capture){var data=new LinkedHashMap<String,Object>(v);data.put("type",type);data.put("tick",tick);events.add(new com.kuangsanfeng.ultimategarden.client.platform.persistence.log.JsonSnapshot(data).data());}}public void close(long tick,String why){}
}
