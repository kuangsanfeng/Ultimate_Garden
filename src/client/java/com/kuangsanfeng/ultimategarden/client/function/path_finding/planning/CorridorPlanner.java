package com.kuangsanfeng.ultimategarden.client.function.path_finding.planning;

import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.TransmissionModel;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.FlightTiming;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.RouteCastTiming;

import java.util.*;
import java.util.function.BooleanSupplier;

import static com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.*;

/** Searches space without headings. The bounded action search runs only around a validated centerline. */
public final class CorridorPlanner {
    public static final int RADIUS = 15;
    public static final int CORRIDOR_MODE_RADIUS = 25;
    public record Timing(double cooldownMs, double speed, double rotationPerSecond, double confirmationMs) {
        public Timing {
            if (!Double.isFinite(cooldownMs) || cooldownMs < 0 || !Double.isFinite(speed) || speed <= 0
                    || !Double.isFinite(rotationPerSecond) || rotationPerSecond <= 0
                    || !Double.isFinite(confirmationMs) || confirmationMs < 0) throw new IllegalArgumentException("Invalid timing");
        }
        public static Timing defaults() { return new Timing(20, 4.3, 180, 50); }
    }
    public record Estimate(double totalMs, double rotationMs, double dispatchAndConfirmationMs, double movementMs, int casts) {}
    private record Cell(Point point, double cost, double priority, Cell parent) {}
    private record State(Point point, double yaw, double pitch, double travel) {}
    private record Action(State state, double cost, double rank, int anchor, Action parent, List<Segment> edges) {}
    private record Candidate(String strategy, List<Segment> actions, Estimate time, double cost) {}
    private static final int[][] AXES = {{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,1,0},{0,-1,0}};

    private final Terrain terrain;
    private final Options options;
    private final Timing timing;
    private final BooleanSupplier cancelled;
    private final SearchAudit audit;
    private final CandidateWorkers workers;
    private final SearchRegion region;
    private final GpuCollision gpu;
    private long coarseChecks,groundChecks;
    private final Map<Point, Boolean> clearance = new HashMap<>();
    private long coarse, local, localChecks, queuePeak, probes;
    private long coarseReopened, localReopened;
    private int peakActiveBranches, splitBranches;
    private boolean branchBatch;

    private boolean robust(){return options.mode()!=Mode.CORRIDOR;}

    public CorridorPlanner(Terrain terrain, Options options, Timing timing, BooleanSupplier cancelled, SearchAudit audit) {
        this(terrain,options,timing,cancelled,audit,new CandidateWorkers(1),null,null);
    }

    CorridorPlanner(Terrain terrain,Options options,Timing timing,BooleanSupplier cancelled,SearchAudit audit,CandidateWorkers workers,SearchRegion region,GpuCollision gpu) {
        this.terrain = terrain;
        this.options = options;
        this.timing = timing;
        this.cancelled = cancelled;
        this.audit = audit;
        this.workers = workers;
        this.region = region;
        this.gpu = gpu;
    }

    private double initialTravel, yaw, pitch;
    private boolean retainPartial, air;
    private Point start, requested, goal;
    private SpaceSearch spaceSearch;
    private final List<ActionSearch> actionSearches = new ArrayList<>();
    private final List<ActionSearch> maskedActions = new ArrayList<>();
    private final List<Candidate> partial = new ArrayList<>();
    private PathPlanner.GraphSearch groundSearch;
    private Result groundResult;
    private long roundAllowance;
    private boolean launchBlocked, launchSucceeded;
    private final Map<GpuCollision.Cast, LaunchCheck> launchChecks=new java.util.concurrent.ConcurrentHashMap<>();

    private enum LaunchCheck { CLEAR, NO_CLEARANCE, COLLISION_TO_RAW, COLLISION_TO_LANDING, UNKNOWN }
    private LaunchCheck checkLaunch(GpuCollision.Cast ray) {
        var check=terrain.motionSweep(ray.landing(),ray.landing());
        if(check==Terrain.MotionCheck.UNKNOWN)return LaunchCheck.UNKNOWN;
        if(check!=Terrain.MotionCheck.CLEAR)return LaunchCheck.NO_CLEARANCE;
        check=terrain.motionSweep(ray.start(),ray.raw());
        if(check==Terrain.MotionCheck.UNKNOWN)return LaunchCheck.UNKNOWN;
        if(check!=Terrain.MotionCheck.CLEAR)return LaunchCheck.COLLISION_TO_RAW;
        check=terrain.motionSweep(ray.raw(),ray.landing());
        if(check==Terrain.MotionCheck.UNKNOWN)return LaunchCheck.UNKNOWN;
        return check==Terrain.MotionCheck.CLEAR?LaunchCheck.CLEAR:LaunchCheck.COLLISION_TO_LANDING;
    }

    public Result plan(Point start, Point requested, double yaw, double pitch) {
        Result immediate = begin(start, requested, yaw, pitch, 0, false);
        return immediate == null ? advanceRound() : immediate;
    }

    /** Returns an immediate answer or initializes frontiers owned by this origin. */
    Result begin(Point start, Point requested, double yaw, double pitch, double travel, boolean retainPartial) {
        this.start = start;
        this.requested = requested;
        this.yaw = yaw;
        this.pitch = pitch;
        this.initialTravel = travel;
        this.retainPartial = retainPartial;
        goal = com.kuangsanfeng.ultimategarden.client.function.path_finding.TargetCoordinates.centered(requested);
        if (!terrain.clear(start) || !terrain.clear(goal))
            return failure(requested, goal, start, terrain.missing().isEmpty() ? Status.NO_SPACE_CORRIDOR : Status.MAP_MISSING, "ENDPOINT_REJECTED");
        air = !terrain.supported(goal);
        if (air && options.projectAir()) {
            var projected = project(goal);
            if (projected.isEmpty()) return failure(requested, goal, start, Status.NO_SPACE_CORRIDOR, "NO_SAFE_PROJECTION");
            goal = projected.get();
            air = false;
        }
        if (cancelled.getAsBoolean()) return failure(requested, goal, start, Status.CANCELLED, "CANCELLED");
        if (start.distance(goal) < .1 || (air && options.transmission() && start.distance(goal) <= options.distance()/2))
            return succeed(requested, goal, start, new Candidate("ALREADY_AT_TARGET", List.of(), estimate(List.of(),yaw,pitch,timing),0));
        if (!air && terminalDrop(start,goal))
            return succeed(requested,goal,start,candidate("TERMINAL_NATURAL_LANDING",
                    List.of(segment(Movement.FALL,start,goal,yaw,90,0)),yaw,pitch));
        if (!options.transmission()) return null;

        if (!air && start.x()==goal.x() && start.z()==goal.z() && start.y()>goal.y()+options.distance()
                && terrain.sweep(start,goal)) {
            var descent=candidate("CONTROLLED_DESCENT",List.of(segment(Movement.FALL,start,goal,yaw,90,0)),yaw,pitch);
            if (options.mode()!=Mode.WEIGHTED) return succeed(requested,goal,start,descent);
            initialCandidates.add(descent);
        }
        if (options.mode() != Mode.WEIGHTED && !air) {
            var direct = sameSight(start, goal, yaw, pitch);
            if (direct != null){
                if(!robust())return succeed(requested,goal,start,direct);
                if(quality(direct).worstRisk()==0)return choose(List.of(direct));
                initialCandidates.add(direct);
            }
        }
        var simple = sky(List.of(start, goal), 0, 1);
        if (!simple.isEmpty()) actionSearches.add(new ActionSearch("SIMPLE_CLEARANCE",simple,air));
        emit("CLEARANCE_PROBES", Map.of("stage","SIMPLE_CLEARANCE","foundGeometry",!simple.isEmpty(),"probes",probes));
        spaceSearch = new SpaceSearch(start,goal);
        return null;
    }

