package com.kuangsanfeng.ultimategarden.client.function.path_finding.execution;

import com.kuangsanfeng.ultimategarden.Ultimate_Garden;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.log.JsonlWriter;
import static com.kuangsanfeng.ultimategarden.client.platform.persistence.log.JsonSnapshot.map;
import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import com.kuangsanfeng.ultimategarden.client.function.esp.adapters.PathRouteESP;
import com.kuangsanfeng.ultimategarden.client.platform.gui.PlayerGuiInput;
import com.kuangsanfeng.ultimategarden.client.platform.input.PhysicalGameInput;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.InstantTransmissionController;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing.ClientTerrain;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing.Terrain.Knowledge;
import com.kuangsanfeng.ultimategarden.client.function.mapping.MapRepository;
import com.kuangsanfeng.ultimategarden.client.function.mapping.MappingPaths;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.PathSettings;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.TransmissionModel;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.CorridorPlanner;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.terrain.MapTerrain;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController;
import com.kuangsanfeng.ultimategarden.client.platform.context.ClientContextEvents;
import com.kuangsanfeng.ultimategarden.client.platform.input.view.NativeViewInput;
import com.kuangsanfeng.ultimategarden.client.platform.input.virtual.VirtualKeyboard;
import com.kuangsanfeng.ultimategarden.client.platform.ui.hud.HudAdjustmentController;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.kuangsanfeng.ultimategarden.client.function.world.WorldInfoService;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.TargetCoordinates;
import com.kuangsanfeng.ultimategarden.client.platform.hint.HintManager;
import com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageManager;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.UncheckedIOException;
import java.util.Map;
import java.util.List;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.FlightTiming;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.RouteCastTiming;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.FlightTiming.*;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.CorridorRepair;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.CorridorRejoin;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.SearchAudit;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.terrain.LivePathTerrain;
import java.util.LinkedHashMap;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim.ShootoutAimClock;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim.ShootoutAimCorrection;
import com.kuangsanfeng.ultimategarden.client.platform.input.view.ViewAngles;
import java.util.function.BooleanSupplier;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.*;

/** Client-thread bridge for native game inputs and server position updates. */
public final class ClientPathFinding {
    private static final BiConsumer<String,Map<String,Object>> NO_EVENTS = (event,data) -> {};
    public static final ClientPathFinding INSTANCE=new ClientPathFinding();
    private final PathService service=new PathService();
    private final Object menuOwner=new Object();
    private MovementMeasurements measurements=new MovementMeasurements();
    private PathService.Ticket menuTicket;
    private NativeDriver active;
    private Running running;
    private RouteRequest relay;
    private NativeDriver sessionDriver;
    public com.kuangsanfeng.ultimategarden.client.function.esp.ESPGeometry scanOverlay(int ageBand){
        var driver=sessionDriver;
        return driver==null||service.stage()!=PathService.Stage.PLANNING?null:driver.display.scan(ageBand);
    }
    public com.kuangsanfeng.ultimategarden.client.function.esp.ESPGeometry calculationOverlay(boolean verified){
        var driver=sessionDriver;
        return driver==null?null:driver.display.actions(verified);
    }
    public Map<String,String> hudFields(){
        var fields=new LinkedHashMap<String,String>();
        var driver=sessionDriver;
        var c=Minecraft.getInstance();
        var stage=service.stage();
        if(driver!=null && running!=null && c.player!=null){
            var goal=running.request().goal();
            var progress=driver.searchProgress;
            String phase=stage==PathService.Stage.PLANNING&&driver.calculationActive?"ACTION_CALCULATION":stage.name();
            fields.put("status",driver.settings.mode+" / "+phase);
            fields.put("goal",goal.x()+" / "+goal.y()+" / "+goal.z());
            fields.put("stage_time",String.format(java.util.Locale.ROOT,"%.1f s",(System.nanoTime()-(phase.equals("ACTION_CALCULATION")?driver.calculationStarted:driver.stageStarted))/1e9));
            long expanded=((Number)progress.getOrDefault("totalExpanded",0)).longValue();
            long budget=(long)driver.settings.searchNodesPerSegment()*driver.settings.maximumSearchSegments();
            fields.put("search_budget",expanded+" / "+budget+" | Round "+progress.getOrDefault("segment","-"));
            var branches=driver.activeBranches;
            if(branches!=null)fields.put("active_branches",branches.toString());
            if(phase.equals("ACTION_CALCULATION")){
                fields.put("progress",progress.getOrDefault("candidateChecksAtOrigin","-")+" checks");
            }else if(stage==PathService.Stage.PLANNING)fields.put("progress",fields.get("search_budget"));
            if(driver.rehearsal!=null && stage==PathService.Stage.EXECUTING){
                fields.put("progress",String.format(java.util.Locale.ROOT,"%d / %d | %.1f blocks",
                        Math.min(driver.previewIndex+1,driver.rehearsalChoices()),driver.rehearsalChoices(),driver.position().distance(goal)));
            }
            if(driver.step!=null)fields.put("current_action",driver.step.movement().name());
            if(driver.castStatus!=null)fields.put("cast_status",driver.castStatus);
            if(driver.heightCorrection!=null)fields.put("height_offset",driver.heightCorrection.toString());
            if(driver.rejoinPending||driver.detour!=null)fields.put("recovery_status","Local Rejoin");
            String reason=driver.failureReason!=null?driver.failureReason:driver.castStatus;
            if(reason!=null)fields.put("reason",reason);
            if(driver.lastFault!=null)fields.put("last_failure",driver.lastFault);
        }else if(service.snapshot()!=null){
            fields.put("last_failure",service.snapshot().reason());
            fields.put("reason",service.snapshot().reason());
        }
        fields.put("input_status",com.kuangsanfeng.ultimategarden.client.platform.input.PhysicalGameInput.describe(c));
        return fields;
    }
    public String latencyStatus(){return sessionDriver==null?"UNAVAILABLE":sessionDriver.latency.status().name();}
    public Double latencyMilliseconds(){return sessionDriver==null?null:sessionDriver.latency.milliseconds();}
    private record MapContext(ClientLevel level,ClientPacketListener connection,String island,String instance,String area,String map,boolean trusted) {}
    private MapContext mapContext;
    private DetectedLocalMap detectedMap=new DetectedLocalMap(DetectedLocalMap.Status.UNKNOWN,null);

    public DetectedLocalMap detectedMap(){return detectedMap;}
    private void refreshMap(Minecraft client,boolean force){
        var info=WorldInfoService.snapshot();
        var context=new MapContext(client.level,client.getConnection(),info.island(),info.instance(),info.subArea(),info.map(),WorldInfoService.trustedLocation());
        if(!force && context.equals(mapContext))return;
        if(mapContext!=null && !context.equals(mapContext)){
            if(running!=null)service.contextChanged("LOCAL_MAP_CONTEXT_CHANGED");
            relay=null;
            candidate=null;
            PathRouteESP.clear();
        }
        mapContext=context;
        detectedMap=DetectedLocalMap.resolve(MappingPaths.root(client.gameDirectory.toPath()),info,context.trusted());
    }

    private record RouteRequest(Object owner, String map, Point goal, double distance,
                                BooleanSupplier equipped, TransmissionEquipment weapon,
                                BiConsumer<String,Map<String,Object>> events, Consumer<Result> completion,
                                ClientLevel level, ClientPacketListener connection, String island, String instance) {
        boolean matches(Minecraft c) {
            if (level!=c.level || connection!=c.getConnection()) return false;
            var world=WorldInfoService.snapshot();
            return java.util.Objects.equals(instance,world.instance())
                    && java.util.Objects.equals(island,world.island());
        }
    }

    private record Running(RouteRequest request, PathService.Ticket ticket) {}
    private long positionSequence;
    private Candidate candidate;
    private Point pendingInput;
    private PathChatInput chatScreen;
    private record Candidate(Point point, long started, long duration) {}
    public record Confirmation(Point point, long remainingMs, double fraction) {}

    public Confirmation confirmation() {
        var current=candidate;
        if (current==null) return null;
        long left=current.duration-(System.nanoTime()-current.started);
        if (left<=0) return null;
        return new Confirmation(current.point, (left+999_999)/1_000_000, (double)left/current.duration);
    }

    public void observeChat(Component message) {
        var c=Minecraft.getInstance();
        if (c.player==null || c.level==null || service.stage()!=PathService.Stage.IDLE || c.screen instanceof PathChatInput) return;
        String raw=message.getString();
        if (raw.contains("[UltimateGarden]")) return;
        TargetCoordinates.extractReceived(raw).ifPresent(point -> {
            long duration=Ultimate_GardenClient.getConfigManager().getPathSettings().coordinateWindowMs()*1_000_000L;
            candidate=new Candidate(point,System.nanoTime(),duration);
        });
    }

    public void startHotkey() {
        var c=Minecraft.getInstance();
        if (c.screen!=null || c.player==null || c.level==null || service.stage()!=PathService.Stage.IDLE || conflict()) return;
        if (relay != null) {
            RouteRequest saved=relay;
            if (saved.matches(c)) {
                try {
                    PathService.Ticket ticket=start(saved);
                    relay=saved;
                    candidate=null;
                    if (saved.owner()==menuOwner) menuTicket=ticket;
                    emitRelay(saved,"RELAY_RESUMED",Map.of("goal",saved.goal(),"actualStart",point(c.player.position()),
                            "yaw",c.player.getYRot(),"pitch",c.player.getXRot(),"velocity",point(c.player.getDeltaMovement()),
                            "grounded",c.player.onGround(),"pathBlocked",PhysicalGameInput.pathBlocked()));
                } catch (IllegalStateException failure) {hint("start_unavailable");}
                return;
            }
            relay=null;
        }
        var confirm=confirmation();
        candidate=null;
        if (confirm!=null) {
            startTarget(confirm.point());
            return;
        }
        chatScreen=new PathChatInput(value-> {
            pendingInput=TargetCoordinates.extract(value).orElse(null);
            if (pendingInput==null) hint("coordinate_error");
        });
        c.setScreen(chatScreen);
        hint("coordinate_prompt");
        hint("coordinate_local");
    }
    public void stopForPlayerGui(PlayerGuiInput.Kind kind) {
        var current=running;
        if (current==null) return;
        var c=Minecraft.getInstance();
        if (c.player==null || !current.request().matches(c)) {
            service.cancel(current.ticket(),"PLAYER_GUI_CONTEXT_CHANGED");
            return;
        }
        var request=current.request();
        relay=request;
        candidate=null;
        var fields=Map.<String,Object>of("goal",request.goal(),"actualFeet",point(c.player.position()),
                "yaw",c.player.getYRot(),"pitch",c.player.getXRot(),"screen",kind.name(),
                "pathBlocked",PhysicalGameInput.pathBlocked(),"focusBlocked",PhysicalGameInput.focusReleased());
        service.cancel(current.ticket(),"PLAYER_GUI_"+kind.name());
        emitRelay(request,"RELAY_STOPPED",fields);
    }

    private static void emitRelay(RouteRequest request,String event,Map<String,Object> fields) {
        Ultimate_Garden.LOGGER.info("Path {} {}",event,fields);
        request.events().accept(event,fields);
    }

