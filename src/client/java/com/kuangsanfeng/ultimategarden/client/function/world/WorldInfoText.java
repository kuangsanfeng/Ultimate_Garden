package com.kuangsanfeng.ultimategarden.client.function.world;
import java.util.*;import java.util.regex.*;
/** Parsing only visible client text; no chat listener, command or network probe. */
public final class WorldInfoText {
 private static final Pattern COLOR=Pattern.compile("(?i)\u00a7[0-9a-fk-orx]");
 private static final Pattern AREA=Pattern.compile("^Area:\\s*(.+)$");
 private WorldInfoText(){}
 public static String clean(String value){return value==null?"":COLOR.matcher(value).replaceAll("").replaceAll("(?U)\\s+"," ").strip();}
 public static String area(List<String> lines){for(String raw:lines){Matcher match=AREA.matcher(clean(raw));if(match.matches())return match.group(1).strip();}return null;}
 public static String subArea(List<String> lines){for(String raw:lines){String line=clean(raw);if(!line.isEmpty()&&(line.charAt(0)=='\u23e3'||line.charAt(0)=='\u0444')){String value=line.substring(1).strip();if(!value.isEmpty())return value;}}return null;}
 public static String modeName(String mode){if(mode==null||mode.isBlank())return null;return switch(mode){case "hub"->"Hub";case "dynamic"->"Private Island";default->mode;};}
}
