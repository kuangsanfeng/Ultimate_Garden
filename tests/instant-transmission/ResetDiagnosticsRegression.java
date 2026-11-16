import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.log.*;
import com.google.gson.*;import java.nio.file.*;import java.util.*;
public final class ResetDiagnosticsRegression {
 static int checks;static void check(boolean v){checks++;if(!v)throw new AssertionError("Reset log "+checks);}
 static double x=17.5,y=30,z=15.5;static boolean ground=true,held;static String state="WALK";
 static Map<String,Object> site(){var out=new LinkedHashMap<String,Object>();out.put("state",state);out.put("feet",List.of(x,y,z));out.put("velocity",List.of(0.,0.,-.07));out.put("onGround",ground);out.put("yaw",0.);out.put("pitch",90.);out.put("blockX",(int)Math.floor(x));out.put("blockZ",(int)Math.floor(z));out.put("baseBlock",List.of(17,30,13));out.put("expectedCenter",List.of(17.5,30.,13.5));out.put("deltaX",x-17.5);out.put("deltaZ",z-13.5);out.put("insideBaseBlock",Math.floor(x)==17&&Math.floor(z)==13);out.put("back",held);return out;}
 public static void main(String[] args)throws Exception{
  var dir=Files.createTempDirectory("ug-reset-diagnostics-");var log=new TransmissionLog(dir);
  var d=new TransmissionResetDiagnostics(ResetDiagnosticsRegression::site,(type,data)->log.event(type,((Number)data.get("observationTick")).longValue(),data));
  d.tick(0);d.tick(1);d.begin(1,"formal-trial-A","TEST_WAIT","LANDING");d.tick(2);
  var before=d.beforeInput(2);held=true;d.submitted(2,"S",false,before,"WALK");
  d.consumed(2,Map.of("S",true,"W",false,"A",false,"D",false));
  z=13.55;d.tick(3);before=d.beforeInput(3);held=false;d.submitted(3,null,true,before,"WALK_TICK_RECOMPUTE");d.event("RESET_WALK_DECISION",3,Map.of("result",true,"decision","STOP_WALKING"),"WALK_DECISION_AFTER_RELEASE");
  state="DOWN_ALIGN";d.state(3,"WALK",state,"EXISTING_ENTER");z=12.5529;d.tick(4);state="RESET_JUMP";d.jump(5,30);ground=false;y=30.42;d.tick(6);y=34.1;d.tick(7);
  d.event("RESET_HEIGHT_GATE",7,Map.of("action","CLICK","requiredHeight",4.),"RESET_HEIGHT_DECISION");
  d.event("RESET_RIGHT_CLICK",7,Map.of("before",d.site(7,"BEFORE_RIGHT_CLICK"),"plannedTick",7,"actualTick",7,"dispatched",true),"AFTER_RIGHT_CLICK_SUBMISSION");
  state="RESET_WAIT";d.state(7,"RESET_JUMP",state,"EXISTING_ENTER");var old=List.of(x,y,z);z=12.5;y=30;ground=true;
  d.event("POSITION_UPDATE",8,Map.of("before",old,"after",List.of(x,y,z),"eventOrder",1),"PLAYER_POSITION_PACKET_APPLIED");d.tick(8);
  d.event("RESET_POSITION_CHECK",9,Map.of("errorX",0.,"errorY",0.,"errorZ",-1.,"xzCentered",false,"yMatches",true,"tolerance",.01,"code","RESET_POSITION_OR_HEIGHT_FAILED"),"RESET_LANDING_CHECK");
  d.end(9,"RESET_POSITION_OR_HEIGHT_FAILED");d.tick(10);d.end(10,"DUPLICATE");
  d.begin(11,null,"START_ALIGN","INITIAL_RESET");d.end(11,"MANUAL_STOP");
  log.close(12,"RESET_POSITION_OR_HEIGHT_FAILED");log.awaitClose(3000);check(log.finished());
  var rows=Files.readAllLines(dir.resolve("instant-transmission.jsonl")).stream().map(s->JsonParser.parseString(s).getAsJsonObject()).toList();
  check(rows.stream().filter(r->r.get("type").getAsString().equals("START")).count()==1);check(rows.stream().filter(r->r.get("type").getAsString().equals("END")).count()==1);
  long sequence=0;for(var r:rows){check(r.get("eventSequence").getAsLong()==++sequence);check(r.has("elapsedMs"));if(!List.of("START","END").contains(r.get("type").getAsString()))check(!r.has("timestamp"));}
  var begin=rows.stream().filter(r->r.get("type").getAsString().equals("RESET_BEGIN")).toList();check(begin.size()==2);String id=begin.getFirst().get("resetId").getAsString();check(begin.getFirst().get("previousTrialId").getAsString().equals("formal-trial-A"));check(!id.equals(begin.getLast().get("resetId").getAsString()));check(begin.getLast().get("nativeInput").isJsonNull());
  var enter=rows.stream().filter(r->r.get("type").getAsString().equals("RESET_BASE_ENTER")).findFirst().orElseThrow();check(enter.get("observationTick").getAsLong()==3);check(enter.getAsJsonObject("before").get("blockZ").getAsInt()==15);check(enter.getAsJsonObject("after").get("blockZ").getAsInt()==13);
  var exit=rows.stream().filter(r->r.get("type").getAsString().equals("RESET_BASE_EXIT")).findFirst().orElseThrow();check(exit.get("observationTick").getAsLong()==4);check(exit.getAsJsonObject("after").get("blockZ").getAsInt()==12);
  var fourth=rows.stream().filter(r->r.get("type").getAsString().equals("RESET_TICK")&&r.get("tick").getAsLong()==4).findFirst().orElseThrow();check(!fourth.get("back").getAsBoolean());check(fourth.getAsJsonObject("nativeInput").get("S").getAsBoolean());check(fourth.get("nativeConsumedClientTick").getAsLong()==3);check(fourth.getAsJsonArray("plannedMovementHeld").isEmpty());
  var click=rows.stream().filter(r->r.get("type").getAsString().equals("RESET_RIGHT_CLICK")).findFirst().orElseThrow();check(click.getAsJsonObject("before").getAsJsonArray("feet").get(2).getAsDouble()==12.5529);check(click.get("resetId").getAsString().equals(id));
  var end=rows.getLast();check(end.get("droppedAtClose").getAsLong()==0);check(end.has("errorsAtClose")&&end.has("writerErrorAtClose"));
  check(rows.stream().noneMatch(r->r.get("type").getAsString().equals("RESET_TICK")&&r.get("tick").getAsLong()==10));
  var opening=rows.stream().filter(r->r.get("type").getAsString().equals("RESET_TICK")&&r.get("tick").getAsLong()==1).toList();check(opening.size()==1);check(opening.getFirst().get("windowOpenedThisTick").getAsBoolean());check(opening.getFirst().get("sampleElapsedMs").getAsDouble()<=opening.getFirst().get("elapsedMs").getAsDouble());check(rows.stream().noneMatch(r->r.toString().contains("captureNanos")));check(click.getAsJsonObject("before").has("sampleElapsedMs"));
  var cached=new ArrayList<Map<String,Object>>();var cacheTest=new TransmissionResetDiagnostics(ResetDiagnosticsRegression::site,(type,data)->{if(type.equals("RESET_TICK"))cached.add(data);});z=15.5;cacheTest.tick(20);z=13.5;cacheTest.begin(20,null,"TEST_WAIT","LANDING");check(cached.size()==1);check(((Number)cached.getFirst().get("blockZ")).intValue()==15);check(cached.getFirst().get("observationPhase").equals("END_CLIENT_TICK_BEFORE_TEST"));cacheTest.end(20,"STOP");
  int[] accepted={0};var unknown=new TransmissionResetDiagnostics(()->{throw new IllegalStateException("not available");},(type,data)->{check(Boolean.FALSE.equals(data.get("observationAvailable")));accepted[0]++;});unknown.begin(1,null,"START_ALIGN","UNKNOWN");unknown.tick(2);unknown.end(3,"STOP");check(accepted[0]==3);
  System.out.println("PASS: "+checks+" RESET JSONL correlation, ordering, boundary, native/submitted phase, precision, writer statistics and unknown-field checks.");
 }
}
