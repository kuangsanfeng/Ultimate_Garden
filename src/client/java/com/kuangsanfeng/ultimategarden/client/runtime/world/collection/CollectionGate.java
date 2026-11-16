package com.kuangsanfeng.ultimategarden.client.runtime.world.collection;
import java.util.Set;
/** Runtime-only master; category persistence is owned by feature configuration. */
public class CollectionGate<C> {
 private boolean enabled;private Set<C> categories=Set.of();
 public boolean enabled(){return enabled;}
 public void setEnabled(boolean value){enabled=value;}
 public void categories(Set<C> value){categories=Set.copyOf(value);}
 public Set<C> categories(){return categories;}
 public boolean collecting(boolean running){return running&&enabled&&!categories.isEmpty();}
}
