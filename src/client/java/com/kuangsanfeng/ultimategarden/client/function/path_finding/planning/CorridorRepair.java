package com.kuangsanfeng.ultimategarden.client.function.path_finding.planning;

import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.FlightTiming;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.RouteCastTiming;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.FlightTiming.*;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.TransmissionModel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import com.kuangsanfeng.ultimategarden.client.platform.input.view.ViewAngles;
import java.util.function.BooleanSupplier;

import static com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.*;

/** Bounded forward repair of one selected corridor, used with both map and client collision queries. */
public final class CorridorRepair {
    public static final int DEFAULT_SEARCH_LIMIT=240;
    private static final int LOOK_AHEAD=4, MAX_DEPTH=140;
    private static final double MIN_PROGRESS=.025, ARRIVAL=.75;

    public record Start(Motion motion,double yaw,double pitch,double airborneDistance,Aim aim,Ready ready,
                        Double heightCorrection,int consecutiveClimbs,boolean upwardTurnUsed) {
        public Start(Motion motion,double yaw,double pitch,double airborneDistance,Aim aim,Ready ready){
            this(motion,yaw,pitch,airborneDistance,aim,ready,null,0,false);
        }
        public Start withHeightCorrection(Double offset){
            return new Start(motion,yaw,pitch,airborneDistance,aim,ready,offset,consecutiveClimbs,upwardTurnUsed);
        }
    }
    public record CastForecast(Point raw,Point landing,double yaw,double pitch) {}

    public record Choice(List<Segment> actions, int reference, double progress, Motion castOrigin,
                         int castTick, double aimMs, boolean jumpAssist, String reason,CastForecast forecast) {
        public Choice(List<Segment> actions,int reference,double progress,Motion castOrigin,int castTick,
                      double aimMs,boolean jumpAssist,String reason){
            this(actions,reference,progress,castOrigin,castTick,aimMs,jumpAssist,reason,null);
        }
        public Choice { actions=List.copyOf(actions); }
        public Segment last(){return actions.getLast();}
    }
    public record Rehearsal(CorridorRepair corridor,List<Choice> choices,String reason,Point blockedAt,int expanded,int open,double initialProgress,double progress,int deadEnds,
                            Map<String,Object> lastGeometryCheck,List<Start> actionInputs) {
        public Rehearsal(CorridorRepair corridor,List<Choice> choices,String reason,Point blockedAt,int expanded,int open,
                         double initialProgress,double progress,int deadEnds,Map<String,Object> lastGeometryCheck){
            this(corridor,choices,reason,blockedAt,expanded,open,initialProgress,progress,deadEnds,lastGeometryCheck,List.of());
        }
        public Rehearsal { choices=List.copyOf(choices);lastGeometryCheck=Map.copyOf(lastGeometryCheck);actionInputs=List.copyOf(actionInputs); }
        public boolean complete(){return reason.equals("VERIFIED");}
    }
    private record Node(Start start,double progress,int depth,double score,Node parent,Choice choice) {}
    private record Visit(Point feet,int yaw,int pitch,int reference,int travel) {}

    private final Result result;
    private final Options options;
    private final double cooldownMs, confirmationMs;
    private final boolean air;
    private final List<Segment> references;
    private final double[] lengths;
    private final BitSet landingReferences=new BitSet();

    public CorridorRepair(Result result,Options options,CorridorPlanner.Timing timing,Terrain terrain) {
        this.result=result;
        this.options=options;
        cooldownMs=timing.cooldownMs(); confirmationMs=timing.confirmationMs();
        references=result.segments();
        lengths=new double[references.size()+1];
        for(int i=0;i<references.size();i++){
            var segment=references.get(i);
            Point bend=referenceBend(segment);
            lengths[i+1]=lengths[i]+(bend==null?segment.from().distance(segment.to()):
                    segment.from().distance(bend)+bend.distance(segment.to()));
        }
        air=!options.projectAir()&&!terrain.supported(result.resolved());
        for(int i=0;i<references.size();i++){
            var step=references.get(i);
            if(step.movement()==Movement.TRANSMISSION && step.pitch()>=89 && step.castDistance()<=6.1
                    && step.from().sameColumn(step.to()) && step.from().y()>step.to().y()
                    && terrain.supported(step.to()))landingReferences.set(i);
        }
    }
    public boolean regional(){return options.mode()==Mode.CORRIDOR;}
    public double radius(){return regional()?CorridorPlanner.CORRIDOR_MODE_RADIUS:CorridorPlanner.RADIUS;}
    public double arc(double progress){
        if(references.isEmpty()||progress>=references.size())return lengths[references.size()];
        int i=referenceIndex(progress);
        return lengths[i]+(lengths[i+1]-lengths[i])*Math.max(0,progress-i);
    }
    public double centerlineDistance(Point feet){
        double best=Double.POSITIVE_INFINITY;
        for(var segment:references)best=Math.min(best,feet.distance(project(feet,segment).point()));
        return best;
    }

    private boolean insideLeg(Point from,Point to){
        if(!regional())return true;
        double distance=from.distance(to);
        int samples=Math.max(1,(int)Math.ceil(distance));
        double margin=distance/(2*samples);
        // Distance to the centerline is 1-Lipschitz, so adjacent samples cover the full interval.
        for(int i=0;i<=samples;i++){
            double t=(double)i/samples;
            var point=new Point(from.x()+(to.x()-from.x())*t,from.y()+(to.y()-from.y())*t,from.z()+(to.z()-from.z())*t);
            if(centerlineDistance(point)>radius()-margin)return false;
        }
        return true;
    }