    private final List<Candidate> initialCandidates = new ArrayList<>();
    private static final long TRANSFER_SLICE = 4096;

    int expandRegion() {
        int restored=spaceSearch==null?0:spaceSearch.restore();
        for(var search:actionSearches)restored+=search.restore();
        for(var search:maskedActions){
            int n=search.restore();
            restored+=n;
            if(n>0)actionSearches.add(search);
        }
        maskedActions.clear();
        if(groundSearch!=null)restored+=groundSearch.restoreRegion();
        return restored;
    }

    private boolean allowed(Point p){return region==null||region.contains(p);}

    long expanded() { return coarse + local; }
    long candidateChecks(){return coarseChecks+localChecks+groundChecks;}
    private void progress(String stage,int open,Point at){
        if(audit!=null&&(expanded()&63)==1) audit.sample(stage,expanded(),candidateChecks(),open,at);
    }

    Map<String,Object> frontiers() {
        var live = new LinkedHashMap<String,Object>();
        if (spaceSearch!=null && spaceSearch.hasFrontier())
            live.put("FLIGHT_SPACE", Map.of("forward",spaceSearch.forward.open.size(),"reverse",spaceSearch.reverse.open.size(),
                    "forwardVisited",spaceSearch.forward.best.size(),"reverseVisited",spaceSearch.reverse.best.size()));
        for (var action:actionSearches) if (action.hasFrontier())
            live.put(action.strategy,Map.of("open",action.open.size(),"visited",action.best.size()));
        if (groundSearch==null && spaceSearch!=null && !air)
            live.put("WALKING_CORRIDOR",Map.of("state","PENDING"));
        else if (groundSearch!=null && groundSearch.hasFrontier())
            live.put("WALKING_CORRIDOR",Map.of("open",groundSearch.openSize(),"expanded",groundSearch.expanded()));
        return live;
    }

    Result advanceRound() { return advanceRound(options.budget(),false); }

    Result advanceRound(long allowance, boolean trialPrefix) {
        long before = expanded();
        roundAllowance=allowance;
        long available = allowance;
        if (spaceSearch==null) {
            var candidate = walk(available);
            if (candidate!=null) return succeed(requested,goal,start,candidate);
            return incomplete(!frontiers().isEmpty());
        }

        var candidates = new ArrayList<>(initialCandidates);
        initialCandidates.clear();
        refineActions(candidates,before,Math.min(5000,available/8));
        if (!candidates.isEmpty() && options.mode()!=Mode.WEIGHTED&&
                (!robust()||candidates.stream().anyMatch(c->quality(c).worstRisk()==0))) return choose(candidates);

        available = remaining(before);
        long reserve = available / 10;
        long coarseLimit = Math.min(available, available - reserve * (Math.abs(start.y()-goal.y())>=80 ? 1 : 4));
        if (spaceSearch.hasFrontier() && coarseLimit>0) loadCorridor(coarseLimit);
        refineActions(candidates,before,12000);
        if (!candidates.isEmpty()) {
            if(robust()&&!air&&remaining(before)>0&&candidates.stream().noneMatch(c->quality(c).worstRisk()==0)){
                var walking=walk(Math.min(1200,remaining(before)));
                if(walking!=null)candidates.add(walking);
            }
            return choose(candidates);
        }
        if (trialPrefix && blockedLaunch()) return blockedPrefix();
        if (retainPartial && !cancelled.getAsBoolean()) searchPrefixes(before);

        available = remaining(before);
        if (available>0 && !cancelled.getAsBoolean()) {
            var candidate = walk(Math.max(1,available/2));
            if (candidate!=null) return succeed(requested,goal,start,candidate);
        }

        // These first-pass quotas reserve opportunities for the other searches.
        // Only the shared round budget limits continued expansion of a live frontier.
        while (remaining(before)>0 && !cancelled.getAsBoolean()) {
            var live = frontiers();
            if (live.isEmpty()) break;
            long previous = expanded();
            emit("BUDGET_TRANSFER",Map.of("remaining",remaining(before),"expandedInRound",previous-before,"frontiers",live));
            if (spaceSearch.hasFrontier() && actionSearches.isEmpty())
                loadCorridor(Math.min(TRANSFER_SLICE,remaining(before)));
            refineActions(candidates,before,TRANSFER_SLICE);
            if (!candidates.isEmpty()) return choose(candidates);
            if (trialPrefix && blockedLaunch()) return blockedPrefix();
            if (remaining(before)>0 && groundSearch!=null && groundSearch.hasFrontier()) {
                var candidate = walk(Math.min(TRANSFER_SLICE,remaining(before)));
                if (candidate!=null) return succeed(requested,goal,start,candidate);
            }
            if (expanded()==previous && frontiers().isEmpty()) break;
        }
        return incomplete(!frontiers().isEmpty());
    }

    private long remaining(long before) { return roundAllowance - (expanded()-before); }

    private boolean blockedLaunch() {
        return launchBlocked && !launchSucceeded && !cancelled.getAsBoolean()
                && actionSearches.stream().noneMatch(ActionSearch::hasFrontier) && maskedActions.isEmpty();
    }

    private Result blockedPrefix() {
        return failure(requested,goal,start,Status.NO_LOCAL_ACTION,"PREFIX_FIRST_ACTION_EXHAUSTED");
    }

    private Result incomplete(boolean live) {
        if (cancelled.getAsBoolean()) return failure(requested,goal,start,Status.CANCELLED,"CANCELLED");
        if (live) return failure(requested,goal,start,Status.BUDGET_EXHAUSTED,"SHARED_NODE_BUDGET");
        if (!terrain.missing().isEmpty()) return failure(requested,goal,start,Status.MAP_MISSING,"FRONTIERS_EXHAUSTED_WITH_MISSING_MAP");
        if (spaceSearch==null) return failure(requested,goal,start,groundResult.status(),groundResult.reason());
        return failure(requested,goal,start,spaceSearch.foundCorridor?Status.NO_LOCAL_ACTION:Status.NO_SPACE_CORRIDOR,
                spaceSearch.foundCorridor?"ACTION_FRONTIERS_EXHAUSTED":"SPACE_FRONTIERS_EXHAUSTED");
    }

    private boolean terminalDrop(Point from,Point to) {
        double height=from.y()-to.y();
        return from.x()==to.x() && from.z()==to.z() && height>0 && height<=.6 && terrain.sweep(from,to);
    }

