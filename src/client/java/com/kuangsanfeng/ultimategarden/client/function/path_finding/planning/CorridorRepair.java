package com.kuangsanfeng.ultimategarden.client.function.path_finding.planning;

import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.FlightTiming;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.FlightTiming.*;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.TransmissionModel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.BooleanSupplier;

import static com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.*;

/** Bounded forward repair of one selected corridor, used with both map and client collision queries. */
public final class CorridorRepair {
    public static final int DEFAULT_SEARCH_LIMIT=240;
    private static final int LOOK_AHEAD=4, MAX_DEPTH=140;
    private static final double MIN_PROGRESS=.025, ARRIVAL=.75;

    public record Start(Motion motion,double yaw,double pitch,double airborneDistance,Aim aim,Ready ready) {}
    public record Choice(List<Segment> actions, int reference, double progress, Motion castOrigin,
                         int castTick, double aimMs, boolean jumpAssist, String reason) {
        public Choice { actions=List.copyOf(actions); }
        public Segment last(){return actions.getLast();}
    }
    public record Rehearsal(CorridorRepair corridor,List<Choice> choices,String reason,Point blockedAt,int expanded,int open,double initialProgress,double progress,int deadEnds,
                            Map<String,Object> lastGeometryCheck) {
        public Rehearsal { choices=List.copyOf(choices);lastGeometryCheck=Map.copyOf(lastGeometryCheck); }
        public boolean complete(){return reason.equals("VERIFIED");}
    }
    private record Node(Start start,double progress,int depth,double score,Node parent,Choice choice) {}
    private record Visit(Point feet,int yaw,int pitch,int reference,int travel) {}

    private final Result result;
    private final Options options;
    private final double cooldownMs, confirmationMs;
    private final boolean air;
    private final List<Segment> references;
    private final BitSet landingReferences=new BitSet();

    public CorridorRepair(Result result,Options options,CorridorPlanner.Timing timing,Terrain terrain) {
        this.result=result;
        this.options=options;
        cooldownMs=timing.cooldownMs(); confirmationMs=timing.confirmationMs();
        references=result.segments();
        air=!options.projectAir()&&!terrain.supported(result.resolved());
        for(int i=0;i<references.size();i++){
            var step=references.get(i);
            if(step.movement()==Movement.TRANSMISSION && step.pitch()>=89 && step.castDistance()<=6.1
                    && step.from().sameColumn(step.to()) && step.from().y()>step.to().y()
                    && terrain.supported(step.to()))landingReferences.set(i);
        }
    }
    public Point goal(){return result.resolved();}
    public double jumpHeight(){return options.jumpHeight();}

    /** Match a supported foot position to the original centerline on the same floor. */
    double rejoinProgress(Point feet,double progress,Terrain terrain) {
        if(references.isEmpty()||!terrain.supported(feet))return -1;
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
        var visited=new HashSet<Visit>();
        var root=new Node(initial,progress,0,0,null,null);
        var best=root;
        queue.add(root);
        int expanded=0,deadEnds=0;
        Map<String,Object> lastGeometryCheck=Map.of();
        while(!queue.isEmpty()&&expanded<limit&&!cancelled.getAsBoolean()) {
            var node=queue.remove();
            var state=node.start();
            var key=new Visit(state.motion().feet(),(int)Math.round(state.yaw()/4),(int)Math.round(state.pitch()/4),
                    referenceIndex(node.progress()),(int)state.airborneDistance());
            if(!visited.add(key)) continue;
            expanded++;
            if(node.progress()>best.progress()) best=node;
            if(reached(state.motion(),node.choice()==null?null:node.choice().last(),terrain))
                return new Rehearsal(this,chain(node),"VERIFIED",state.motion().feet(),expanded,queue.size(),progress,node.progress(),deadEnds,lastGeometryCheck);
            if(node.depth()>=MAX_DEPTH) continue;
            var available=choices(state,node.progress(),terrain,7,cancelled);
            if(available.isEmpty()) {
                deadEnds++;
                lastGeometryCheck=terrain.diagnosticFailure();
            }
            for(var choice:available) {
                var next=after(state,choice,terrain);
                double rank=-choice.progress()*12+node.depth()*.8+choice.castTick()*.12
                        +(next.motion().grounded()?0:1.2);
                queue.add(new Node(next,choice.progress(),node.depth()+1,rank,node,choice));
            }
        }
        return new Rehearsal(this,chain(best),cancelled.getAsBoolean()?"CANCELLED":
                !queue.isEmpty()&&expanded>=limit?"CORRIDOR_REHEARSAL_LIMIT":"CORRIDOR_REHEARSAL_BLOCKED",
                best.start().motion().feet(),expanded,queue.size(),progress,best.progress(),deadEnds,lastGeometryCheck);
    }
    private static List<Choice> chain(Node node) {
        var out=new ArrayList<Choice>();
        for(;node.choice()!=null;node=node.parent())out.add(node.choice());
        Collections.reverse(out);
        return out;
    }
    private Start after(Start start,Choice choice,Terrain geometry) {
        var step=choice.last();
        var motion=Motion.confirmed(step.to(),geometry);
        boolean cast=step.movement()==Movement.TRANSMISSION;
        double travel=choice.castOrigin()!=null&&choice.castOrigin().grounded()?0:start.airborneDistance();
        if(cast) travel+=step.castDistance();
        if(motion.grounded())travel=0;
        // Native aim for the following action starts on the confirmation tick. The CD ran in parallel.
        double cooldown=cast?Math.max(0,cooldownMs-confirmationMs):0;
        return new Start(motion,step.yaw(),step.pitch(),travel,start.aim(),new Ready(cooldown,0,0));
    }