    private double regionalProjection(Point feet,double progress,double allowance){
        double old=arc(progress),best=Double.POSITIVE_INFINITY,selected=-1;
        int first=Math.max(0,referenceIndex(progress)-1);
        for(int i=first;i<references.size()&&lengths[i]<=old+allowance;i++){
            var projection=project(feet,references.get(i));
            double distance=feet.distance(projection.point());
            double next=i+projection.fraction();
            if(distance<=radius()&&distance<best&&arc(next)<=old+allowance){
                best=distance;
                selected=next;
            }
        }
        return selected;
    }

    public Point goal(){return result.resolved();}
    public double jumpHeight(){return options.jumpHeight();}

    /** Match a supported foot position to the original centerline on the same floor. */
    double rejoinProgress(Point feet,double progress,Terrain terrain) {
        if(references.isEmpty()||!terrain.supported(feet))return -1;
        if(regional()){
            double next=regionalProjection(feet,progress,32);
            return next>=0&&arc(next)+2>=arc(progress)?Math.max(progress,next):-1;
        }
        int current=referenceIndex(progress);
        double best=-1,bestDistance=Double.POSITIVE_INFINITY;
        for(int i=Math.max(0,current-2);i<=Math.min(references.size()-1,current+LOOK_AHEAD+2);i++){
            var reference=references.get(i);
            var projection=project(feet,reference);
            var at=projection.point();
            double distance=feet.distance(at);
            if(Math.abs(feet.y()-at.y())>1.25 || distance>3 || !reconnect(feet,projection,reference,terrain))continue;
            double next=i+projection.fraction();
            if((next>=progress)!=(best>=progress) ? next>=progress : distance<bestDistance){
                best=next;
                bestDistance=distance;
            }
        }
        return best;
    }

    public double confirmedRejoin(Point feet,double progress,Terrain terrain) {
        return rejoinProgress(feet,progress,terrain);
    }
    public double originalHeight(double progress) {
        if(progress>=references.size())return goal().y();
        var segment=reference(referenceIndex(progress));
        double fraction=Math.clamp(progress-referenceIndex(progress),0,1);
        return segment.from().y()+fraction*(segment.to().y()-segment.from().y());
    }

    public boolean resumesOriginal(Start actual,double progress,Terrain terrain) {
        if(Math.abs(actual.motion().feet().y()-originalHeight(progress))>.6)return false;
        var next=choices(actual.withHeightCorrection(null),progress,terrain,2,()->false);
        return !next.isEmpty()&&next.stream().anyMatch(c->c.reason().equals("REFERENCE")||
                c.last().to().distance(reference(c.reference()).to())<=.6);
    }

    private Point guidance(Start start,int index){
        var anchor=references.get(index).to();
        return start.heightCorrection()==null||regional()||index==references.size()-1?anchor:
                anchor.add(new Point(0,start.heightCorrection(),0));
    }

    public Segment reference(int index){return references.get(index);}
    public List<Segment> referenceSegments(){return references;}

    public Rehearsal rehearse(Start initial,Terrain terrain,BooleanSupplier cancelled) {
        return rehearse(initial,terrain,cancelled,DEFAULT_SEARCH_LIMIT);
    }

    public Rehearsal rehearse(Start initial,Terrain terrain,BooleanSupplier cancelled,int limit) {
        return rehearse(initial,terrain,cancelled,limit,0);
    }

    public Rehearsal rehearse(Start initial,Terrain terrain,BooleanSupplier cancelled,int limit,double progress) {
        if(limit<1) throw new IllegalArgumentException("Invalid rehearsal limit");
        if(reached(initial.motion(),null,terrain)) return new Rehearsal(this,List.of(),"VERIFIED",initial.motion().feet(),0,0,progress,progress,0,Map.of());
        var queue=new PriorityQueue<Node>(Comparator.comparingDouble(Node::score));
        var visited=new HashSet<Object>();
        var root=new Node(initial,progress,0,0,null,null);
        var best=root;
        queue.add(root);
        int expanded=0,deadEnds=0;
        Map<String,Object> lastGeometryCheck=Map.of();
        while(!queue.isEmpty()&&expanded<limit&&!cancelled.getAsBoolean()) {
            var node=queue.remove();
            var state=node.start();
            Object key=regional()?new Visit(state.motion().feet(),(int)Math.round(ViewAngles.wrap(state.yaw())/4),
                    (int)Math.round(state.pitch()/4),referenceIndex(node.progress()),(int)state.airborneDistance()):
                    List.of(state,node.progress());
            if(!visited.add(key)) continue;
            expanded++;
            if(node.progress()>best.progress()) best=node;
            if(reached(state.motion(),node.choice()==null?null:node.choice().last(),terrain))
                return new Rehearsal(this,chain(node),"VERIFIED",state.motion().feet(),expanded,queue.size(),progress,node.progress(),deadEnds,lastGeometryCheck,actionInputs(node));
            if(node.depth()>=MAX_DEPTH) continue;
            var available=choices(state,node.progress(),terrain,7,cancelled);
            if(available.isEmpty()) {
                deadEnds++;
                lastGeometryCheck=terrain.diagnosticFailure();
            }
            for(var choice:available) {
                var next=after(state,choice,terrain);
                if(next==null){deadEnds++;continue;}
                double rank=-choice.progress()*12+node.depth()*.8+choice.castTick()*.12
                        +(next.motion().grounded()?0:1.2);
                if(regional()){
                    double turn=rotationCost(state,choice.last());
                    rank=-arc(choice.progress())*1.3+node.depth()*1.5+choice.castTick()*.16
                            +turn*.045+next.motion().feet().distance(goal())*.1;
                }
                queue.add(new Node(next,choice.progress(),node.depth()+1,rank,node,choice));
            }
        }
        return new Rehearsal(this,chain(best),cancelled.getAsBoolean()?"CANCELLED":
                !queue.isEmpty()&&expanded>=limit?"CORRIDOR_REHEARSAL_LIMIT":"CORRIDOR_REHEARSAL_BLOCKED",
                best.start().motion().feet(),expanded,queue.size(),progress,best.progress(),deadEnds,lastGeometryCheck,actionInputs(best));
    }
    private static List<Choice> chain(Node node) {
        var out=new ArrayList<Choice>();
        for(;node.choice()!=null;node=node.parent())out.add(node.choice());
        Collections.reverse(out);
        return out;
    }
    private static List<Start> actionInputs(Node node){
        var inputs=new ArrayList<Start>();
        for(;node.parent()!=null;node=node.parent())inputs.add(node.parent().start());
        Collections.reverse(inputs);
        return inputs;
    }