    private record Quality(double executionMs,int worstRisk,int worstActions,int longestRiskyAir,
                           int riskyActions,int groundActions,int flightActions) {}

    private Quality quality(Candidate candidate){
        double y=yaw,p=pitch,elapsed=0,cooldown=0;
        int worst=0,worstCount=0,streak=0,longest=0,risky=0,ground=0,flight=0;
        for(var step:candidate.actions){
            var aim=RouteCastTiming.estimate(
                    y,p,step.yaw(),step.pitch(),
                    FlightTiming.Aim.defaults());
            double aimMs=aim==null?10_000:aim.ms();
            int risk=0;
            double duration;
            if(step.movement()==Movement.TRANSMISSION){
                flight++;
                var ready=new FlightTiming.Ready(cooldown,0,0);
                int tick=RouteCastTiming.castTick(ready,aimMs,false);
                duration=tick*50+timing.confirmationMs();
                cooldown=Math.max(0,timing.cooldownMs()-timing.confirmationMs());
                var motion=FlightTiming.Motion.confirmed(step.from(),terrain);
                if(!motion.grounded()){
                    var expected=FlightTiming.predict(motion,tick,terrain);
                    var late=expected==null?null:FlightTiming.predict(expected,1,terrain);
                    if(expected==null||aim==null||castLanding(expected.feet(),aim.yaw(),aim.pitch(),step.castDistance())==null)risk=3;
                    else if(late==null||castLanding(late.feet(),aim.yaw(),aim.pitch(),step.castDistance())==null)risk=2;
                    else if(aimMs>=250&&!terrain.supported(step.to()))risk=1;
                }else{
                    var offset=new Point(.15,0,0);
                    if(castLanding(step.from().add(offset),step.yaw(),step.pitch(),step.castDistance())==null||
                            castLanding(step.from().add(new Point(0,0,.15)),step.yaw(),step.pitch(),step.castDistance())==null)risk=1;
                }
                streak=risk>0&&!motion.grounded()?streak+1:0;
                longest=Math.max(longest,streak);
            }else{
                boolean supported=terrain.supported(step.to());
                if(supported)ground++;else flight++;
                if(step.movement()==Movement.WALK&&(!supported||!terrain.clear(step.to().add(new Point(.15,0,0)))))risk=1;
                if(step.movement()==Movement.FALL&&!supported)risk=1;
                duration=aimMs+estimate(List.of(step),step.yaw(),step.pitch(),timing).movementMs();
                cooldown=Math.max(0,cooldown-duration);
                if(supported)streak=0;
            }
            if(risk>0){
                risky++;
                if(risk>worst){worst=risk;worstCount=1;}
                else if(risk==worst)worstCount++;
            }
            elapsed+=duration;
            y=step.yaw();p=step.pitch();
        }
        return new Quality(elapsed,worst,worstCount,longest,risky,ground,flight);
    }

    private Result choose(List<Candidate> candidates) {
        if(robust()){
            var qualities=new LinkedHashMap<Candidate,Quality>();
            candidates.forEach(c->qualities.put(c,quality(c)));
            double best=qualities.values().stream().mapToDouble(Quality::executionMs).min().orElseThrow();
            var preference=Comparator.comparingInt(Quality::worstRisk).thenComparingInt(Quality::worstActions)
                    .thenComparingInt(Quality::longestRiskyAir).thenComparingInt(Quality::riskyActions)
                    .thenComparingDouble(Quality::executionMs);
            var chosen=qualities.entrySet().stream().filter(e->e.getValue().executionMs()<=best*1.15)
                    .min(Map.Entry.comparingByValue(preference)).orElseThrow();
            emit("ROUTE_QUALITY",Map.of("strategy",chosen.getKey().strategy,"quality",chosen.getValue(),
                    "fastestDiscoveredMs",best,"admittedRatio",1.15,"completedCandidates",qualities.size(),
                    "peakActiveBranches",peakActiveBranches,"branchSplits",splitBranches));
            return succeed(requested,goal,start,chosen.getKey());
        }
        Comparator<Candidate> preference=Comparator.comparingDouble((Candidate c)->c.time.totalMs)
                .thenComparing(c->c.strategy.equals("CLEARANCE_SHORTCUT"));
        return succeed(requested,goal,start,candidates.stream().min(preference).orElseThrow());
    }

    private void loadCorridor(long quota) {
        emit("COARSE_PLANNING_START",Map.of());
        if (quota<=0 || actionSearches.stream().anyMatch(search -> !search.strategy.startsWith("SIMPLE_CLEARANCE"))) return;
        var line = spaceSearch.advance(quota);
        if (line.isEmpty()) return;
        actionSearches.add(new ActionSearch("FLIGHT_CORRIDOR",line,air));
        var shortcut = shortcut(line);
        if (!shortcut.line().isEmpty() && !shortcut.line().equals(line))
            actionSearches.add(new ActionSearch("CLEARANCE_SHORTCUT",shortcut.line(),air));
        emit("SHORTCUT_PROBES", Map.of("anchors",shortcut.anchors(),"clearancePoints",shortcut.line(),"mode",options.mode()));
    }

    private void refineActions(List<Candidate> candidates,long before,long quota) {
        if(quota>0&&remaining(before)>0&&actionSearches.stream().anyMatch(ActionSearch::hasFrontier))
            emit("ACTION_CALCULATION_START",Map.of());
        Candidate original = null, bypass = null;
        int width=robust()&&terrain.parallelQueries()&&(gpu==null||!gpu.enabled())?workers.parallelism():1;
        if(width>1&&quota>0&&!actionSearches.isEmpty()){
            // Seed one frontier expansion so the root can hand distinct successors to idle workers.
            if(actionSearches.size()==1&&actionSearches.getFirst().unsplitRoot()&&remaining(before)>0){
                var found=actionSearches.getFirst().advance(1);
                if(found!=null)candidates.add(found);
            }
            int existing=actionSearches.size();
            for(int i=0;i<existing&&actionSearches.size()<width;i++){
                var split=actionSearches.get(i).split();
                if(split!=null){actionSearches.add(split);splitBranches++;}
            }
            var batch=actionSearches.stream().filter(ActionSearch::hasFrontier)
                    .limit(Math.min(width,Math.max(0,remaining(before)))).toList();
            if(!batch.isEmpty()){
                long lease=Math.min(quota,remaining(before)/batch.size());
                var output=new Candidate[batch.size()];
                peakActiveBranches=Math.max(peakActiveBranches,batch.size());
                branchBatch=true;
                try{workers.check(batch.size(),2,i->output[i]=batch.get(i).advance(lease));}
                finally{
                    branchBatch=false;
                    for(var frontier:batch)frontier.mergeCounters();
                }
                for(var found:output)if(found!=null)candidates.add(found);
            }
            emit("BRANCH_BATCH",Map.of("workerLimit",width,"activeBranches",batch.size(),"peakActiveBranches",peakActiveBranches,
                    "splitBranches",splitBranches,"remainingInRound",remaining(before)));
        }else for (var search:actionSearches) {
            long allowance = Math.min(quota,remaining(before));
            if (allowance<=0) break;
            peakActiveBranches=Math.max(peakActiveBranches,1);
            var found = search.advance(allowance);
            if (found==null) continue;
            candidates.add(found);
            if (found.strategy.equals("FLIGHT_CORRIDOR")) original=found;
            if (found.strategy.equals("CLEARANCE_SHORTCUT")) bypass=found;
        }
        if (original!=null && bypass!=null) emit("SHORTCUT_COMPARISON",Map.of("original",original.time,"shortcut",bypass.time,
                "mode",options.mode(),"reason",options.mode()==Mode.WEIGHTED?"COMPARE_BOTH_WEIGHTS":
                        preferShortcut(original.time,bypass.time)?"SHORTCUT_FASTER":"KEEP_ORIGINAL_ON_EQUAL_OR_FASTER"));
        actionSearches.removeIf(search->{
            if (search.hasFrontier()) return false;
            addPartial(search.partial());
            if(!search.masked.isEmpty())maskedActions.add(search);
            return true;
        });
    }

