package com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootLogCategory;

import com.google.gson.*;
import java.util.*;
/** Account-scoped numeric settings; running state is deliberately absent. */
public final class ZombieShootoutConfig {
 public enum Setting {
  AIM_SPEED("aimPeakSpeedDegPerSecond",300,1,2000), AIM_CURVATURE("aimCurveCurvatureDeg",15,Double.MIN_VALUE,Double.MAX_VALUE), AIM_UPDATE_RATE("aimUpdateRateHz",20,20,120), SNAP("snapAngleDeg",15,0,180), AIM_TOLERANCE("aimCompletionToleranceDeg",.50,0,30), AIM_CORRECTION_RATIO("aimCorrectionRandomRatio",.25,0,.5),
  PING_INTERVAL("pingMeasurementIntervalSeconds",15,.5,Double.MAX_VALUE),
  SHOOT_RATIO("shootToleranceRatio",1.50,0,999.99),
  LEATHER_ADULT("LEATHER_ADULT",.30,.05,3), LEATHER_BABY("LEATHER_BABY",.30,.05,3),
  IRON_ADULT("IRON_ADULT",.30,.05,3), IRON_BABY("IRON_BABY",.30,.05,3),
  GOLD_ADULT("GOLD_ADULT",.30,.05,3), GOLD_BABY("GOLD_BABY",.30,.05,3),
  DIAMOND_ADULT("DIAMOND_ADULT",.30,.05,3), DIAMOND_BABY("DIAMOND_BABY",.30,.05,3),
  BABY_STALL_WINDOW("babyZombieStallWindowSeconds",.40,.05,3), BABY_STALL_RATIO("babyZombieStallThresholdRatio",.50,0,1),
  COOLDOWN("shotCooldownSeconds",.55,.5,10), JITTER_MIN("shotJitterMinSeconds",0,0,10), JITTER_MAX("shotJitterMaxSeconds",.1,0,10), BABY_OFFSET("babyAimTopOffsetBlocks",.1,.001,1);
  public final String field; public final double initial,min,max;
  Setting(String field,double initial,double min,double max){this.field=field;this.initial=initial;this.min=min;this.max=max;}
  public boolean window(){return ordinal()>=LEATHER_ADULT.ordinal()&&ordinal()<=DIAMOND_BABY.ordinal();}
 }
 private boolean autoAimEnabled=true,autoShootEnabled=false;
 private boolean occlusionCheckEnabled;
 public boolean isOcclusionCheckEnabled(){return occlusionCheckEnabled;}
 public void setOcclusionCheckEnabled(boolean value){occlusionCheckEnabled=value;}
 private final EnumSet<ZombieShootLogCategory> logCategories=EnumSet.noneOf(ZombieShootLogCategory.class);
 private Runnable logOptionsChanged=()->{};
 public void setLogOptionsChanged(Runnable value){logOptionsChanged=Objects.requireNonNull(value);}
 public Set<ZombieShootLogCategory> logCategories(){return Set.copyOf(logCategories);}
 public boolean logEnabled(ZombieShootLogCategory category){return logCategories.contains(category);}
 public void setLogEnabled(ZombieShootLogCategory category,boolean value){if(value)logCategories.add(category);else logCategories.remove(category);logOptionsChanged.run();}
 private boolean manualShotRestrictionEnabled=true;
 public boolean isManualShotRestrictionEnabled(){return manualShotRestrictionEnabled;}
 public void setManualShotRestrictionEnabled(boolean value){manualShotRestrictionEnabled=value;}
 public boolean manualShotRestrictionEditable(){return autoAimEnabled&&!autoShootEnabled;}
 public boolean manualShotRestrictionEffective(){return manualShotRestrictionEnabled&&manualShotRestrictionEditable();}
 private Runnable automationChanged=()->{};
 public void setAutomationChangeListener(Runnable listener){automationChanged=Objects.requireNonNull(listener);}
 public boolean isAutoAimEnabled(){return autoAimEnabled;}
 public boolean isAutoShootEnabled(){return autoShootEnabled;}
 public boolean autoShootEffective(){return autoAimEnabled&&autoShootEnabled;}
 public void setAutoAimEnabled(boolean value){if(autoAimEnabled!=value){autoAimEnabled=value;automationChanged.run();}}
 public void setAutoShootEnabled(boolean value){if(autoShootEnabled!=value){autoShootEnabled=value;automationChanged.run();}}
 private boolean arrowSpawnConfirmationEnabled=true;
 public boolean isArrowSpawnConfirmationEnabled(){return arrowSpawnConfirmationEnabled;}
 public void setArrowSpawnConfirmationEnabled(boolean enabled){arrowSpawnConfirmationEnabled=enabled;}
 private boolean autoStartZombieShootout;
 private Runnable cancelAutoStart=()->{};
 public boolean isAutoStartZombieShootout(){return autoStartZombieShootout;}
 public void setAutoStartCancellation(Runnable cancel){cancelAutoStart=Objects.requireNonNull(cancel);}
 public void setAutoStartZombieShootout(boolean value){autoStartZombieShootout=value;if(!value)cancelAutoStart.run();}
 private final EnumMap<Setting,Double> values=new EnumMap<>(Setting.class);
 public ZombieShootoutConfig(){resetToDefault();}
 public void resetToDefault(){occlusionCheckEnabled=false;logCategories.clear();logOptionsChanged.run();manualShotRestrictionEnabled=true;setAutoAimEnabled(true);setAutoShootEnabled(false);arrowSpawnConfirmationEnabled=true;setAutoStartZombieShootout(false);for(var s:Setting.values())values.put(s,s.initial);}
 public double get(Setting s){return values.get(s);}
 public void set(Setting s,double v){if(!Double.isFinite(v)||v<s.min||v>s.max||s==Setting.AIM_UPDATE_RATE&&v!=Math.rint(v))throw new IllegalArgumentException("Invalid Shootout setting: "+s.field);if(s==Setting.JITTER_MIN&&v>get(Setting.JITTER_MAX)||s==Setting.JITTER_MAX&&v<get(Setting.JITTER_MIN))throw new IllegalArgumentException("Jitter Min must be <= Max");values.put(s,v);}
 public boolean load(JsonObject doc){boolean repaired=doc.has("speedWindowsSeconds")&&!doc.get("speedWindowsSeconds").isJsonObject();var windows=doc.has("speedWindowsSeconds")&&doc.get("speedWindowsSeconds").isJsonObject()?doc.getAsJsonObject("speedWindowsSeconds"):new JsonObject();
  for(String key:List.of("autoAimEnabled","autoShootEnabled")){if(!doc.has(key))continue;try{var value=doc.get(key);if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException();if(key.equals("autoAimEnabled"))setAutoAimEnabled(value.getAsBoolean());else setAutoShootEnabled(value.getAsBoolean());}catch(RuntimeException e){repaired=true;}}
  if(doc.has("occlusionCheckEnabled")){try{var value=doc.get("occlusionCheckEnabled");if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException();setOcclusionCheckEnabled(value.getAsBoolean());}catch(RuntimeException e){repaired=true;}}
  if(doc.has("developerLogs")){var logs=doc.get("developerLogs");if(!logs.isJsonObject())repaired=true;else for(var category:ZombieShootLogCategory.values())if(logs.getAsJsonObject().has(category.name())){try{var value=logs.getAsJsonObject().get(category.name());if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException();setLogEnabled(category,value.getAsBoolean());}catch(RuntimeException e){repaired=true;}}}
  if(doc.has("manualShotRestrictionEnabled")){try{var value=doc.get("manualShotRestrictionEnabled");if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException();setManualShotRestrictionEnabled(value.getAsBoolean());}catch(RuntimeException e){repaired=true;}}
  if(doc.has("autoStartZombieShootout")){try{var value=doc.get("autoStartZombieShootout");if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException();setAutoStartZombieShootout(value.getAsBoolean());}catch(RuntimeException e){repaired=true;}}
  if(doc.has("arrowSpawnConfirmationEnabled")){try{var value=doc.get("arrowSpawnConfirmationEnabled");if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException();setArrowSpawnConfirmationEnabled(value.getAsBoolean());}catch(RuntimeException e){repaired=true;}}
  var candidate=new EnumMap<Setting,Double>(values);for(var s:Setting.values()){var source=s.window()?windows:doc;if(!source.has(s.field))continue;try{var e=source.get(s.field);if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException();double v=e.getAsDouble();if(!Double.isFinite(v)||v<s.min||v>s.max||s==Setting.AIM_UPDATE_RATE&&v!=Math.rint(v))throw new IllegalArgumentException();candidate.put(s,v);}catch(RuntimeException e){repaired=true;}}
  if(candidate.get(Setting.JITTER_MIN)>candidate.get(Setting.JITTER_MAX)){candidate.put(Setting.JITTER_MIN,0.);candidate.put(Setting.JITTER_MAX,.1);repaired=true;}values.putAll(candidate);return repaired;
 }
 public JsonObject toJson(JsonObject original){var doc=original.deepCopy();doc.remove("enabled");doc.remove("logsEnabled");doc.addProperty("occlusionCheckEnabled",occlusionCheckEnabled);var logs=doc.has("developerLogs")&&doc.get("developerLogs").isJsonObject()?doc.getAsJsonObject("developerLogs").deepCopy():new JsonObject();for(var category:ZombieShootLogCategory.values())logs.addProperty(category.name(),logEnabled(category));doc.add("developerLogs",logs);doc.addProperty("manualShotRestrictionEnabled",manualShotRestrictionEnabled);doc.addProperty("autoAimEnabled",autoAimEnabled);doc.addProperty("autoShootEnabled",autoShootEnabled);doc.addProperty("arrowSpawnConfirmationEnabled",arrowSpawnConfirmationEnabled);doc.addProperty("autoStartZombieShootout",autoStartZombieShootout);var windows=doc.has("speedWindowsSeconds")&&doc.get("speedWindowsSeconds").isJsonObject()?doc.getAsJsonObject("speedWindowsSeconds").deepCopy():new JsonObject();for(var s:Setting.values())(s.window()?windows:doc).addProperty(s.field,get(s));doc.add("speedWindowsSeconds",windows);return doc;}
}