    public Start after(Start start,Choice choice,Terrain geometry) {
        var step=choice.last();
        var motion=Motion.confirmed(step.to(),geometry);
        boolean cast=step.movement()==Movement.TRANSMISSION;
        if(cast&&!regional()){
            motion=RouteCastTiming.expectedHandoff(motion,geometry).motion();
            if(motion==null)return null;
        }
        double travel=choice.castOrigin()!=null&&choice.castOrigin().grounded()?0:start.airborneDistance();
        if(cast) travel+=regional()?step.castDistance():choice.actions().stream()
                .filter(action->action.movement()==Movement.TRANSMISSION).mapToDouble(Segment::castDistance).sum();
        if(motion.grounded())travel=0;
        // Native aim for the following action starts on the confirmation tick. The CD ran in parallel.
        double cooldown=cast?Math.max(0,cooldownMs-confirmationMs):0;
        boolean climbing=cast&&step.to().y()-step.from().y()>=1&&step.pitch()<=-30;
        int climbs=regional()||!climbing?0:start.consecutiveClimbs()+1;
        return new Start(motion,step.yaw(),step.pitch(),travel,start.aim(),new Ready(cooldown,0,0),
                start.heightCorrection(),climbs,climbing&&start.upwardTurnUsed());
    }

    public List<Choice> choices(Start start,double progress,Terrain terrain,int limit,BooleanSupplier cancelled) {
        if(references.isEmpty()||cancelled.getAsBoolean())return List.of();
        if(regional())return regionalChoices(start,progress,terrain,limit,cancelled);
        Point feet=start.motion().feet();
        int first=referenceIndex(progress),last=Math.min(references.size()-1,first+LOOK_AHEAD);
        Segment ref=references.get(first);
        if(start.motion().grounded()&&!air && terminalZone(feet,progress)) {
            var ground=groundPath(feet,goal(),start.yaw(),start.pitch(),terrain,cancelled,100);
            if(!ground.isEmpty()&&ground.stream().allMatch(e->within(e.to())))
                return List.of(new Choice(ground,first,references.size(),null,0,0,false,"TERMINAL_GROUND_PATH"));
        }
        var moves=new ArrayList<Choice>();
        if(ref.movement()==Movement.FALL && feet.sameColumn(ref.to()) && feet.y()>ref.to().y()) {
            Point end=new Point(feet.x(),ref.to().y(),feet.z());
            if(terrain.sweep(feet,end)) {
                double next=advanceTo(end,progress,terrain);
                if (next>progress+MIN_PROGRESS)
                    moves.add(new Choice(List.of(new Segment(Movement.FALL,feet,end,start.yaw(),ref.pitch(),0,0)),
                            first,next,null,0,0,false,"VERTICAL_DESCENT"));
            }
        }
        if(!start.motion().grounded() && ref.movement()!=Movement.FALL) {
            var settled=FlightTiming.predict(start.motion(),20,terrain);
            if(settled!=null&&settled.grounded()&&settled.feet().sameColumn(feet)&&settled.feet().y()<feet.y()-.1) {
                double next=advanceTo(settled.feet(),progress,terrain);
                if(next>progress+MIN_PROGRESS) {
                    moves.add(new Choice(List.of(new Segment(Movement.FALL,feet,settled.feet(),start.yaw(),90,0,0)),
                            first,next,null,0,0,false,"CONNECTED_LANDING"));
                }
            }
        }
        if(start.motion().grounded()) {
            for(int i=first;i<=Math.min(last,first+1);i++){
                Point target=guidance(start,i);
                if(feet.distance(target)>5||!terrain.supported(target))continue;
                var ground=groundPath(feet,target,start.yaw(),start.pitch(),terrain,cancelled,42);
                double next=ground.isEmpty()?-1:advanceTo(ground.getLast().to(),progress,terrain);
                if(next>progress+MIN_PROGRESS && ground.stream().allMatch(e->within(e.to())))
                    moves.add(new Choice(ground,i,next,null,0,0,false,"LOCAL_GROUND_REPAIR"));
            }
        }
        if(!options.transmission())return moves.stream().limit(limit).toList();

        var ordinary=castChoices(start,progress,terrain,limit,cancelled,false);
        if(start.consecutiveClimbs()>=2&&!start.upwardTurnUsed()){
            var assist=upwardTurn(start,progress,terrain);
            if(assist!=null){
                var combined=new ArrayList<Choice>();
                combined.add(assist);
                combined.addAll(ordinary);
                ordinary=combined;
            }
        }
        if(moves.isEmpty() && start.motion().grounded() &&
                terminalZone(feet,progress)
                && ordinary.stream().noneMatch(c->reached(Motion.confirmed(c.last().to(),terrain),c.last(),terrain))) {
            var boosted=castChoices(start,progress,terrain,limit,cancelled,true);
            if(!boosted.isEmpty()){
                var combined=new ArrayList<>(ordinary);
                combined.addAll(boosted);
                ordinary=combined.stream().sorted(Comparator.<Choice>comparingDouble(Choice::progress).reversed()
                        .thenComparing(Choice::jumpAssist).thenComparingInt(Choice::castTick)).limit(limit).toList();
            }
        }
        if(ordinary.isEmpty() && moves.isEmpty() && start.motion().grounded()){
            double x=Math.floor(feet.x())+.5,z=Math.floor(feet.z())+.5;
            for(int[] direction:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
                var at=new Point(x+direction[0],feet.y(),z+direction[1]);
                if(!terrain.supported(at))continue;
                var walk=groundPath(feet,at,start.yaw(),start.pitch(),terrain,cancelled,12);
                if(walk.isEmpty())continue;
                double next=advanceTo(walk.getLast().to(),progress,terrain);
                if(next>progress+MIN_PROGRESS||connected(walk.getLast().to(),progress,terrain))
                    moves.add(new Choice(walk,first,Math.max(progress,next),null,0,0,false,"ADJUST_LAUNCH_POSITION"));
            }
        }
        if(!moves.isEmpty() && (ref.movement()!=Movement.TRANSMISSION||
                ordinary.stream().noneMatch(c->terrain.supported(c.last().to())||lateUsable(start,c,progress,terrain)))) {
            moves.addAll(ordinary);
            return moves.stream().limit(limit).toList();
        }
        var result=new ArrayList<>(ordinary);
        result.addAll(moves);
        return result.stream().limit(limit).toList();
    }

