package com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.log;
import java.util.*;import java.util.function.*;
/** Bounded, observation-only RESET window; all input and movement decisions stay in the controller. */
public final class TransmissionResetDiagnostics {
 private final Supplier<Map<String,Object>> sampler;private final BiConsumer<String,Map<String,Object>> sink;
 private String id,previousTrialId;private long startTick,lastSubmissionTick=-1,nativeTick=-1,nativeSequence,errors;
 private Double takeoffY;private boolean airborne;private Map<String,Object> previous,nativeInput,tickSnapshot;private long tickSnapshotTick=-1;
 private final Set<String> planned=new LinkedHashSet<>();
 public TransmissionResetDiagnostics(Supplier<Map<String,Object>> sampler,BiConsumer<String,Map<String,Object>> sink){this.sampler=sampler;this.sink=sink;}
 public boolean active(){return id!=null;}
 public Map<String,Object> decorate(Map<String,Object> data){var out=new LinkedHashMap<>(data);if(active()){out.put("resetId",id);out.put("previousTrialId",previousTrialId);out.put("resetStartTick",startTick);}return out;}
 public void begin(long tick,String previousTrial,String fromState,String why){if(active())return;id=UUID.randomUUID().toString();previousTrialId=previousTrial;startTick=tick;lastSubmissionTick=nativeTick=-1;nativeSequence=errors=0;takeoffY=null;airborne=false;previous=nativeInput=null;planned.clear();var beginSite=snapshot(tick,"RESET_BEGIN");var beginRecord=new LinkedHashMap<>(beginSite);beginRecord.putAll(map("fromState",fromState,"reason",why));write("RESET_BEGIN",beginRecord);if(tickSnapshot!=null&&tickSnapshotTick==tick){var sample=new LinkedHashMap<>(tickSnapshot);sample.put("windowOpenedThisTick",true);observe(tick,sample);write("RESET_TICK",sample);}observe(tick,beginSite);}
 private Map<String,Object> snapshot(long tick,String phase){
  Map<String,Object> out;
  try{out=new LinkedHashMap<>(sampler.get());}catch(RuntimeException e){errors++;out=map("observationAvailable",false,"observationError",e.getClass().getSimpleName());}
  out.put("captureNanos",System.nanoTime());out.put("observationPhase",phase);out.put("observationTick",tick);out.put("plannedHeld",List.copyOf(planned));
  out.put("plannedMovementHeld",planned.stream().filter(k->Set.of("W","A","S","D").contains(k)).toList());
  out.put("lastSubmissionTick",lastSubmissionTick<0?null:lastSubmissionTick);out.put("lastSubmissionPhase",lastSubmissionTick<0?null:"END_CLIENT_TICK_TEST_ACTION");
  out.put("planAppliesFromClientTick",lastSubmissionTick<0?null:lastSubmissionTick+1);
  out.put("nativeInput",nativeInput);out.put("nativeConsumedClientTick",nativeTick<0?null:nativeTick);out.put("nativeReadPhase",nativeInput==null?null:"KEYBOARD_INPUT_TICK_RETURN");out.put("nativeInputSequence",nativeSequence);
  out.put("takeoffY",takeoffY);
  Object feet=out.get("feet");if(takeoffY!=null&&feet instanceof List<?> v&&v.size()==3&&v.get(1) instanceof Number y)out.put("relativeTakeoffHeight",y.doubleValue()-takeoffY);
  return out;
 }
 private void write(String type,Map<String,Object> value){try{sink.accept(type,decorate(value));}catch(RuntimeException e){errors++;}}
 private void observe(long tick,Map<String,Object> now){
  if(previous!=null&&previous.get("insideBaseBlock") instanceof Boolean before&&now.get("insideBaseBlock") instanceof Boolean after&&before!=after)
   write(after?"RESET_BASE_ENTER":"RESET_BASE_EXIT",map("observationTick",tick,"observationPhase",now.get("observationPhase"),"before",previous,"after",now,"firstObserved",true));
  previous=new LinkedHashMap<>(now);
  if(!airborne&&takeoffY!=null&&Boolean.FALSE.equals(now.get("onGround"))){airborne=true;write("RESET_AIRBORNE",map("observationTick",tick,"site",now));}
 }
 public void event(String type,long tick,Map<String,Object> extra,String phase){if(!active())return;var site=snapshot(tick,phase);observe(tick,site);site.putAll(extra);write(type,site);}
 public void tick(long tick){tickSnapshotTick=tick;tickSnapshot=snapshot(tick,"END_CLIENT_TICK_BEFORE_TEST");if(active()){observe(tick,tickSnapshot);write("RESET_TICK",tickSnapshot);}}
 public Map<String,Object> beforeInput(long tick){if(!active())return null;var s=snapshot(tick,"END_CLIENT_TICK_BEFORE_INPUT");observe(tick,s);return s;}
 public void submitted(long tick,String key,boolean releaseAll,Map<String,Object> before,String why){
  if(!active())return;if(releaseAll)planned.clear();else planned.add(key);lastSubmissionTick=tick;
  event(releaseAll?"RESET_INPUT_RELEASE":"RESET_INPUT_SUBMITTED",tick,map("request",releaseAll?"RELEASE_ALL":key,"reason",why,"before",before),"END_CLIENT_TICK_AFTER_INPUT");
 }
 public void released(long tick,String key,Map<String,Object> before){if(!active())return;planned.remove(key);lastSubmissionTick=tick;event("RESET_INPUT_RELEASE",tick,map("request","RELEASE_"+key,"reason","EXISTING_KEY_UP","before",before),"END_CLIENT_TICK_AFTER_INPUT");}
 public void jump(long tick,double y){if(!active())return;takeoffY=y;airborne=false;event("RESET_JUMP_ISSUED",tick,Map.of("takeoffY",y),"END_CLIENT_TICK_AFTER_JUMP_SUBMISSION");}
 public void consumed(long controllerTick,Map<String,Object> input){if(!active())return;nativeInput=Collections.unmodifiableMap(new LinkedHashMap<>(input));nativeTick=controllerTick+1;nativeSequence++;event("RESET_NATIVE_INPUT",nativeTick,map("controllerTickAtCallback",controllerTick),"KEYBOARD_INPUT_TICK_RETURN");}
 public Map<String,Object> site(long tick,String phase){if(!active())return Map.of();var site=snapshot(tick,phase);observe(tick,site);return site;}
 public void state(long tick,String before,String after,String why){event("RESET_STATE_CHANGE",tick,map("fromState",before,"toState",after,"reason",why),"STATE_TRANSITION");}
 public void end(long tick,String why){if(!active()){tickSnapshot=null;tickSnapshotTick=-1;return;}event("RESET_END",tick,map("reason",why,"observationErrors",errors),"RESET_WINDOW_END");id=previousTrialId=null;previous=nativeInput=null;takeoffY=null;planned.clear();tickSnapshot=null;tickSnapshotTick=-1;}
 private static Map<String,Object> map(Object... pairs){var out=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)out.put((String)pairs[i],pairs[i+1]);return out;}
}
