package com.kuangsanfeng.ultimategarden.client.runtime.world.collection;
import java.util.*;
/** Data acceptance boundary; no game objects, file policy or macro lifecycle. */
public final class CollectionSession<C> {
 private volatile boolean accepting=true;private volatile Set<C> categories;
 private final String id;
 public CollectionSession(Set<C> categories){this(java.time.Instant.now().toString().replace(':','-')+"_"+UUID.randomUUID(),categories);}
 public CollectionSession(String id,Set<C> categories){this.id=Objects.requireNonNull(id);select(categories);}
 public String id(){return id;}
 public void select(Set<C> categories){this.categories=Set.copyOf(categories);}
 public boolean accepts(C category,boolean metadata){return accepting&&(metadata||category!=null&&categories.contains(category));}
 public synchronized void stop(){accepting=false;}
 public synchronized boolean submit(C category,boolean metadata,String file,Map<String,Object> value,java.util.function.BiPredicate<String,Map<String,Object>> sink){
  return accepts(category,metadata)&&sink.test(file,new com.kuangsanfeng.ultimategarden.client.platform.persistence.log.JsonSnapshot(value).data());
 }
}