    private void searchPrefixes(long before) {
        var matching = spaceSearch.prefixCandidates();
        if (matching.isEmpty()) return;
        long quota = Math.min(2500, remaining(before)/2/matching.size());
        if (quota<=0) return;
        emit("ACTION_CALCULATION_START",Map.of());
        for (var node:matching) {
            var search = new ActionSearch("FORWARD_PREFIX",cellPath(node),false);
            var found = search.advance(quota);
            addPartial(found==null?search.partial():found);
        }
    }

    private void addPartial(Candidate path) {
        if (retainPartial && path!=null && !path.actions.isEmpty()) partial.add(path);
    }

    private Candidate walk(long quota) {
        emit("COARSE_PLANNING_START",Map.of());
        if (groundSearch==null) {
            var groundOptions = new Options(options.mode(),false,false,options.distance(),options.jumpHeight(),options.budget(),options.weights());
            groundSearch = new PathPlanner().groundSearch(start,goal,yaw,pitch,groundOptions,terrain,cancelled,region);
        }
        long previous = groundSearch.expanded(), previousChecks = groundSearch.checks();
        if(audit!=null) groundSearch.observe(p->{audit.expanded("WALKING_CORRIDOR",p.point());audit.sample("WALKING_CORRIDOR",expanded()+p.expanded()-previous,
                candidateChecks()+p.checks()-previousChecks,p.open(),p.point());});
        groundResult = groundSearch.advance(quota);
        groundChecks += groundSearch.checks()-previousChecks;
        long used = groundSearch.expanded()-previous;
        local += used;
        emit("STAGE",Map.of("stage","WALKING_CORRIDOR","status",groundResult.status(),"reason",groundResult.reason(),
                "expanded",used,"stageExpanded",groundSearch.expanded(),"allowance",quota,"open",groundSearch.openSize()));
        if (groundResult.status()==Status.ARRIVED) {
            var steps=options.transmission()?assistWalk(groundResult.segments(),yaw,pitch):groundResult.segments();
            return candidate("WALKING_CORRIDOR",steps,yaw,pitch);
        }
        return null;
    }

    private Optional<Point> project(Point goal) {
        var ground=terrain.ground(goal);
        if (ground.isPresent()) return ground;
        // A dangerous floor can reject the whole column. Consider adjacent safe columns.
        for(int radius=1;radius<=5;radius++) {
            Point nearest=null;
            for(int x=-radius;x<=radius;x++) for(int z=-radius;z<=radius;z++) {
                if(Math.max(Math.abs(x),Math.abs(z))!=radius)continue;
                var p=terrain.ground(goal.add(new Point(x,0,z)));
                if(p.isPresent()&&(nearest==null||goal.distance(p.get())<goal.distance(nearest)))nearest=p.get();
            }
            if(nearest!=null)return Optional.of(nearest);
        }
        return Optional.empty();
    }

    private List<Segment> assistWalk(List<Segment> steps,double yaw,double pitch) {
        var result=new ArrayList<Segment>();
        for (int i=0;i<steps.size();) {
            var first=steps.get(i);
            Segment shortcut=null;
            int next=i+1;
            if(first.movement()==Movement.WALK)for(int j=i+1;j<Math.min(steps.size(),i+32);j++){
                var last=steps.get(j);
                if(last.movement()!=Movement.WALK)break;
                var q=last.to();
                if(Math.abs(q.y()-first.from().y())>Math.ceil(2+options.jumpHeight()))continue;
                double[] angle=TransmissionModel.toward(first.from(),q);
                if(!q.equals(checkedCast(first.from(),angle[0],angle[1],options.distance()))||!terrain.supported(q))continue;
                var candidate=cast(first.from(),q,angle[0],angle[1],options.distance());
                var original=steps.subList(i,j+1);
                // Include the first shared successor to price turning back into the existing route.
                var alternative=new ArrayList<Segment>(List.of(candidate));
                if(j+1<steps.size()){original=steps.subList(i,j+2);alternative.add(steps.get(j+1));}
                var walking=candidate("WALK",original,yaw,pitch);
                var teleport=candidate("TELEPORT",alternative,yaw,pitch);
                if(options.mode()!=Mode.WEIGHTED?preferShortcut(walking.time,teleport.time):teleport.cost<walking.cost) {
                    shortcut=candidate;
                    next=j+1;
                }
            }
            Segment chosen=shortcut==null?first:shortcut;
            result.add(chosen);yaw=chosen.yaw();pitch=chosen.pitch();i=next;
        }
        return result;
    }

    private static final class Frontier {
        final Point goal;
        final PriorityQueue<Cell> open=new PriorityQueue<>(Comparator.comparingDouble(Cell::priority));
        final Map<Point,Cell> best=new HashMap<>();
        final Set<Point> expanded=new HashSet<>();
        Frontier(Point start,Point goal) {
            this.goal=goal;
            offer(new Cell(start,0,start.distance(goal),null));
        }
        void offer(Cell cell) {best.put(cell.point,cell);open.add(cell);}
    }
    private static List<Point> cellPath(Cell node) {
        var result = new ArrayList<Point>();
        for (Cell cell = node; cell != null; cell = cell.parent) result.add(cell.point);
        Collections.reverse(result);
        return result;
    }

    List<List<Segment>> prefixes(Point endpoint) {
        for (var search:actionSearches) addPartial(search.partial());
        if (groundSearch!=null && !groundSearch.partialPath().isEmpty())
            addPartial(candidate("WALKING_PREFIX",groundSearch.partialPath(),yaw,pitch));
        return partial.stream().filter(p -> !p.actions.isEmpty()).sorted(
                Comparator.<Candidate>comparingLong(p -> (long)(p.actions.getLast().to().distance(endpoint)*4))
                        .thenComparingDouble(p -> options.mode()==Mode.WEIGHTED?p.cost:p.time.totalMs)
        ).map(Candidate::actions).distinct().toList();
    }

    private final class SpaceSearch {
        final Frontier forward, reverse;
        final int[] spacing;
        boolean foundCorridor;
        final List<Cell> forwardMasked=new ArrayList<>(),reverseMasked=new ArrayList<>();
        boolean reverseDeferred;