    private List<Choice> regionalChoices(Start start,double progress,Terrain terrain,int limit,BooleanSupplier cancelled){
        Point feet=start.motion().feet();
        int index=referenceIndex(progress);
        if(centerlineDistance(feet)>radius())return List.of();
        if(start.motion().grounded()&&!air&&feet.distance(goal())<=15){
            var ground=groundPath(feet,goal(),start.yaw(),start.pitch(),terrain,cancelled,100);
            if(!ground.isEmpty()&&ground.stream().allMatch(this::insideAction))
                return List.of(new Choice(ground,index,references.size(),null,0,0,false,"REGION_TERMINAL_GROUND"));
        }
        var candidates=new ArrayList<Choice>();
        for(var step:CorridorRejoin.movement(start,terrain,jumpHeight())){
            double next=advanceTo(step.to(),progress,terrain);
            if(insideAction(step)&&next>progress+MIN_PROGRESS)
                candidates.add(new Choice(List.of(step),index,next,null,0,0,false,"REGION_GROUND_HEADING"));
        }
        if(options.transmission())candidates.addAll(directionalCasts(start,progress,terrain,cancelled,false));
        return candidates.stream().sorted(Comparator.comparingDouble(c->regionalRank(start,progress,c)))
                .limit(limit).toList();
    }

    public boolean insideAction(Segment step){
        Point bend=referenceBend(step);
        if(step.movement()==Movement.JUMP)bend=new Point(step.from().x(),step.to().y()+.1,step.from().z());
        return bend==null?insideLeg(step.from(),step.to()):insideLeg(step.from(),bend)&&insideLeg(bend,step.to());
    }

    private double regionalRank(Start start,double progress,Choice choice){
        var step=choice.last();
        double advance=arc(choice.progress())-arc(progress);
        return -2*advance+rotationCost(start,step)*.18+choice.castTick()*.14
                +step.cost()*.1+step.to().distance(goal())*.06;
    }

    public static double rotationCost(Start start,Segment step){
        return Math.hypot(ViewAngles.wrap(step.yaw()-start.yaw()),step.pitch()-start.pitch());
    }

    /** Optional observer for an offline experiment. Production leaves this unset. */
    public interface CandidateTrace {
        void record(String outcome);
        default void batch(int count) {}
        default void ray(Point origin,Point raw,Point landing) {}
        default void timing(long headingNs,long simulationNs) {}
    }

    public List<Choice> experimentalCandidates(Start start,double progress,Terrain terrain,
                                                BooleanSupplier cancelled,CandidateTrace trace,boolean strategic){
        if(!regional()||references.isEmpty()||centerlineDistance(start.motion().feet())>radius())return List.of();
        Point feet=start.motion().feet();
        int index=referenceIndex(progress);
        var candidates=new ArrayList<Choice>();
        if(start.motion().grounded()&&!air&&feet.distance(goal())<=15){
            var walk=groundPath(feet,goal(),start.yaw(),start.pitch(),terrain,cancelled,100);
            if(!walk.isEmpty()&&walk.stream().allMatch(this::insideAction)){
                trace.record("accepted");
                candidates.add(new Choice(walk,index,references.size(),null,0,0,false,"REGION_TERMINAL_GROUND"));
            }
        }
        for(var step:CorridorRejoin.movement(start,terrain,jumpHeight())){
            double next=advanceTo(step.to(),progress,terrain);
            if(next<=progress+MIN_PROGRESS)trace.record("connectivity");
            else if(!insideAction(step))trace.record("corridor");
            else{
                trace.record("accepted");
                candidates.add(new Choice(List.of(step),index,next,null,0,0,false,"REGION_GROUND_HEADING"));
            }
        }
        if(options.transmission())candidates.addAll(directionalCasts(start,progress,terrain,cancelled,false,trace,strategic));
        return candidates;
    }

    List<Choice> directionalCasts(Start start,double progress,Terrain terrain,BooleanSupplier cancelled,boolean detour){
        return directionalCasts(start,progress,terrain,cancelled,detour,null,false);
    }

