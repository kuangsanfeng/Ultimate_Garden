import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import com.kuangsanfeng.ultimategarden.client.function.mapping.MapRepository;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.FlightTiming.*;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.*;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.terrain.MapTerrain;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.Map;
import java.util.function.BiConsumer;

import static com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.*;

/** Single-route process. A caller can continue the matrix after a failed JVM. */
public final class BudgetRouteAudit {
    private static final Point[] POINTS = {
        new Point(-48.5,200,-121.5), new Point(129.5,196.5,193.5), new Point(.5,128,160.5),
        new Point(-167.5,149,17.5), new Point(-138.5,199,-69.5), new Point(-9.5,145,-19.5)
    };
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();

    public static void main(String[] args) throws IOException {
        String scenario=args[0];
        int workers=Integer.getInteger("ug.path.workers",Runtime.getRuntime().availableProcessors());
        boolean gpu=Boolean.parseBoolean(System.getProperty("ug.path.gpu","true"));
        boolean replan=Boolean.getBoolean("ug.path.replan");
        int previewLimit=Integer.getInteger("ug.path.diagnosticRehearsal",240);
        Mode mode=Mode.valueOf(args[1]);
        boolean deep=scenario.equals("D1");
        String world=deep?"Deep Caverns":"Dwarven Mines";
        Point start=replan?new Point(118.49997946823053,195.0625,192.40764720837942):deep?new Point(4,157,84):POINTS[scenario.charAt(1)-'1'];
        Point goal=deep?new Point(35,12,17):POINTS[scenario.charAt(4)-'1'];
        Path map=Path.of(deep?"run/config/ultimategarden/map":"map").toAbsolutePath();
        double yaw=replan?180.00294494628906:0;
        double pitch=0;
        String id=(deep?"DC-":"DM-")+scenario+"-"+mode+"-w"+workers+(gpu?"-gpu":"-cpu")+(replan?"-replan":"");
        long began=System.nanoTime();
        try (var log=Files.newBufferedWriter(Path.of(args[2]),StandardOpenOption.CREATE_NEW)) {
            BiConsumer<String,Map<String,Object>> emit=(event,data)->{
                try {
                    log.write(JSON.toJson(Map.of("runId",id,"scenario",scenario,"mode",mode,"world",world,
                            "originalStart",start,"originalTarget",goal,"event",event,"data",data,
                            "elapsedMs",(System.nanoTime()-began)/1e6)));
                    log.newLine();
                    log.flush();
                } catch(IOException failure) {throw new UncheckedIOException(failure);}
            };
            var options=new Options(mode,true,true,12,1,500_000,Weights.defaults());
            int rounds=4;
            var timing=new CorridorPlanner.Timing(20,4.3,300,50);
            emit.accept("RUN_START",Map.of("mapRoot",map.toString(),"options",options,"rounds",rounds,
                    "totalLimit",Math.multiplyExact(options.budget(),rounds),"timing",timing,"yaw",yaw,"pitch",pitch,"workers",workers,"gpuEnabled",gpu,"diagnosticPreviewLimit",previewLimit));
            String stage="LOAD_MAP";
            long expanded=0;
            try {
                SharedConstants.tryDetectVersion();
                Bootstrap.bootStrap();
                var repo=new MapRepository(map,world);
                var terrain=new MapTerrain(repo);
                var config=terrain.config();
                var resolved=new Point(Math.floor(goal.x())+.5,goal.y(),Math.floor(goal.z())+.5);
                emit.accept("ENDPOINT_VALIDATION",Map.of("start",endpoint(repo,terrain,start),
                        "target",endpoint(repo,terrain,resolved),"origin",config.origin(),
                        "maxY",config.maxY(),"layer",config.selectedFestival()));
                stage="PLANNING";
                long planning=System.nanoTime();
                long[] candidates={0};
                var audit=new SearchAudit((event,data)->{
                    if(event.equals("SEGMENT_END")) candidates[0]+=((Number)data.get("candidateChecks")).longValue();
                    emit.accept(event,data);
                });
                var result=SegmentedPlanner.plan(terrain,options,rounds,timing,()->false,audit,start,goal,yaw,pitch,workers,gpu);
                double planningMs=(System.nanoTime()-planning)/1e6;
                expanded=result.explored();
                emit.accept("PLAN_FINISHED",Map.of("status",result.status(),"reason",result.reason(),"expanded",expanded,
                        "lastValid",result.end(),"planningMs",planningMs,"missing",result.missing(),"candidateChecks",candidates[0]));
                if(result.status()!=Status.ARRIVED)
                    emit.accept("ERROR",Map.of("stage",stage,"reason",result.reason(),"status",result.status(),"point",result.end(),"expanded",expanded));
                String rehearsalStatus="NOT_RUN";
                int rehearsalExpanded=0;
                double rehearsalMs=0;
                if(result.status()==Status.ARRIVED) {
                    stage="REHEARSAL";
                    long rehearsing=System.nanoTime();
                    var state=new CorridorRepair.Start(Motion.confirmed(start,terrain),yaw,pitch,0,Aim.defaults(),new Ready(0,0,0));
                    emit.accept("OFFLINE_REHEARSAL_INPUT",Map.of("state",state,"limit",240));
                    var repair=new CorridorRepair(result,options,timing,terrain);
                    var preview=repair.rehearse(state,terrain,()->false);
                    rehearsalMs=(System.nanoTime()-rehearsing)/1e6;
                    rehearsalExpanded=preview.expanded();
                    rehearsalStatus=preview.reason();
                    emit.accept("OFFLINE_REHEARSAL",Map.of("status",rehearsalStatus,"expanded",rehearsalExpanded,
                            "end",preview.blockedAt(),"choices",preview.choices(),"ms",rehearsalMs,"open",preview.open(),
                            "progress",preview.progress(),"deadEnds",preview.deadEnds(),"lastGeometryCheck",preview.lastGeometryCheck()));
                    if(previewLimit>240&&!preview.complete()) {
                        var probe=repair.rehearse(state,terrain,()->false,previewLimit);
                        emit.accept("DIAGNOSTIC_REHEARSAL",Map.of("limit",previewLimit,"status",probe.reason(),"expanded",probe.expanded(),
                                "end",probe.blockedAt(),"choices",probe.choices(),"open",probe.open(),"progress",probe.progress(),
                                "deadEnds",probe.deadEnds(),"lastGeometryCheck",probe.lastGeometryCheck()));
                    }
                    if(!rehearsalStatus.equals("VERIFIED"))
                        emit.accept("ERROR",Map.of("stage",stage,"reason",rehearsalStatus,"point",preview.blockedAt(),
                                "expanded",expanded,"rehearsalExpanded",rehearsalExpanded));
                }
                var files=terrain.loadedFiles().stream().sorted().map(f->Map.of("loadedPath",f)).toList();
                emit.accept("RUN_END",Map.of("status",result.status(),"reason",result.reason(),"expanded",expanded,
                        "planningMs",planningMs,"rehearsalStatus",rehearsalStatus,"rehearsalExpanded",rehearsalExpanded,
                        "rehearsalMs",rehearsalMs,"result",result,"loadedFiles",files,"candidateChecks",candidates[0]));
            } catch(Exception failure) {
                emit.accept("ERROR",Map.of("stage",stage,"reason",failure.toString(),"expanded",expanded));
                emit.accept("RUN_END",Map.of("status","EXCEPTION","reason",failure.toString(),"expanded",expanded,"rehearsalStatus","NOT_RUN"));
                throw failure;
            }
        }
    }

    private static Map<String,Object> endpoint(MapRepository repo,MapTerrain terrain,Point point) throws IOException {
        var config=terrain.config();
        int x=Math.floorDiv((int)Math.floor(point.x()),16), z=Math.floorDiv((int)Math.floor(point.z()),16);
        return Map.of("point",point,"worldChunk",new int[]{x,z},"file",repo.chunkPath(config,config.selectedFestival(),x,z).toString(),
                "clear",terrain.clear(point),"supported",terrain.supported(point),"detail",terrain.diagnosticFailure());
    }
}
