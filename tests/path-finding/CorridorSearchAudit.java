import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.FlightTiming.*;
import com.google.gson.*;import java.io.*;import java.nio.file.*;import java.security.*;import java.util.*;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.*;import static com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.*;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.*;import com.kuangsanfeng.ultimategarden.client.function.path_finding.terrain.*;
import com.kuangsanfeng.ultimategarden.client.function.mapping.*;import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
public final class CorridorSearchAudit {
 static String hash(Path p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
 public static void main(String[] args)throws Exception {
  String scenario=args[0];Mode mode=Mode.valueOf(args[1]);boolean segmented=Boolean.getBoolean("ug.pathAudit.segmented");boolean timed=Boolean.getBoolean("ug.pathAudit.timed");String runId=scenario+"-"+mode+(timed?"-timed":segmented?"-segmented":"-200k");Path log=Path.of("build/path-audit",timed?"timed-corridor-pathfinding.jsonl":segmented?"segmented-pathfinding.jsonl":"corridor-pathfinding.jsonl");Files.createDirectories(log.getParent());
  var gson=new GsonBuilder().serializeNulls().create();long started=System.nanoTime();
  try(var writer=Files.newBufferedWriter(log,java.nio.charset.StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND)){
   java.util.function.BiConsumer<String,Object> emit=(type,data)->{var row=new JsonObject();row.addProperty("event",type);row.addProperty("runId",runId);row.addProperty("scenario",scenario);row.addProperty("mode",mode.name());row.addProperty("elapsedMs",(System.nanoTime()-started)/1e6);row.add("data",gson.toJsonTree(data));try{writer.write(gson.toJson(row));writer.newLine();writer.flush();}catch(IOException e){throw new UncheckedIOException(e);}};
   var termination=new java.util.concurrent.atomic.AtomicReference<Map<String,Object>>(Map.of());var audit=new SearchAudit((type,data)->{if(type.equals("DECISION"))termination.set(data);emit.accept(type,data);});
   try {
    net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
    Path root=MappingPaths.root(Path.of("run"));String folder=scenario.equals("D1")?"deep-caverns":"hub";var repo=new MapRepository(root,scenario.equals("D1")?"Deep Caverns":"Hub");var terrain=new MapTerrain(repo);
    Point start=scenario.equals("D1")?new Point(4,157,84):new Point(0,77,-1),goal=scenario.equals("D1")?new Point(35,12,17):scenario.equals("H1")?new Point(91,75,174):new Point(83,102,113),resolved=new Point(Math.floor(goal.x())+.5,goal.y(),Math.floor(goal.z())+.5);
    if (scenario.equals("DESCENT")) {
        Point found=null;
        scan: for(int x=-4;x<=12;x++)for(int z=-5;z<=6;z++)for(int y=74;y<=92;y++){
            var feet=new Point(x+.5,y,z+.5);
            var high=feet.add(new Point(0,42,0));
            if(terrain.supported(feet)&&terrain.sweep(high,feet)){found=feet;break scan;}
        }
        if(found==null)throw new IllegalStateException("No recorded Hub descent shaft");
        goal=resolved=found;start=goal.add(new Point(0,42,0));
    }
    var options=new Options(mode,true,true,12,1,200000,Weights.defaults());
    var timing=segmented?new CorridorPlanner.Timing(20,4.3,300,50):CorridorPlanner.Timing.defaults();
    emit.accept("RUN_START",Map.ofEntries(Map.entry("mapRoot",root.toString()),Map.entry("sourceMapRoot",Path.of("map",folder).toAbsolutePath().toString()),Map.entry("layer",terrain.config().selectedFestival()),Map.entry("origin",terrain.config().origin()),Map.entry("start",start),Map.entry("internalStart",start),Map.entry("initialYaw",0),Map.entry("initialPitch",0),Map.entry("closestDefinition","closest non-stale expanded state; completeStates includes discovered states"),Map.entry("requested",goal),Map.entry("resolvedTarget",resolved),Map.entry("startSupported",terrain.supported(start)),Map.entry("targetSupported",terrain.supported(resolved)),Map.entry("startClear",terrain.clear(start)),Map.entry("targetClear",terrain.clear(resolved)),Map.entry("options",options),Map.entry("timing",timing),Map.entry("stages",mode==Mode.NORMAL?List.of("direct","simple_clearance","flight_corridor","walking_corridor"):List.of("simple_clearance","flight_corridor","weighted_candidate_comparison","walking_corridor")),Map.entry("limits",Map.of("expandedNodes",200000,"maxSegments",segmented?10:1,"timeoutSeconds",180,"corridorRadius",CorridorPlanner.RADIUS,"optimality","NOT_PROVEN"))));
    long searchStart=System.nanoTime();var timer=(java.util.function.BooleanSupplier)()->System.nanoTime()-searchStart>180_000_000_000L;
    var result=segmented?SegmentedPlanner.plan(terrain,options,10,timing,timer,audit,start,goal,0,0)
            :new CorridorPlanner(terrain,options,timing,timer,audit).plan(start,goal,0,0);double searchMs=(System.nanoTime()-searchStart)/1e6;
    if(result.status()==Status.ARRIVED)emit.accept("ROUTE_FOUND",Map.of("status","PLANNED_ONLY","cost",result.cost(),"steps",result.segments().size(),"actions",result.segments()));
    CorridorRepair.Rehearsal rehearsal=null;
    if(timed&&result.status()==Status.ARRIVED){
     var forecast=new CorridorRepair.Start(Motion.confirmed(start,terrain),0,0,0,Aim.defaults(),new Ready(0,0,0));
     rehearsal=new CorridorRepair(result,options,timing,terrain).rehearse(forecast,terrain,timer);
     emit.accept("OFFLINE_REHEARSAL",Map.of("status",rehearsal.reason(),"end",rehearsal.blockedAt(),"expanded",rehearsal.expanded(),"choices",rehearsal.choices()));
    }
    var files=new ArrayList<Map<String,Object>>();for(String f:terrain.loadedFiles()){Path target=Path.of(f);Path relative=root.resolve(folder).relativize(target);Path source=Path.of("map",folder).resolve(relative);String actual=hash(target);files.add(Map.of("loadedPath",f,"sha256",actual,"sourceExists",Files.exists(source),"matchesSource",Files.exists(source)&&actual.equals(hash(source))));}
    var end=new LinkedHashMap<String,Object>();end.put("status",result.status());end.put("termination",result.reason());end.put("expanded",result.explored());end.put("budget",options.budget());end.put("searchMs",searchMs);end.put("totalMs",(System.nanoTime()-started)/1e6);end.put("result",result);if(rehearsal!=null)end.put("rehearsalStatus",rehearsal.reason());end.put("searchDiagnostics",termination.get());end.put("loadedFiles",files);end.put("clientExecuted",false);end.put("actualEndpoint",null);end.put("timeoutTriggered",System.nanoTime()-searchStart>180_000_000_000L);emit.accept("RUN_END",end);
    System.out.println(runId+" "+result.status()+" expanded="+result.explored()+" ms="+searchMs);
   } catch(Throwable e){try{audit.summary();emit.accept("RUN_END",Map.of("status",e instanceof OutOfMemoryError?"RESOURCE_EXHAUSTED":"EXCEPTION","exception",e.toString(),"latestProgress",audit.latest(),"expanded",audit.latest().getOrDefault("expanded",0),"budget",200000,"clientExecuted",false,"totalMs",(System.nanoTime()-started)/1e6));}catch(Throwable ignored){}System.err.println(runId+" failed "+e);System.exit(2);}
  }
 }
}
