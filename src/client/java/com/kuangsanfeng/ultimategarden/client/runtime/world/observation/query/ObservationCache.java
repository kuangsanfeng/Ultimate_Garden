package com.kuangsanfeng.ultimategarden.client.runtime.world.observation.query;
import java.util.LinkedHashMap;
/** Insertion-order bounded cache. Replacing a key does not change its eviction order. */
public final class ObservationCache<K,V> extends LinkedHashMap<K,V> {
 private final int capacity;
 public ObservationCache(int capacity){if(capacity<1)throw new IllegalArgumentException("capacity");this.capacity=capacity;}
 @Override protected boolean removeEldestEntry(java.util.Map.Entry<K,V> eldest){return size()>capacity;}
 public void invalidate(){clear();}
}