    private List<Choice> directionalCasts(Start start,double progress,Terrain terrain,BooleanSupplier cancelled,
                                          boolean detour,CandidateTrace trace,boolean strategic){
        long headingStart=trace==null?0:System.nanoTime();
        if(!options.transmission()||references.isEmpty())return List.of();
        Point feet=start.motion().feet();
        int reference=referenceIndex(progress);
        var tangent=references.get(reference);
        var ahead=references.get(Math.min(references.size()-1,reference+2));
        var towardGoal=TransmissionModel.toward(feet,goal());
        var along=TransmissionModel.toward(tangent.from(),ahead.to());
        var headings=new LinkedHashSet<List<Double>>();
        headings.add(List.of(ViewAngles.wrap(start.yaw()),start.pitch()));
        headings.add(List.of(towardGoal[0],towardGoal[1]));
        for(double base:new double[]{along[0], towardGoal[0], ViewAngles.wrap(start.yaw())}){
            for(int yawOffset:new int[]{0,-20,20,-45,45})
                for(double pitch:new double[]{along[1],0,-22,22,44})
                    headings.add(List.of(ViewAngles.wrap(base+yawOffset),pitch));
        }
        if(detour){
            for(int yaw=0;yaw<360;yaw+=45)
                for(int pitch:new int[]{-60,-30,0,30,60})headings.add(List.of((double)yaw,(double)pitch));
            headings.add(List.of(start.yaw(),90.0));
            headings.add(List.of(start.yaw(),-90.0));
        }
        if(strategic){
            int far=reference;
            while(far+1<references.size()&&lengths[far+1]<arc(progress)+96)far++;
            var bearing=TransmissionModel.toward(feet,references.get(far).to());
            headings.add(List.of(ViewAngles.wrap(bearing[0]),bearing[1]));
        }
        var pending=new ArrayList<>(headings);
        var alternatives=new ArrayList<Choice>();
        boolean boundaryAdded=false;
        long simulationStart=trace==null?0:System.nanoTime();
        var times=new HashMap<List<Double>,AimTime>();
        var motions=new HashMap<Integer,Motion>();
        var best=new LinkedHashMap<Point,Choice>();
        for(int h=0;h<pending.size();h++){
            var head=pending.get(h);
            if(cancelled.getAsBoolean())break;
            double distance=options.distance();
            var angle=TransmissionModel.angle(head.get(0),head.get(1),towardGoal[0],towardGoal[1]);
            if(!detour&&angle<.01&&feet.distance(goal())<distance)
                distance=feet.distance(goal());
            var candidate=converge(start,start.motion(),progress,reference,goal(),head.get(0),head.get(1),
                    distance,true,false,terrain,times,motions,detour,trace);
            if(trace!=null){
                if(candidate!=null)alternatives.add(candidate);
                else if(!boundaryAdded){
                    var failure=terrain.diagnosticFailure();
                    if("OBSTACLE".equals(failure.get("reason"))&&failure.get("blockX") instanceof Number x
                            &&failure.get("blockY") instanceof Number y&&failure.get("blockZ") instanceof Number z){
                        var block=new Point(x.doubleValue()+.5,y.doubleValue()+.5,z.doubleValue()+.5);
                        if(feet.distance(block)<=options.distance()+2){
                            var toward=TransmissionModel.toward(feet,block);
                            for(int side:new int[]{-1,1}){
                                var edge=List.of(ViewAngles.wrap(toward[0]+side*35),head.get(1));
                                if(headings.add(edge))pending.add(edge);
                            }
                            boundaryAdded=true;
                        }
                    }
                }
            }else if(candidate!=null){
                best.merge(candidate.last().to(),candidate,(a,b)->rotationCost(start,a.last())<=rotationCost(start,b.last())?a:b);
            }
        }
        if(trace!=null){
            trace.batch(pending.size());
            trace.timing(simulationStart-headingStart,System.nanoTime()-simulationStart);
            return alternatives;
        }
        return new ArrayList<>(best.values());
    }

    public record Continuation(boolean connected,String reason) {}

    public boolean viable(Start state,double progress,Terrain terrain){
        return continuation(state,progress,terrain).connected();
    }

    public Continuation continuation(Start state,double progress,Terrain terrain){
        if(reached(state.motion(),null,terrain))return new Continuation(true,"GOAL");
        if(!choices(state,progress,terrain,1,()->false).isEmpty())return new Continuation(true,"IMMEDIATE_NEXT_CHOICE");
        if(!canSettle(state.motion(),progress,terrain))
            return new Continuation(false,"NO_IMMEDIATE_CHOICE_AND_CANNOT_SETTLE");
        var settled=FlightTiming.predict(state.motion(),20,terrain);
        var landed=new Start(settled,state.yaw(),state.pitch(),0,state.aim(),new Ready(0,0,0));
        boolean connected=reached(settled,null,terrain)||!choices(landed,progress,terrain,1,()->false).isEmpty();
        return new Continuation(connected,connected?"CHOICE_AFTER_SETTLING":"NO_CHOICE_AFTER_SETTLING");
    }

    public Choice upwardTurn(Start start,double progress,Terrain terrain){
        if(regional()||!options.transmission()||start.motion().grounded()||start.consecutiveClimbs()<2||
                start.upwardTurnUsed()||start.pitch()>-35)return null;
        int index=referenceIndex(progress);
        var anchor=guidance(start,index);
        var direction=TransmissionModel.toward(start.motion().feet(),anchor);
        if(Math.abs(direction[1])>25||start.motion().feet().distance(anchor)<1)return null;
        double range=Math.min(options.distance(),start.motion().feet().distance(anchor));
        var direct=converge(start,start.motion(),progress,index,anchor,direction[0],direction[1],range,false,false,
                terrain,new HashMap<>(),new HashMap<>());
        if(direct!=null&&lateUsable(start,direct,progress,terrain))return null;
        double liftRange=Math.min(options.distance(),5);
        var lift=converge(start,start.motion(),progress,index,anchor,start.yaw(),-90,liftRange,true,false,
                terrain,new HashMap<>(),new HashMap<>(),true,null);
        if(lift==null||!insideAction(lift.last())||lift.last().to().y()<=start.motion().feet().y()+1)return null;
        var raised=after(start,lift,terrain);
        if(raised==null)return null;
        var bearing=TransmissionModel.toward(raised.motion().feet(),anchor);
        var onward=converge(raised,raised.motion(),progress,index,anchor,bearing[0],bearing[1],range,false,false,
                terrain,new HashMap<>(),new HashMap<>());
        if(onward==null||!lateUsable(raised,onward,progress,terrain))return null;
        return new Choice(List.of(lift.last(),onward.last()),index,onward.progress(),lift.castOrigin(),lift.castTick(),
                lift.aimMs(),false,"UPWARD_TURN_ASSIST",lift.forecast());
    }

