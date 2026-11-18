import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.PathSettings;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.execution.CastGate;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.CorridorPlanner;
import com.google.gson.JsonObject;
import java.util.*;
import static com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.*;

public final class CorridorRegression {
    private static void check(boolean success,String why) { if(!success)throw new AssertionError(why); }
    private static Segment cast(Point from, Point to,double yaw,double pitch,double range) {
        return new Segment(Movement.TRANSMISSION,from,to,yaw,pitch,0,0,range);
    }
    public static void main(String[] args) {
        var timing=CorridorPlanner.Timing.defaults();
        var a=new Point(.5,10,.5);
        var b=new Point(.5,10,12.5);
        var goal=new Point(.5,10,18.5);
        var first=cast(a,b,0,0,12);
        var second=cast(b,goal,0,0,6);
        var detour=List.of(new Segment(Movement.WALK,a,goal,0,0,0,0));
        check(CorridorPlanner.preferShortcut(CorridorPlanner.estimate(detour,0,0,timing),
                CorridorPlanner.estimate(List.of(first,second),0,0,timing)), "Open shortcut should win");
        var highA=a.add(new Point(0,12,0));var highB=b.add(new Point(0,12,0));var highGoal=goal.add(new Point(0,12,0));
        var elevated=List.of(cast(a,highA,0,-90,12),cast(highA,highB,0,0,12),cast(highB,highGoal,0,0,6),cast(highGoal,goal,0,90,12));
        var bend=new Point(4.5,10,9.5);
        var original=List.of(cast(a,bend,-24,0,12),cast(bend,goal,24,0,12));
        check(a.distance(bend)+bend.distance(goal)>a.distance(goal),"Elevated route has shorter horizontal traverse");
        check(!CorridorPlanner.preferShortcut(CorridorPlanner.estimate(original,0,0,timing),
                CorridorPlanner.estimate(elevated,0,0,timing)), "Climb and entry time must count");
        check(!CorridorPlanner.preferShortcut(CorridorPlanner.estimate(List.of(first),0,0,timing),
                CorridorPlanner.estimate(List.of(first),0,0,timing)), "Ties preserve original");
        var flat=new Terrain(){
            public boolean clear(Point p){return p.y()>=10;}
            public boolean supported(Point p){return p.y()==10;}
            public boolean sweep(Point a,Point b){return clear(a)&&clear(b);}
            public Optional<Point> ground(Point p){return Optional.of(new Point(p.x(),10,p.z()));}
            public Set<String> missing(){return Set.of();}
        };
        var o=Options.defaults();
        var route=new CorridorPlanner(flat,o,timing,()->false,null).plan(a,goal,0,0);
        check(route.status()==Status.ARRIVED && route.segments().stream().map(Segment::castDistance).toList().equals(List.of(12.,6.)),"Shared model must generate 12+6");
        var walking=new Options(Mode.NORMAL,false,true,12,1,20000,Weights.defaults());
        var walked=new CorridorPlanner(flat,walking,timing,()->false,null).plan(a,b,0,0);
        check(walked.status()==Status.ARRIVED && walked.segments().stream().allMatch(s->s.movement()==Movement.WALK),"TP disabled");
        var settings=new PathSettings();
        check(settings.teleportCooldownMs()==20 && settings.speedSampleDistance()==80,"Defaults");
        settings.setCooldown(85);var doc=new JsonObject();doc.add("pathFinding",settings.toJson());var copy=new PathSettings();
        check(!copy.load(doc)&&copy.teleportCooldownMs()==85 && !copy.toJson().has("measuredSpeed"),"Persist configured CD only");
        var gate=new CastGate();check(gate.ready(0,20),"First cast ready");gate.dispatch(0);
        check(!gate.ready(50_000_000,20),"Await packet");gate.confirm();
        check(!gate.ready(19_000_000,20)&&gate.ready(20_000_000,20),"20 ms is minimum");
        check(gate.dispatch(50_000_000)==50_000_000,"Tick interval is retained");gate.confirm();
        check(!gate.ready(100_000_000,85)&&gate.ready(150_000_000,85),"Edited CD enforced across ticks");
        System.out.println("PASS corridor selection timing, 12+6, walking only, persisted CD, packet gate and tick spacing");
    }
}
