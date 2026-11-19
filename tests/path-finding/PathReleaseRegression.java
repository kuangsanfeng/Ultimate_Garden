import com.google.gson.*;
import com.kuangsanfeng.ultimategarden.client.function.esp.adapters.*;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import com.kuangsanfeng.ultimategarden.client.function.mapping.*;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.PathSettings;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.execution.*;
import com.kuangsanfeng.ultimategarden.client.function.world.WorldInfoSnapshot;
import com.kuangsanfeng.ultimategarden.client.platform.input.InputRequests;
import com.kuangsanfeng.ultimategarden.client.platform.network.NativeRttService;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.storage.AccountIdentity;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.HudConfig;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.log.JsonlWriter;
import java.nio.file.*;
import java.util.*;
import static com.kuangsanfeng.ultimategarden.client.platform.input.InputRequests.Channel.*;

public final class PathReleaseRegression {
    private static int passed;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void pass(String group){System.out.println("PASS "+group);passed++;}
    public static void main(String[] args)throws Exception{
        Path output=Path.of(args[0]).toAbsolutePath();
        defaults();
        input();
        latency();
        map(Files.createTempDirectory(output,"map-fixture-"));
        espAndHud();
        accounts(Files.createTempDirectory(output,"config-fixture-"));
        journal(output);
        System.out.println("PASS_GROUPS="+passed);
    }
    private static void defaults(){
        var settings=new PathSettings();
        check(settings.aim(PathSettings.Aim.RATE)==60 && !settings.gpuAcceleration()&&!settings.pathfindingLog(),"new path defaults");
        var document=new JsonObject();
        var path=new JsonObject();document.add("pathFinding",path);
        check(!settings.load(document)&&settings.aim(PathSettings.Aim.RATE)==60&&!settings.gpuAcceleration(),"missing settings");
        path.addProperty("gpuAcceleration",true);path.addProperty(PathSettings.Aim.RATE.field(),120);
        check(!settings.load(document)&&settings.gpuAcceleration()&&settings.aim(PathSettings.Aim.RATE)==120,"preserve explicit choices");
        pass("configuration defaults and existing choices");
    }
    private static void input(){
        var requests=new InputRequests();var u=new Object();var path=new Object();var farm=new Object();
        requests.set(u,MOTION,CAPTURE);
        check(requests.blocked(MOTION)&&requests.blocked(CAPTURE)&&!requests.blocked(BUTTONS)&&!requests.blocked(WHEEL)&&!requests.blocked(KEYBOARD),"U pair");
        requests.set(path,MOTION,BUTTONS,WHEEL,KEYBOARD);
        requests.set(farm,CAPTURE,MOTION,KEYBOARD);
        requests.remove(u);
        check(requests.blocked(CAPTURE)&&requests.blocked(MOTION)&&requests.blocked(KEYBOARD),"U exit preserves macro");
        requests.remove(path);
        check(requests.blocked(MOTION)&&!requests.blocked(BUTTONS)&&requests.blocked(CAPTURE),"path exit releases path only");
        requests.remove(farm);
        for(var channel:InputRequests.Channel.values())check(!requests.blocked(channel),"all owners released");
        pass("input request OR merge and owner release");
    }
    private static void latency(){
        var service=new NativeRttService();var path=new PathLatency(service);var connection=new Object();
        var sent=new ArrayList<Long>();var requested=new ArrayList<String>();
        java.util.function.BiConsumer<String,Long> observer=(phase,time)->requested.add(phase);
        path.tick(connection,10,-1,sent::add,observer);check(sent.isEmpty(),"coarse stage does not sample");
        path.tick(connection,20,0,sent::add,observer);path.tick(connection,21,0,sent::add,observer);
        check(sent.size()==1&&requested.getFirst().equals("ACTION_CALCULATION"),"calculation single flight");
        check(!service.pong(new Object(),sent.getFirst(),1_000_020),"reject wrong connection");
        check(service.pong(connection,sent.getFirst(),1_000_020)&&path.milliseconds()==1.0,"native RTT milliseconds");
        path.beginExecution(2_000_000);
        path.beginExecution(3_000_000);
        path.tick(connection,3_001_999_999L,0,sent::add,observer);check(sent.size()==1,"execution cadence is anchored");
        path.tick(connection,3_002_000_000L,0,sent::add,observer);
        path.tick(connection,6_002_000_000L,0,sent::add,observer);
        check(sent.size()==2&&requested.getLast().equals("EXECUTION_IN_FLIGHT"),"no overlap");
        path.close();
        check(!service.pong(connection,sent.getLast(),6_003_000_000L),"cancelled session ignores pong");
        pass("native RTT calculate/run scheduling and cancellation");
    }
    private static WorldInfoSnapshot world(String island,String area){
        return new WorldInfoSnapshot("fixture","minecraft:overworld","SKYBLOCK",island,area,"mini1","mode",null,null,"PRODUCTION");
    }
    private static void map(Path root)throws Exception{
        for(String name:List.of("Dwarven Mines","Hub")){
            var repository=new MapRepository(root,name);
            repository.saveConfig(new MapConfig(1,name,new MapConfig.Origin(0,0,0),256,Set.of(Festival.NONE),Festival.NONE));
            Files.createFile(repository.directory().resolve(name+" 0 0.litematic"));
        }
        check(DetectedLocalMap.resolve(root,world("Dwarven Mines","The Lift"),true).name().equals("Dwarven Mines"),"trusted map lookup");
        check(DetectedLocalMap.resolve(root,world("Dwarven Mines",null),false).status()==DetectedLocalMap.Status.UNKNOWN,"untrusted identity");
        check(DetectedLocalMap.resolve(root,world("The Park",null),true).status()==DetectedLocalMap.Status.MISSING,"no alias fallback");
        check(DetectedLocalMap.resolve(root,world("Hub","Dwarven Mines"),true).status()==DetectedLocalMap.Status.AMBIGUOUS,"two supported datasets");
        check(DetectedLocalMap.resolve(root,world("Hub",null),true).name().equals("Hub"),"exact valid switch");
        pass("trusted world identity, dataset lookup, switch and ambiguity");
    }
    private static void espAndHud(){
        check(!PathRouteESP.corridorVisible(true,true,false)&&PathRouteESP.corridorVisible(true,false,false),"corridor filter");
        var enabled=new java.util.concurrent.atomic.AtomicBoolean();
        var scan=new PathSearchESP(enabled::get,enabled::get);
        scan.expanded("FLIGHT_SPACE",new Point(1,0,0));check(scan.nodes().isEmpty(),"disabled collector");
        enabled.set(true);
        for(int i=0;i<200;i++)scan.expanded("FLIGHT_SPACE",new Point(i,0,0));
        check(scan.nodes().size()==PathSearchESP.NODE_CAP&&scan.nodes().getLast().sequence()==200,"bounded nodes");
        scan.calculation();check(scan.nodes().isEmpty()&&scan.scan(0)==null,"stage cleanup");
        scan.coarse();check(scan.scan(3)==null&&scan.actions(false)==null,"attempt cleanup");
        var hud=new HudConfig();var id=HudConfig.HudId.PATH_FINDING;
        check(hud.compact(id,"status")&&hud.compact(id,"progress")&&hud.compact(id,"reason")&&!hud.compact(id,"latency"),"compact defaults");
        pass("ESP collection gates and HUD default fields");
    }
    private static void accounts(Path root)throws Exception{
        var a=new AccountIdentity(UUID.randomUUID(),"FixtureA");var b=new AccountIdentity(UUID.randomUUID(),"FixtureB");
        var config=new ConfigManager(root,a);var aProfile=config.currentAccount();
        check(aProfile!=null,"verified account A");
        config.getPathSettings().setGpuAcceleration(true);
        check(config.save(ConfigManager.Module.GENERAL)&&config.save(ConfigManager.Module.HUD),"A fixture saved");
        check(config.activateAccount(b),"verified account B");
        config.getPathSettings().setGpuAcceleration(true);
        check(config.save(ConfigManager.Module.GENERAL),"B fixture saved");
        check(config.activateAccount(a),"switch back to A");
        Map<Path,byte[]> before=new HashMap<>();
        try(var paths=Files.walk(root)){
            for(var p:paths.filter(p->p.toString().endsWith(".json")).toList())before.put(p,Files.readAllBytes(p));
        }
        var confirm=new MappingConfirmation();
        check(!confirm.click(aProfile,"general",100),"arm only");
        check(!confirm.click(aProfile,"general",200),"first confirmation click");
        confirm.validate(b);check(!confirm.click(aProfile,"general",300),"changed account invalidates");
        check(!config.restoreAccountDefaults(aProfile,config.accountRevision()-1,ConfigManager.Module.GENERAL),"stale transaction");
        for(var e:before.entrySet())check(Arrays.equals(e.getValue(),Files.readAllBytes(e.getKey())),"no write before confirmed");
        check(!confirm.click(aProfile,"general",400)&&confirm.click(aProfile,"general",500),"confirmed double click");
        check(config.restoreAccountDefaults(aProfile,config.accountRevision(),ConfigManager.Module.GENERAL),"restore selected");
        check(!config.getPathSettings().gpuAcceleration(),"live config reload");
        int differences=0;
        for(var e:before.entrySet())if(!Arrays.equals(e.getValue(),Files.readAllBytes(e.getKey()))){
            check(e.getKey().getFileName().toString().equals("general.json"),"selected module only");differences++;
        }
        check(differences==1,"one file changed across both accounts");
        pass("account guard, double confirmation, one file replacement");
    }
    private static void journal(Path output)throws Exception{
        var disabled=new PathSettings();
        check(!disabled.pathfindingLog(),"journal default disabled");
        var journal=new JsonlWriter(output,"fixture",false,w->{});
        var trace=new PathFailureTrace("NORMAL","fixture",packet->{
            check(journal.offer("fixture-failures.jsonl",packet),"journal enqueue");
        });
        trace.begin();
        trace.gate(6,6,1,"AIM_NOT_READY",Map.of("firstBlocker","AIM_NOT_READY","source","TEST_FIXTURE",
                "errorDegrees",.233,"outerThreshold",.5,"correctionThreshold",.125));
        trace.add("AIM_FRAME","RENDER_AFTER_CLIENT_TICKS",6,1,Map.of("ready",true));
        trace.add("INPUT_SENT","END_CLIENT_TICK",7,1,Map.of("source","TEST_FIXTURE"));
        trace.add("CONFIRMATION_CONSUMED","END_CLIENT_TICK",8,2,Map.of());
        trace.export("LATE_INPUT_CONFIRMED");
        trace.begin();long rejected=trace.actionId();
        trace.add("CAST_GEOMETRY","END_CLIENT_TICK",9,2,Map.of("firstBlocker","CAST_RAY_BLOCKED"));
        trace.begin();trace.replaces(rejected);
        trace.add("FALLBACK_ACTION_SELECTED","END_CLIENT_TICK",9,2,Map.of());
        trace.add("INPUT_SENT","END_CLIENT_TICK",11,2,Map.of());
        trace.add("CONFIRMATION_CONSUMED","END_CLIENT_TICK",12,3,Map.of());
        trace.fallbackOutcome("FALLBACK_INPUT_CONFIRMED");
        journal.close();journal.awaitClose(4000);
        var lines=Files.readAllLines(output.resolve("fixture-failures.jsonl"));
        check(lines.size()==3&&lines.getFirst().contains("AIM_NOT_READY")&&lines.get(1).contains("LATE_INPUT_CONFIRMED")
                &&!lines.get(1).contains("AIM_NOT_READY")&&lines.getLast().contains("CAST_RAY_BLOCKED")
                &&lines.getLast().contains("replacesActionId")&&lines.getLast().contains("FALLBACK_INPUT_CONFIRMED"),"causal and incremental JSONL");
        pass("bounded failure journal, late success and confirmed replacement fixtures");
    }
}