    private boolean lateUsable(Start start,Choice choice,double progress,Terrain terrain){
        if(choice.castOrigin()==null)return false;
        var late=FlightTiming.predict(choice.castOrigin(),1,terrain);
        var step=choice.last();
        var aim=RouteCastTiming.estimate(start.yaw(),start.pitch(),step.yaw(),step.pitch(),start.aim());
        if(late==null||aim==null)return false;
        var landed=landing(late.feet(),aim.yaw(),aim.pitch(),step.castDistance(),terrain);
        return landed!=null&&castProgress(late.feet(),landed,aim.yaw(),aim.pitch(),step.castDistance(),progress,terrain)>progress;
    }

    public Choice retime(Choice hint,Start start,double progress,Terrain terrain) {
        if(!regional()&&hint.reason().equals("UPWARD_TURN_ASSIST"))return upwardTurn(start,progress,terrain);
        if(!regional()&&start.heightCorrection()!=null)return null;
        var step=hint.last();
        if(step.movement()!=Movement.TRANSMISSION || hint.jumpAssist()&&!start.motion().grounded())return null;
        var motion=hint.jumpAssist()?start.motion().jump(FlightTiming.jumpImpulse(options.jumpHeight())):start.motion();
        var aims=new HashMap<List<Double>,AimTime>();
        var future=new HashMap<Integer,Motion>();
        var sameAim=converge(start,motion,progress,hint.reference(),step.to(),step.yaw(),step.pitch(),
                step.castDistance(),true,hint.jumpAssist(),terrain,aims,future);
        if(sameAim!=null&&sameAim.last().to().distance(step.to())<=1.25)
            return new Choice(sameAim.actions(),sameAim.reference(),sameAim.progress(),sameAim.castOrigin(),
                    sameAim.castTick(),sameAim.aimMs(),sameAim.jumpAssist(),"REHEARSED_ANGLE",sameAim.forecast());
        if(regional())return null;
        var adjusted=converge(start,motion,progress,hint.reference(),step.to(),step.yaw(),step.pitch(),
                step.castDistance(),false,hint.jumpAssist(),terrain,aims,future);
        return adjusted!=null&&adjusted.last().to().distance(step.to())<=1.25?adjusted:null;
    }

    private List<Choice> castChoices(Start start,double progress,Terrain terrain,int limit,
                                     BooleanSupplier cancelled,boolean jumpAssist) {
        Motion motion=jumpAssist?start.motion().jump(FlightTiming.jumpImpulse(options.jumpHeight())):start.motion();
        Point feet=motion.feet();
        int first=referenceIndex(progress),last=Math.min(references.size()-1,first+LOOK_AHEAD);
        int landing=landingReferences.nextSetBit(first);
        if(landing>=0)last=Math.min(last,landing);
        var reference=references.get(first);
        var candidates=new ArrayList<Choice>();
        var used=new HashSet<Point>();
        var aimTimes=new HashMap<List<Double>,AimTime>();
        var predicted=new HashMap<Integer,Motion>();
        if(reference.movement()==Movement.TRANSMISSION&&!jumpAssist&&start.heightCorrection()==null) {
            var fixed=converge(start,motion,progress,first,reference.to(),reference.yaw(),reference.pitch(),
                    reference.castDistance(),true,false,terrain,aimTimes,predicted);
            if(fixed!=null){candidates.add(fixed);used.add(fixed.last().to());}
        }
        for(int i=last;i>=first && !cancelled.getAsBoolean();i--){
            Point anchor=guidance(start,i);
            if(feet.distance(anchor)>options.distance()+14)continue;
            var base=TransmissionModel.toward(feet,anchor);
            for(int yawOffset:new int[]{0,-12,12,-24,24,-40,40})
                for(int pitchOffset:new int[]{0,-9,9,-19,19}) {
                    double yaw=base[0]+yawOffset,pitch=Math.clamp(base[1]+pitchOffset,-90,90);
                    Point aimPoint=anchor;
                    if(yawOffset!=0||pitchOffset!=0)
                        aimPoint=TransmissionModel.raw(feet,yaw,pitch,Math.max(.1,feet.distance(anchor)));
                    boolean terminal=i==references.size()-1&&yawOffset==0&&pitchOffset==0;
                    double range=terminal?Math.min(options.distance(),feet.distance(anchor)):options.distance();
                    var next=converge(start,motion,progress,i,aimPoint,yaw,pitch,range,false,jumpAssist,terrain,aimTimes,predicted);
                    if(next!=null&&used.add(next.last().to()))candidates.add(next);
                }
        }
        return candidates.stream().sorted(Comparator.<Choice>comparingDouble(c->-c.progress()*12
                +c.castTick()*.06 + (terrain.supported(c.last().to())?0:.85)
                +c.last().to().distance(references.get(referenceIndex(c.progress())).to())*.09
                +Math.abs(c.last().to().y()-references.get(c.reference()).to().y())*.025))
                .limit(limit).toList();
    }

    private Choice converge(Start start,Motion initial,double progress,int referenceIndex,Point aimPoint,
                            double yaw,double pitch,double distance,boolean fixed,boolean jumpAssist,Terrain terrain,
                            Map<List<Double>,AimTime> times,Map<Integer,Motion> motions) {
        return converge(start,initial,progress,referenceIndex,aimPoint,yaw,pitch,distance,fixed,jumpAssist,terrain,times,motions,false,null);
    }

