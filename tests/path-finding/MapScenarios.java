import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.*;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.*;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.terrain.*;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.execution.*;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.*;import static com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.*;
import com.kuangsanfeng.ultimategarden.client.function.mapping.*;import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import java.nio.file.*;
public final class MapScenarios {
 public static void main(String[] args)throws Exception {
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();Path root=MappingPaths.root(Path.of("run"));
  Point[] starts={new Point(0,77,-1),new Point(0,77,-1),new Point(4,157,84)};Point[] goals={new Point(91,75,174),new Point(83,102,113),new Point(35,12,17)};
  var reports=new com.google.gson.JsonArray();var gson=new com.google.gson.GsonBuilder().setPrettyPrinting().create();
  for(int i=0;i<(java.util.Arrays.asList(args).contains("--hub-only")?2:3);i++)for(var mode:Mode.values()){
   var terrain=new MapTerrain(new MapRepository(root,i==2?"Deep Caverns":"Hub"));long before=System.nanoTime();var options=new Options(mode,true,true,12,1,20000,Weights.defaults());
   var r=new PathPlanner().plan(starts[i],goals[i],0,0,options,terrain,()->false);
   var record=new com.google.gson.JsonObject();record.addProperty("scenario",i==0?"H1":i==1?"H2":"D1");record.addProperty("mapRoot",root.toString());record.add("options",gson.toJsonTree(options));record.add("start",gson.toJsonTree(starts[i]));record.add("result",gson.toJsonTree(r));record.addProperty("executed",false);record.add("actualEndpoint",com.google.gson.JsonNull.INSTANCE);var counts=new com.google.gson.JsonObject();for(var movement:Movement.values())counts.addProperty(movement.name(),r.segments().stream().filter(e->e.movement()==movement).count());record.add("plannedSegmentCounts",counts);reports.add(record);
   System.out.println("SCENARIO "+(i==0?"H1":i==1?"H2":"D1")+" mode="+mode+" root="+root+" start="+starts[i]+" requested="+goals[i]+" resolved="+r.resolved()+" status="+r.status()+" reason="+r.reason()+" expanded="+r.explored()+" segments="+r.segments().size()+" cost="+r.cost()+" ms="+(System.nanoTime()-before)/1e6+" missing="+r.missing().stream().limit(4).toList()+" EXECUTION_NOT_RUN");
  }
  Files.createDirectories(Path.of("build/path-audit"));Files.writeString(Path.of("build/path-audit/scenarios.json"),gson.toJson(reports));
 }
}
