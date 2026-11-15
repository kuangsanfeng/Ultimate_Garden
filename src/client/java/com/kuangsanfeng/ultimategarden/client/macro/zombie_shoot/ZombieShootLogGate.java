package com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot;
import com.kuangsanfeng.ultimategarden.client.config.ZombieShootLogCategory;
import java.util.Set;
/** Process-local master switch; only child selections are persisted by ZombieShootoutConfig. */
public final class ZombieShootLogGate {
 private boolean enabled;private Set<ZombieShootLogCategory> categories=Set.of();
 public boolean enabled(){return enabled;}
 public void setEnabled(boolean value){enabled=value;}
 public void categories(Set<ZombieShootLogCategory> value){categories=Set.copyOf(value);}
 public Set<ZombieShootLogCategory> categories(){return categories;}
 public boolean collecting(boolean macroRunning){return macroRunning&&enabled&&!categories.isEmpty();}
}
