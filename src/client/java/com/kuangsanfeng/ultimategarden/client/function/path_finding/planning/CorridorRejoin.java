package com.kuangsanfeng.ultimategarden.client.function.path_finding.planning;

import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.TransmissionModel;

import java.util.*;

import static com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.*;

/** Short ground-only search from actual feet. Teleport angles do not determine a corridor entrance. */
public final class CorridorRejoin {
    private static final int NODE_LIMIT=128, STEP_LIMIT=10;
    private static final double MAX_STEP=.6;
    private static final int[][] DIRECTIONS={{1,0},{-1,0},{0,1},{0,-1}};
    private static final String[] AXES={"+X","-X","+Z","-Z"};

    public record Edge(String direction, Point from, Point target, String reason) {}
    public record Entrance(Point point, double progress, int steps) {}
    public record Search(List<Segment> actions, Entrance selected, int expanded, int open,
                         List<Edge> firstEdges, List<Entrance> entrances) {}
    private record Node(Point feet, Node parent, Segment step, int depth) {}
    private record Candidate(Segment step, Point point, String reason) {}

    private CorridorRejoin() {}

    public static Search find(CorridorRepair corridor,CorridorRepair.Start start,double progress,Terrain terrain,
                              Set<Point> rejected,double jumpHeight) {
        Point feet=start.motion().feet();
        var root=new Node(feet,null,null,0);
        var queue=new ArrayDeque<Node>();
        var visited=new HashSet<Point>();
        var firstEdges=new ArrayList<Edge>();
        var entrances=new ArrayList<Entrance>();
        queue.add(root);
        visited.add(feet);
        Node selected=null;
        double bestProgress=-1;
        int expanded=0;
        while(!queue.isEmpty() && expanded<NODE_LIMIT){
            var node=queue.removeFirst();
            expanded++;
            if(node.depth>0 && !rejected.contains(node.feet)){
                double at=corridor.rejoinProgress(node.feet,progress,terrain);
                if(at>=0){
                    entrances.add(new Entrance(node.feet,at,node.depth));
                    if(selected==null || preference(at,node.depth,progress)>preference(bestProgress,selected.depth,progress)){
                        selected=node;
                        bestProgress=at;
                    }
                }
            }
            if(node.depth==STEP_LIMIT)continue;
            // All four directions are checked from the real starting position, even at a corridor boundary.
            for(int d=0;d<DIRECTIONS.length;d++){
                var vector=DIRECTIONS[d];
                double x=Math.floor(node.feet.x())+.5+vector[0],z=Math.floor(node.feet.z())+.5+vector[1];
                var candidate=neighbor(node.feet,x,z,terrain,jumpHeight);
                if(node==root)firstEdges.add(new Edge(AXES[d],node.feet,candidate.point,candidate.reason));
                if(candidate.step!=null && visited.add(candidate.point))
                    queue.addLast(new Node(candidate.point,node,candidate.step,node.depth+1));
            }
        }
        if(selected==null)return new Search(List.of(),null,expanded,queue.size(),List.copyOf(firstEdges),List.copyOf(entrances));
        var actions=new ArrayList<Segment>();
        for(Node n=selected;n.step!=null;n=n.parent)actions.add(n.step);
        Collections.reverse(actions);
        return new Search(List.copyOf(actions),new Entrance(selected.feet,bestProgress,selected.depth),expanded,queue.size(),
                List.copyOf(firstEdges),List.copyOf(entrances));
    }

    private static double preference(double at,int steps,double progress) {
        // Prefer an entrance ahead of the old progress. Keep the approach short when progress is similar.
        return (at>=progress?2:0)+(at-progress)-steps*.22;
    }

    private static Candidate neighbor(Point from,double x,double z,Terrain terrain,double jumpHeight) {
        Point level=new Point(x,from.y(),z);
        if(terrain.supported(level))return edge(from,level,terrain,jumpHeight);
        double rise=Math.max(jumpHeight,MAX_STEP);
        var support=terrain.localSurface(new Point(x,from.y()+rise,z),rise+MAX_STEP);
        if(support.isPresent())return edge(from,support.get(),terrain,jumpHeight);
        var check=terrain.motionSweep(level,level);
        return new Candidate(null,level,switch(check){
            case CLEAR -> "NO_SUPPORT_IN_STEP_OR_JUMP_RANGE";
            case BLOCKED -> "BODY_COLLISION_OR_HEIGHT";
            case UNKNOWN -> "UNKNOWN_GEOMETRY";
        });
    }

    private static Candidate edge(Point from,Point to,Terrain terrain,double jumpHeight){
        double dy=to.y()-from.y();
        if(dy < -MAX_STEP-.001 || dy>Math.max(jumpHeight,MAX_STEP)+.001)
            return new Candidate(null,to,"HEIGHT_DIFFERENCE");
        if(!terrain.supported(to))return new Candidate(null,to,"NO_TARGET_SUPPORT");
        Movement movement=dy>MAX_STEP?Movement.JUMP:Movement.WALK;
        Terrain.MotionCheck check;
        if(Math.abs(dy)<.001){
            check=terrain.motionSweep(from,to);
            if(check==Terrain.MotionCheck.CLEAR && !terrain.supported(new Point((from.x()+to.x())/2,from.y(),(from.z()+to.z())/2)))
                return new Candidate(null,to,"NO_INTERMEDIATE_SUPPORT");
        }else{
            double top=Math.max(from.y(),to.y())+(movement==Movement.JUMP?.1:0);
            Point up=new Point(from.x(),top,from.z()),across=new Point(to.x(),top,to.z());
            check=terrain.motionSweep(from,up);
            if(check==Terrain.MotionCheck.CLEAR)check=terrain.motionSweep(up,across);
            if(check==Terrain.MotionCheck.CLEAR)check=terrain.motionSweep(across,to);
        }
        if(check!=Terrain.MotionCheck.CLEAR)
            return new Candidate(null,to,check==Terrain.MotionCheck.UNKNOWN?"UNKNOWN_GEOMETRY":"SWEPT_COLLISION");
        double yaw=TransmissionModel.toward(from,to)[0];
        return new Candidate(new Segment(movement,from,to,yaw,0,Math.max(0,dy),0),to,"LEGAL_"+movement.name());
    }
}
