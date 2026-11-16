package com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat;
import com.kuangsanfeng.ultimategarden.client.function.collection.zombie_shoot.ZombieShootCoordinateSystem;

import java.nio.file.Path;import java.util.Set;import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootLogCategory;
public final class ZombieShootSession extends com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.ZombieShootSession {
 public ZombieShootSession(Path root,ZombieShootCoordinateSystem origin,String dimension,String version,Set<ZombieShootLogCategory> selected){super(root,origin,dimension,version,selected);}
}
