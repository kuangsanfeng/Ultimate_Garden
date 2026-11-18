import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.FlightTiming;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.FlightTiming.*;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.TransmissionModel;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.CorridorRepair;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.CorridorRejoin;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.CorridorPlanner;
import java.util.*;
import static com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.*;

public final class TimedCorridorRegression {
    private static int checks;
    private static void check(boolean passed,String message){
        checks++;
        if(!passed)throw new AssertionError(message);
    }
    private static void near(double actual,double expected,String message){
        check(Math.abs(actual-expected)<.000002,message+" actual="+actual);
    }
    private static class Floor implements Terrain {
        protected double floor(Point p){return 0;}
        public boolean clear(Point p){return p.y()>=floor(p)&&p.y()<=200;}
        public boolean supported(Point p){return Math.abs(p.y()-floor(p))<.00002;}
        public boolean sweep(Point a,Point b) {
            for(int i=0;i<=16;i++){
                double t=i/16.0;
                if(!clear(new Point(a.x()+(b.x()-a.x())*t,a.y()+(b.y()-a.y())*t,a.z()+(b.z()-a.z())*t)))return false;
            }
            return true;
        }
        public Optional<Point> ground(Point p){return p.y()>=floor(p)?Optional.of(new Point(p.x(),floor(p),p.z())):Optional.empty();}
        public Set<String> missing(){return Set.of();}
    }
    private static CorridorRepair route(Point a,Point b,boolean cast,Terrain terrain,Movement type){
        var angle=TransmissionModel.toward(a,b);
        var options=new Options(Mode.NORMAL,cast,true,12,1,20000,Weights.defaults());
        var ref=new Segment(type,a,b,angle[0],angle[1],0,0,type==Movement.TRANSMISSION?Math.min(12,a.distance(b)):0);
        return new CorridorRepair(new Result(Status.ARRIVED,List.of(ref),b,b,b,0,0,Set.of(),"TEST"),options,
                CorridorPlanner.Timing.defaults(),terrain);
    }
    private static CorridorRepair.Start start(Point p,Terrain t,double yaw,double pitch,Aim aim){
        return new CorridorRepair.Start(Motion.confirmed(p,t),yaw,pitch,0,aim,new Ready(0,0,0));
    }
    public static void main(String[] args){
        var stairs=new Floor(){
            protected double floor(Point p){return p.x()< -1?2:p.x()<0?1:0;}
        };
        var guide=route(new Point(-1.5,2,-3.5),new Point(-1.5,2,6.5),false,stairs,Movement.WALK);
        var below=new Point(.5,0,.5);
        check(guide.confirmedRejoin(below,0,stairs)<0,"A lower floor cannot count as an entrance");
        var rejoin=CorridorRejoin.find(guide,start(below,stairs,0,0,Aim.defaults()),0,stairs,Set.of(),1);
        check(rejoin.selected()!=null && rejoin.selected().point().y()==2,"Reach a standing entrance on the original floor");
        check(rejoin.firstEdges().size()==4 && rejoin.firstEdges().stream().anyMatch(e->e.direction().equals("-X")
                && e.reason().equals("LEGAL_JUMP")),"Consider a valid -X jump outside the corridor");
        check(rejoin.actions().stream().allMatch(a->a.movement()==Movement.WALK||a.movement()==Movement.JUMP),
                "Detour does not depend on a valid teleport launch");
        var entry=rejoin.selected();
        var continuation=guide.rehearse(start(entry.point(),stairs,0,0,Aim.defaults()),stairs,()->false,100,entry.progress());
        check(continuation.complete() && continuation.initialProgress()==entry.progress(),
                "Rehearse from the confirmed entrance along the original guide");

        var flat=new Floor();
        Point upright=new Point(.5,80,.5);
        var reset=Motion.confirmed(upright,flat);
        near(FlightTiming.predict(reset,7,flat).feet().y(),78.40739540236494,"Logged seven ticks");
        near(FlightTiming.predict(reset,8,flat).feet().y(),77.89044745325997,"Logged eight ticks");
        near(FlightTiming.predict(new Motion(upright,0,-.5,0,false),1,flat).feet().y(),79.5,"Keep live downward velocity");
        var shelf=new Floor(){protected double floor(Point p){return 78;}};
        var landed=FlightTiming.predict(reset,15,shelf);
        check(landed.grounded(),"Land during aim");
        near(landed.feet().y(),78,"Clip at floor");
        near(landed.vy(),0,"Stop vertical motion on support");
        var ceiling=new Floor(){public boolean clear(Point p){return super.clear(p)&&p.y()<=81;}};
        var bumped=FlightTiming.predict(new Motion(upright,0,1.5,0,false),1,ceiling);
        near(bumped.feet().y(),81,"Clip at ceiling");
        near(bumped.vy(),-.08*FlightTiming.VERTICAL_DRAG,"Restart gravity");
        var unknown=new Floor(){public MotionCheck motionSweep(Point a,Point b){return MotionCheck.UNKNOWN;}};
        check(FlightTiming.predict(reset,1,unknown)==null,"Unknown stays unknown");
        var aim=FlightTiming.estimate(-76.29,-14.947,-26.565,-24.604,Aim.defaults());
        check(aim!=null&&aim.ms()>200&&aim.ms()%50==0,"Native curve and frame cadence "+aim);
        check(new Ready(20,100,0).castTick(350)==8,"Ready events overlap and frame after exact tick");
        check(new Ready(20,100,0).castTick(0)==2,"Already aligned can wait only for confirmation");
        check(FlightTiming.estimate(0,0,0,0,Aim.defaults()).ms()==0,"Already aligned native path");

        Point a=new Point(.5,0,.5),b=new Point(.5,0,3.5);
        var walk=route(a,b,false,flat,Movement.WALK).rehearse(start(a.add(new Point(.2,0,0)),flat,0,0,Aim.defaults()),flat,()->false);
        check(walk.complete()&&walk.choices().getLast().reason().equals("TERMINAL_GROUND_PATH"),"Walk repairs a small offset with teleport disabled");
        var step=new Floor(){protected double floor(Point p){return p.z()>=1?1:0;}};
        Point top=new Point(.5,1,1.5);
        var jump=route(a,top,true,step,Movement.TRANSMISSION).rehearse(start(a,step,0,0,Aim.defaults()),step,()->false);
        check(jump.complete()&&jump.choices().getLast().actions().stream().anyMatch(s->s.movement()==Movement.JUMP)
                &&jump.choices().stream().flatMap(c->c.actions().stream()).noneMatch(s->s.movement()==Movement.TRANSMISSION),"Terminal ordinary jump before considering teleport");

        var divided=new Floor(){public boolean clear(Point p){return super.clear(p)&&!(p.x()>=1.2&&p.x()<=1.8);}};
        var walled=route(a,b,true,divided,Movement.WALK);
        check(walled.advanceTo(new Point(2.5,0,2.8),0,divided)<0,"Wall prevents projecting onto a nearby guide arm");
        check(walled.advanceTo(new Point(.7,0,2.8),0,divided)>.5,"Connected offset advances without revisiting reference");

        var raw=TransmissionModel.raw(a,-26,-10,10);
        var snapped=TransmissionModel.landing(a,-26,-10,10);
        var rayTerrain=new Floor(){
            public boolean sweep(Point from,Point to){
                return (!to.equals(snapped)||from.distance(raw)<.001)&&super.sweep(from,to);
            }
        };
        var ray=new Segment(Movement.TRANSMISSION,a,snapped,-26,-10,0,0,10);
        var rayGuide=new CorridorRepair(new Result(Status.ARRIVED,List.of(ray),snapped,snapped,snapped,0,0,Set.of(),"TEST"),
                Options.defaults(),CorridorPlanner.Timing.defaults(),rayTerrain);
        var halfway=new Point((a.x()+raw.x())/2,(a.y()+raw.y())/2,(a.z()+raw.z())/2);
        check(rayGuide.advanceTo(halfway,0,rayTerrain)>.4,"Reconnect using the verified ray and snap, including the bend");

        var finalLeg=route(a,b,true,flat,Movement.WALK);
        double overshot=finalLeg.advanceTo(new Point(.5,0,6.5),0,flat);
        check(overshot>0&&finalLeg.advanceTo(new Point(.5,0,5.5),overshot,flat)>overshot,
                "Terminal progress can recover from overshoot before standing arrival");

        Point elevated=new Point(.5,3,4.5);
        var ledge=new Floor(){
            public boolean supported(Point p){return p.equals(a)||p.equals(elevated);}
            public boolean sweep(Point from,Point to){
                if(Math.abs(to.z()-from.z())>.001 && from.y()<.25)return false;
                return super.sweep(from,to);
            }
        };
        var fast=new Aim(2000,6,120,.5,.25,.5,false,false,.5);
        var climbing=route(a,elevated,true,ledge,Movement.TRANSMISSION);
        var assisted=climbing.choices(start(a,ledge,0,-33,fast),0,ledge,7,()->false);
        check(assisted.stream().anyMatch(CorridorRepair.Choice::jumpAssist),"Higher unsupported path offers timed jump plus cast");
        var noAbility=route(a,elevated,false,ledge,Movement.TRANSMISSION).rehearse(start(a,ledge,0,-33,fast),ledge,()->false);
        check(!noAbility.complete(),"Disabled teleport leaves inaccessible terminal blocked");
        var midAir=new Point(.5,4,.5);
        var airGoal=new Point(.5,4,2.5);
        var afterRay=CorridorRepair.landing(midAir,0,0,12,flat);
        var airCast=new Segment(Movement.TRANSMISSION,midAir,afterRay,0,0,0,0,12);
        var airRoute=new CorridorRepair(new Result(Status.ARRIVED,List.of(airCast),airGoal,airGoal,afterRay,0,0,Set.of(),"TEST"),
                new Options(Mode.NORMAL,true,false,12,1,20000,Weights.defaults()),CorridorPlanner.Timing.defaults(),flat);
        var airState=Motion.confirmed(afterRay,flat);
        check(!airRoute.reached(airState,null,flat)&&airRoute.reached(airState,airCast,flat),
                "An air goal outside arrival radius can be crossed by the validated effective ray");
        System.out.println("PASS "+checks+" flight timing, physical motion, ordered corridor and terminal repair checks");
    }
}