    private void startTarget(Point goal) {
        var c=Minecraft.getInstance();
        refreshMap(c,true);
        if(detectedMap.status()!=DetectedLocalMap.Status.FOUND){hint("local_map_"+detectedMap.status().name().toLowerCase(java.util.Locale.ROOT));return;}
        String world=detectedMap.name();
        var settings=Ultimate_GardenClient.getConfigManager().getPathSettings();
        var weapon=settings.transmission?TransmissionEquipment.select(c.player.getInventory()).orElse(null):null;
        if (weapon==null) hint(settings.transmission?"no_weapon":"walking_only");
        try {
            menuTicket=start(menuOwner,world,goal,weapon==null?12:weapon.range(),
                    () -> weapon!=null && equipped(c,weapon),weapon,NO_EVENTS,result->{});
        } catch (IllegalStateException failure) {hint("start_unavailable");}
    }
    private static boolean equipped(Minecraft c, TransmissionEquipment weapon) {
        if (c.player==null) return false;
        var inv=c.player.getInventory();
        return inv.getSelectedSlot()==weapon.slot() && TransmissionEquipment.read(weapon.slot(),c.player.getMainHandItem()).filter(weapon::equals).isPresent();
    }
    private static void hint(String key) {
        HintManager.printFor("CropSelect",new LanguageManager(Ultimate_GardenClient.getConfigManager()).text("ui.ultimategarden.path_finding."+key));
    }
    private Point lastPositionUpdate;

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            refreshMap(c,false);
            if (candidate!=null && confirmation()==null) candidate=null;
            if (relay!=null && !relay.matches(c)) relay=null;
            if (running!=null && !running.request().matches(c)) service.contextChanged("WORLD_CONTEXT_CHANGED");
            if (chatScreen!=null && c.screen!=chatScreen) chatScreen=null;
            if (pendingInput!=null && c.screen==null && c.mouseHandler.isMouseGrabbed()) {
                Point goal=pendingInput;
                pendingInput=null;
                if (c.player!=null && c.level!=null) startTarget(goal);
            }
            if(sessionDriver!=null)sessionDriver.sessionTick();
            service.tick();
        });
        ClientContextEvents.onDisconnect((handler,client)->{service.contextChanged("DISCONNECTED");clear();});
        ClientContextEvents.onChange(event->{service.contextChanged(event.change().name());clear();});
        ClientContextEvents.onStopping(client->{service.close();clear();});
    }
    private void clear() {
        mapContext=null;
        detectedMap=new DetectedLocalMap(DetectedLocalMap.Status.UNKNOWN,null);
        candidate=null;
        pendingInput=null;
        relay=null;
        PathRouteESP.clear();
        var c=Minecraft.getInstance();
        if (chatScreen!=null && c.screen==chatScreen) c.setScreen(null);
        chatScreen=null;
        measurements=new MovementMeasurements();
        menuTicket=null;
        positionSequence=0;
        lastPositionUpdate=null;
    }
    public void positionUpdate(Vec3 position) {
        positionSequence++;
        lastPositionUpdate=point(position);
        if(active!=null)active.packetApplied(lastPositionUpdate);
    }
    public void aimFrame() {
        if (active!=null) active.frame();
    }
    public boolean observingPosition() { return active!=null; }
    public PathService service() { return service; }
    public String status() {
        var result=service.snapshot();
        return service.stage().name()+(result==null?"":" / "+result.reason());
    }
    public boolean menuTaskActive() { return menuTicket!=null && service.stage()!=PathService.Stage.IDLE; }
    public void cancelMenuTask() { if (menuTaskActive()) service.cancel(menuTicket); }

    private PathService.Ticket start(Object owner,String world,Point goal,double distance,
                                     BooleanSupplier equipped,TransmissionEquipment weapon,BiConsumer<String,Map<String,Object>> events,
                                     Consumer<Result> completion) {
        var c=Minecraft.getInstance();
        var info=WorldInfoService.snapshot();
        return start(new RouteRequest(owner,world,goal,distance,equipped,weapon,events,completion,
                c.level,c.getConnection(),info.island(),info.instance()));
    }

    private PathService.Ticket start(RouteRequest request) {
        var c=Minecraft.getInstance();
        if (!c.isSameThread() || c.player==null || c.level==null || service.stage()!=PathService.Stage.IDLE || conflict())
            throw new IllegalStateException("Input unavailable");

        PathSettings settings=Ultimate_GardenClient.getConfigManager().getPathSettings();
        boolean transmission=settings.transmission && (request.weapon()!=null || request.equipped().getAsBoolean());
        var path=MappingPaths.root(c.gameDirectory.toPath());
        var options=new Options(settings.mode,transmission,true,request.distance(),settings.jumpDetection?measurements.jump():1,
                settings.searchNodesPerSegment(),settings.weights);
        var timing=new CorridorPlanner.Timing(settings.teleportCooldownMs(),settings.speedDetection?measurements.speed():4.3,settings.aim(PathSettings.Aim.SPEED),50);
        var driver=new NativeDriver(c,settings,request.distance(),request.equipped(),request.weapon(),transmission,request.events(),request.map());
        PhysicalGameInput.beginPath(c,driver);
        PathService.Ticket ticket;
        try {
            ticket=service.start(request.owner(),point(c.player.position()),request.goal(),c.player.getYRot(),c.player.getXRot(),options,
                ()->{try{return new MapTerrain(new MapRepository(path,request.map()));}catch(java.io.IOException e){throw new UncheckedIOException(e);}},
                timing,driver,result->{
                    running=null;
                    if(sessionDriver==driver)sessionDriver=null;
                    if (result.status()==Status.ARRIVED) relay=null;
                    driver.finishInput(result);
                    request.completion().accept(result);
                });
        } catch (RuntimeException | Error failure) {
            PhysicalGameInput.endPath(driver);
            driver.closeSession();
            throw failure;
        }
        sessionDriver=driver;
        running=new Running(request,ticket);
        relay=null;
        return ticket;
    }
    private static boolean conflict() {
        var farm=Ultimate_GardenClient.getFarmController();
        var input=farm==null?null:farm.inputOwnership();
        return (input!=null&&(input.running()||input.heldInput()||input.actionPending()))
                || InstantTransmissionController.INSTANCE.running() || ZombieShootoutController.INSTANCE.enabled() || HudAdjustmentController.isActive();
    }
    private static Point point(Vec3 p){return new Point(p.x,p.y,p.z);}

    private final class NativeDriver implements PathService.Driver {
        private final Minecraft client;
        private final Object world, player;
        private final VirtualKeyboard keyboard;
        private final NativeViewInput mouse=new NativeViewInput();
        private final ShootoutAimClock aimClock=new ShootoutAimClock();
        private final ShootoutAimCorrection correction=new ShootoutAimCorrection();
        private boolean aimActive, aimReady;
        private double aimYaw, aimPitch, startAimY, alignedY;
        private long aimStarted, alignedAt;
        private boolean landingNeedsTurn;
        private boolean downwardCast;
        private record DescentSample(CorridorRepair.Start start,long tick,long sequence) {}
        private DescentSample descentSample;
        private final double aimDraw;
        private Double heightCorrection;
        private int correctionHops,plannedCastAfter,consecutiveClimbs;
        private boolean upwardTurnUsed;
        private long actionStartTick;
        private boolean normalWeighted;
        private final PathFailureTrace faultTrace;
        private JsonlWriter failureWriter;
        private final LocalCandidateFallback fallback=new LocalCandidateFallback();
        private long packetTick;
        private int estimatedAimAfter;
        private boolean aimWithinTolerance,actionWasLate;
        private volatile Map<String,Object> lastPlanningSummary=Map.of();
        private CorridorRepair.Rehearsal rehearsal;
        private PathRouteESP.Prepared overlay;
        private CorridorRepair.Choice choice, rejected;
        private int previewIndex, legIndex, localRetries, settleWait;
        private final java.util.Set<Point> repositioned=new java.util.HashSet<>();
        private double routeProgress, pendingProgress;
        private CorridorRejoin.Search detour;
        private java.util.concurrent.Future<CorridorRejoin.Search> detourPlanning;
        private Point detourPlanningOrigin;
        private int detourStep, rejoinWait, rejoinAttempts;
        private Point rejoinOrigin;
        private double totalYaw, totalPitch;
        private final java.util.Set<Point> triedEntrances=new java.util.HashSet<>();
        private PathService.CorridorContinuation continuation;
        private boolean rejoinPending;
        private long choiceTick;
        private Segment lastActualCast;
        private TerminalMovement terminalMovement=null;
        private enum TerminalMovement { WALK, JUMP, TRANSMISSION, JUMP_ASSIST, FALL }
        private final CastGate gate=new CastGate();
        private final PathSettings settings;
        private final double range;
        private final BooleanSupplier equipped;
        private final TransmissionEquipment weapon;
        private final boolean transmission;
        private boolean selectingWeapon;
        private int weaponWait;
        private final BiConsumer<String,Map<String,Object>> events;
        private Segment step;
        private Point previous, predicted;
        private long previousPositionSequence;
        private long confirmationSequence, dispatchTime;
        private double confirmedTravel, pendingDistance;
        private int ticks;
        private int settleTicks;
        private boolean failed, jumpPressed, seenAir, done, mustGround;
        private String failureReason;
        private enum Prep { FIND_SPEED, SPEED, FIND_JUMP, WALK_TO_JUMP, JUMP, COMPLETE }
        private Prep prep=Prep.FIND_SPEED;
        private Point probe;
        private long probeSequence;
        private int probeTicks;
        NativeDriver(Minecraft c,PathSettings settings,double range,BooleanSupplier equipped,TransmissionEquipment weapon,boolean transmission,BiConsumer<String,Map<String,Object>> events,String mapId) {
            client=c;world=c.level;player=c.player;keyboard=new VirtualKeyboard(c);this.settings=settings;
            this.range=range;this.equipped=equipped;this.events=events;
            this.weapon=weapon;this.transmission=transmission;selectingWeapon=weapon!=null;
            aimDraw=Math.random();
            faultTrace=!settings.pathfindingLog()?null:new PathFailureTrace(settings.mode.name(),mapId,this::writeFailure);
        }
        private volatile Map<String,Object> searchProgress=Map.of();
        private volatile int hudAttempt=-1;
        private volatile Number activeBranches;
        private long stageStarted=System.nanoTime();
        private PathService.Stage lastStage;
        private String castStatus,lastFault;
        private double aimLastElapsed,aimLastMovement;
        private final com.kuangsanfeng.ultimategarden.client.function.esp.adapters.PathSearchESP display=
                new com.kuangsanfeng.ultimategarden.client.function.esp.adapters.PathSearchESP(
                        ()->Ultimate_GardenClient.getConfigManager().getESPConfig().pathScan(),
                        ()->Ultimate_GardenClient.getConfigManager().getESPConfig().pathCalculation());
        private final PathLatency latency=new PathLatency(com.kuangsanfeng.ultimategarden.client.platform.network.ClientRttConnection.pathService());
        private volatile int calculationAttempt=-1;
        private volatile boolean logClosed;
        private volatile long calculationStarted;
        private volatile boolean calculationActive;
        private void sessionTick(){
            var connection=client.getConnection();
            long now=System.nanoTime();
            var stage=INSTANCE.service.stage();
            if(stage!=lastStage){lastStage=stage;stageStarted=now;}
            latency.tick(connection,now,calculationAttempt,
                    com.kuangsanfeng.ultimategarden.client.platform.network.ClientRttConnection.sender(connection),
                    (phase,at)->{
                        if(logging())trace("LATENCY_REQUEST","END_CLIENT_TICK",map("stage",phase,"source","NATIVE_PING_PONG","requestNanos",at));
                    });
            if(latency.newResult()&&logging())trace("LATENCY_STATUS","END_CLIENT_TICK",
                    map("stage",latency.phase(),"source","NATIVE_PING_PONG","status",latency.status(),"milliseconds",latency.milliseconds()));
        }
        private void closeSession(){latency.close();if(failureWriter!=null)failureWriter.close();}
        private boolean logging(){
            if(faultTrace==null)return false;
            if(!Ultimate_GardenClient.getConfigManager().getPathSettings().pathfindingLog()&&!logClosed){
                logClosed=true;
                if(failureWriter!=null)failureWriter.close();
            }
            return faultTrace!=null&&!logClosed;
        }

        private void trace(String event,String phase,Map<String,Object> data){
            if(logging()&&client.level==world)
                faultTrace.add(event,phase,client.level.getGameTime(),positionSequence,data);
            else if(logging()&&event.equals("TASK_END"))
                faultTrace.add(event,phase,-1,positionSequence,data);
        }

        private void exportFailure(String reason){
            if(logging())faultTrace.export(reason);
        }

        private void writeFailure(Map<String,Object> packet){
            if(failureWriter==null){
                var root=client.gameDirectory.toPath().resolve("logs").resolve("path-finding");
                failureWriter=new JsonlWriter(root,(String)packet.get("runId"),true,writer->{
                    if(writer.error!=null)Ultimate_Garden.LOGGER.warn("Path failure journal {}",writer.error);
                });
            }
            if(packet.get("reason") instanceof String reason&&(reason.startsWith("ARRIVED:")||reason.startsWith("CANCELLED:"))){
                failureWriter.endBatch("failures.jsonl",packet);
            }else if(!failureWriter.offer("failures.jsonl",packet))
                Ultimate_Garden.LOGGER.warn("Path failure window dropped {}",packet.get("runId"));
            if(events!=NO_EVENTS)events.accept("PATH_FAILURE_WINDOW",packet);
        }

        private void packetApplied(Point feet){
            if(logging()&&client.level==world){
                packetTick=client.level.getGameTime();
                trace("PACKET_APPLIED","POSITION_PACKET_TAIL",Map.of("state",forecast(),"packetFeet",feet));
            }
        }

        public Point position(){return point(client.player.position());}
        public boolean usable(){return client.level==world && client.player==player && client.player.isAlive() && !conflict();}
        public void begin(Segment action) {
            latency.beginExecution(System.nanoTime());
            step=action;active=this;failed=jumpPressed=seenAir=done=false;ticks=0;previous=position();predicted=null;
            failureReason=null;
            descentSample=null;
            actionStartTick=client.level.getGameTime();
            plannedCastAfter=0;
            estimatedAimAfter=0;
            aimWithinTolerance=false;
            actionWasLate=false;
            if(logging())faultTrace.begin();
            castStatus=null;
            if(display.calculating()&&choice!=null)display.choice(choice,true);
            if(normalWeighted&&action.movement()==Movement.TRANSMISSION){
                var initial=forecast();
                var estimate=RouteCastTiming.estimate(initial.yaw(),initial.pitch(),action.yaw(),action.pitch(),initial.aim());
                estimatedAimAfter=detour==null&&choice!=null&&legIndex==0?RouteCastTiming.aimTick(choice.aimMs()):
                        estimate==null?-1:RouteCastTiming.aimTick(estimate.ms());
                plannedCastAfter=detour==null&&choice!=null&&legIndex==0?choice.castTick():
                        estimate==null?0:RouteCastTiming.castTick(initial.ready(),estimate.ms(),heightCorrection!=null);
            }
            mustGround=action.movement()==Movement.FALL && sameVerticalColumn(action)
                    && new ClientTerrain(client.level).descent(action.to(),action.to()).knowledge()==Knowledge.CLEAR;
            previousPositionSequence=positionSequence;
            PathRouteESP.next(action,choice,legIndex,previewIndex,routeProgress);
            var frame=PathRouteESP.snapshot();
            var fields=new LinkedHashMap<String,Object>();
            fields.put("action",action);
            fields.put("actualFeet",previous);
            fields.put("yaw",yaw());
            fields.put("pitch",pitch());
            if (frame!=null && frame.guide()!=null) fields.put("guide",frame.guide());
            events.accept("ACTION_START",fields);
            if(logging())trace("ACTION_SELECTED","END_CLIENT_TICK_AFTER_PHYSICS",map(
                    "state",forecast(),"action",action,"choice",choice,"progress",routeProgress,
                    "startTick",actionStartTick,"expectedAimTick",estimatedAimAfter<0?null:actionStartTick+estimatedAimAfter,
                    "expectedCastTick",actionStartTick+plannedCastAfter,"mode",heightCorrection==null?"N_PLUS_ONE":"N",
                    "timingReason",heightCorrection==null?"NORMAL_FOLLOW":"HEIGHT_CORRECTION",
                    "forecastOriginStage","CONFIRMATION_CONSUMED","plannedPhysicsSteps",plannedCastAfter,
                    "matchedRehearsal",previewIndex<rehearsalChoices()?rehearsal.choices().get(previewIndex):null,
                    "matchedRehearsalInput",previewIndex<rehearsalInputs()?rehearsal.actionInputs().get(previewIndex):null));
        }
        private int rehearsalChoices(){return rehearsal==null?0:rehearsal.choices().size();}
        private int rehearsalInputs(){return rehearsal==null?0:rehearsal.actionInputs().size();}
        public void planReady(Result result) {
            trace("EXECUTABLE_PLAN","PLANNING_COMPLETED",Map.of("expanded",result.explored(),"completeCandidatesAtLeast",1,
                    "actions",result.segments().size(),"farthest",result.end(),"reason",result.reason()));
            events.accept("EXECUTABLE_PLAN",Map.of("steps",result.segments().size(),"cost",result.cost(),"expanded",result.explored(),"reason",result.reason()));
        }
        private Motion motion() {
            var velocity=client.player.getDeltaMovement();
            return new Motion(position(),velocity.x,velocity.y,velocity.z,client.player.onGround());
        }
        public CorridorRepair.Start forecast() {
            var aim=new Aim(settings.aim(PathSettings.Aim.SPEED),settings.aim(PathSettings.Aim.CURVE),
                    (int)settings.aim(PathSettings.Aim.RATE),settings.aim(PathSettings.Aim.TOLERANCE),settings.aim(PathSettings.Aim.CORRECTION),
                    client.options.sensitivity().get(),client.options.invertMouseX().get(),client.options.invertMouseY().get(),aimDraw);
            return new CorridorRepair.Start(motion(),yaw(),pitch(),confirmedTravel,aim,
                    new Ready(gate.remainingMs(System.nanoTime(),settings.teleportCooldownMs()),0,0),
                    heightCorrection,consecutiveClimbs,upwardTurnUsed);
        }
        @Override public void rehearsalStarting(CorridorRepair.Start initial,int limit){
            if(!calculationActive){
                calculationActive=true;
                calculationStarted=System.nanoTime();
                calculationAttempt++;
            }
            display.calculation();
            trace("REHEARSAL_INPUT","END_CLIENT_TICK_SNAPSHOT",Map.of("state",initial,"limit",limit));
            events.accept("REHEARSAL_INPUT",Map.of("state",initial,"limit",limit));
        }
        @Override public void prepareRehearsalOverlay(CorridorRepair.Rehearsal result,BooleanSupplier cancelled) {
            overlay=PathRouteESP.prepare(result,cancelled);
        }
        public void rehearsalReady(CorridorRepair.Rehearsal result) {
            rehearsal=result;
            if(display.calculating())result.choices().stream().limit(12).forEach(candidate->display.choice(candidate,true));
            fallback.reset();
            normalWeighted=!result.corridor().regional();
            if(!rejoinPending){heightCorrection=null;correctionHops=consecutiveClimbs=0;upwardTurnUsed=false;}
            PathRouteESP.show(overlay);
            choice=rejected=null;
            previewIndex=legIndex=localRetries=settleWait=0;
            repositioned.clear();
            routeProgress=result.initialProgress();
            rejoinWait=0;
            detour=null;
            detourPlanning=null;
            if(rejoinPending) {
                events.accept("REJOIN_REHEARSAL_RESULT",Map.of("actual",position(),"startProgress",routeProgress,
                        "finalProgress",result.progress(),"status",result.reason(),"expanded",result.expanded(),
                        "remaining",result.open(),"blockedAt",result.blockedAt()));
                events.accept(result.complete()?"REJOIN_NAV_SUCCESS":"REJOIN_NAV_FAILED",
                        Map.of("actual",position(),"progress",routeProgress,"reason",result.reason()));
                rejoinPending=false;
            }else {triedEntrances.clear();rejoinAttempts=0;}
            step=lastActualCast=null;
            failed=false;
            terminalMovement=null;
            var rehearsalStatus=map("status",result.reason(),"expanded",result.expanded(),"open",result.open(),
                    "completeCandidates",result.complete()?1:0,"blockedAt",result.blockedAt(),"progress",result.progress(),
                    "deadEnds",result.deadEnds(),"blockingSample",result.lastGeometryCheck());
            trace("REHEARSAL_END","REHEARSAL_COMPLETED",rehearsalStatus);
            if(!result.complete())exportFailure("REHEARSAL_"+result.reason());
            events.accept("REHEARSAL_END",Map.of("status",result.reason(),"verifiedChoices",result.choices().size(),"open",result.open(),"progress",result.progress(),
                    "deadEnds",result.deadEnds(),"lastGeometryCheck",result.lastGeometryCheck(),
                    "end",result.blockedAt(),"expanded",result.expanded(),"aim",forecast().aim()));
            if(events==NO_EVENTS)return;
            int rehearsalIndex=0;
            for(var proposed:result.choices()) {
                var original=result.corridor().reference(proposed.reference());
                var row=new LinkedHashMap<String,Object>();
                row.put("reference",proposed.reference());row.put("originalY",original.from().y());
                row.put("rehearsedY",proposed.castOrigin()==null?proposed.actions().getFirst().from().y():proposed.castOrigin().feet().y());
                row.put("aimMs",proposed.aimMs());row.put("castTickAfterAimStart",proposed.castTick());row.put("reason",proposed.reason());
                row.put("steps",proposed.actions());row.put("jumpAssist",proposed.jumpAssist());
                if(!result.corridor().regional()){
                    row.put("forecast",proposed.forecast());row.put("castOrigin",proposed.castOrigin());
                    if(rehearsalIndex<result.actionInputs().size())row.put("input",result.actionInputs().get(rehearsalIndex));
                }
                rehearsalIndex++;
                events.accept("REHEARSAL_ACTION",row);
            }
        }
        public boolean followsCorridor() {return rehearsal!=null&&rehearsal.complete();}
        public boolean advanceCorridor() {
            int previousChoice=previewIndex;
            double previousProgress=routeProgress;
            boolean reached=advanceAction();
            // Packet confirmation can start the next aim on this same END_CLIENT_TICK.
            if(!reached&&!failed&&continuation==null&&detour==null&&step==null&&(previewIndex!=previousChoice||routeProgress>previousProgress))
                return advanceAction();
            return reached;
        }
        private boolean advanceAction() {
            var corridor=rehearsal.corridor();
            if (step==null && corridor.reached(motion(),lastActualCast,new LivePathTerrain(client.level))) {
                events.accept("TERMINAL_ARRIVAL",Map.of("actual",position(),"target",corridor.goal(),"movement",
                        terminalMovement==null?"ALREADY_AT_TARGET":terminalMovement.name()));
                return true;
            }
            if(client.screen!=null||client.isPaused()||!client.isWindowActive()||!client.mouseHandler.isMouseGrabbed()){
                keyboard.releaseAll();
                return false;
            }
            if(detourPlanning!=null){pollDetour(corridor);return false;}
            if(detour!=null){advanceDetour(corridor);return false;}
            if(choice==null) {
                var terrain=new LivePathTerrain(client.level);
                var initial=forecast();
                if(!initial.motion().grounded() && settleWait<8 && corridor.isTerminalLanding(position(),terrain)){
                    settleWait++;
                    return false;
                }
                if(settleWait>0 && settleWait<20 && !initial.motion().grounded()){
                    settleWait++;
                    return false;
                }
                var hint=previewIndex<rehearsal.choices().size()?rehearsal.choices().get(previewIndex):null;
                if(hint!=null&&rejected==null && !corridor.terminalZone(position(),routeProgress))
                    choice=corridor.retime(hint,initial,routeProgress,terrain);
                if(choice==null || sameCast(choice,rejected)) {
                    choice=corridor.choices(initial,routeProgress,terrain,7,()->false).stream()
                            .filter(c->!sameCast(c,rejected))
                            .filter(c->!c.reason().equals("ADJUST_LAUNCH_POSITION")||
                                    repositioned.size()<4&&!repositioned.contains(c.last().to()))
                            .findFirst().orElse(null);
                }
                if(choice==null && hint!=null && rejected==null)
                    choice=corridor.retime(hint,initial,routeProgress,terrain);
                if(choice==null){
                    if(settleWait==0 && corridor.canSettle(initial.motion(),routeProgress,terrain)){
                        settleWait=1;
                        events.accept("CORRIDOR_WAIT_FOR_SUPPORT",Map.of("actual",position(),"progress",routeProgress));
                        return false;
                    }
                    events.accept("CORRIDOR_BLOCKED",Map.of("actual",position(),"progress",routeProgress,"segment",
                            corridor.referenceIndex(routeProgress),"reason","NO_CONNECTED_LOCAL_ACTION","motion",initial.motion(),
                            "aimYaw",yaw(),"aimPitch",pitch(),"confirmedTravel",confirmedTravel,"previewIndex",previewIndex,
                            "repositioned",List.copyOf(repositioned)));
                    if(!initial.motion().grounded() && rejoinWait++<24)return false;
                    if(!startDetour(corridor,initial))fail("NO_CONNECTED_LOCAL_ACTION");
                    return false;
                }
                legIndex=0;
                settleWait=0;
                choiceTick=client.level.getGameTime();
                if(corridor.regional()){
                    var data=new LinkedHashMap<String,Object>();
                    data.put("mode","CORRIDOR");data.put("actual",position());
                    data.put("yaw",yaw());data.put("pitch",pitch());
                    data.put("radius",corridor.radius());data.put("arcProgress",corridor.arc(routeProgress));
                    data.put("centerlineDistance",corridor.centerlineDistance(position()));
                    data.put("referenceTargeted",false);data.put("reason",choice.reason());
                    data.put("turn",CorridorRepair.rotationCost(initial,choice.actions().getFirst()));
                    data.put("nextArcProgress",corridor.arc(choice.progress()));
                    data.put("continuationRehearsed",hint!=null&&choice.reason().equals("REHEARSED_ANGLE"));
                    events.accept("CORRIDOR_REGION_ACTION",data);
                }
                if(choice.reason().equals("UPWARD_TURN_ASSIST"))
                    events.accept("UPWARD_TURN_SELECTED",Map.of("actual",position(),"direct","UNSTABLE_OR_BLOCKED",
                            "actions",choice.actions(),"extraCasts",1,"plannedFirstCastTick",choice.castTick()));
                events.accept("CORRIDOR_ACTION",Map.of("segment",choice.reference(),"progress",routeProgress,"nextProgress",choice.progress(),
                        "reason",choice.reason(),"actual",position(),"aimMs",choice.aimMs(),"startTick",choiceTick,
                        "expectedCastTick",choiceTick+choice.castTick(),"actions",choice.actions()));
                begin(choice.actions().getFirst());
            }
            boolean actionDone=completed();
            if(failed){
                if((corridor.regional()||heightCorrection!=null)&&!failureReason().equals("TELEPORT_TRANSPORT_OR_WEAPON_UNAVAILABLE"))
                    recoverRegion(corridor,failureReason());
                return false;
            }
            if(!actionDone)return false;
            if(step.movement()!=Movement.TRANSMISSION&&!accepts(step)) {
                if(corridor.regional()||heightCorrection!=null)recoverRegion(corridor,"CORRIDOR_MOVEMENT_DIVERGED");
                else fail("CORRIDOR_MOVEMENT_DIVERGED");
                return false;
            }
            var movement=step.movement();
            if(logging()&&movement!=Movement.TRANSMISSION){
                trace("MOVEMENT_ACCEPTED","END_CLIENT_TICK_AFTER_PHYSICS",Map.of("action",step,"state",forecast()));
                faultTrace.fallbackOutcome("FALLBACK_MOVEMENT_ACCEPTED");
            }
            terminalMovement=choice.jumpAssist()?TerminalMovement.JUMP_ASSIST:TerminalMovement.valueOf(movement.name());
            release();
            step=null;
            if(++legIndex<choice.actions().size()){
                begin(choice.actions().get(legIndex));
                return false;
            }
            double verified=movement==Movement.TRANSMISSION?pendingProgress:
                    corridor.advanceTo(position(),routeProgress,new LivePathTerrain(client.level));
            if(verified>routeProgress) {
                if(verified>=routeProgress+1 && Math.floor(verified)>Math.floor(routeProgress)){triedEntrances.clear();rejoinAttempts=0;}
                routeProgress=verified;
                rejoinWait=0;
                repositioned.clear();
            } else if(corridor.reached(motion(),lastActualCast,new LivePathTerrain(client.level)))routeProgress=choice.progress();
            else if(choice.reason().equals("ADJUST_LAUNCH_POSITION")&&corridor.connected(position(),routeProgress,new LivePathTerrain(client.level)))
                repositioned.add(choice.last().to());
            else {
                if(corridor.regional()||heightCorrection!=null)recoverRegion(corridor,"CORRIDOR_MOVEMENT_DID_NOT_REJOIN");
                else fail("CORRIDOR_MOVEMENT_DID_NOT_REJOIN");
                return false;
            }
            if(normalWeighted){
                boolean climbing=movement==Movement.TRANSMISSION&&lastActualCast!=null
                        &&lastActualCast.to().y()-lastActualCast.from().y()>=1&&lastActualCast.pitch()<=-30;
                consecutiveClimbs=climbing?consecutiveClimbs+1:0;
                if(!climbing)upwardTurnUsed=false;
                if(movement==Movement.TRANSMISSION)updateHeightCorrection(corridor);
            }
            choice=rejected=null;
            localRetries=0;
            fallback.reset();
            while(previewIndex<rehearsal.choices().size()&&rehearsal.choices().get(previewIndex).progress()<=routeProgress+.01)previewIndex++;
            PathRouteESP.advance(previewIndex,routeProgress);
            return false;
        }
        private void updateHeightCorrection(CorridorRepair corridor){
            double original=corridor.originalHeight(routeProgress);
            double delta=lastPositionUpdate.y()-original;
            if(heightCorrection==null&&delta>=-.35)return;
            if(heightCorrection!=null)correctionHops++;
            var terrain=new LivePathTerrain(client.level);
            if(heightCorrection!=null&&corridor.resumesOriginal(forecast(),routeProgress,terrain)){
                events.accept("HEIGHT_CORRECTION_REJOINED",Map.of("actual",lastPositionUpdate,"referenceY",original,
                        "progress",routeProgress,"deltaY",delta,"correctionHops",correctionHops,"nextAimExtraTicks",1));
                heightCorrection=null;
                correctionHops=0;
            }else{
                heightCorrection=Math.min(0,delta);
                events.accept("HEIGHT_CORRECTION",Map.of("actual",lastPositionUpdate,"referenceY",original,
                        "progress",routeProgress,"deltaY",delta,"correctionHops",correctionHops,"nextAimExtraTicks",0));
            }
        }

        private void recoverRegion(CorridorRepair corridor,String reason){
            release();
            step=null;
            choice=rejected=null;
            failed=false;
            if(!startDetour(corridor,forecast()))fail(reason);
        }

        private boolean startDetour(CorridorRepair corridor,CorridorRepair.Start start){
            if(rejoinAttempts++>=3){
                trace("REJOIN_NAV_FAILED","END_CLIENT_TICK",Map.of("state",start,"reason","LOCAL_ATTEMPT_LIMIT"));
                exportFailure("REJOIN_NAV_FAILED");
                events.accept("REJOIN_NAV_FAILED",Map.of("actual",position(),"reason","LOCAL_ATTEMPT_LIMIT"));
                return false;
            }
            trace("REJOIN_NAV_START","END_CLIENT_TICK",Map.of("state",start,"progress",routeProgress,
                    "attempt",rejoinAttempts,"decision","SCHEDULE_LOCAL_REJOIN"));
            if(logging())exportFailure("LOCAL_REJOIN_STARTED");
            events.accept("REJOIN_NAV_START",Map.of("actual",position(),"yaw",yaw(),"pitch",pitch(),"progress",routeProgress,
                    "radius",corridor.radius(),"reason",corridor.centerlineDistance(position())>corridor.radius()
                            ?"OUTSIDE_CORRIDOR":"NO_CONNECTED_LOCAL_ACTION"));
            release();
            step=null;
            choice=rejected=null;
            detourPlanningOrigin=start.motion().feet();
            detourPlanning=service.planRejoin(corridor,start,routeProgress,triedEntrances);
            return true;
        }

        private void pollDetour(CorridorRepair corridor){
            if(!detourPlanning.isDone())return;
            CorridorRejoin.Search found;
            try{
                found=detourPlanning.get();
            }catch(InterruptedException e){
                Thread.currentThread().interrupt();
                fail("REJOIN_PLANNING_INTERRUPTED");
                return;
            }catch(java.util.concurrent.ExecutionException|java.util.concurrent.CancellationException e){
                trace("REJOIN_NAV_FAILED","END_CLIENT_TICK",map("state",forecast(),"reason",e.toString()));
                exportFailure("REJOIN_NAV_FAILED");
                events.accept("REJOIN_NAV_FAILED",Map.of("actual",position(),"reason",e.toString()));
                fail("REJOIN_PLANNING_FAILED");
                return;
            }finally{
                detourPlanning=null;
            }
            if(position().distance(detourPlanningOrigin)>.75){
                trace("REJOIN_NAV_FAILED","END_CLIENT_TICK",map("state",forecast(),"reason","START_MOVED_DURING_SEARCH"));
                exportFailure("REJOIN_NAV_FAILED");
                events.accept("REJOIN_NAV_FAILED",Map.of("actual",position(),"reason","START_MOVED_DURING_SEARCH"));
                if(!startDetour(corridor,forecast()))fail("REJOIN_START_MOVED");
                return;
            }
            trace("REJOIN_NAV_PLAN","END_CLIENT_TICK",Map.of("state",forecast(),"expanded",found.expanded(),
                    "open",found.open(),"reason",found.reason(),"selected",found.selected()!=null));
            if(found.selected()==null)exportFailure("LOCAL_REJOIN_FAILED");
            events.accept("REJOIN_NAV_PLAN",Map.of("actual",position(),"progress",routeProgress,"expanded",found.expanded(),
                    "remaining",found.open(),"reason",found.reason(),"actions",found.actions()));
            if(found.selected()==null){
                trace("REJOIN_NAV_FAILED","END_CLIENT_TICK",map("state",forecast(),"reason",found.reason(),"expanded",found.expanded()));
                exportFailure("REJOIN_NAV_FAILED");
                events.accept("REJOIN_NAV_FAILED",Map.of("actual",position(),"reason",found.reason()));
                fail(found.reason());
                return;
            }
            triedEntrances.add(found.selected().point());
            release();
            choice=rejected=null;
            detour=found;
            detourStep=0;
            rejoinOrigin=position();
            beginDetourAction();
        }

        private void beginDetourAction(){
            var action=detour.actions().get(detourStep);
            if(!CorridorRejoin.validateMotion(action,forecast(),new LivePathTerrain(client.level),rehearsal.corridor().jumpHeight())){
                rejectDetour("REJOIN_ACTION_GEOMETRY_CHANGED");
                return;
            }
            choiceTick=client.level.getGameTime();
            events.accept("REJOIN_NAV_ACTION",Map.of("index",detourStep,"actual",position(),"action",action));
            begin(action);
        }

        private void advanceDetour(CorridorRepair corridor){
            boolean done=completed();
            if(!done&&!failed)return;
            Point actual=position();
            if(failed||!accepts(step)){
                rejectDetour(failed?failureReason():"REJOIN_MOVEMENT_DIVERGED");
                return;
            }
            events.accept("REJOIN_NAV_CONFIRMED",Map.of("index",detourStep,"actual",actual,"target",step.to(),"movement",step.movement()));
            release();
            step=null;
            if(++detourStep<detour.actions().size()){
                beginDetourAction();
                return;
            }
            var terrain=new LivePathTerrain(client.level);
            double joined=corridor.confirmedRejoin(actual,routeProgress,terrain);
            if(!client.player.onGround()||joined<0||!corridor.viable(forecast(),joined,terrain)){
                rejectDetour("REJOIN_ENTRANCE_NOT_VIABLE");
                return;
            }
            trace("REJOIN_CONFIRMED","END_CLIENT_TICK",Map.of("state",forecast(),"oldProgress",routeProgress,"newProgress",joined));
            exportFailure("LOCAL_REJOIN_CONFIRMED");
            events.accept("REJOIN_CONFIRMED",Map.of("actual",actual,"plannedEntrance",detour.selected(),
                    "previousProgress",routeProgress,"newProgress",joined,"yaw",yaw(),"pitch",pitch()));
            detour=null;
            rejoinPending=true;
            continuation=new PathService.CorridorContinuation(corridor,joined);
        }

        private void rejectDetour(String reason){
            trace("REJOIN_NAV_FAILED","END_CLIENT_TICK",Map.of("state",forecast(),"reason",reason));
            exportFailure("REJOIN_NAV_FAILED");
            events.accept("REJOIN_NAV_FAILED",Map.of("actual",position(),"target",detour.selected().point(),"reason",reason));
            detour=null;
            release();
            step=null;
            failed=false;
            if(!startDetour(rehearsal.corridor(),forecast()))fail(reason);
        }

        @Override public PathService.CorridorContinuation takeCorridorContinuation(){
            var joined=continuation;
            continuation=null;
            return joined;
        }

        private boolean sameCast(CorridorRepair.Choice a,CorridorRepair.Choice b) {
            if(a==null||b==null||a.last().movement()!=Movement.TRANSMISSION||b.last().movement()!=Movement.TRANSMISSION)return false;
            return TransmissionModel.angle(a.last().yaw(),a.last().pitch(),b.last().yaw(),b.last().pitch())<1
                    && Math.abs(a.last().castDistance()-b.last().castDistance())<.15;
        }
        private void recoverCast(String reason) {
            if(logging()){
                trace("CAST_REJECTED","END_CLIENT_TICK_AFTER_PHYSICS",map("reason",reason,"state",forecast(),
                        "action",step,"choice",choice,"progress",routeProgress,"pending",gate.pending()));
                exportFailure(reason);
            }
            if(normalWeighted&&detour==null){
                if(gate.pending())return;
                if(heightCorrection!=null)
                    events.accept("HEIGHT_CORRECTION_BLOCKED",Map.of("reason",reason,"actual",position(),"beforeDispatch",true));
                var corridor=rehearsal.corridor();
                var actual=forecast();
                var terrain=new LivePathTerrain(client.level);
                var decision=fallback.choose(corridor,actual,routeProgress,positionSequence,false,choice,terrain,
                        candidate->!candidate.reason().equals("ADJUST_LAUNCH_POSITION")||
                                repositioned.size()<4&&!repositioned.contains(candidate.last().to()));
                var details=Map.<String,Object>of("cause",reason,"state",actual,"decision",decision,"progress",routeProgress);
                trace("LOCAL_CANDIDATE_FALLBACK","END_CLIENT_TICK_AFTER_PHYSICS",details);
                events.accept("LOCAL_CANDIDATE_FALLBACK",details);
                exportFailure(decision.reason());
                if(decision.choice()!=null){
                    long rejectedActionId=faultTrace==null?0:faultTrace.actionId();
                    release();
                    choice=decision.choice();
                    rejected=null;
                    legIndex=0;
                    choiceTick=client.level.getGameTime();
                    begin(choice.actions().getFirst());
                    if(logging()){
                        faultTrace.replaces(rejectedActionId);
                        trace("FALLBACK_ACTION_SELECTED","END_CLIENT_TICK_AFTER_PHYSICS",
                                Map.of("choice",choice,"state",actual,"replacesActionId",rejectedActionId));
                    }
                    if(step.movement()==Movement.TRANSMISSION)align(step.yaw(),step.pitch());
                }else{
                    recoverRegion(corridor,reason);
                }
                return;
            }
            if(detour!=null||++localRetries>3){fail(reason);return;}
            events.accept("LOCAL_CAST_REPAIR",Map.of("reason",reason,"segment",choice.reference(),"position",position(),"attempt",localRetries));
            rejected=choice;
            PathRouteESP.advance(previewIndex,routeProgress);
            release();
            choice=null;
            step=null;
        }
        public boolean failed(){return failed;}
        public boolean needsPreparation(){
            return selectingWeapon || (settings.speedDetection&&settings.initialSpeed&&!measurements.initialSpeedTried)
                    || (settings.jumpDetection&&settings.initialJump&&!measurements.initialJumpTried);
        }
        @Override public SearchAudit planningAudit(int attempt) {
            boolean detailed=logging()||events!=NO_EVENTS;
            if(logging())faultTrace.planAttempt(attempt);
            hudAttempt=attempt;searchProgress=Map.of();activeBranches=null;calculationActive=false;display.coarse();
            return new SearchAudit((event, data) -> {
                if(hudAttempt!=attempt)return;
                if(event.equals("ACTION_CALCULATION_START")){
                    if(!calculationActive){
                        calculationActive=true;
                        calculationStarted=System.nanoTime();
                        calculationAttempt++;
                    }
                    display.calculation();
                }
                if(event.equals("COARSE_PLANNING_START")){
                    calculationActive=false;
                    display.coarse();
                }
                if(event.equals("WORK_PROGRESS"))searchProgress=data;
                if(event.equals("BRANCH_BATCH"))activeBranches=(Number)data.get("activeBranches");
                if(event.equals("WORKER_END")||event.equals("DECISION")){
                    lastPlanningSummary=Map.copyOf(data);
                }
                if(events!=NO_EVENTS){
                    var fields=new LinkedHashMap<String,Object>(data);
                    fields.put("planAttempt",attempt);
                    events.accept("PLANNING_"+event,fields);
                }
            },()->detailed&&!logClosed,display);
        }
        @Override public int candidateWorkers(){return settings.effectiveWorkers();}
        @Override public boolean gpuAcceleration(){return settings.gpuAcceleration();}
        @Override public int rehearsalLimit(){
            return CorridorRepair.DEFAULT_SEARCH_LIMIT;
        }
        @Override public void terminalPosition(Point measured,Point planned){
            var fields=new LinkedHashMap<String,Object>();
            fields.put("lastMeasuredFeet",measured);fields.put("plannedEnd",planned);
            fields.put("nativeYawDegrees",totalYaw);fields.put("nativePitchDegrees",totalPitch);
            events.accept("TERMINAL_POSITION",fields);
        }
        public int maximumSearchSegments(){return settings.maximumSearchSegments();}
        public Options updateOptions(Options old) {
            return new Options(old.mode(),old.transmission()&&(weapon==null||equipped.getAsBoolean()),old.projectAir(),old.distance(),
                    settings.jumpDetection?measurements.jump():1,old.budget(),old.weights());
        }
        public CorridorPlanner.Timing updateTiming(CorridorPlanner.Timing old) {
            return new CorridorPlanner.Timing(settings.teleportCooldownMs(),settings.speedDetection?measurements.speed():4.3,old.rotationPerSecond(),old.confirmationMs());
        }
        public boolean prepare(){
            if(client.screen!=null||client.isPaused()||!client.isWindowActive()||!client.mouseHandler.isMouseGrabbed())return false;
            active=this;
            if (selectingWeapon) {
                if (equipped.getAsBoolean()) selectingWeapon=false;
                else if (weaponWait++==0) {keyboard.slot(weapon.slot()+1);return false;}
                else if (weaponWait<=20) return false;
                else {selectingWeapon=false;hint("no_weapon");}
            }
            var feet=position();
            var p=client.player;
            switch(prep) {
                case FIND_SPEED -> {
                    measurements.initialSpeedTried|=settings.speedDetection&&settings.initialSpeed;
                    if(settings.speedDetection&&settings.initialSpeed&&p.onGround()) probe=findProbe(feet,false);
                    previous=feet;probeSequence=positionSequence;probeTicks=0;
                    prep=probe==null?Prep.FIND_JUMP:Prep.SPEED;
                }
                case SPEED -> {
                    if (probeSequence==positionSequence && p.onGround() && !p.horizontalCollision) {
                        measurements.initialWalk(previous,feet);
                        if(++probeTicks<35&&feet.distance(probe)>.4){previous=feet;walkTowards(feet,probe);return false;}
                        measurements.finishInitialWalk();
                    }
                    keyboard.releaseAll();prep=Prep.FIND_JUMP;
                }
                case FIND_JUMP -> {
                    measurements.initialJumpTried|=settings.jumpDetection&&settings.initialJump;
                    probe=settings.jumpDetection&&settings.initialJump&&p.onGround()?findProbe(feet,true):null;
                    probeSequence=positionSequence;probeTicks=0;
                    prep=probe==null?Prep.COMPLETE:Prep.WALK_TO_JUMP;
                }
                case WALK_TO_JUMP -> {
                    if(probeSequence!=positionSequence || ++probeTicks>100 || !p.onGround()) {keyboard.releaseAll();prep=Prep.COMPLETE;}
                    else if(feet.distance(probe)>.4)walkTowards(feet,probe);
                    else {
                        keyboard.releaseAll();
                        var velocity=p.getDeltaMovement();
                        if(Math.hypot(velocity.x,velocity.z)<.035) {
                            var up=feet.add(new Point(0,7.2,0));
                            if(new ClientTerrain(client.level).transfer(feet,up).knowledge()!=Knowledge.CLEAR){prep=Prep.COMPLETE;return false;}
                            measurements.beginJump(feet);seenAir=false;probeTicks=0;
                            keyboard.keyDown("SPACE");prep=Prep.JUMP;
                        }
                    }
                }
                case JUMP -> {
                    keyboard.keyUp("SPACE");
                    measurements.observeJump(feet);
                    seenAir|=!p.onGround();
                    if(probeSequence!=positionSequence || (p.verticalCollision&&!p.onGround()) || ++probeTicks>100) {
                        measurements.rejectJump();prep=Prep.COMPLETE;
                    } else if(seenAir&&p.onGround()) {measurements.finishJump();prep=Prep.COMPLETE;}
                }
                case COMPLETE -> {keyboard.releaseAll();return true;}
            }
            return false;
        }
        private Point findProbe(Point feet,boolean jump){
            var terrain=new ClientTerrain(client.level);
            int[] distances=jump?new int[]{0,2,4,6,8,10}:new int[]{5,3};
            for(int radius:distances)for(int[] d:new int[][]{{1,0},{0,1},{-1,0},{0,-1}}) {
                var q=feet.add(new Point(radius*d[0],0,radius*d[1]));
                if(terrain.transfer(feet,q).knowledge()!=Knowledge.CLEAR)continue;
                boolean supported=true;
                for(int i=0;i<=radius*2;i++) {
                    var at=feet.add(new Point(i*.5*d[0],0,i*.5*d[1]));
                    if(terrain.descent(at,at).knowledge()!=Knowledge.CLEAR){supported=false;break;}
                }
                if(supported&&(!jump||terrain.transfer(q,q.add(new Point(0,7.2,0))).knowledge()==Knowledge.CLEAR))return q;
            }
            return null;
        }
        private void walkTowards(Point from,Point to){
            var direction=TransmissionModel.toward(from,to);
            if(!align(direction[0],0) && ViewAngles.error(yaw(),pitch(),direction[0],0)>5)keyboard.releaseAll();
            else keyboard.keyDown("W");
        }
        private void frame() {
            if (!aimActive || !usable() || client.screen!=null || client.isPaused() || !client.isWindowActive() || !client.mouseHandler.isMouseGrabbed()) {
                aimClock.reset();
                aimReady=false;
                return;
            }
            long now=System.nanoTime();
            double elapsed=aimClock.step(now,(int)settings.aim(PathSettings.Aim.RATE));
            try {
                aimLastElapsed=elapsed;aimLastMovement=0;
                if (elapsed>0){
                    double beforeYaw=yaw(),beforePitch=pitch();
                    mouse.aim(client,aimYaw,aimPitch,settings.aim(PathSettings.Aim.SPEED),settings.aim(PathSettings.Aim.CURVE),elapsed);
                    aimLastMovement=ViewAngles.error(beforeYaw,beforePitch,yaw(),pitch());
                    totalYaw+=Math.abs(ViewAngles.wrap(yaw()-beforeYaw));
                    totalPitch+=Math.abs(pitch()-beforePitch);
                }
                double error=ViewAngles.error(yaw(),pitch(),aimYaw,aimPitch);
                boolean readyBefore=aimReady;
                if (correction.ready(error,settings.aim(PathSettings.Aim.TOLERANCE),settings.aim(PathSettings.Aim.CORRECTION),now,()->aimDraw)) {
                    if (!aimReady) {alignedY=position().y();alignedAt=now;}
                    aimReady=true;
                } else aimReady=false;
                if(logging()){
                    boolean within=error<=settings.aim(PathSettings.Aim.TOLERANCE);
                    if(within!=aimWithinTolerance||readyBefore!=aimReady)
                        trace("AIM_FRAME","RENDER_AFTER_CLIENT_TICKS",Map.of("state",forecast(),"errorDegrees",error,
                                "withinTolerance",within,"ready",aimReady,"targetYaw",aimYaw,"targetPitch",aimPitch,"aimController",aimEvidence()));
                    aimWithinTolerance=within;
                }
            } catch (IllegalStateException unavailable) {
                fail("AIM_FAILED");
                events.accept("AIM_FAILED",Map.of("reason",unavailable.getMessage(),"feet",position()));
            }
        }
        private boolean align(double yaw,double pitch) {
            if (!aimActive || ViewAngles.error(aimYaw,aimPitch,yaw,pitch)>1e-5) {
                if (!aimActive) {startAimY=position().y();aimStarted=System.nanoTime();}
                correction.clear();
                aimYaw=yaw;aimPitch=pitch;aimReady=false;aimActive=true;
                if (ViewAngles.error(yaw(),pitch(),aimYaw,aimPitch)<=settings.aim(PathSettings.Aim.TOLERANCE)) {
                    aimReady=true;
                    alignedY=position().y();alignedAt=System.nanoTime();
                }
            }
            return aimReady && ViewAngles.error(yaw(),pitch(),aimYaw,aimPitch)<=settings.aim(PathSettings.Aim.TOLERANCE);
        }
        public boolean canReplan(){return usable();}
        public boolean accepts(Segment action){
            if(action.movement()==Movement.FALL&&sameVerticalColumn(action)&&!mustGround){
                var p=position();
                if(p.sameColumn(action.to())&&p.y()<=action.to().y()
                        &&p.y()>=action.to().y()-(client.player.onGround()?6.1:2.5))return true;
            }
            return position().distance(action.to())<=.75;
        }
        public double yaw(){return client.player.getYRot();}
        public double pitch(){return client.player.getXRot();}
        public void replanning(String reason,int attempt){
            settleTicks=0;
            PathRouteESP.clear();
            trace("REPLAN","END_CLIENT_TICK",Map.of("reason",reason,"attempt",attempt,"state",forecast()));
            exportFailure("GLOBAL_REPLAN");
            events.accept("REPLAN",Map.of("reason",reason,"attempt",attempt,"actual",position(),
                    "pathInputBlocked",PhysicalGameInput.pathBlocked(),"routeEspCleared",PathRouteESP.snapshot()==null,
                    "strategy",settings.mode));
        }
        @Override public void replanSuppressed(String reason){
            trace("REPLAN_SUPPRESSED","END_CLIENT_TICK",Map.of("reason",reason,"state",forecast(),
                    "basis","UNCHANGED_SEARCH_INPUT","comparedFields",List.of("feet","yaw","pitch","options","timing")));
            exportFailure("REPLAN_SUPPRESSED");
            events.accept("REPLAN_SUPPRESSED",Map.of("actual",position(),"yaw",yaw(),"pitch",pitch(),
                    "reason",reason,"basis","UNCHANGED_SEARCH_INPUT"));
        }
        public boolean readyForReplan(){return client.player.onGround()||++settleTicks>=18;}
        public String failureReason(){return failureReason==null?"INPUT_UNAVAILABLE":failureReason;}
        private void fail(String reason){
            failed=true;failureReason=reason;lastFault=reason;
            if(logging()&&client.level==world){
                trace("DRIVER_FAILURE","END_CLIENT_TICK",Map.of("reason",reason,"state",forecast()));
                exportFailure(reason);
            }
        }
        public boolean completed() {
            if(done||failed) return done;
            if(client.screen!=null||client.isPaused()||!client.isWindowActive()||!client.mouseHandler.isMouseGrabbed()) {
                if(step!=null&&step.movement()==Movement.TRANSMISSION)castGate("CLIENT_INPUT_UNAVAILABLE",null,null,null);
                keyboard.releaseAll();return false;
            }
            if(++ticks>200){fail(gate.pending()?"POSITION_CONFIRMATION_TIMEOUT":"ACTION_TIMEOUT");return false;}
            if (gate.pending()) {
                if (!confirmPosition()){
                    if(logging()&&step!=null&&step.movement()==Movement.TRANSMISSION&&positionSequence==confirmationSequence)
                        trace("WAITING_AFTER_DISPATCH","END_CLIENT_TICK",Map.of("state",forecast(),"reason","POSITION_CONFIRMATION_PENDING"));
                    return done;
                }
                if (!downwardCast) return done;
            }
            if(step.movement()==Movement.TRANSMISSION) cast();
            else {
                if(display.calculating()&&previous!=null)display.movement(previous,position());
                move();
            }
            return done;
        }
        private Map<String,Object> aimEvidence(){
            return map("aimActive",aimActive,"readyFlag",aimReady,"outerThreshold",settings.aim(PathSettings.Aim.TOLERANCE),
                    "correctionActive",correction.active(),"correctionThreshold",correction.active()?correction.threshold():null,
                    "lastFrameElapsed",aimLastElapsed,"lastFrameViewChange",aimLastMovement,"nativeInput",aimLastElapsed<=0?"FRAME_RATE_WAIT":mouse.lastStep(),"readyMonotonicNanos",aimReady?alignedAt:null);
        }
        private void castGate(String blocker,Boolean aligned,Boolean schedule,Boolean cooldownPassed){
            castStatus=blocker;
            if(!logging())return;
            long at=client.level.getGameTime(),expected=actionStartTick+plannedCastAfter;
            faultTrace.gate(at,expected,positionSequence,blocker,map("firstBlocker",blocker,"aimController",aimEvidence(),
                    "state",forecast(),"expectedAimTick",estimatedAimAfter<0?null:actionStartTick+estimatedAimAfter,
                    "expectedCastTick",expected,"actionStartTick",actionStartTick,
                    "targetYaw",step.yaw(),"targetPitch",step.pitch(),
                    "errorDegrees",ViewAngles.error(yaw(),pitch(),step.yaw(),step.pitch()),
                    "tolerance",settings.aim(PathSettings.Aim.TOLERANCE),
                    "aligned",aligned==null?"NOT_EVALUATED":aligned,"schedulePassed",schedule==null?"NOT_EVALUATED":schedule,
                    "cooldownPassed",cooldownPassed==null?"NOT_EVALUATED":cooldownPassed,
                    "cooldownRemainingMs",gate.remainingMs(System.nanoTime(),settings.teleportCooldownMs()),
                    "confirmationPending",gate.pending(),"confirmationGate",blocker.equals("CLIENT_INPUT_UNAVAILABLE")?
                            "NOT_EVALUATED":!gate.pending(),"timing",heightCorrection==null?"N_PLUS_ONE":"N"));
        }

        private void cast() {
            if (!canCast()){
                castGate("TELEPORT_TRANSPORT_OR_WEAPON_UNAVAILABLE",null,null,null);
                fail("TELEPORT_TRANSPORT_OR_WEAPON_UNAVAILABLE");return;
            }
            if (choice!=null&&choice.jumpAssist()) {
                if(!jumpPressed&&client.player.onGround()) {keyboard.keyDown("SPACE");jumpPressed=true;}
                else keyboard.keyUp("SPACE");
                if(!client.player.onGround())seenAir=true;
            }
            boolean aligned=align(step.yaw(),step.pitch());
            String timingBlocker=RouteCastTiming.aimScheduleBlocker(normalWeighted,client.level.getGameTime(),
                    actionStartTick,plannedCastAfter,aligned);
            if(!timingBlocker.equals("NONE")){
                castGate(timingBlocker,aligned,aligned?Boolean.FALSE:null,null);return;
            }
            if(choice!=null&&choice.jumpAssist() && (!seenAir||client.player.onGround())){
                castGate("JUMP_WINDOW_NOT_READY",true,true,null);
                if(seenAir)recoverCast("JUMP_WINDOW_PASSED");
                else if(ticks>=5)recoverCast("JUMP_DID_NOT_TAKE_OFF");
                return;
            }
            if (!gate.ready(System.nanoTime(),settings.teleportCooldownMs())){
                castGate("CAST_COOLDOWN",true,true,false);return;
            }
            castGate("NONE",true,true,true);
            Point feet=position();
            double yaw=yaw(),pitch=pitch(),distance=step.castDistance();
            if(!RouteCastTiming.validView(yaw,pitch)){recoverCast("CAST_VIEW_OUT_OF_RANGE");return;}
            if(client.player.onGround())confirmedTravel=0;
            if(confirmedTravel+distance>180 && !(pitch>=89&&distance<=6.1)){
                recoverCast("LANDING_REQUIRED");
                return;
            }
            var terrain=new LivePathTerrain(client.level);
            Point actualLanding=TransmissionModel.landing(feet,yaw,pitch,distance);
            var corridor=rehearsal.corridor();
            pendingProgress=detour==null?corridor.castProgress(feet,actualLanding,yaw,pitch,distance,routeProgress,terrain):-1;
            if(upwardFirst()){
                if(!corridor.insideAction(new Segment(Movement.TRANSMISSION,feet,actualLanding,yaw,pitch,0,0,distance))
                        ||actualLanding.distance(step.to())>.25){
                    recoverCast("UPWARD_TURN_LIFT_CHANGED");return;
                }
                var onward=choice.actions().get(1);
                var live=forecast();
                var alignedNext=RouteCastTiming.estimate(yaw,pitch,onward.yaw(),onward.pitch(),live.aim());
                var raised=RouteCastTiming.expectedHandoff(Motion.confirmed(actualLanding,terrain),terrain).motion();
                var nextMotion=alignedNext==null||raised==null?null:FlightTiming.predict(raised,
                        RouteCastTiming.castTick(new Ready(0,0,0),alignedNext.ms(),heightCorrection!=null),terrain);
                var nextLanding=nextMotion==null?null:CorridorRepair.landing(nextMotion.feet(),alignedNext.yaw(),
                        alignedNext.pitch(),onward.castDistance(),terrain);
                if(nextLanding==null||corridor.castProgress(nextMotion.feet(),nextLanding,alignedNext.yaw(),
                        alignedNext.pitch(),onward.castDistance(),routeProgress,terrain)<=routeProgress){
                    recoverCast("UPWARD_TURN_CONTINUATION_CHANGED");return;
                }
            }
            if(!upwardFirst()&&detour==null&&(corridor.regional()||heightCorrection!=null)&&pendingProgress>routeProgress){
                var packetMotion=Motion.confirmed(actualLanding,terrain);
                var handoff=normalWeighted?RouteCastTiming.expectedHandoff(packetMotion,terrain):null;
                var landingMotion=handoff==null?packetMotion:handoff.motion();
                var next=landingMotion==null?null:new CorridorRepair.Start(landingMotion,yaw,pitch,
                        landingMotion.grounded()?0:confirmedTravel+distance,forecast().aim(),new Ready(0,0,0));
                var continuation=next==null?new CorridorRepair.Continuation(false,"CONFIRMATION_MOTION_UNKNOWN"):
                        corridor.continuation(next,pendingProgress,terrain);
                boolean connected=continuation.connected();
                trace("CAST_CONTINUATION","END_CLIENT_TICK_AFTER_PHYSICS",map("state",forecast(),
                        "landing",actualLanding,"pendingProgress",pendingProgress,"continuation",continuation,
                        "packetMotion",packetMotion,"handoff",handoff,
                        "rayAndLandingCheck",connected?"PENDING":"NOT_EVALUATED"));
                events.accept("CORRIDOR_CAST_CONTINUATION",Map.of("actual",feet,"predicted",actualLanding,
                        "yaw",yaw,"pitch",pitch,"nextArcProgress",corridor.arc(pendingProgress),"connected",connected));
                if(!connected){
                    if(display.calculating())display.reject(actualLanding);
                    recoverCast("REGION_LANDING_HAS_NO_CONTINUATION");
                    return;
                }
            }
            if(!tryDispatch(new ClientTerrain(client.level,logging()||display.calculating()),feet,yaw,pitch,distance,step.to(),false)){
                recoverCast("CAST_RECHECK_FAILED");
                return;
            }
            lastActualCast=new Segment(Movement.TRANSMISSION,feet,actualLanding,yaw,pitch,0,0,distance);
            if(upwardFirst()&&gate.pending())upwardTurnUsed=true;
        }
        private boolean upwardFirst(){
            return normalWeighted&&detour==null&&choice!=null&&legIndex==0&&choice.reason().equals("UPWARD_TURN_ASSIST");
        }
        private boolean canCast() {
            return transmission&&settings.transmission&&equipped.getAsBoolean()
                    &&!client.player.isShiftKeyDown()&&!client.options.keyShift.isDown();
        }
        private boolean confirmPosition() {
            if (positionSequence==confirmationSequence) return false;
            gate.confirm();
            mouse.consumed();
            if(logging())trace("CONFIRMATION_CONSUMED","END_CLIENT_TICK_AFTER_PHYSICS",
                    Map.of("state",forecast(),"packetTick",packetTick,
                            "elapsedLevelTicksSincePacket",client.level.getGameTime()-packetTick,"packetFeet",lastPositionUpdate));
            events.accept("POSITION_CONFIRMATION",Map.of("predicted",predicted,"actual",lastPositionUpdate,
                    "actualY",lastPositionUpdate.y(),"delayMs",(System.nanoTime()-dispatchTime)/1e6,"downwardBurst",downwardCast));
            boolean near=lastPositionUpdate.distance(predicted)<=.25;
            if(upwardFirst()){
                if(!near)fail("UPWARD_TURN_CONFIRMATION_DIFFERS");
            }else if(!downwardCast&&choice!=null){
                var geometry=new LivePathTerrain(client.level);
                var corridor=rehearsal.corridor();
                double next=near&&lastActualCast!=null
                        ?corridor.castProgress(lastActualCast.from(),lastPositionUpdate,lastActualCast.yaw(),lastActualCast.pitch(),
                                lastActualCast.castDistance(),routeProgress,geometry)
                        :corridor.advanceTo(lastPositionUpdate,routeProgress,geometry);
                if(next<=routeProgress||!geometry.sweep(predicted,lastPositionUpdate))
                    fail("CONFIRMED_POSITION_OUTSIDE_CONNECTED_CORRIDOR");
                else {
                    pendingProgress=next;
                    if(!near)lastActualCast=null;
                    if(!lastPositionUpdate.equals(predicted))
                        events.accept("CORRIDOR_PACKET_DEVIATION",Map.of("actual",lastPositionUpdate,"segment",choice.reference(),
                                "progress",next,"reason","CONNECTED_ACTUAL_LANDING"));
                }
            } else if(!near)fail("CONFIRMED_POSITION_DIFFERS");
            if (!failed) confirmedTravel+=pendingDistance;
            if(logging()){
                trace("CONFIRMATION_DISPOSITION","END_CLIENT_TICK",map("accepted",!failed,"packetFeet",lastPositionUpdate,
                        "reason",failed?failureReason():null));
                if(actionWasLate)exportFailure(failed?"LATE_INPUT_CONFIRMATION_REJECTED":"LATE_INPUT_CONFIRMED");
                faultTrace.fallbackOutcome(failed?"FALLBACK_CONFIRMATION_REJECTED":"FALLBACK_INPUT_CONFIRMED");
            }
            if(!downwardCast) done=true;
            return !failed;
        }
        private boolean tryDispatch(ClientTerrain terrain,Point feet,double yaw,double pitch,double distance,Point expected,boolean burst) {
            if(!RouteCastTiming.validView(yaw,pitch))return false;
            var raw=TransmissionModel.raw(feet,yaw,pitch,distance);
            predicted=TransmissionModel.landing(feet,yaw,pitch,distance);
            var segment=terrain.transfer(feet,raw);
            var segmentHit=terrain.lastHit();
            var arrival=terrain.transfer(raw,predicted);
            var arrivalHit=terrain.lastHit();
            boolean inCorridor=burst?predicted.equals(expected):detour!=null?
                    predicted.distance(expected)<=.25&&predicted.distance(rejoinOrigin)<=CorridorRejoin.RANGE
                            &&feet.distance(rejoinOrigin)<=CorridorRejoin.RANGE:
                    upwardFirst()?predicted.distance(expected)<=.25:pendingProgress>routeProgress;
            String geometryBlocker=!inCorridor?"OUTSIDE_CONNECTED_CORRIDOR":predicted.equals(feet)?"ZERO_MOVEMENT":
                    segment.knowledge()!=Knowledge.CLEAR?"CAST_RAY_"+segment.knowledge():
                    arrival.knowledge()!=Knowledge.CLEAR?"QUANTIZED_ARRIVAL_"+arrival.knowledge():"NONE";
            boolean legal=geometryBlocker.equals("NONE");
            var details=new LinkedHashMap<String,Object>();
            details.put("planningY",choice==null?step.from().y():rehearsal.corridor().reference(choice.reference()).from().y());
            details.put("rehearsedY",choice==null||choice.castOrigin()==null?step.from().y():choice.castOrigin().feet().y());
            details.put("corridorSegment",choice==null?-1:choice.reference());details.put("castTick",client.level.getGameTime());
            details.put("expectedCastTick",choice==null?client.level.getGameTime():choiceTick+choice.castTick());
            details.put("theoreticalAimMs",choice==null?0:choice.aimMs());
            if(normalWeighted&&!burst){
                details.put("expectedCastTick",actionStartTick+plannedCastAfter);
                details.put("heightCorrecting",heightCorrection!=null);
                details.put("aimExtraTicks",heightCorrection==null?1:0);
            }
            String correctionReason=!inCorridor?"OUTSIDE_CONNECTED_CORRIDOR":
                    segment.knowledge()!=Knowledge.CLEAR?"CAST_RAY_"+segment.knowledge():
                    arrival.knowledge()!=Knowledge.CLEAR?"QUANTIZED_ARRIVAL_"+arrival.knowledge():
                    burst?"VERTICAL_DESCENT":detour!=null?"REJOIN_NAV_TRANSMISSION":!predicted.equals(expected)?"CONNECTED_LANDING_OFFSET":choice==null?"DIRECT_ACTION":choice.reason();
            details.put("correctionReason",correctionReason);details.put("aimStartY",startAimY);details.put("alignedY",alignedY);
            details.put("castY",feet.y());details.put("predictedY",predicted.y());details.put("origin",feet);
            details.put("predicted",predicted);details.put("planned",step.to());details.put("yaw",yaw);details.put("pitch",pitch);
            details.put("segment",segment);details.put("arrival",arrival);details.put("compatibleWithPlannedAction",inCorridor);
            details.put("aimMs",(alignedAt-aimStarted)/1e6);details.put("postAlignmentMs",(System.nanoTime()-alignedAt)/1e6);
            details.put("downwardBurst",burst);
            if(logging())trace("CAST_GEOMETRY","END_CLIENT_TICK_AFTER_PHYSICS",map(
                    "state",forecast(),"origin",feet,"raw",raw,"landing",predicted,"range",distance,"yaw",yaw,"pitch",pitch,
                    "rayVerdict",segment.knowledge().name(),"rayReason",segment.reason(),"rayHit",segmentHit,
                    "arrivalVerdict",arrival.knowledge().name(),"arrivalReason",arrival.reason(),"arrivalHit",arrivalHit,
                    "collisionSource","LIVE_CLIENT_SWEEP","blockDetails",segmentHit!=null||arrivalHit!=null?"AVAILABLE":
                            "NO_BLOCK_HIT_OR_GEOMETRY_UNKNOWN","connectedProgress",inCorridor,"legal",legal,
                    "plannedOrigin",choice==null?null:choice.castOrigin(),"plannedLanding",expected,
                    "firstBlocker",geometryBlocker,"expectedCastTick",actionStartTick+plannedCastAfter));
            if(!legal&&display.calculating()){
                var hit=segmentHit!=null?segmentHit:arrivalHit;
                display.reject(hit==null?predicted:new Point(hit.x()+.5,hit.y()+.5,hit.z()+.5));
            }
            if (!legal) {if (!burst)events.accept("CAST_RECHECK_FAILED",details);return false;}
            confirmationSequence=positionSequence;
            if(!mouse.click(client)){
                trace("INPUT_NOT_SENT","END_CLIENT_TICK",Map.of("reason","NATIVE_USE_INPUT_BUSY"));
                exportFailure("NATIVE_USE_INPUT_BUSY");
                return true;
            }
            if(logging()){
                long at=client.level.getGameTime(),extra=Math.max(0,at-actionStartTick-plannedCastAfter);
                trace("INPUT_SENT","END_CLIENT_TICK_AFTER_PHYSICS",Map.of("state",forecast(),"origin",feet,"raw",raw,
                        "landing",predicted,"expectedCastTick",actionStartTick+plannedCastAfter,
                        "extraPhysicsTicksByLevelClock",extra,"transport","NATIVE_MOUSE"));
                actionWasLate=!burst&&extra>0;
                if(actionWasLate)exportFailure("LATE_INPUT_SENT");
                faultTrace.fallbackOutcome("FALLBACK_INPUT_SENT");
            }
            dispatchTime=System.nanoTime();
            pendingDistance=distance;downwardCast=burst;
            long interval=gate.dispatch(dispatchTime);
            details.put("distance",distance);details.put("cooldownMs",settings.teleportCooldownMs());
            details.put("intervalMs",interval<0?-1:interval/1e6);
            details.put("weightedCastCost",settings.weights.transmission());
            if (burst) {
                details.put("weightedTurnCost",0);
                addDescentTiming(details);
            }
            if (!burst && pitch<89) logLandingTurn(yaw,pitch);
            events.accept("CAST",details);
            return true;
        }
        private void addDescentTiming(Map<String,Object> details) {
            var sample=descentSample;
            if(sample==null)return;
            var start=sample.start();
            var estimate=FlightTiming.estimate(start.yaw(),start.pitch(),aimYaw,90,start.aim());
            if(estimate==null)return;
            int tick=normalWeighted?RouteCastTiming.castTick(start.ready(),estimate.ms(),start.heightCorrection()!=null):
                    start.ready().castTick(estimate.ms());
            var origin=FlightTiming.predict(start.motion(),tick,new LivePathTerrain(client.level));
            details.put("theoreticalAimMs",estimate.ms());
            details.put("expectedCastTick",sample.tick()+tick);
            details.put("rehearsedY",origin==null?null:origin.feet().y());
            if(estimate.ms()==0)details.put("aimMs",0);
        }
        private void descend(Point to) {
            keyboard.releaseAll();
            var p=client.player;
            var feet=position();
            if (p.onGround()) {
                if (feet.sameColumn(to) && feet.y()<=to.y() && feet.y()>=to.y()-6.1) {
                    events.accept("LANDING_CONFIRMED",Map.of("distanceBeforeReset",confirmedTravel,"feet",feet,"yaw",yaw(),"pitch",pitch()));
                    landingNeedsTurn=pitch()>=89;
                    confirmedTravel=0;
                }
                return;
            }
            if (!transmission || !settings.transmission || !feet.sameColumn(to)) return;
            if (!equipped.getAsBoolean() || p.isShiftKeyDown() || client.options.keyShift.isDown()) return;
            double retainedYaw=aimActive?aimYaw:yaw();
            if(descentSample==null||descentSample.sequence()!=positionSequence)
                descentSample=new DescentSample(forecast(),client.level.getGameTime(),positionSequence);
            boolean aligned=align(retainedYaw,90);
            if(normalWeighted){
                var start=descentSample.start();
                var aim=RouteCastTiming.estimate(start.yaw(),start.pitch(),retainedYaw,90,start.aim());
                if(aim==null||!RouteCastTiming.ready(client.level.getGameTime(),descentSample.tick(),
                        RouteCastTiming.castTick(start.ready(),aim.ms(),heightCorrection!=null),aligned))return;
            }else if(!aligned)return;
            if(!gate.ready(System.nanoTime(),settings.teleportCooldownMs()))return;
            feet=position();
            if (p.onGround() || p.getDeltaMovement().y>0) return;
            double distance=Math.min(range,feet.y()-to.y());
            if (distance<.5) return;
            var landing=TransmissionModel.landing(feet,yaw(),pitch(),distance);
            if (landing.y()>=feet.y()-.05 || landing.y()<to.y()-.1 || !landing.sameColumn(to)) return;
            tryDispatch(new ClientTerrain(client.level),feet,yaw(),pitch(),distance,landing,true);
        }
        private void move() {
            var feet=position();
            var to=step.to();
            var p=client.player;
            boolean corrected=previousPositionSequence!=positionSequence;
            previousPositionSequence=positionSequence;
            if(settings.speedDetection&&step.movement()==Movement.WALK&&keyboard.hasHeldKeys()&&p.onGround()&&!p.horizontalCollision&&!corrected)
                measurements.walk(previous,feet,settings.speedSampleDistance());
            previous=feet;
            if(step.movement()==Movement.JUMP&&settings.jumpDetection) {
                if(corrected||(p.verticalCollision&&!p.onGround()))measurements.rejectJump();
                measurements.observeJump(feet);
            }
            if(!p.onGround())seenAir=true;
            if (step.movement()==Movement.FALL && sameVerticalColumn(step)) {
                descend(to);
                if (gate.pending()) return;
                feet=position();
            }
            if (Math.hypot(feet.x()-to.x(),feet.z()-to.z())<=.25 && Math.abs(feet.y()-to.y())<=.4
                    && (p.onGround()||step.movement()==Movement.FALL&&!mustGround)) {
                if (seenAir&&step.movement()==Movement.JUMP&&p.onGround())measurements.finishJump();
                keyboard.releaseAll();done=true;return;
            }
            if(step.movement()==Movement.FALL && sameVerticalColumn(step)) {
                done=!mustGround&&feet.sameColumn(to)&&feet.y()<=to.y()&&feet.y()>=to.y()-(p.onGround()?6.1:2.5);
                return;
            }
            var direction=TransmissionModel.toward(feet,to);
            if(!align(direction[0],0) && ViewAngles.error(yaw(),pitch(),direction[0],0)>5){keyboard.releaseAll();return;}
            logLandingTurn(yaw(),pitch());
            keyboard.keyDown("W");
            if (step.movement()==Movement.JUMP&&!jumpPressed&&p.onGround()) {
                keyboard.keyDown("SPACE");jumpPressed=true;
                if(settings.jumpDetection)measurements.beginJump(feet);
            } else keyboard.keyUp("SPACE");
        }
        public void release() {
            keyboard.releaseAll();mouse.cancel(client);gate.confirm();
            aimClock.reset();correction.clear();aimActive=false;aimReady=false;downwardCast=false;
            if(active==this)active=null;
        }
        private void finishInput(Result result) {
            try {
            if(logging()){
                trace("TASK_END","CLIENT_TASK_COMPLETION",Map.of("status",result.status().name(),"reason",result.reason(),
                        "end",result.end(),"expanded",result.explored(),"planningSummary",lastPlanningSummary,"evidenceSource","LIVE_CLIENT",
                        "planningFrontierEvidence",lastPlanningSummary.isEmpty()?"NOT_AVAILABLE_WITHOUT_SEARCH_AUDIT":"STAGE_LOGS"));
                exportFailure(result.status().name()+":"+result.reason());
                if(failureWriter!=null)failureWriter.close();
            }
            } finally {
                closeSession();
                finishInputOwnership(result);
            }
        }
        private void finishInputOwnership(Result result){
            boolean wasBlocked=PhysicalGameInput.endPath(this);
            PathRouteESP.clear();
            if (wasBlocked) {
                var fields=Map.<String,Object>of("actualFeet",result.end(),"reason",result.reason(),
                        "focusBlocked",PhysicalGameInput.focusReleased());
                Ultimate_Garden.LOGGER.info("Path INPUT_BLOCK_ENDED {}",fields);
                events.accept("INPUT_BLOCK_ENDED",fields);
            }
        }
        private void logLandingTurn(double yaw,double pitch) {
            if (!landingNeedsTurn) return;
            landingNeedsTurn=false;
            double angle=TransmissionModel.angle(yaw,90,yaw,pitch);
            events.accept("LANDING_REALIGN",Map.of("yaw",yaw,"pitch",pitch,"angleDeg",angle,
                    "weightedTurnCost",settings.weights.turn()*Math.pow(angle/30,settings.weights.exponent())));
        }
        private boolean sameVerticalColumn(Segment action){
            return Math.abs(action.from().x()-action.to().x())<.01&&Math.abs(action.from().z()-action.to().z())<.01;
        }
    }
}