        SpaceSearch(Point start, Point goal) {
            forward=new Frontier(start,goal);
            reverse=new Frontier(goal,start);
            boolean largeDrop=Math.abs(start.y()-goal.y())>=80;
            if (!largeDrop && (region==null||!region.expanded())) {
                reverse.open.clear();
                reverseDeferred=true;
            }
            spacing=largeDrop?new int[]{1,4,12}:new int[]{1,4};
        }

        boolean hasFrontier() {
            discardStale(forward);
            discardStale(reverse);
            return !forward.open.isEmpty() || !reverse.open.isEmpty();
        }

        private void discardStale(Frontier frontier) {
            while (!frontier.open.isEmpty() && frontier.best.get(frontier.open.peek().point)!=frontier.open.peek())
                frontier.open.remove();
        }

        int restore(){
            int restored=0;
            if(reverseDeferred){
                reverse.offer(reverse.best.get(goal));
                reverseDeferred=false;
                restored++;
            }
            restored+=restore(forward,forwardMasked);
            restored+=restore(reverse,reverseMasked);
            return restored;
        }
        private int restore(Frontier frontier,List<Cell> masked) {
            int old=frontier.open.size();
            for(var cell:masked)offer(frontier,cell);
            masked.clear();
            return frontier.open.size()-old;
        }
        private void offer(Frontier frontier,Cell cell){
            var q=cell.point;
            var previous=frontier.best.get(q);
            if(previous!=null&&cell.cost>=previous.cost)return;
            coarseChecks++;
            if(clearance.computeIfAbsent(q,terrain::corridorClear)&&terrain.corridorSweep(cell.parent.point,q))
                frontier.offer(cell);
        }

        List<Point> advance(long allowance) {
            long baseline=coarse;
            List<Point> path=List.of();
            while (hasFrontier() && coarse-baseline<allowance && !cancelled.getAsBoolean()) {
                queuePeak=Math.max(queuePeak,forward.open.size()+reverse.open.size());
                boolean forwardTurn=reverse.open.isEmpty()||!forward.open.isEmpty()&&(coarse<2000 || coarse%128<64);
                Frontier primary=forwardTurn?forward:reverse, other=forwardTurn?reverse:forward;
                var node=primary.open.remove();
                var p=node.point;
                coarse++;
                if(audit!=null&&audit.scanning())audit.expanded("FLIGHT_SPACE",p);
                if(region!=null)region.visit(p,forwardTurn?0:1);
                progress("FLIGHT_SPACE",forward.open.size()+reverse.open.size(),p);
                if(!primary.expanded.add(p))coarseReopened++;
                var meeting=other.best.get(p);
                if (meeting==null && p.distance(primary.goal)<=4 && terrain.corridorSweep(p,primary.goal)) meeting=other.best.get(primary.goal);
                Point origin=centered(p);
                for (int[] axis:AXES) for (int step:spacing) {
                    var q=origin.add(new Point(axis[0]*step,axis[1]*step,axis[2]*step));
                    double cost=node.cost+p.distance(q);
                    var cell=new Cell(q,cost,cost+q.distance(primary.goal)*1.5,node);
                    if(allowed(q))offer(primary,cell);
                    else if(region.deferred(q))(forwardTurn?forwardMasked:reverseMasked).add(cell);
                }
                if (meeting!=null) {
                    Cell prefix=forwardTurn?node:meeting, suffix=forwardTurn?meeting:node;
                    var points=new ArrayList<>(cellPath(prefix));
                    for (Cell c=suffix;c!=null;c=c.parent) if (!points.getLast().equals(c.point)) points.add(c.point);
                    path=List.copyOf(points);
                    foundCorridor=true;
                    break;
                }
            }
            if (audit!=null) {
                String status=!path.isEmpty()?"SPACE_FOUND":cancelled.getAsBoolean()?"CANCELLED":hasFrontier()?"COARSE_BUDGET":"OPEN_LIST_EXHAUSTED";
                emit("STAGE",Map.of("stage","FLIGHT_CORRIDOR","status",status,"limit",allowance,"expanded",coarse-baseline,
                        "stageExpanded",coarse,"startSidePositions",forward.best.size(),"targetSidePositions",reverse.best.size(),
                        "open",forward.open.size()+reverse.open.size(),"centerline",path));
            }
            return path;
        }

        List<Cell> prefixCandidates() {
            if (!hasFrontier() || foundCorridor) return List.of();
            var nearest = Comparator.comparingDouble((Cell c) -> c.point.distance(goal)).thenComparingDouble(Cell::cost)
                    .thenComparingDouble(c -> c.point.x()).thenComparingDouble(c -> c.point.y()).thenComparingDouble(c -> c.point.z());
            var ordered = forward.best.values().stream().filter(c -> c.parent != null && forward.expanded.contains(c.point))
                    .filter(c -> c.point.distance(goal) < start.distance(goal)).sorted(nearest).toList();
            var distinct = new ArrayList<Cell>();
            for (var cell : ordered) {
                if (distinct.stream().noneMatch(c -> c.point.distance(cell.point) < 4)) distinct.add(cell);
                if (distinct.size()==4) break;
            }
            return distinct;
        }
    }

    private record Shortcut(List<Point> line,List<Point> anchors) {}
    private Shortcut shortcut(List<Point> line) {
        // Sample endpoints, turns and a bounded set of positions near either end.
        var result = List.<Point>of();
        var anchors = List.<Point>of();
        for (int i : new int[]{0,Math.min(3,line.size()-1), Math.min(7,line.size()-1)})
            for (int j : new int[]{line.size()-1,Math.max(0,line.size()-4),Math.max(0,line.size()-8)}) {
                if (i>=j) continue;
                var middle=sky(line,i,j);
                if (middle.isEmpty()) continue;
                var path = new ArrayList<>(line.subList(0,i));
                path.addAll(middle);
                path.addAll(line.subList(j+1,line.size()));
                if (result.isEmpty() || length(path)<length(result)) {
                    result=List.copyOf(path);
                    anchors=List.of(line.get(i),line.get(j));
                }
            }
        return new Shortcut(result,anchors);
    }

    private List<Point> sky(List<Point> base, int i, int j) {
        var from=base.get(i);
        var to=base.get(j);
        for (int rise : new int[]{0,4,12,24,36,60}) {
            double height=Math.max(from.y(),to.y())+rise;
            for (int offset : new int[]{0,-3,3}) {
                var a=new Point(from.x()+offset,height,from.z());
                var b=new Point(to.x()+offset,height,to.z());
                probes++;
                if (terrain.corridorSweep(from,a) && terrain.corridorSweep(a,b) && terrain.corridorSweep(b,to))
                    return List.of(from,a,b,to);
            }
        }
        return List.of();
    }

