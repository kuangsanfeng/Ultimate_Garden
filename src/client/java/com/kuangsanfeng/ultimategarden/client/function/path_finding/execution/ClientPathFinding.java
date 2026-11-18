package com.kuangsanfeng.ultimategarden.client.function.path_finding.execution;

import com.kuangsanfeng.ultimategarden.Ultimate_Garden;
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

/** Client-thread bridge. The debug transport is restricted to integrated-server sessions. */
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

    private record RouteRequest(Object owner, String map, Point goal, double distance, boolean simulate,
                                BooleanSupplier equipped, TransmissionEquipment weapon,
                                BiConsumer<String,Map<String,Object>> events, Consumer<Result> completion,
                                ClientLevel level, ClientPacketListener connection, String island, String instance) {
        boolean matches(Minecraft c) {
            if (level!=c.level || connection!=c.getConnection()) return false;
            var world=WorldInfoService.snapshot();
            return simulate || java.util.Objects.equals(instance,world.instance())
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
        String world=WorldInfoService.snapshot().island();
        if (world==null || world.isBlank()) {hint("world_unavailable");return;}
        var c=Minecraft.getInstance();
        var settings=Ultimate_GardenClient.getConfigManager().getPathSettings();
        var weapon=settings.transmission?TransmissionEquipment.select(c.player.getInventory()).orElse(null):null;
        if (weapon==null) hint(settings.transmission?"no_weapon":"walking_only");
        try {
            menuTicket=start(menuOwner,world,goal,weapon==null?12:weapon.range(),false,
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
            if (candidate!=null && confirmation()==null) candidate=null;
            if (relay!=null && !relay.matches(c)) relay=null;
            if (running!=null && !running.request().matches(c)) service.contextChanged("WORLD_CONTEXT_CHANGED");
            if (chatScreen!=null && c.screen!=chatScreen) chatScreen=null;
            if (pendingInput!=null && c.screen==null && c.mouseHandler.isMouseGrabbed()) {
                Point goal=pendingInput;
                pendingInput=null;
                if (c.player!=null && c.level!=null) startTarget(goal);
            }
            service.tick();
        });
        ClientContextEvents.onDisconnect((handler,client)->{service.contextChanged("DISCONNECTED");clear();});
        ClientContextEvents.onChange(event->{service.contextChanged(event.change().name());clear();});
        ClientContextEvents.onStopping(client->{service.close();clear();});
    }
    private void clear() {
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
    public boolean menuTestAvailable() {
        var c=Minecraft.getInstance();
        return c.getSingleplayerServer()!=null && c.player!=null && !conflict() && c.hitResult instanceof BlockHitResult
                && service.stage()==PathService.Stage.IDLE;
    }
    public void cancelMenuTask() { if (menuTaskActive()) service.cancel(menuTicket); }
    public void startMenuTest(String localMap) {
        var c=Minecraft.getInstance();
        if (!menuTestAvailable() || !(c.hitResult instanceof BlockHitResult hit)) throw new IllegalStateException("Local target unavailable");
        var pos=hit.getBlockPos();
        var shape=c.level.getBlockState(pos).getCollisionShape(c.level,pos);
        if (shape.isEmpty()) throw new IllegalStateException("Target has no standing surface");
        String world=localMap.strip();
        if (world.isBlank()) throw new IllegalStateException("Enter the local map name");
        var goal=new Point(pos.getX()+.5,pos.getY()+shape.max(net.minecraft.core.Direction.Axis.Y),pos.getZ()+.5);
        menuTicket=start(menuOwner,world,goal,12,true,()->false,NO_EVENTS,result->{});
        c.setScreen(null);
    }
    /** Caller supplies the confirmed range and a live check of the equipped AOTE/AOTV. */
    public PathService.Ticket start(Object owner,String world,Point goal,double distance,boolean localSimulation,
                                    BooleanSupplier equipped,BiConsumer<String,Map<String,Object>> events,Consumer<Result> completion) {
        return start(owner,world,goal,distance,localSimulation,equipped,null,events,completion);
    }
    private PathService.Ticket start(Object owner,String world,Point goal,double distance,boolean localSimulation,
                                     BooleanSupplier equipped,TransmissionEquipment weapon,BiConsumer<String,Map<String,Object>> events,
                                     Consumer<Result> completion) {
        var c=Minecraft.getInstance();
        var info=WorldInfoService.snapshot();
        return start(new RouteRequest(owner,world,goal,distance,localSimulation,equipped,weapon,events,completion,
                c.level,c.getConnection(),info.island(),info.instance()));
    }

    private PathService.Ticket start(RouteRequest request) {
        var c=Minecraft.getInstance();
        if (!c.isSameThread() || c.player==null || c.level==null || conflict())
            throw new IllegalStateException("Input unavailable");
        if (request.simulate() && c.getSingleplayerServer()==null)
            throw new IllegalStateException("Local simulator requires integrated server");

        PathSettings settings=Ultimate_GardenClient.getConfigManager().getPathSettings();
        boolean transmission=settings.transmission && (request.simulate() || request.weapon()!=null || request.equipped().getAsBoolean());
        var path=MappingPaths.root(c.gameDirectory.toPath());
        var options=new Options(settings.mode,transmission,true,request.distance(),settings.jumpDetection?measurements.jump():1,
                settings.searchNodesPerSegment(),settings.weights);
        var timing=new CorridorPlanner.Timing(settings.teleportCooldownMs(),settings.speedDetection?measurements.speed():4.3,settings.aim(PathSettings.Aim.SPEED),50);
        var driver=new NativeDriver(c,settings,request.distance(),request.simulate(),request.equipped(),request.weapon(),transmission,request.events());
        var ticket=service.start(request.owner(),point(c.player.position()),request.goal(),c.player.getYRot(),c.player.getXRot(),options,
                ()->{try{return new MapTerrain(new MapRepository(path,request.map()));}catch(java.io.IOException e){throw new UncheckedIOException(e);}},
                timing,driver,result->{
                    running=null;
                    if (result.status()==Status.ARRIVED) relay=null;
                    driver.finishInput(result);
                    request.completion().accept(result);
                });
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
        private final double aimDraw=Math.random();
        private CorridorRepair.Rehearsal rehearsal;
        private PathRouteESP.Prepared overlay;
        private CorridorRepair.Choice choice, rejected;
        private int previewIndex, legIndex, localRetries, settleWait;
        private final java.util.Set<Point> repositioned=new java.util.HashSet<>();
        private double routeProgress, pendingProgress;
        private CorridorRejoin.Search detour;
        private int detourStep, rejoinWait;
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
        private final boolean simulate;
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
        NativeDriver(Minecraft c,PathSettings settings,double range,boolean simulate,BooleanSupplier equipped,TransmissionEquipment weapon,boolean transmission,BiConsumer<String,Map<String,Object>> events) {
            client=c;world=c.level;player=c.player;keyboard=new VirtualKeyboard(c);this.settings=settings;
            this.range=range;this.simulate=simulate;this.equipped=equipped;this.events=events;
            this.weapon=weapon;this.transmission=transmission;selectingWeapon=weapon!=null;
        }
        public Point position(){return point(client.player.position());}
        public boolean usable(){return client.level==world && client.player==player && client.player.isAlive() && !conflict();}
        public void begin(Segment action) {
            step=action;active=this;failed=jumpPressed=seenAir=done=false;ticks=0;previous=position();predicted=null;
            failureReason=null;
            descentSample=null;
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
        }
        public void planReady(Result result) {
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
                    new Ready(gate.remainingMs(System.nanoTime(),settings.teleportCooldownMs()),0,0));
        }
        @Override public void rehearsalStarting(CorridorRepair.Start initial,int limit){
            events.accept("REHEARSAL_INPUT",Map.of("state",initial,"limit",limit));
        }
        @Override public void prepareRehearsalOverlay(CorridorRepair.Rehearsal result,BooleanSupplier cancelled) {
            overlay=PathRouteESP.prepare(result,cancelled);
        }
        public void rehearsalReady(CorridorRepair.Rehearsal result) {
            rehearsal=result;
            PathRouteESP.show(overlay);
            choice=rejected=null;
            previewIndex=legIndex=localRetries=settleWait=0;
            repositioned.clear();
            routeProgress=result.initialProgress();
            rejoinWait=0;
            detour=null;
            if(rejoinPending) {
                events.accept("REJOIN_REHEARSAL_RESULT",Map.of("actual",position(),"startProgress",routeProgress,
                        "finalProgress",result.progress(),"status",result.reason(),"expanded",result.expanded(),
                        "remaining",result.open(),"blockedAt",result.blockedAt()));
                rejoinPending=false;
            }else triedEntrances.clear();
            step=lastActualCast=null;
            failed=false;
            terminalMovement=null;
            events.accept("REHEARSAL_END",Map.of("status",result.reason(),"verifiedChoices",result.choices().size(),"open",result.open(),"progress",result.progress(),
                    "deadEnds",result.deadEnds(),"lastGeometryCheck",result.lastGeometryCheck(),
                    "end",result.blockedAt(),"expanded",result.expanded(),"aim",forecast().aim()));
            for(var proposed:result.choices()) {
                var original=result.corridor().reference(proposed.reference());
                var row=new LinkedHashMap<String,Object>();
                row.put("reference",proposed.reference());row.put("originalY",original.from().y());
                row.put("rehearsedY",proposed.castOrigin()==null?proposed.actions().getFirst().from().y():proposed.castOrigin().feet().y());
                row.put("aimMs",proposed.aimMs());row.put("castTickAfterAimStart",proposed.castTick());row.put("reason",proposed.reason());
                row.put("steps",proposed.actions());row.put("jumpAssist",proposed.jumpAssist());
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
                    if(!initial.motion().grounded() || !startDetour(corridor,initial,terrain))fail("NO_CONNECTED_LOCAL_ACTION");
                    return false;
                }
                legIndex=0;
                settleWait=0;
                choiceTick=client.level.getGameTime();
                events.accept("CORRIDOR_ACTION",Map.of("segment",choice.reference(),"progress",routeProgress,"nextProgress",choice.progress(),
                        "reason",choice.reason(),"actual",position(),"aimMs",choice.aimMs(),"startTick",choiceTick,
                        "expectedCastTick",choiceTick+choice.castTick(),"actions",choice.actions()));
                begin(choice.actions().getFirst());
            }
            if(!completed())return false;
            if(failed)return false;
            if(step.movement()!=Movement.TRANSMISSION&&!accepts(step)) {
                fail("CORRIDOR_MOVEMENT_DIVERGED");
                return false;
            }
            var movement=step.movement();
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
                if(verified>=routeProgress+1 && Math.floor(verified)>Math.floor(routeProgress))triedEntrances.clear();
                routeProgress=verified;
                rejoinWait=0;
                repositioned.clear();
            } else if(corridor.reached(motion(),lastActualCast,new LivePathTerrain(client.level)))routeProgress=choice.progress();
            else if(choice.reason().equals("ADJUST_LAUNCH_POSITION")&&corridor.connected(position(),routeProgress,new LivePathTerrain(client.level)))
                repositioned.add(choice.last().to());
            else {fail("CORRIDOR_MOVEMENT_DID_NOT_REJOIN");return false;}
            choice=rejected=null;
            localRetries=0;
            while(previewIndex<rehearsal.choices().size()&&rehearsal.choices().get(previewIndex).progress()<=routeProgress+.01)previewIndex++;
            PathRouteESP.advance(previewIndex,routeProgress);
            return false;
        }
        private boolean startDetour(CorridorRepair corridor,CorridorRepair.Start start,LivePathTerrain terrain){
            if(triedEntrances.size()>=3)return false;
            var found=CorridorRejoin.find(corridor,start,routeProgress,terrain,triedEntrances,corridor.jumpHeight());
            var details=new LinkedHashMap<String,Object>();
            details.put("from",position());details.put("progress",routeProgress);details.put("expanded",found.expanded());
            details.put("remaining",found.open());details.put("firstEdges",found.firstEdges());
            details.put("entrances",found.entrances());details.put("selected",found.selected());details.put("actions",found.actions());
            events.accept("REJOIN_SEARCH",details);
            if(found.selected()==null)return false;
            triedEntrances.add(found.selected().point());
            release();
            choice=rejected=null;
            detour=found;
            detourStep=0;
            begin(found.actions().getFirst());
            return true;
        }

        private void advanceDetour(CorridorRepair corridor){
            boolean done=completed();
            if(!done&&!failed)return;
            Point actual=position();
            var expected=step.to();
            events.accept("REJOIN_STEP",Map.of("index",detourStep,"actual",actual,"target",expected,
                    "movement",step.movement(),"grounded",client.player.onGround(),"status",failed?failureReason():"COMPLETED"));
            if(failed || !accepts(step)){
                rejectDetour("REJOIN_MOVEMENT_DIVERGED");
                return;
            }
            release();
            step=null;
            if(++detourStep<detour.actions().size()){
                begin(detour.actions().get(detourStep));
                return;
            }
            double joined=corridor.confirmedRejoin(actual,routeProgress,new LivePathTerrain(client.level));
            if(!client.player.onGround()||joined<0){
                rejectDetour("REJOIN_ENTRANCE_NOT_CONFIRMED");
                return;
            }
            events.accept("REJOIN_CONFIRMED",Map.of("actual",actual,"plannedEntrance",detour.selected(),
                    "previousProgress",routeProgress,"newProgress",joined,"yaw",yaw(),"pitch",pitch()));
            detour=null;
            rejoinPending=true;
            continuation=new PathService.CorridorContinuation(corridor,joined);
        }

        private void rejectDetour(String reason){
            events.accept("REJOIN_FAILED",Map.of("actual",position(),"target",detour.selected().point(),"reason",reason));
            detour=null;
            release();
            step=null;
            failed=false;
            var initial=forecast();
            if(!initial.motion().grounded() || !startDetour(rehearsal.corridor(),initial,new LivePathTerrain(client.level)))
                fail(reason);
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
            if(++localRetries>3){fail(reason);return;}
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
            if (events==NO_EVENTS) return null;
            return new SearchAudit((event, data) -> {
                var fields=new LinkedHashMap<String,Object>(data);
                fields.put("planAttempt",attempt);
                events.accept("PLANNING_"+event,fields);
            });
        }
        @Override public int candidateWorkers(){return settings.effectiveWorkers();}
        @Override public boolean gpuAcceleration(){return settings.gpuAcceleration();}
        @Override public int rehearsalLimit(){
            return simulate?Math.max(1,Integer.getInteger("ug.path.rehearsalLimit",CorridorRepair.DEFAULT_SEARCH_LIMIT)):
                    CorridorRepair.DEFAULT_SEARCH_LIMIT;
        }
        @Override public void terminalPosition(Point measured,Point planned){
            var fields=new LinkedHashMap<String,Object>();
            fields.put("lastMeasuredFeet",measured);fields.put("plannedEnd",planned);
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
                if (elapsed>0) mouse.aim(client,aimYaw,aimPitch,settings.aim(PathSettings.Aim.SPEED),settings.aim(PathSettings.Aim.CURVE),elapsed);
                double error=ViewAngles.error(yaw(),pitch(),aimYaw,aimPitch);
                if (correction.ready(error,settings.aim(PathSettings.Aim.TOLERANCE),settings.aim(PathSettings.Aim.CORRECTION),now,()->aimDraw)) {
                    if (!aimReady) {alignedY=position().y();alignedAt=now;}
                    aimReady=true;
                } else aimReady=false;
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
            events.accept("REPLAN",Map.of("reason",reason,"attempt",attempt,"actual",position(),
                    "pathInputBlocked",PhysicalGameInput.pathBlocked(),"routeEspCleared",PathRouteESP.snapshot()==null,
                    "strategy",attempt==2&&settings.mode==Mode.WEIGHTED?Mode.NORMAL:settings.mode));
        }
        public boolean readyForReplan(){return client.player.onGround()||++settleTicks>=18;}
        public String failureReason(){return failureReason==null?"INPUT_UNAVAILABLE":failureReason;}
        private void fail(String reason){failed=true;failureReason=reason;}
        public boolean completed() {
            if(done||failed) return done;
            if(client.screen!=null||client.isPaused()||!client.isWindowActive()||!client.mouseHandler.isMouseGrabbed()) {keyboard.releaseAll();return false;}
            if(++ticks>200){fail(gate.pending()?"POSITION_CONFIRMATION_TIMEOUT":"ACTION_TIMEOUT");return false;}
            if (gate.pending()) {
                if (!confirmPosition()) return done;
                if (!downwardCast) return done;
            }
            if (PhysicalGameInput.beginPath(client,this)) {
                var fields=Map.<String,Object>of("actualFeet",position(),"firstAction",step.movement(),
                        "focusBlocked",PhysicalGameInput.focusReleased());
                Ultimate_Garden.LOGGER.info("Path INPUT_BLOCK_STARTED {}",fields);
                events.accept("INPUT_BLOCK_STARTED",fields);
            }
            if(step.movement()==Movement.TRANSMISSION) cast(); else move();
            return done;
        }
        private void cast() {
            if (!canCast()){fail("TELEPORT_TRANSPORT_OR_WEAPON_UNAVAILABLE");return;}
            if (choice.jumpAssist()) {
                if(!jumpPressed&&client.player.onGround()) {keyboard.keyDown("SPACE");jumpPressed=true;}
                else keyboard.keyUp("SPACE");
                if(!client.player.onGround())seenAir=true;
            }
            if (!align(step.yaw(),step.pitch())) return;
            if(choice.jumpAssist() && (!seenAir||client.player.onGround())){
                if(seenAir)recoverCast("JUMP_WINDOW_PASSED");
                else if(ticks>=5)recoverCast("JUMP_DID_NOT_TAKE_OFF");
                return;
            }
            if (!gate.ready(System.nanoTime(),settings.teleportCooldownMs())) return;
            Point feet=position();
            double yaw=yaw(),pitch=pitch(),distance=step.castDistance();
            if(client.player.onGround())confirmedTravel=0;
            if(confirmedTravel+distance>180 && !(pitch>=89&&distance<=6.1)){
                recoverCast("LANDING_REQUIRED");
                return;
            }
            var terrain=new LivePathTerrain(client.level);
            Point actualLanding=TransmissionModel.landing(feet,yaw,pitch,distance);
            var corridor=rehearsal.corridor();
            pendingProgress=corridor.castProgress(feet,actualLanding,yaw,pitch,distance,routeProgress,terrain);
            if(!tryDispatch(new ClientTerrain(client.level),feet,yaw,pitch,distance,step.to(),false)){
                recoverCast("CAST_RECHECK_FAILED");
                return;
            }
            lastActualCast=new Segment(Movement.TRANSMISSION,feet,actualLanding,yaw,pitch,0,0,distance);
        }
        private boolean canCast() {
            return transmission&&settings.transmission&&(simulate?client.getSingleplayerServer()!=null:
                    equipped.getAsBoolean()&&!client.player.isShiftKeyDown()&&!client.options.keyShift.isDown());
        }
        private boolean confirmPosition() {
            if (positionSequence==confirmationSequence) return false;
            gate.confirm();
            mouse.consumed();
            events.accept("POSITION_CONFIRMATION",Map.of("predicted",predicted,"actual",lastPositionUpdate,
                    "actualY",lastPositionUpdate.y(),"delayMs",(System.nanoTime()-dispatchTime)/1e6,"downwardBurst",downwardCast));
            boolean near=lastPositionUpdate.distance(predicted)<=.25;
            if(!downwardCast&&choice!=null){
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
            if(!downwardCast) done=true;
            return !failed;
        }
        private boolean tryDispatch(ClientTerrain terrain,Point feet,double yaw,double pitch,double distance,Point expected,boolean burst) {
            var raw=TransmissionModel.raw(feet,yaw,pitch,distance);
            predicted=TransmissionModel.landing(feet,yaw,pitch,distance);
            var segment=terrain.transfer(feet,raw);
            var arrival=terrain.transfer(raw,predicted);
            boolean inCorridor=burst?predicted.equals(expected):pendingProgress>routeProgress;
            boolean legal=inCorridor && !predicted.equals(feet) && segment.knowledge()==Knowledge.CLEAR && arrival.knowledge()==Knowledge.CLEAR;
            var details=new LinkedHashMap<String,Object>();
            details.put("planningY",choice==null?step.from().y():rehearsal.corridor().reference(choice.reference()).from().y());
            details.put("rehearsedY",choice==null||choice.castOrigin()==null?step.from().y():choice.castOrigin().feet().y());
            details.put("corridorSegment",choice==null?-1:choice.reference());details.put("castTick",client.level.getGameTime());
            details.put("expectedCastTick",choice==null?client.level.getGameTime():choiceTick+choice.castTick());
            details.put("theoreticalAimMs",choice==null?0:choice.aimMs());
            String correctionReason=!inCorridor?"OUTSIDE_CONNECTED_CORRIDOR":
                    segment.knowledge()!=Knowledge.CLEAR?"CAST_RAY_"+segment.knowledge():
                    arrival.knowledge()!=Knowledge.CLEAR?"QUANTIZED_ARRIVAL_"+arrival.knowledge():
                    burst?"VERTICAL_DESCENT":!predicted.equals(expected)?"CONNECTED_LANDING_OFFSET":choice.reason();
            details.put("correctionReason",correctionReason);details.put("aimStartY",startAimY);details.put("alignedY",alignedY);
            details.put("castY",feet.y());details.put("predictedY",predicted.y());details.put("origin",feet);
            details.put("predicted",predicted);details.put("planned",step.to());details.put("yaw",yaw);details.put("pitch",pitch);
            details.put("segment",segment);details.put("arrival",arrival);details.put("compatibleWithPlannedAction",inCorridor);
            details.put("aimMs",(alignedAt-aimStarted)/1e6);details.put("postAlignmentMs",(System.nanoTime()-alignedAt)/1e6);
            details.put("downwardBurst",burst);
            if (!legal) {if (!burst)events.accept("CAST_RECHECK_FAILED",details);return false;}
            confirmationSequence=positionSequence;
            if(simulate) client.player.connection.sendCommand("tp @s "+predicted.x()+" "+predicted.y()+" "+predicted.z());
            else if(!mouse.click(client)) return true;
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
            int tick=start.ready().castTick(estimate.ms());
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
            if (!simulate && (!equipped.getAsBoolean() || p.isShiftKeyDown() || client.options.keyShift.isDown())) return;
            double retainedYaw=aimActive?aimYaw:yaw();
            if(descentSample==null||descentSample.sequence()!=positionSequence)
                descentSample=new DescentSample(forecast(),client.level.getGameTime(),positionSequence);
            if (!align(retainedYaw,90) || !gate.ready(System.nanoTime(),settings.teleportCooldownMs())) return;
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
