package com.kuangsanfeng.ultimategarden.client.config;

/** Disk categories actually emitted by the legacy passive collector. Session metadata is automatic. */
public enum ZombieShootLogCategory {
 LEATHER_ADULT("zombies/leather_adult"),LEATHER_BABY("zombies/leather_baby"),
 IRON_ADULT("zombies/iron_adult"),IRON_BABY("zombies/iron_baby"),
 GOLD_ADULT("zombies/gold_adult"),GOLD_BABY("zombies/gold_baby"),
 DIAMOND_ADULT("zombies/diamond_adult"),DIAMOND_BABY("zombies/diamond_baby"),
 ARROWS("arrows"),PATH_POINTS("paths/raw"),PATH_MODELS("paths/models"),TURN_EVENTS("paths/turns"),
 BLOCKS("environment/blocks"),BLOCK_SCANS("environment/scans"),OCCLUSION("environment/occlusion"),TAGS("tags"),DIAGNOSTICS("diagnostics"),AIM_SHOT_DIAGNOSTICS("aim-shot-diagnostics");
 public final String folder;
 ZombieShootLogCategory(String folder){this.folder=folder;}
 public static ZombieShootLogCategory folder(String folder){for(var value:values())if(folder.equals(value.folder)||folder.startsWith(value.folder+"/"))return value;return null;}
 public boolean zombie(){return ordinal()<=DIAMOND_BABY.ordinal();}
 public boolean legacy(){return this!=AIM_SHOT_DIAGNOSTICS;}
}