    private Choice converge(Start start,Motion initial,double progress,int referenceIndex,Point aimPoint,
                            double yaw,double pitch,double distance,boolean fixed,boolean jumpAssist,Terrain terrain,
                            Map<List<Double>,AimTime> times,Map<Integer,Motion> motions,boolean detour,CandidateTrace trace) {
        if(distance<.05||distance>options.distance())return rejected(trace,"motion");
        for(int attempt=0; attempt<4; attempt++){
            double y=yaw,p=pitch;
            var aim=times.computeIfAbsent(List.of(y,p),unused->regional()?FlightTiming.estimate(start.yaw(),start.pitch(),y,p,start.aim()):
                    RouteCastTiming.estimate(start.yaw(),start.pitch(),y,p,start.aim()));
            if(aim==null)return rejected(trace,"motion");
            int ready=regional()?start.ready().castTick(aim.ms()):
                    RouteCastTiming.castTick(start.ready(),aim.ms(),start.heightCorrection()!=null);
            int tick=Math.max(jumpAssist?1:0,ready);
            var motion=motions.computeIfAbsent(tick,t->FlightTiming.predict(initial,t,terrain));
            if(motion==null)return rejected(trace,"motion");
            if(jumpAssist&&motion.grounded())return null;
            Point origin=motion.feet();
            if(!fixed && attempt<3 && origin.distance(aimPoint)>=.05) {
                var toward=TransmissionModel.toward(origin,aimPoint);
                if(TransmissionModel.angle(yaw,pitch,toward[0],toward[1])>=.08) {
                    yaw=toward[0];pitch=toward[1];
                    if(distance<options.distance()) distance=Math.min(options.distance(),origin.distance(aimPoint));
                    continue;
                }
            }
            if(!fixed && distance<options.distance())
                distance=Math.min(options.distance(),origin.distance(aimPoint));
            if(distance<.05 || !motion.grounded() && start.airborneDistance()+distance>180) return rejected(trace,"motion");
            if(trace!=null){
                var endpoint=TransmissionModel.landing(origin,aim.yaw(),aim.pitch(),distance);
                if(centerlineDistance(endpoint)>radius())return rejected(trace,"corridor");
                // Exact static clearance is mandatory in landing(). Check it first to avoid two futile sweeps.
                if(!terrain.clear(endpoint))return rejected(trace,"staticGeometry");
                trace.ray(origin,TransmissionModel.raw(origin,aim.yaw(),aim.pitch(),distance),endpoint);
            }
            Point landing=landing(origin,aim.yaw(),aim.pitch(),distance,terrain);
            if(trace!=null&&landing==null)return rejected(trace,"exactGeometry");
            if(landing!=null) {
                double next=detour?progress+1:castProgress(origin,landing,aim.yaw(),aim.pitch(),distance,progress,terrain);
                if(next>progress+MIN_PROGRESS) {
                    var step=new Segment(Movement.TRANSMISSION,origin,landing,yaw,pitch,0,options.weights().transmission(),distance);
                    String reason=detour?"REJOIN_NAV_TRANSMISSION":regional()?"REGION_CAST_HEADING":jumpAssist?"JUMP_ASSIST":fixed&&landing.equals(references.get(referenceIndex).to())
                            ?"REFERENCE":"CORRIDOR_LOCAL_TARGET";
                    if(trace!=null)trace.record("accepted");
                    var prediction=regional()?null:new CastForecast(
                            TransmissionModel.raw(origin,aim.yaw(),aim.pitch(),distance),landing,aim.yaw(),aim.pitch());
                    return new Choice(List.of(step),referenceIndex,next,motion,tick,aim.ms(),jumpAssist,reason,prediction);
                }
            }
            if(fixed||origin.distance(aimPoint)<.05)return rejected(trace,"connectivity");
            var toward=TransmissionModel.toward(origin,aimPoint);
            if(TransmissionModel.angle(yaw,pitch,toward[0],toward[1])<.02) return null;
            yaw=toward[0];pitch=toward[1];
        }
        return null;
    }

    private static Choice rejected(CandidateTrace trace,String outcome){
        if(trace!=null)trace.record(outcome);
        return null;
    }

