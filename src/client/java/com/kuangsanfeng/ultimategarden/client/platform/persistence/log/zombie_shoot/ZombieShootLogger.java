package com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.log.JsonSnapshot;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.log.JsonlWriter;

import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.io.IOException;
import com.google.gson.GsonBuilder;

public class ZombieShootLogger extends JsonlWriter {
 public ZombieShootLogger(Path root,String id){this(root,id,false);}
 public ZombieShootLogger(Path root,String id,boolean append){super(root,id,append,writer->{
  if(!append)try{
   Files.createDirectories(root.resolve("sessions"));
   Files.writeString(root.resolve("sessions/"+id+"-summary.json"),new GsonBuilder().serializeNulls().create().toJson(JsonSnapshot.map("sessionId",id,"endTimestamp",java.time.Instant.now().toString(),"written",writer.written.get(),"droppedSamples",writer.dropped.get(),"collectionErrors",writer.errors.get(),"failureReason",writer.error,"counts",new java.util.TreeMap<>(writer.counts()))),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
  }catch(IOException ex){writer.reportFailure(ex);}
 });}
}
