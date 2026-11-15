package com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot;
import java.util.*;
/** Fixed user priority; lifetimes are metadata, never ordering inputs. */
public final class ShootoutPolicy {
 public static final List<String> ORDER=List.of("LAMP","DIAMOND_BABY","DIAMOND_ADULT","GOLD_BABY","GOLD_ADULT","IRON_BABY","IRON_ADULT","LEATHER_BABY","LEATHER_ADULT");
 public record Metadata(int points,int lifetimeSeconds,double lampMultiplier,int lampBuffSeconds){}
 public static int priority(String type){int i=ORDER.indexOf(type);return i<0?Integer.MAX_VALUE:i;}
 public static boolean retryAllowed(int previousRetries,boolean alreadyQueued,boolean sameIdentity,boolean observedAlive){return previousRetries==0&&!alreadyQueued&&sameIdentity&&observedAlive;}
 public static Metadata metadata(String type){if(type.equals("LAMP"))return new Metadata(100,6,1.2,6);int base=type.startsWith("DIAMOND")?120:type.startsWith("GOLD")?80:type.startsWith("IRON")?50:30;int life=type.startsWith("DIAMOND")?4:type.startsWith("GOLD")?5:type.startsWith("IRON")?6:7;return new Metadata(base*(type.endsWith("BABY")?2:1),life,1,0);}
}
