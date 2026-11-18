package com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model;
import com.google.gson.*;
public final class ViewDeviationConfig {
 public boolean enabled=true,viewChecksEnabled=true;
 public boolean effective(Settings settings){return enabled&&viewChecksEnabled&&settings.enabled;}
 private static boolean flag(JsonObject doc,String key,boolean fallback){if(!doc.has(key))return fallback;var v=doc.get(key);if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException(key);return v.getAsBoolean();}
 public static final class Settings {
  public boolean enabled;private double warning=5,stop=15,duration=5;
  public double warning(){return warning;}public double stop(){return stop;}public double duration(){return duration;}
  public void warning(double v){valid(v,0,179.99);if(v>=stop)throw new IllegalArgumentException("Warning must be below Stop");warning=v;}
  public void stop(double v){valid(v,.001,180);if(v<=warning)throw new IllegalArgumentException("Stop must exceed Warning");stop=v;}
  public void duration(double v){valid(v,.1,60);duration=v;}
  private static void valid(double v,double min,double max){if(!Double.isFinite(v)||v<min||v>max)throw new IllegalArgumentException("Invalid view check setting");}
 }
 private Settings farm=new Settings(),zombie=new Settings();public Settings farm(){return farm;}public Settings zombie(){return zombie;}
 public void resetToDefault(){enabled=true;viewChecksEnabled=true;farm=new Settings();zombie=new Settings();}
 public boolean load(JsonObject doc){boolean repaired=false;try{enabled=flag(doc,"enabled",true);var root=doc.has("viewDeviationCheck")?doc.getAsJsonObject("viewDeviationCheck"):new JsonObject();viewChecksEnabled=flag(root,"enabled",true);for(String name:new String[]{"farm","zombieShootout"}){var settings=name.equals("farm")?farm:zombie;var j=root.has(name)?root.getAsJsonObject(name):new JsonObject();try{if(j.has("enabled")){var e=j.get("enabled");if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException();settings.enabled=e.getAsBoolean();}double w=number(j,"warningThresholdDeg",5),s=number(j,"stopThresholdDeg",15),d=number(j,"warningDurationSeconds",5);Settings.valid(w,0,179.99);Settings.valid(s,.001,180);Settings.valid(d,.1,60);if(w>=s)throw new IllegalArgumentException();settings.warning=w;settings.stop=s;settings.duration=d;}catch(RuntimeException e){repaired=true;}}}catch(RuntimeException e){repaired=true;}return repaired;}
 private static double number(JsonObject j,String key,double fallback){if(!j.has(key))return fallback;var e=j.get(key);if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException();return e.getAsDouble();}
 public JsonObject toJson(JsonObject original){var doc=original.deepCopy();doc.addProperty("enabled",enabled);var root=doc.has("viewDeviationCheck")&&doc.get("viewDeviationCheck").isJsonObject()?doc.getAsJsonObject("viewDeviationCheck"):new JsonObject();root.addProperty("enabled",viewChecksEnabled);for(String name:new String[]{"farm","zombieShootout"}){var settings=name.equals("farm")?farm:zombie;var j=root.has(name)&&root.get(name).isJsonObject()?root.getAsJsonObject(name):new JsonObject();j.addProperty("enabled",settings.enabled);j.addProperty("warningThresholdDeg",settings.warning);j.addProperty("stopThresholdDeg",settings.stop);j.addProperty("warningDurationSeconds",settings.duration);root.add(name,j);}doc.add("viewDeviationCheck",root);return doc;}
}