    private Candidate sameSight(Point start, Point goal, double yaw, double pitch) {
        var actions=new ArrayList<Segment>();
        double[] direction=TransmissionModel.toward(start,goal);
        Point p=start;
        double travel=initialTravel;
        for (int i=0;i<Math.min(1000,options.budget()) && !cancelled.getAsBoolean();i++) {
            double length=Math.min(p.distance(goal),options.distance());
            if (length<.1) return candidate("DIRECT",actions,yaw,pitch);
            var q=checkedCast(p,direction[0],direction[1],length);
            if (q==null || (travel+length>180 && !q.equals(goal))) return null;
            actions.add(cast(p,q,direction[0],direction[1],length));
            travel+=length;
            p=q;
        }
        return null;
    }

    private final class ActionSearch {
        final String strategy;
        final List<Point> line;
        final Point goal;
        final boolean air, prefix;
        final State origin;
        final PriorityQueue<Action> open = new PriorityQueue<>(Comparator.comparingDouble(Action::rank));
        final Map<List<Object>,Double> best = new HashMap<>();
        final Set<State> expandedActions = new HashSet<>();
        final List<MaskedAction> masked=new ArrayList<>();
        long branchExpanded,branchChecks,branchReopens,branchQueuePeak;
        boolean branchLaunchBlocked,branchLaunchSucceeded;
        Action closest;
        boolean complete;

        ActionSearch(String strategy,List<Point> line,boolean air) {
            this.strategy=strategy;
            this.line=line;
            this.goal=line.getLast();
            this.air=air;
            this.prefix=strategy.equals("FORWARD_PREFIX");
            origin=new State(line.getFirst(),yaw,pitch,initialTravel);
            best.put(localKey(origin),0.0);
            open.add(new Action(origin,0,0,0,null,List.of()));
        }

        ActionSearch(ActionSearch parent,Action seed){
            strategy=parent.strategy+"#"+(splitBranches+1);
            line=parent.line;goal=parent.goal;air=parent.air;prefix=parent.prefix;origin=parent.origin;
            best.put(localKey(seed.state),seed.cost);
            open.add(seed);
        }

        boolean unsplitRoot(){return !complete&&open.size()==1&&open.peek().parent==null;}

        ActionSearch split(){
            if(!hasFrontier()||open.size()<2)return null;
            var preferred=open.peek();
            var held=new ArrayList<Action>();
            Action seed=null;
            for(int i=0;i<12&&!open.isEmpty();i++){
                var next=open.remove();
                if(next!=preferred&&!next.edges.isEmpty()&&!preferred.edges.isEmpty()
                        &&(next.edges.getLast().movement()!=preferred.edges.getLast().movement()
                        ||Math.abs(next.state.point.y()-preferred.state.point.y())>=2
                        ||next.state.point.distance(preferred.state.point)>=5)){
                    seed=next;break;
                }
                held.add(next);
            }
            open.addAll(held);
            return seed==null?null:new ActionSearch(this,seed);
        }

        void mergeCounters(){
            local+=branchExpanded;localChecks+=branchChecks;localReopened+=branchReopens;
            queuePeak=Math.max(queuePeak,branchQueuePeak);
            launchBlocked|=branchLaunchBlocked;launchSucceeded|=branchLaunchSucceeded;
            branchExpanded=branchChecks=branchReopens=branchQueuePeak=0;
            branchLaunchBlocked=branchLaunchSucceeded=false;
        }

        boolean hasFrontier() {
            while (!open.isEmpty() && open.peek().cost>best.getOrDefault(localKey(open.peek().state),Double.POSITIVE_INFINITY))
                open.remove();
            return !complete && !open.isEmpty();
        }

        int restore(){
            int old=open.size();
            for(var pending:masked)addAction(open,best,pending.parent,line,pending.edges,pending.travel);
            masked.clear();
            return open.size()-old;
        }

        void offer(Action parent,List<Segment> edges,double travel){
            if(edges.isEmpty()||allowed(edges.getLast().to()))addAction(open,best,parent,line,edges,travel);
            else if(region.deferred(edges.getLast().to()))masked.add(new MaskedAction(parent,edges,travel));
        }

        Candidate partial() { return closest==null?null:candidate(strategy,actionPath(closest),yaw,pitch); }

        Candidate advance(long limit) {
            try{return advanceBranch(limit);}
            finally{if(!branchBatch)mergeCounters();}
        }

