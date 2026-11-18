package com.kuangsanfeng.ultimategarden.client.function.world;
import java.util.*;
/** Stable HUD filter keys; independent of map existence and never renames stored maps. */
public final class WorldIds {
 public static final List<String> NAMES=List.of("Backwater Bayou","Crimson Isle","Deep Caverns","Dungeon Hub","Dwarven Mines","Gold Mine","Hub","Lotus Atoll","Moonglade Marsh","Spider's Den","The End","The Farming Islands","The Park","Torrhus Canyon","Private Island","Jerry's Workshop","Crystal Hollows","Garden","The Rift","Dungeon","Glacite Mineshaft");
 private static String slug(String value){return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("^-|-$","");}
 public static String id(String name){if(name==null||name.isBlank())return null;String key=slug(name.strip());key=switch(key){case "dynamic"->"private-island";case "jerrys-workshop"->"jerry-s-workshop";case "spiders-den"->"spider-s-den";default->key;};for(String n:NAMES)if(slug(n).equals(key))return key;return null;}
 public static Set<String> all(){var ids=new LinkedHashSet<String>();for(String n:NAMES)ids.add(id(n));return ids;}
 private WorldIds(){}
}
