import com.google.gson.*;
import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import com.kuangsanfeng.ultimategarden.client.function.mapping.*;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.Mode;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.execution.ClientPathFinding;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.terrain.MapTerrain;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Test-only integrated client. Imports corresponding local map blocks before driving public API. */
public final class CorridorClientAudit implements ClientModInitializer {
    private final Gson gson=new GsonBuilder().serializeNulls().create();
    private final Path log=Path.of(System.getProperty("ug.corridor.log"));
    private final List<JsonObject> runs=new ArrayList<>();
    private boolean chatSmokePending=Boolean.getBoolean("ug.corridor.chatSmoke"), checkingChat;
    private int outbound;
    private long confirmationExpiry;
    private final boolean prealignedDescent=Boolean.getBoolean("ug.corridor.prealignedDescent");
    private final boolean descentOnly=Boolean.getBoolean("ug.corridor.descentOnly");
    private final boolean slowDescent=Boolean.getBoolean("ug.corridor.slowDescent");
    private final boolean walkingSmoke=Boolean.getBoolean("ug.corridor.walkOnly");
    private final Set<String> imported = new HashSet<>();
    private int next, ticks;
    private volatile JsonObject run;
    private long observedTicks,lastTickNanos,lastHeartbeat;
    private long sumTickNanos,maxTickNanos,tickIntervals;
    private Point lastSampledFeet;
    private volatile long lastTickActivity,lastSearchActivity;
    private volatile int progressAttempt;
    private volatile long progressNodes;
    private volatile boolean searchActive;
    private CompletableFuture<Void> importWork;
    private long started;
    private int casts,confirmed,replans;
    private boolean opening, executing, closing;
    public void onInitializeClient() {
        try {
            for (var line:Files.readAllLines(log)) {
                var row=JsonParser.parseString(line).getAsJsonObject();
                if (row.get("event").getAsString().equals("RUN_END") && (row.getAsJsonObject("data").get("status").getAsString().equals("ARRIVED")
                        || Boolean.getBoolean("ug.corridor.auditFailedPlans")&&row.getAsJsonObject("data").has("result")))
                    if ((!walkingSmoke || row.get("runId").getAsString().startsWith("H1-NORMAL-"))
                            && (!(slowDescent||prealignedDescent||descentOnly) || row.get("scenario").getAsString().equals("DESCENT"))) runs.add(row);
            }
        } catch (Exception e) {throw new RuntimeException(e);}
        if (chatSmokePending) {
            net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_CHAT.register(message->{if(checkingChat){outbound++;return false;}return true;});
            net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_COMMAND.register(message->{if(checkingChat){outbound++;return false;}return true;});
        }
        if(Boolean.getBoolean("ug.corridor.threadSamples")){
            var sampler=new Thread(this::sampleThreads,"ug-path-audit-monitor");
            sampler.setDaemon(true);
            sampler.start();
        }
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            heartbeat(c);
            try { tick(c); } catch(RuntimeException e) {
                e.printStackTrace();
                if(run!=null) record("CLIENT_END",Map.of("status","HARNESS_ERROR","error",e.toString()));
                c.stop();closing=true;
            }
        });
    }
    private void tick(Minecraft c) {
        if (closing) return;
        if (!opening && c.screen instanceof TitleScreen && c.getOverlay()==null) {
            opening=true;c.createWorldOpenFlows().openWorld("UG Corridor Audit",()->{throw new IllegalStateException("Test world failed to open");});
        }
        if (c.getSingleplayerServer()==null||c.player==null||executing) return;
        if (run==null) {
            if(next==runs.size()){System.out.println("CORRIDOR_NATIVE_FINISHED");closing=true;c.stop();return;}
            run=runs.get(next++);ticks=0;
            record("CLIENT_PREPARE",Map.of("reason","IMPORT_RECORDED_GEOMETRY"));
            importWork=setup(c,run);
            return;
        }
        if (!importWork.isDone() || ++ticks<(descent()?2:35)) return;
        importWork.join();
        if (chatSmokePending && !checkChat(c)) return;
        var data=run.getAsJsonObject("data");
        var result=data.getAsJsonObject("result");
        var goal=gson.fromJson(walkingSmoke?firstWalk(run).get("to"):result.get("requested"),Point.class);
        var settings=Ultimate_GardenClient.getConfigManager().getPathSettings();
        settings.mode=Mode.valueOf(run.get("mode").getAsString());
        settings.transmission=!walkingSmoke;
        settings.setCooldown(Integer.getInteger("ug.corridor.cooldown",20));
        settings.initialJump=false;settings.initialSpeed=false;
        settings.setBudget(Long.getLong("ug.corridor.nodes",settings.searchNodesPerSegment()),
                Integer.getInteger("ug.corridor.rounds",settings.maximumSearchSegments()));
        var speedSetting=com.kuangsanfeng.ultimategarden.client.function.path_finding.PathSettings.Aim.SPEED;
        if(slowDescent)settings.aim(speedSetting,25);
        settings.setGpuAcceleration(Boolean.parseBoolean(System.getProperty("ug.path.gpu","true")));
        settings.setCpuWorkers(Integer.getInteger("ug.path.workers",0));
        executing=true;started=System.nanoTime();casts=confirmed=replans=0;
        observedTicks=0;lastTickNanos=0;lastHeartbeat=0;maxTickNanos=0;sumTickNanos=0;tickIntervals=0;lastSampledFeet=null;
        record("CLIENT_START",Map.of("actualStart",feet(c),"yaw",c.player.getYRot(),"pitch",c.player.getXRot(),
                "configuredCooldownMs",settings.teleportCooldownMs(),"aimSpeed",settings.aim(speedSetting),
                "nodesPerRound",settings.searchNodesPerSegment(),"maxRounds",settings.maximumSearchSegments(),
                "workers",settings.effectiveWorkers(),"gpuEnabled",settings.gpuAcceleration()));
        ClientPathFinding.INSTANCE.start(this,mapName(),goal,12,true,()->false,this::record,out->{
            record("CLIENT_END",Map.of("status",out.status(),"reason",out.reason(),"actualEndpoint",out.end(),"elapsedMs",(System.nanoTime()-started)/1e6,"casts",casts,"confirmations",confirmed,"replans",replans));
            executing=false;run=null;
        });
    }
    private boolean checkChat(Minecraft c) {
        var controller=ClientPathFinding.INSTANCE;
        var settings=Ultimate_GardenClient.getConfigManager().getPathSettings();
        if (confirmationExpiry==0) {
            c.setScreen(null);
            checkingChat=true;
            controller.startHotkey();
            if (!(c.screen instanceof net.minecraft.client.gui.screens.ChatScreen chat)) throw new IllegalStateException("Local chat screen not opened");
            chat.insertText("/say path-coordinate-probe",true);
            chat.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,0,0));
            if(c.screen!=null||outbound!=0)throw new IllegalStateException("Local command was not intercepted");
            controller.startHotkey();
            if (!(c.screen instanceof net.minecraft.client.gui.screens.ChatScreen cancel)) throw new IllegalStateException("Local chat not reopened");
            cancel.insertText("-12 70 34",true);
            cancel.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0));
            if(c.screen!=null||outbound!=0||controller.menuTaskActive())throw new IllegalStateException("Escape failed to discard coordinates");
            int old=settings.coordinateWindowMs();
            settings.setCoordinateWindowMs(450);
            c.gui.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal("go -12.5 70 34"));
            settings.setCoordinateWindowMs(old);
            var pending=controller.confirmation();
            if(pending==null||!pending.point().equals(new Point(-12.5,70,34))||pending.fraction()<=0||pending.fraction()>1)
                throw new IllegalStateException("Visible chat did not start a confirmation window");
            confirmationExpiry=System.nanoTime()+550_000_000L;
            return false;
        }
        if(System.nanoTime()<confirmationExpiry)return false;
        if(controller.confirmation()!=null||controller.menuTaskActive()||outbound!=0)throw new IllegalStateException("Confirmation timeout changed task state");
        var ordinary=new net.minecraft.client.gui.screens.ChatScreen("",false);
        c.setScreen(ordinary);
        ordinary.insertText("ordinary chat probe",true);
        ordinary.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,0,0));
        if(outbound!=1)throw new IllegalStateException("Chat send control probe did not reach Fabric");
        chatSmokePending=checkingChat=false;
        record("CHAT_SMOKE",Map.of("escapeCancelled",true,"slashCommandIntercepted",true,"candidateExpired",true,
                "actualCandidate",new Point(-12.5,70,34),"ordinarySendListenerReachable",true));
        return true;
    }
    private boolean descent(){return run!=null && run.get("scenario").getAsString().equals("DESCENT");}

    private CompletableFuture<Void> setup(Minecraft c,JsonObject row) {
        var done=new CompletableFuture<Void>();
        var server=c.getSingleplayerServer();
        String world=mapName();
        var segments=row.getAsJsonObject("data").getAsJsonObject("result").getAsJsonArray("segments");
        var start=gson.fromJson(walkingSmoke?firstWalk(row).get("from"):savedStart(row),Point.class);
        var keys=new LinkedHashMap<Long,int[]>();
        for(var json:segments) addChunks(keys,json.getAsJsonObject());
        if(row.get("runId").getAsString().endsWith("-timed") || Boolean.getBoolean("ug.corridor.importAuditedChunks")){
            // Import the captured surroundings used by corridor-local repair as well as the guide.
            for(var loaded:row.getAsJsonObject("data").getAsJsonArray("loadedFiles")){
                String path=loaded.getAsJsonObject().get("loadedPath").getAsString();
                var name=Path.of(path).getFileName().toString();
                var chunk=java.util.regex.Pattern.compile(" (-?[0-9]+) (-?[0-9]+)\\.litematic$").matcher(name);
                if(!chunk.find())continue;
                try {
                    var config=new MapRepository(MappingPaths.root(c.gameDirectory.toPath()),world).load();
                    int x=Math.floorDiv(config.origin().x(),16)+Integer.parseInt(chunk.group(1));
                    int z=Math.floorDiv(config.origin().z(),16)+Integer.parseInt(chunk.group(2));
                    keys.put(((long)x<<32)^(z&0xffffffffL),new int[]{x,z});
                }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
            }
        }
        server.execute(()->{
            try {
                var terrain=new MapTerrain(new MapRepository(MappingPaths.root(c.gameDirectory.toPath()),world));
                var level=server.overworld();
                var player=server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.CREATIVE);
                player.getAbilities().flying=false;
                player.onUpdateAbilities();
                server.getPlayerList().op(new net.minecraft.server.players.NameAndId(player.getGameProfile()));
                for(var k:keys.values()) {
                    if(!imported.add(world+":"+k[0]+":"+k[1]))continue;
                    var origin=new BlockPos(k[0]*16,terrain.getMinY(),k[1]*16);
                    try{terrain.getBlockState(origin);}catch(RuntimeException missing){continue;}
                    var chunk=level.getChunk(k[0],k[1]);
                    var position=new BlockPos.MutableBlockPos();
                    for(int y=Math.max(level.getMinY(),terrain.getMinY());y<Math.min(level.getMaxY()+1,terrain.getMinY()+terrain.getHeight());y++) {
                        int section=level.getSectionIndex(y);
                        var blocks=chunk.getSections()[section];
                        for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                            position.set(k[0]*16+x,y,k[1]*16+z);
                            blocks.setBlockState(x,Math.floorMod(y,16),z,terrain.getBlockState(position));
                        }
                    }
                    chunk.markUnsaved();
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(chunk,level.getChunkSource().getLightEngine(),null,null));
                }
                float startYaw=Float.parseFloat(System.getProperty("ug.corridor.startYaw","0"));
                player.connection.teleport(start.x(),start.y(),start.z(),startYaw,prealignedDescent?90:0);
                done.complete(null);
            }catch(Exception e){done.completeExceptionally(e);}
        });
        return done;
    }
    private void addChunks(Map<Long,int[]> keys,JsonObject action) {
        var a=gson.fromJson(action.get("from"),Point.class);var b=gson.fromJson(action.get("to"),Point.class);
        for(int x=chunk(Math.min(a.x(),b.x())-1);x<=chunk(Math.max(a.x(),b.x())+1);x++)
            for(int z=chunk(Math.min(a.z(),b.z())-1);z<=chunk(Math.max(a.z(),b.z())+1);z++)
                keys.put(((long)x<<32)^(z&0xffffffffL),new int[]{x,z});
    }
    private JsonObject firstWalk(JsonObject row) {
        for(var step:row.getAsJsonObject("data").getAsJsonObject("result").getAsJsonArray("segments")) {
            var action=step.getAsJsonObject();
            if(action.get("movement").getAsString().equals("WALK"))return action;
        }
        throw new IllegalStateException("No walking fixture in planned route");
    }
    private JsonElement savedStart(JsonObject row) {
        var segments=row.getAsJsonObject("data").getAsJsonObject("result").getAsJsonArray("segments");
        return segments.isEmpty()?row.get("originalStart"):segments.get(0).getAsJsonObject().get("from");
    }
    private static int chunk(double v){return Math.floorDiv((int)Math.floor(v),16);}
    private String mapName(){return run.get("scenario").getAsString().equals("D1")?"Deep Caverns":"Hub";}
    private static Point feet(Minecraft c){return new Point(c.player.getX(),c.player.getY(),c.player.getZ());}
    private void heartbeat(Minecraft c){
        if(!executing||run==null||c.player==null)return;
        long now=System.nanoTime();
        lastTickActivity=now;
        observedTicks++;
        if(lastTickNanos!=0){
            long gap=now-lastTickNanos;
            sumTickNanos+=gap;maxTickNanos=Math.max(maxTickNanos,gap);tickIntervals++;
        }
        lastTickNanos=now;
        if(lastHeartbeat!=0&&now-lastHeartbeat<1_000_000_000L)return;
        lastHeartbeat=now;
        Point point=feet(c);
        record("CLIENT_HEARTBEAT",Map.of("ticks",observedTicks,"point",point,
                "movedSinceSample",lastSampledFeet!=null&&!lastSampledFeet.equals(point),"planning",searchActive,
                "planAttempt",progressAttempt,"expanded",progressNodes,"lastProgressNanos",lastSearchActivity,
                "avgTickMs",tickIntervals==0?0:sumTickNanos/1e6/tickIntervals,"maxTickMs",maxTickNanos/1e6));
        lastSampledFeet=point;
        sumTickNanos=0;maxTickNanos=0;tickIntervals=0;
    }

    private void sampleThreads(){
        var bean=java.lang.management.ManagementFactory.getThreadMXBean();
        if(bean.isThreadCpuTimeSupported()&&!bean.isThreadCpuTimeEnabled())bean.setThreadCpuTimeEnabled(true);
        while(!Thread.currentThread().isInterrupted()){
            try {Thread.sleep(5000);}catch(InterruptedException stop){Thread.currentThread().interrupt();return;}
            if(run==null||!searchActive)continue;
            long now=System.nanoTime();
            var samples=new ArrayList<Map<String,Object>>();
            for(var info:bean.getThreadInfo(bean.getAllThreadIds(),18)){
                if(info==null||!(info.getThreadName().equals("Render thread")||info.getThreadName().startsWith("ug-path-planning")
                        ||info.getThreadName().startsWith("ug-path-candidate-")||info.getThreadName().equals("ug-path-opencl")))continue;
                samples.add(Map.of("name",info.getThreadName(),"state",info.getThreadState(),
                        "cpuNanos",bean.getThreadCpuTime(info.getThreadId()),
                        "stack",Arrays.stream(info.getStackTrace()).map(StackTraceElement::toString).toList()));
            }
            record("THREAD_SAMPLE",Map.of("planAttempt",progressAttempt,"expanded",progressNodes,
                    "tickSilenceMs",(now-lastTickActivity)/1e6,"workSilenceMs",(now-lastSearchActivity)/1e6,
                    "threads",samples));
        }
    }

    private synchronized void record(String event,Map<String,Object> data) {
        if(run==null)return;
        if(event.equals("PLANNING_WORKER_START")){
            searchActive=true;progressNodes=0;lastSearchActivity=System.nanoTime();
            progressAttempt=((Number)data.get("planAttempt")).intValue();
        }else if(event.equals("PLANNING_WORK_PROGRESS")){
            long count=((Number)data.get("totalExpanded")).longValue();
            if(count>progressNodes){progressNodes=count;lastSearchActivity=System.nanoTime();}
        }else if(event.equals("PLANNING_WORKER_END")||event.equals("PLANNING_WORKER_ERROR")||event.equals("CLIENT_END")){
            searchActive=false;
        }
        if(event.equals("CAST"))casts++;
        if(event.equals("POSITION_CONFIRMATION"))confirmed++;
        if(event.equals("REPLAN"))replans++;
        var row=new JsonObject();
        for(String field:List.of("runId","scenario","mode"))row.add(field,run.get(field));
        row.addProperty("monotonicNanos",System.nanoTime());row.addProperty("event",walkingSmoke?"WALK_SMOKE_"+event:event);row.addProperty("clientTrial",Integer.getInteger("ug.corridor.trial",1));row.add("data",gson.toJsonTree(data));
        try {Files.writeString(log,gson.toJson(row)+System.lineSeparator(),StandardOpenOption.APPEND);}
        catch(Exception e){throw new RuntimeException(e);}
        if(event.equals("CLIENT_END"))System.out.println(gson.toJson(row));
    }
}