        private Candidate advanceBranch(long limit){
            while (hasFrontier() && branchExpanded<limit && !cancelled.getAsBoolean()) {
                branchQueuePeak=Math.max(branchQueuePeak,open.size());
                var node=open.remove();
                var p=node.state.point;
                branchExpanded++;
                if(region!=null)region.visit(p,2);
                if(!branchBatch&&audit!=null&&((expanded()+branchExpanded)&63)==1)
                    audit.sample(strategy,expanded()+branchExpanded,candidateChecks()+branchChecks,open.size(),p);
                if(audit!=null && !expandedActions.add(node.state))branchReopens++;
                if (retainPartial && node.parent!=null && p.distance(goal)<origin.point.distance(goal)
                        && (closest==null || p.distance(goal)<closest.state.point.distance(goal))) closest=node;
                if (p.distance(goal)<.1 || (air && (p.distance(goal)<=options.distance()/2 || crossesAirTarget(node,goal)))) {

                    complete=true;
                    emitStage("ACTIONS_FOUND",branchExpanded,branchChecks,limit);
                    return candidate(strategy,actionPath(node),yaw,pitch);
                }
                // A quantized cast can finish half a block above a slab; gravity completes the route.
                if(!prefix && !air && terminalDrop(p,goal)){
                    var steps=new ArrayList<>(actionPath(node));
                    steps.add(segment(Movement.FALL,p,goal,node.state.yaw,90,0));
                    complete=true;
                    emitStage("ACTIONS_FOUND",branchExpanded,branchChecks,limit);
                    return candidate(strategy,steps,yaw,pitch);
                }
                if (node.state.travel>=180-options.distance()) {
                    var landing=terrain.ground(p);
                    if (landing.isPresent() && terrain.sweep(p,landing.get())) {
                        Point q=landing.get();
                        if (p.distance(q)<.1) offer(node,List.of(),0);
                        else {
                            if (retainPartial && (prefix || initialTravel>0))
                                offer(node,List.of(segment(Movement.FALL,p,q,node.state.yaw,90,0)),0);
                            Point near=new Point(q.x(),Math.min(q.y()+6,p.y()),q.z());
                            var edges=new ArrayList<Segment>();
                            if (p.distance(near)>.05) edges.add(segment(Movement.FALL,p,near,node.state.yaw,90,0));
                            branchChecks++;
                            var reset=near.distance(q)>options.distance()?null:
                                    castLanding(near,node.state.yaw,90,near.distance(q));
                            if (q.equals(reset)) {
                                edges.add(cast(near,q,node.state.yaw,90,near.distance(q)));
                                offer(node,edges,0);
                            }
                        }
                    }
                }
                var aims=new LinkedHashSet<Point>();
                if (p.distance(goal)<=options.distance()) aims.add(goal);
                for (int i=Math.min(node.anchor+1,line.size()-1);i<line.size();i++) {
                    var anchor=line.get(i);
                    if (p.distance(anchor)>options.distance()*2 && i>node.anchor+3) break;
                    aims.add(anchor);
                }
                var targets=List.copyOf(aims);
                var probes=new Segment[targets.size()*9];
                var rays=new GpuCollision.Cast[probes.length];
                branchChecks+=probes.length;
                double finishDistance=Math.min(p.distance(goal),options.distance());
                workers.check(targets.size(),3,i->{
                    Point aim=targets.get(i);
                    double[] angle=TransmissionModel.toward(p,aim);
                    double[] offsets={0,-12,12};
                    for(int y=0;y<offsets.length;y++) for(int t=0;t<offsets.length;t++) {
                        double nextYaw=angle[0]+offsets[y],nextPitch=Math.clamp(angle[1]+offsets[t],-90,90);
                        double distance=!prefix&&aim.equals(goal)&&y==0&&t==0?finishDistance:options.distance();
                        if(distance<=0)continue;
                        Point q=TransmissionModel.landing(p,nextYaw,nextPitch,distance);
                        if(q.equals(p)||node.state.travel+distance>180&&(prefix||!q.equals(goal)))continue;
                        int slot=i*9+y*3+t;
                        probes[slot]=cast(p,q,nextYaw,nextPitch,distance);
                        rays[slot]=new GpuCollision.Cast(p,TransmissionModel.raw(p,nextYaw,nextPitch,distance),q);
                    }
                });
                int count=0;
                for(var ray:rays)if(ray!=null)count++;
                boolean root=retainPartial && node.parent==null && node.state.equals(origin) && !prefix;
                // Reuse exact first-step geometry checks when a different centerline asks for the same cast.
                int cacheBefore=root?launchChecks.size():0;
                var launchVerdicts=root?new LaunchCheck[rays.length]:null;
                var batch=gpu==null||root?null:gpu.submit(rays,count);
                var walk=new ArrayList<Segment>();
                if(terrain.supported(p)){
                    for(int[] axis:AXES)if(axis[1]==0){
                        var q=centered(p).add(new Point(axis[0],0,axis[2]));
                        double[] angle=TransmissionModel.toward(p,q);
                        if(terrain.supported(q)&&terrain.sweep(p,q))walk.add(segment(Movement.WALK,p,q,angle[0],0,0));
                    }
                }
                var results=GpuCollision.result(batch);
                if(results==null)workers.check(targets.size(),3,i->{
                    for(int j=i*9;j<i*9+9;j++){
                        var ray=rays[j];
                        if(ray==null)continue;
                        if(root){
                            launchVerdicts[j]=launchChecks.computeIfAbsent(ray,CorridorPlanner.this::checkLaunch);
                            if(launchVerdicts[j]!=LaunchCheck.CLEAR)probes[j]=null;
                        } else if(!(terrain.clear(ray.landing())&&terrain.sweep(ray.start(),ray.raw())&&terrain.sweep(ray.raw(),ray.landing())))
                            probes[j]=null;
                    }
                });
                for(int i=0;i<probes.length;i++)if(probes[i]!=null&&(results==null||results[i])){
                    var probe=probes[i];
                    offer(node,List.of(probe),node.state.travel+probe.castDistance());
                }
                for(var step:walk)offer(node,List.of(step),0);
                if(root){
                    boolean legal=hasFrontier();
                    branchLaunchSucceeded|=legal;
                    branchLaunchBlocked|=!legal && masked.isEmpty() && !strategy.equals("SIMPLE_CLEARANCE");
                    if(audit!=null){
                        var rejected=new EnumMap<LaunchCheck,Integer>(LaunchCheck.class);
                        for(var verdict:launchVerdicts)if(verdict!=null)rejected.merge(verdict,1,Integer::sum);
                        emit("FIRST_ACTION_CHECK",Map.of("strategy",strategy,"origin",p,"yaw",node.state.yaw,
                                "pitch",node.state.pitch,"castVerdicts",rejected,"groundEdges",walk.size(),
                                "admitted",open.size(),"masked",masked.size(),"reusedGeometryChecks",
                                count-(launchChecks.size()-cacheBefore),"target",goal));
                    }
                }
            }
            emitStage(cancelled.getAsBoolean()?"CANCELLED":hasFrontier()?"LOCAL_BUDGET":"NO_LOCAL_ACTION",
                    branchExpanded,branchChecks,limit);
            return null;
        }

        private void emitStage(String status,long expanded,long checks,long allowance) {
            emit("STAGE",Map.of("stage",strategy,"status",status,"expanded",expanded,"candidateChecks",checks,"allowance",allowance,
                    "open",open.size(),"visited",best.size(),"target",goal));
        }
    }

    private record MaskedAction(Action parent,List<Segment> edges,double travel) {}

    private static List<Segment> actionPath(Action node) {
        var route=new ArrayList<List<Segment>>();
        for (Action action=node;action!=null;action=action.parent) route.add(action.edges);
        Collections.reverse(route);
        return route.stream().flatMap(List::stream).toList();
    }

    private void addAction(PriorityQueue<Action> open, Map<List<Object>,Double> best, Action old, List<Point> line, List<Segment> edges, double travel) {
        Point p=edges.isEmpty()?old.state.point:edges.getLast().to();
        int nearest=old.anchor;
        double offset=Double.POSITIVE_INFINITY;
        for (int i=0;i<line.size()-1;i++) {
            double d=segmentDistance(p,line.get(i),line.get(i+1));
            if (d<=offset) {offset=d;nearest=i;}
        }
        if (offset>(options.mode()==Mode.CORRIDOR?CORRIDOR_MODE_RADIUS:RADIUS)) return;
        var last=edges.isEmpty()?null:edges.getLast();
        var state=new State(p,last==null?old.state.yaw:last.yaw(),last==null?old.state.pitch:last.pitch(),travel);
        double cost=old.cost;
        double y=old.state.yaw, pitch=old.state.pitch;
        for (var e:edges) {cost+=actionCost(e,y,pitch);y=e.yaw();pitch=e.pitch();}
        List<Object> key=localKey(state);
        if (cost>=best.getOrDefault(key,Double.POSITIVE_INFINITY)) return;
        best.put(key,cost);
        double remaining=p.distance(line.getLast())+offset*2;
        if (nearest+1<old.anchor) remaining+=25;
        open.add(new Action(state,cost,remaining+cost*(options.mode()==Mode.WEIGHTED?.07:.03),nearest,old,edges));
    }
    private List<Object> localKey(State state) {
        return robust()?List.of(state):List.of(state.point,(int)(state.travel/options.distance()));
    }

    private Point checkedCast(Point p,double yaw,double pitch,double distance) {
        localChecks++;
        if (distance<=0 || distance>options.distance()) return null;
        return castLanding(p,yaw,pitch,distance);
    }

    private Point castLanding(Point p,double yaw,double pitch,double distance) {
        var raw=TransmissionModel.raw(p,yaw,pitch,distance);
        var q=TransmissionModel.landing(p,yaw,pitch,distance);
        return !q.equals(p) && terrain.clear(q) && terrain.sweep(p,raw) && terrain.sweep(raw,q)?q:null;
    }

