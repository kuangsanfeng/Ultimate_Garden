package com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.instant_transmission;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.TransmissionHudSnapshot;
import java.util.*;import java.util.function.Function;
/** Pure presentation: never polls inputs, advances trials, or infers successful skill uses. */
public final class TransmissionHudContent {
 public record Row(String key,String label,String value){}
 private static String number(Double value){return value==null?"--":String.format(Locale.ROOT,"%+.2f\u00b0",value);}
 private static String angles(Double yaw,Double pitch){return number(yaw)+" / "+number(pitch);}
 private static String text(Function<String,String> t,String key){return t.apply("ui.ultimategarden.transmission_hud."+key);}
 public static String reason(Function<String,String> t,String code){
  if(code==null)return "--";
  if(code.startsWith("STATE_TIMEOUT_"))return text(t,"reason.STATE_TIMEOUT")+" ("+text(t,"phase."+code.substring(14))+")";
  if(code.startsWith("TEST_EXCEPTION_"))return text(t,"reason.TEST_EXCEPTION")+" ("+code.substring(15)+")";
  String key="ui.ultimategarden.transmission_hud.reason."+code,value=t.apply(key);return value.equals(key)?code:value;
 }
 public static String status(TransmissionHudSnapshot s,Function<String,String> t){
  if(s.running())return text(t,"running")+" \u00b7 "+text(t,"phase."+s.phase());
  return text(t,s.reason().equals("COMPLETED")?"completed":s.reason().equals("MANUAL_STOP")?"manual_stop":s.reason().equals("NONE")?"stopped":"failed");
 }
 public static List<Row> rows(TransmissionHudSnapshot s,Function<String,String> t){
  return rows(s,t,key->true);
 }
 public static List<Row> rows(TransmissionHudSnapshot s,Function<String,String> t,java.util.function.Predicate<String> selected){
  var out=new ArrayList<Row>();
  out.add(new Row("progress",text(t,"progress"),String.format(Locale.ROOT,text(t,"progress_value"),s.groupIndex(),s.totalGroups(),s.clickGroup())));
  out.add(new Row("test_angles",text(t,"test_angles"),angles(s.testYaw(),s.testPitch())));
  out.add(new Row("actual_angles",text(t,"actual_angles"),angles(s.actualYaw(),s.actualPitch())));
  if(s.reset())out.add(new Row("reset_alignment",text(t,"reset_alignment"),angles(s.alignmentYaw(),s.alignmentPitch())));
  if(s.phase().equals("RESET_JUMP"))out.add(new Row("height",text(t,"height"),(s.relativeHeight()==null?"--":String.format(Locale.ROOT,"%.2f",s.relativeHeight()))+" / 4.00 "+text(t,"blocks")));
  if(s.phase().equals("SETTLE"))out.add(new Row("samples",text(t,"samples"),s.preSamples()+" / 2 Tick"));
  if(s.relativeTick()!=null){
   out.add(new Row("clicks",text(t,s.reset()?"reset_clicks":"clicks"),s.dispatched()+" / "+s.planned()));
   out.add(new Row("since_first",text(t,"since_first"),s.relativeTick()+" Tick"));
   out.add(new Row("updates",text(t,"updates"),Integer.toString(s.updates())));
  }
  if(!s.running()){
   if(s.terminationStage()!=null)out.add(new Row("failure_stage",text(t,"failure_stage"),text(t,"stage."+s.terminationStage())+" / "+text(t,"phase."+s.phase())));
   out.add(new Row("reason",text(t,"reason"),reason(t,s.reason())));
   if(s.lastResult()!=null)out.add(new Row("last_result",text(t,"last_result"),reason(t,s.lastResult())));
  }
  out.removeIf(row->!selected.test(row.key()));return List.copyOf(out);
 }
}