    public List<Choice> choices(Start start,double progress,Terrain terrain,int limit,BooleanSupplier cancelled) {
        if(references.isEmpty()||cancelled.getAsBoolean())return List.of();
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
                Point target=references.get(i).to();
                if(feet.distance(target)>5||!terrain.supported(target))continue;
                var ground=groundPath(feet,target,start.yaw(),start.pitch(),terrain,cancelled,42);
                double next=ground.isEmpty()?-1:advanceTo(ground.getLast().to(),progress,terrain);
                if(next>progress+MIN_PROGRESS && ground.stream().allMatch(e->within(e.to())))
                    moves.add(new Choice(ground,i,next,null,0,0,false,"LOCAL_GROUND_REPAIR"));
            }
        }
        if(!options.transmission())return moves.stream().limit(limit).toList();

        var ordinary=castChoices(start,progress,terrain,limit,cancelled,false);
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
        if(!moves.isEmpty() && ref.movement()!=Movement.TRANSMISSION) {
            moves.addAll(ordinary);
            return moves.stream().limit(limit).toList();
        }
        var result=new ArrayList<>(ordinary);
        result.addAll(moves);
        return result.stream().limit(limit).toList();
    }

    public Choice retime(Choice hint,Start start,double progress,Terrain terrain) {
        var step=hint.last();
        if(step.movement()!=Movement.TRANSMISSION || hint.jumpAssist()&&!start.motion().grounded())return null;
        var motion=hint.jumpAssist()?start.motion().jump(FlightTiming.jumpImpulse(options.jumpHeight())):start.motion();
        var aims=new HashMap<List<Double>,AimTime>();
        var future=new HashMap<Integer,Motion>();
        var sameAim=converge(start,motion,progress,hint.reference(),step.to(),step.yaw(),step.pitch(),
                step.castDistance(),true,hint.jumpAssist(),terrain,aims,future);
        if(sameAim!=null&&sameAim.last().to().distance(step.to())<=1.25)
            return new Choice(sameAim.actions(),sameAim.reference(),sameAim.progress(),sameAim.castOrigin(),
                    sameAim.castTick(),sameAim.aimMs(),sameAim.jumpAssist(),"REHEARSED_ANGLE");
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
        if(reference.movement()==Movement.TRANSMISSION&&!jumpAssist) {
            var fixed=converge(start,motion,progress,first,reference.to(),reference.yaw(),reference.pitch(),
                    reference.castDistance(),true,false,terrain,aimTimes,predicted);
            if(fixed!=null){candidates.add(fixed);used.add(fixed.last().to());}
        }
        for(int i=last;i>=first && !cancelled.getAsBoolean();i--){
            Point anchor=references.get(i).to();
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
        if(distance<.05||distance>options.distance())return null;
        for(int attempt=0; attempt<4; attempt++){
            double y=yaw,p=pitch;
            var aim=times.computeIfAbsent(List.of(y,p),unused->FlightTiming.estimate(start.yaw(),start.pitch(),y,p,start.aim()));
            if(aim==null)return null;
            int tick=Math.max(jumpAssist?1:0,start.ready().castTick(aim.ms()));
            var motion=motions.computeIfAbsent(tick,t->FlightTiming.predict(initial,t,terrain));
            if(motion==null)return null;
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
            if(distance<.05 || !motion.grounded() && start.airborneDistance()+distance>180) return null;
            Point landing=landing(origin,aim.yaw(),aim.pitch(),distance,terrain);
            if(landing!=null) {
                double next=castProgress(origin,landing,aim.yaw(),aim.pitch(),distance,progress,terrain);
                if(next>progress+MIN_PROGRESS) {
                    var step=new Segment(Movement.TRANSMISSION,origin,landing,yaw,pitch,0,options.weights().transmission(),distance);
                    String reason=jumpAssist?"JUMP_ASSIST":fixed&&landing.equals(references.get(referenceIndex).to())
                            ?"REFERENCE":"CORRIDOR_LOCAL_TARGET";
                    return new Choice(List.of(step),referenceIndex,next,motion,tick,aim.ms(),jumpAssist,reason);
                }
            }
            if(fixed||origin.distance(aimPoint)<.05)return null;
            var toward=TransmissionModel.toward(origin,aimPoint);
            if(TransmissionModel.angle(yaw,pitch,toward[0],toward[1])<.02) return null;
            yaw=toward[0];pitch=toward[1];
        }
        return null;
    }

    public double castProgress(Point origin,Point landing,double yaw,double pitch,double distance,double progress,Terrain terrain) {
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