    private static boolean crossesAirTarget(Action node,Point target) {
        if (node.edges.isEmpty()) return false;
        var edge=node.edges.getLast();
        if (edge.movement()!=Movement.TRANSMISSION) return false;
        Point end=TransmissionModel.raw(edge.from(),edge.yaw(),edge.pitch(),edge.castDistance());
        double y=Math.floor(target.y());
        return new net.minecraft.world.phys.AABB(Math.floor(target.x()),y,Math.floor(target.z()),
                Math.floor(target.x())+1,y+1,Math.floor(target.z())+1)
                .clip(new net.minecraft.world.phys.Vec3(edge.from().x(),edge.from().y(),edge.from().z()),
                        new net.minecraft.world.phys.Vec3(end.x(),end.y(),end.z())).isPresent();
    }

    private Segment segment(Movement movement,Point from,Point to,double yaw,double pitch,double ascent) {
        return new Segment(movement,from,to,yaw,pitch,ascent,0);
    }
    private Segment cast(Point from,Point to,double yaw,double pitch,double distance) {
        return new Segment(Movement.TRANSMISSION,from,to,yaw,pitch,0,0,distance);
    }

    private double actionCost(Segment step,double yaw,double pitch) {
        var w=options.weights();
        double cost=step.movement()==Movement.TRANSMISSION?w.transmission():
                w.walk()*(Math.abs(step.from().x()-step.to().x())+Math.abs(step.from().z()-step.to().z()))
                        +w.jump()*step.ascent()+w.fall()*Math.max(0,step.from().y()-step.to().y());
        if (step.movement()==Movement.FALL && step.pitch()==90 && step.from().sameColumn(step.to()) && options.transmission())
            cost+=Math.ceil(Math.max(0,step.from().y()-step.to().y())/options.distance())*w.transmission();
        if ((step.movement()==Movement.FALL || step.movement()==Movement.TRANSMISSION)
                && step.pitch()==90 && step.from().sameColumn(step.to()) && step.to().y()<step.from().y()) return cost;
        return cost+w.turn()*Math.pow(TransmissionModel.angle(yaw,pitch,step.yaw(),step.pitch())/30,w.exponent());
    }

    private Candidate candidate(String strategy,List<Segment> steps,double yaw,double pitch) {
        var out=new ArrayList<Segment>();
        double cost=0;
        var baseTime=estimate(steps,yaw,pitch,timing);
        int estimatedDownwardCasts=0;
        if (options.transmission()) for (var step:steps)
            if (step.movement()==Movement.FALL && step.pitch()==90 && step.from().sameColumn(step.to()))
                estimatedDownwardCasts+=(int)Math.ceil(Math.max(0,step.from().y()-step.to().y())/options.distance());
        double downCastMs=estimatedDownwardCasts*Math.max(timing.cooldownMs,timing.confirmationMs);
        var time=new Estimate(baseTime.totalMs+downCastMs,baseTime.rotationMs,baseTime.dispatchAndConfirmationMs+downCastMs,
                baseTime.movementMs,baseTime.casts+estimatedDownwardCasts);
        for (var s:steps) {
            double value=actionCost(s,yaw,pitch);
            out.add(new Segment(s.movement(),s.from(),s.to(),s.yaw(),s.pitch(),s.ascent(),value,s.castDistance()));
            cost+=value;
            yaw=s.yaw();pitch=s.pitch();
        }
        return new Candidate(strategy,List.copyOf(out),time,cost);
    }

    public static boolean preferShortcut(Estimate original, Estimate shortcut) {
        return shortcut.totalMs()<original.totalMs();
    }

    public static Estimate estimate(List<Segment> steps,double yaw,double pitch,Timing timing) {
        double rotation=0, dispatch=0, move=0, total=0, previousCast=Double.NEGATIVE_INFINITY;
        int casts=0;
        for (var s:steps) {
            double turn=1000*TransmissionModel.angle(yaw,pitch,s.yaw(),s.pitch())/timing.rotationPerSecond();
            yaw=s.yaw();pitch=s.pitch();
            rotation+=turn;
            total+=turn;
            if (s.movement()==Movement.TRANSMISSION) {
                double wait=Math.max(0,previousCast+timing.cooldownMs()-total);
                previousCast=total+wait;
                dispatch+=wait+timing.confirmationMs();
                total+=wait+timing.confirmationMs();
                casts++;
            } else {
                double duration=1000*Math.hypot(s.from().x()-s.to().x(),s.from().z()-s.to().z())/timing.speed();
                if (s.movement()==Movement.FALL) duration+=1000*Math.sqrt(2*Math.max(0,s.from().y()-s.to().y())/32);
                if (s.movement()==Movement.JUMP) duration+=2000*Math.sqrt(2*s.ascent()/32);
                total+=duration;move+=duration;
            }
        }
        return new Estimate(total,rotation,dispatch,move,casts);
    }

    private Result succeed(Point requested,Point goal,Point start,Candidate chosen) {
        emit("DECISION",Map.of("strategy",chosen.strategy,"estimate",chosen.time,"cost",chosen.cost,"optimality","NOT_PROVEN","coarseExpanded",coarse,"localExpanded",local,"localChecks",localChecks,"queuePeak",queuePeak,"clearanceProbes",probes));
        emit("STATE_REOPENS",Map.of("space",coarseReopened,"local",localReopened,"walkingIncluded",false));
        return new Result(Status.ARRIVED,chosen.actions,requested,goal,chosen.actions.isEmpty()?start:chosen.actions.getLast().to(),
                chosen.cost,coarse+local,terrain.missing(),chosen.strategy);
    }
    private Result failure(Point requested,Point goal,Point start,Status status,String reason) {
        emit("DECISION",Map.of("strategy","NONE","reason",reason,"coarseExpanded",coarse,"localExpanded",local,"localChecks",localChecks,"queuePeak",queuePeak,"clearanceProbes",probes));
        emit("STATE_REOPENS",Map.of("space",coarseReopened,"local",localReopened,"walkingIncluded",false));
        if (!retainPartial && !terrain.missing().isEmpty() && (status==Status.NO_SPACE_CORRIDOR || status==Status.NO_LOCAL_ACTION))
            status=Status.MAP_MISSING;
        return new Result(status,List.of(),requested,goal,start,0,coarse+local,terrain.missing(),reason);
    }
    private synchronized void emit(String event,Map<String,Object> fields){if(audit!=null)audit.emit(event,fields);}
    private static Point centered(Point p){return new Point(Math.floor(p.x())+.5,p.y(),Math.floor(p.z())+.5);}
    private static double length(List<Point> points){double sum=0;for(int i=1;i<points.size();i++)sum+=points.get(i-1).distance(points.get(i));return sum;}
    private static double segmentDistance(Point p,Point a,Point b) {
        double x=b.x()-a.x(),y=b.y()-a.y(),z=b.z()-a.z(),len=x*x+y*y+z*z;
        double t=len==0?0:Math.clamp(((p.x()-a.x())*x+(p.y()-a.y())*y+(p.z()-a.z())*z)/len,0,1);
        return p.distance(new Point(a.x()+t*x,a.y()+t*y,a.z()+t*z));
    }
}
