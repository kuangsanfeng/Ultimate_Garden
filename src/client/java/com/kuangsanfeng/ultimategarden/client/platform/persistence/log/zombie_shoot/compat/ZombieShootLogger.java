package com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat;
import java.nio.file.Path;
/** Compatibility facade; file and queue policy belongs to the log module. */
public final class ZombieShootLogger extends com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.ZombieShootLogger {
 public ZombieShootLogger(Path root,String id){super(root,id);}
 public ZombieShootLogger(Path root,String id,boolean append){super(root,id,append);}
}
