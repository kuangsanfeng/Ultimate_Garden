package com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat;
import java.util.Map;
public record ZombieShootSnapshot(Map<String,Object> data) {
 public ZombieShootSnapshot{data=new com.kuangsanfeng.ultimategarden.client.platform.persistence.log.JsonSnapshot(data).data();}
 public static Map<String,Object> map(Object... pairs){return com.kuangsanfeng.ultimategarden.client.platform.persistence.log.JsonSnapshot.map(pairs);}
}
