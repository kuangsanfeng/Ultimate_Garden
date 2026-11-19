package com.kuangsanfeng.ultimategarden.client.function.path_finding.planning;

import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.FlightTiming;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.TransmissionModel;
import com.kuangsanfeng.ultimategarden.client.platform.input.view.ViewAngles;

import java.util.*;

import static com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.*;

/** Bounded temporary route to a region with a validated outgoing action. */
public final class CorridorRejoin {
    private static final int NODE_LIMIT=96, STEP_LIMIT=10;
    public static final double RANGE=32;
    private static final double MAX_STEP=.6;

    public record Entrance(Point point, double progress, int steps) {}
    public record Search(List<Segment> actions, Entrance selected, int expanded, int open, String reason) {}
    private record Node(CorridorRepair.Start state, Node parent, Segment step, int depth, double cost, double rank) {}
    private record Key(Point point, int yaw, int pitch, int travel) {}

    private CorridorRejoin() {}

    public static Search find(CorridorRepair corridor,CorridorRepair.Start start,double progress,Terrain terrain,
                              Set<Point> rejected,double jumpHeight){
        return find(corridor,start,progress,terrain,rejected,jumpHeight,()->false);
    }

    public static Search find(CorridorRepair corridor,CorridorRepair.Start start,double progress,Terrain terrain,
                              Set<Point> rejected,double jumpHeight,java.util.function.BooleanSupplier cancelled){
        Point origin=start.motion().feet();
        var queue=new PriorityQueue<Node>(Comparator.comparingDouble(Node::rank).thenComparingInt(Node::depth));
        var best=new HashMap<Key,Double>();
        queue.add(new Node(start,null,null,0,0,0));
        best.put(key(start),0.0);
        int expanded=0;
        while(!queue.isEmpty()&&expanded<NODE_LIMIT&&!cancelled.getAsBoolean()){
            var node=queue.remove();
            if(node.cost()>best.getOrDefault(key(node.state()),Double.POSITIVE_INFINITY))continue;
            expanded++;
            var state=node.state();
            Point point=state.motion().feet();
            if(node.depth()>0&&!rejected.contains(point)){
                double next=corridor.confirmedRejoin(point,progress,terrain);
                if(next>=0&&corridor.viable(state,next,terrain)){
                    var route=new ArrayList<Segment>();
                    for(var n=node;n.step()!=null;n=n.parent())route.add(n.step());
                    Collections.reverse(route);
                    return new Search(List.copyOf(route),new Entrance(point,next,node.depth()),expanded,queue.size(),"CONNECTED_REGION");
                }
            }
            if(node.depth()>=STEP_LIMIT)continue;
            var choices=new ArrayList<CorridorRepair.Choice>();
            for(var step:movement(state,terrain,jumpHeight))
                choices.add(new CorridorRepair.Choice(List.of(step),0,progress,null,0,0,false,"REJOIN_NAV_GROUND"));
            choices.addAll(corridor.directionalCasts(state,progress,terrain,cancelled,true));
            for(var choice:choices){
                var step=choice.last();
                if(step.to().distance(origin)>RANGE||step.from().distance(origin)>RANGE)continue;
                var next=corridor.after(state,choice,terrain);
                if(next==null)continue;
                double cost=node.cost()+1+step.cost()+choice.castTick()*.12+CorridorRepair.rotationCost(state,step)*.016;
                Key key=key(next);
                if(cost>=best.getOrDefault(key,Double.POSITIVE_INFINITY))continue;
                best.put(key,cost);
                double distance=corridor.centerlineDistance(step.to());
                double rank=cost+Math.min(RANGE,distance)*.18+step.to().distance(corridor.goal())*.025;
                queue.add(new Node(next,node,step,node.depth()+1,cost,rank));
            }
        }
        return new Search(List.of(),null,expanded,queue.size(),cancelled.getAsBoolean()?"CANCELLED":queue.isEmpty()?"REJOIN_REGION_UNREACHABLE":"REJOIN_NODE_BUDGET");
    }

    private static Key key(CorridorRepair.Start state){
        return new Key(state.motion().feet(),(int)Math.round(ViewAngles.wrap(state.yaw())/15),
                (int)Math.round(state.pitch()/15),(int)(state.airborneDistance()/6));
    }

    static List<Segment> movement(CorridorRepair.Start state,Terrain terrain,double jumpHeight){
        var motion=state.motion();
        Point from=motion.feet();
        var actions=new ArrayList<Segment>();
        if(!motion.grounded()){
            var settled=FlightTiming.predict(motion,24,terrain);
            if(settled!=null&&settled.grounded()&&from.sameColumn(settled.feet())&&settled.feet().y()<from.y()-.05)
                actions.add(new Segment(Movement.FALL,from,settled.feet(),state.yaw(),0,0,1));
            return actions;
        }
        return groundMovement(from,terrain,jumpHeight);
    }

    static List<Segment> groundMovement(Point from,Terrain terrain,double jumpHeight){
        var actions=new ArrayList<Segment>();
        // All eight neighbors are expanded repeatedly as graph edges, including diagonals.
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            if(dx==0&&dz==0)continue;
            double x=Math.floor(from.x())+.5+dx,z=Math.floor(from.z())+.5+dz;
            double rise=Math.max(jumpHeight,MAX_STEP);
            Point level=new Point(x,from.y(),z);
            if(terrain.supported(level))addGround(actions,from,level,terrain,jumpHeight);
            else terrain.localSurface(new Point(x,from.y()+rise,z),rise+MAX_STEP)
                    .ifPresent(surface->addGround(actions,from,surface,terrain,jumpHeight));
        }
        return actions;
    }

    public static boolean validateMotion(Segment step,CorridorRepair.Start start,Terrain terrain,double jumpHeight){
        if(step.movement()==Movement.TRANSMISSION)return true;
        Point from=start.motion().feet(),to=step.to();
        if(step.movement()==Movement.FALL)
            return from.sameColumn(to)&&from.y()>=to.y()&&terrain.sweep(from,to)&&terrain.supported(to);
        if(!start.motion().grounded()||from.distance(to)>2.5)return false;
        var available=new ArrayList<Segment>();
        addGround(available,from,to,terrain,jumpHeight);
        return !available.isEmpty()&&available.getFirst().movement()==step.movement();
    }

    private static void addGround(List<Segment> actions,Point from,Point to,Terrain terrain,double jumpHeight){
        double dy=to.y()-from.y();
        if(dy< -MAX_STEP-.001||dy>Math.max(jumpHeight,MAX_STEP)+.001||!terrain.clear(to)||!terrain.supported(to))return;
        Movement movement=dy>MAX_STEP?Movement.JUMP:Movement.WALK;
        if(Math.abs(dy)<.001){
            if(!terrain.sweep(from,to)||!terrain.supported(new Point((from.x()+to.x())/2,from.y(),(from.z()+to.z())/2)))return;
        }else{
            double y=Math.max(from.y(),to.y())+(movement==Movement.JUMP?.1:0);
            Point up=new Point(from.x(),y,from.z()),across=new Point(to.x(),y,to.z());
            if(!terrain.sweep(from,up)||!terrain.sweep(up,across)||!terrain.sweep(across,to))return;
        }
        double yaw=TransmissionModel.toward(from,to)[0];
        actions.add(new Segment(movement,from,to,yaw,0,Math.max(0,dy),from.distance(to)));
    }
}