    public double castProgress(Point origin,Point landing,double yaw,double pitch,double distance,double progress,Terrain terrain) {
        if(regional()){
            var raw=TransmissionModel.raw(origin,yaw,pitch,distance);
            if(!insideLeg(origin,raw)||!insideLeg(raw,landing))return -1;
        }
        if(air && reached(Motion.confirmed(landing,terrain),new Segment(Movement.TRANSMISSION,origin,landing,yaw,pitch,0,0,distance),terrain))
            return references.size();
        return advanceTo(landing,progress,terrain);
    }
    public static Point landing(Point origin,double yaw,double pitch,double distance,Terrain terrain) {
        Point raw=TransmissionModel.raw(origin,yaw,pitch,distance);
        Point to=TransmissionModel.landing(origin,yaw,pitch,distance);
        return !origin.equals(to)&&terrain.sweep(origin,raw)&&terrain.sweep(raw,to)&&terrain.clear(to)?to:null;
    }
    public double advanceTo(Point at,double progress,Terrain terrain) {
        if(isTerminalLanding(at,terrain)) return references.size();
        if(regional()){
            double next=regionalProjection(at,progress,options.distance()+18);
            return next>progress+MIN_PROGRESS?next:-1;
        }
        int first=referenceIndex(progress);
        int last=Math.min(references.size()-1,first+LOOK_AHEAD);
        int landing=landingReferences.nextSetBit(first);
        boolean outstanding=landing>=0 && landing<=last
                && (!terrain.supported(at) || at.distance(references.get(landing).to())>2);
        if(outstanding)last=landing;
        for(int i=last;i>=first;i--){
            var ref=references.get(i);
            var projection=project(at,ref);
            // A spatially close arm behind a wall cannot advance the ordered guide.
            double distance=at.distance(projection.point());
            double radius=i==referenceIndex(progress)?CorridorPlanner.RADIUS:8;
            boolean atExit=at.distance(ref.to())<=.35&&terrain.sweep(at,ref.to());
            if(!atExit&&(distance>radius||!reconnect(at,projection,ref,terrain)))continue;
            double next=i+(atExit?1:projection.fraction());
            if(outstanding && i==landing)next=Math.min(next,i+.95);
            if(i==references.size()-1) {
                // Keep room for a genuine approach after an overshoot.
                double nearGoal=.995-.30*Math.min(at.distance(goal())/10,1);
                next=Math.min(next,i+nearGoal);
            }
            if(next>progress+MIN_PROGRESS)return next;
        }
        return -1;
    }
    public boolean terminalZone(Point feet,double progress) {
        return referenceIndex(progress)>=references.size()-2||feet.distance(goal())<=Math.max(12,options.distance()+2);
    }
    public boolean isTerminalLanding(Point at,Terrain terrain) {
        return !air&&at.distance(goal())<=ARRIVAL&&terrain.supported(at)&&terrain.sweep(at,goal());
    }
    public boolean connected(Point at,double progress,Terrain terrain) {
        if(references.isEmpty())return false;
        if(regional())return regionalProjection(at,progress,options.distance()+18)>=0;
        int index=referenceIndex(progress);
        for(int i=Math.max(0,index-1);i<=Math.min(references.size()-1,index+1);i++){
            var ref=references.get(i);
            var projection=project(at,ref);
            if(at.distance(projection.point())<=CorridorPlanner.RADIUS&&reconnect(at,projection,ref,terrain))return true;
        }
        return false;
    }
    public boolean canSettle(Motion motion,double progress,Terrain terrain) {
        if(motion.grounded()||references.isEmpty())return false;
        var settled=FlightTiming.predict(motion,20,terrain);
        if(settled==null||!settled.grounded())return false;
        var at=settled.feet();
        if(isTerminalLanding(at,terrain))return true;
        if(!air&&terminalZone(at,progress)&&!groundPath(at,goal(),0,0,terrain,()->false,50).isEmpty())return true;
        if(regional())return insideLeg(motion.feet(),at)&&advanceTo(at,progress,terrain)>progress;
        var ref=references.get(referenceIndex(progress));
        var projection=project(at,ref);
        return at.distance(projection.point())<=CorridorPlanner.RADIUS
                && reconnect(at,projection,ref,terrain);
    }
    private boolean within(Point at) {
        return references.stream().anyMatch(s->at.distance(project(at,s).point())<=CorridorPlanner.RADIUS);
    }
    public int referenceIndex(double progress) {
        return Math.max(0,Math.min(references.size()-1,(int)Math.floor(progress)));
    }
    private record Projection(Point point,double fraction,Point remainingBend) {}
    private static boolean reconnect(Point at,Projection projection,Segment ref,Terrain terrain) {
        if(!terrain.sweep(at,projection.point()))return false;
        Point bend=projection.remainingBend();
        return bend==null?terrain.sweep(projection.point(),ref.to()):
                terrain.sweep(projection.point(),bend)&&terrain.sweep(bend,ref.to());
    }
    /** Bend between the effective cast and its quantized endpoint, or between moving off a ledge and falling. */
    public static Point referenceBend(Segment ref) {
        Point a=ref.from(),b=ref.to();
        if(ref.movement()==Movement.TRANSMISSION&&ref.castDistance()>0)
            return TransmissionModel.raw(a,ref.yaw(),ref.pitch(),ref.castDistance());
        if(ref.movement()==Movement.FALL&&!a.sameColumn(b))
            return new Point(b.x(),a.y(),b.z());
        return null;
    }
    private static Projection project(Point at,Segment ref) {
        Point a=ref.from(),b=ref.to(),bend=referenceBend(ref);
        if(bend==null||bend.equals(a)||bend.equals(b))return projectLine(at,a,b);
        var first=projectLine(at,a,bend);
        var second=projectLine(at,bend,b);
        double firstLength=a.distance(bend),total=firstLength+bend.distance(b);
        if(at.distance(first.point())<=at.distance(second.point()))
            return new Projection(first.point(),first.fraction()*firstLength/total,bend);
        return new Projection(second.point(),(firstLength+second.fraction()*(total-firstLength))/total,null);
    }
    private static Projection projectLine(Point at,Point a,Point b) {
        double x=b.x()-a.x(),y=b.y()-a.y(),z=b.z()-a.z(),len=x*x+y*y+z*z;
        double t=len==0?1:Math.clamp(((at.x()-a.x())*x+(at.y()-a.y())*y+(at.z()-a.z())*z)/len,0,1);
        return new Projection(a.add(new Point(t*x,t*y,t*z)),t,null);
    }
    private List<Segment> groundPath(Point from,Point to,double yaw,double pitch,Terrain terrain,BooleanSupplier cancelled,long budget) {
        var walk=new Options(options.mode(),false,false,options.distance(),options.jumpHeight(),budget,options.weights());
        var route=new PathPlanner().planGround(from,to,yaw,pitch,walk,terrain,cancelled);
        return route.status()==Status.ARRIVED?route.segments():List.of();
    }
    public boolean reached(Motion motion,Segment last,Terrain terrain) {
        Point feet=motion.feet();
        if(!air)return motion.grounded()&&feet.distance(goal())<=ARRIVAL&&terrain.sweep(feet,goal());
        if(feet.distance(goal())<=options.distance()/2)return true;
        if(last==null||last.movement()!=Movement.TRANSMISSION)return false;
        Point raw=TransmissionModel.raw(last.from(),last.yaw(),last.pitch(),last.castDistance());
        int x=(int)Math.floor(goal().x()),y=(int)Math.floor(goal().y()),z=(int)Math.floor(goal().z());
        return new AABB(x,y,z,x+1,y+1,z+1).clip(new Vec3(last.from().x(),last.from().y(),last.from().z()),
                new Vec3(raw.x(),raw.y(),raw.z())).isPresent();
    }
}
