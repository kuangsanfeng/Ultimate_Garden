package com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model;
import com.google.gson.*;
import java.util.*;
/** Account-local parameters; execution takes an immutable validated copy. */
public final class InstantTransmissionConfig {
 public enum Setting { YAW_START(0,-180,180),YAW_END(45,-180,180),YAW_STEP(5,.001,360),PITCH_START(0,-90,90),PITCH_END(-90,-90,90),PITCH_STEP(-5,-180,180);
  public final double initial,min,max;Setting(double d,double lo,double hi){initial=d;min=lo;max=hi;}}
 private com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing.RouteStrategy strategy=com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing.RouteStrategy.DIRECT_AIM;
 public com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing.RouteStrategy strategy(){return strategy;}
 public void setStrategy(com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing.RouteStrategy value){strategy=Objects.requireNonNull(value);}
 private String distanceBonusNote="";
 public String distanceBonusNote(){return distanceBonusNote;}
 public void setDistanceBonusNote(String value){if(value==null||value.length()>256)throw new IllegalArgumentException("Note too long");distanceBonusNote=value;}
 private final EnumMap<Setting,Double> values=new EnumMap<>(Setting.class);
 public InstantTransmissionConfig(){reset();}
 public void reset(){strategy=com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing.RouteStrategy.DIRECT_AIM;distanceBonusNote="";for(var s:Setting.values())values.put(s,s.initial);}
 public double get(Setting s){return values.get(s);}
 public void set(Setting s,double value){if(!Double.isFinite(value)||value<s.min||value>s.max||s==Setting.PITCH_STEP&&value==0)throw new IllegalArgumentException("Invalid angle / step");values.put(s,value);}
 public static List<Double> axis(double start,double end,double step){
  if(!Double.isFinite(start)||!Double.isFinite(end)||!Double.isFinite(step)||step==0||start!=end&&Math.signum(step)!=Math.signum(end-start))throw new IllegalArgumentException("Invalid range / step direction");
  if((end-start)/step>10000)throw new IllegalArgumentException("Too many samples");
  var out=new ArrayList<Double>();out.add(start);if(start==end)return List.copyOf(out);
  double direction=Math.signum(step);
  for(int i=1;;i++){double v=start+i*step;if(direction*(end-v)<=4*Math.max(Math.ulp(end),Math.ulp(v)))break;out.add(v);}
  out.add(end);return List.copyOf(out);
 }
 public record Plan(List<Double> yaws,List<Double> pitches){}
 public Plan plan(){var y=axis(get(Setting.YAW_START),get(Setting.YAW_END),get(Setting.YAW_STEP));var x=axis(get(Setting.PITCH_START),get(Setting.PITCH_END),get(Setting.PITCH_STEP));if((long)y.size()*x.size()*3>30000)throw new IllegalArgumentException("Too many trials");return new Plan(y,x);}
 public boolean load(JsonObject parent){reset();if(!parent.has("instantTransmission"))return false;if(!parent.get("instantTransmission").isJsonObject())return true;boolean repaired=false;var o=parent.getAsJsonObject("instantTransmission");if(o.has("distanceBonusNote"))try{var v=o.get("distanceBonusNote");if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw new IllegalArgumentException();setDistanceBonusNote(v.getAsString());}catch(RuntimeException e){repaired=true;}for(var s:Setting.values())if(o.has(s.name()))try{var v=o.get(s.name());if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException();set(s,v.getAsDouble());}catch(RuntimeException ex){repaired=true;}
  if(o.has("strategy"))try{if(!o.get("strategy").isJsonPrimitive()||!o.get("strategy").getAsJsonPrimitive().isString())throw new IllegalArgumentException();setStrategy(com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing.RouteStrategy.valueOf(o.get("strategy").getAsString()));}catch(RuntimeException e){repaired=true;}
  if(legacyPitchDefaults(o)){set(Setting.PITCH_END,-90);set(Setting.PITCH_STEP,-5);repaired=true;}
  return repaired;
 }
 private static boolean legacyPitchDefaults(JsonObject o){
  var fields=List.of(Setting.PITCH_START,Setting.PITCH_END,Setting.PITCH_STEP);double[] old={0,90,5};
  for(int i=0;i<fields.size();i++){var v=o.get(fields.get(i).name());if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber()||v.getAsDouble()!=old[i])return false;}
  return true;
 }
 public JsonObject toJson(){var o=new JsonObject();o.addProperty("distanceBonusNote",distanceBonusNote);o.addProperty("strategy",strategy.name());for(var s:Setting.values())o.addProperty(s.name(),get(s));return o;}
}
