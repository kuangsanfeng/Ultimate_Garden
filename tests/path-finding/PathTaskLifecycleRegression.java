package com.kuangsanfeng.ultimategarden.client.function.path_finding.execution;

import com.google.gson.JsonParser;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.TargetCoordinates;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.execution.*;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.FlightTiming.*;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.*;
import com.kuangsanfeng.ultimategarden.client.platform.input.InputRequests;
import com.kuangsanfeng.ultimategarden.client.runtime.hotkey.PathHotkey;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.lwjgl.glfw.GLFW;
import static com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.*;
import static com.kuangsanfeng.ultimategarden.client.platform.input.InputRequests.Channel.*;
import static com.kuangsanfeng.ultimategarden.client.platform.persistence.log.JsonSnapshot.map;

public final class PathTaskLifecycleRegression {
    static final Point START=new Point(.5,0,.5), A=new Point(2.5,0,.5), B=new Point(7.5,0,.5);
    static final Options OPTIONS=new Options(Mode.NORMAL,false,false,12,1.25,60,Weights.defaults());
    static final LocalDateTime TIME=LocalDateTime.of(2026,11,20,3,41,8,237_000_000);
    static final String A_RAW="2.5 0 .5", B_RAW="7.5 0 .5";

    static void check(boolean pass,String why){if(!pass)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);
        for(String scenario:List.of("planning-failure","action-failure","cancel-during-planning","late-worker","stop-during-preparation","stop-during-rejoin","stop-during-rehearsal"))
            lifecycle(root.resolve(scenario),scenario);
        explicitRouting(root.resolve("routing"));
        receiptsAndInput();
        terminal();
        collision(root.resolve("collision"));
        check(PathTaskLog.openIfEnabled(false,root.resolve("disabled"),TIME,"Hub",B,"disabled",w->{})==null
                &&!Files.exists(root.resolve("disabled/logs/ultimategarden")),"disabled creates no writer or directory");
        System.out.println("PASS disabled logging");
    }
    static void lifecycle(Path root,String scenario)throws Exception{
        try(var service=new PathService()){
            var requests=new InputRequests();
            Object independent=new Object();
            requests.set(independent,CAPTURE);
            var first=new Driver(root,START,A_RAW,scenario.equals("action-failure"),scenario.equals("stop-during-preparation"),requests);
            var second=new Driver(root,START,B_RAW,false,false,requests);
            var resultsA=new ArrayList<Result>();
            var resultsB=new ArrayList<Result>();
            var entered=new CountDownLatch(1);
            var release=new CountDownLatch(1);
            var invocations=new java.util.concurrent.atomic.AtomicInteger();
            if(scenario.equals("stop-during-rejoin"))first.rejoinService=service;
            if(scenario.equals("stop-during-rehearsal")){
                first.rehearsalEntered=entered;first.rehearsalRelease=release;
            }
            Supplier<Terrain> oldTerrain=()->{
                int call=invocations.incrementAndGet();
                if(scenario.equals("planning-failure"))return terrain(x->null);
                if(scenario.contains("planning")||scenario.equals("late-worker")||scenario.equals("stop-during-rejoin")&&call>1){
                    entered.countDown();
                    long end=System.nanoTime()+2_000_000_000L;
                    while(System.nanoTime()<end&&release.getCount()!=0){
                        try{release.await(20,TimeUnit.MILLISECONDS);}catch(InterruptedException ignored){}
                    }
                }
                return terrain(x->0.);
            };
            var ticket=service.start(first,START,parse(A_RAW),-90,0,OPTIONS,oldTerrain,CorridorPlanner.Timing.defaults(),first,
                    result->{resultsA.add(result);first.finish(result);});
            if(scenario.equals("planning-failure")||scenario.equals("action-failure")){
                tickUntil(service,()->!resultsA.isEmpty());
                check(resultsA.getFirst().status()!=Status.ARRIVED,"A fixture failed");
            }else{
                if(first.rejoinService!=null)tickUntil(service,()->first.rejoining!=null);
                if(first.rehearsalEntered!=null)
                    tickUntil(service,()->service.workPhase().equals("REHEARSAL"));
                if(!first.preparing)check(entered.await(2,TimeUnit.SECONDS),"worker entered");
                first.trace.add("HOTKEY","FIXTURE",0,0,map("decision","STOP","stage",service.stage()));
                service.cancel(ticket);
                if(first.rejoining!=null)check(first.rejoining.isCancelled(),"rejoin future cancelled");
                check(resultsA.getFirst().status()==Status.CANCELLED,"A cancelled");
            }
            check(requests.blocked(CAPTURE)&&!requests.blocked(MOTION),"A releases only own request");
            var replacement=service.start(second,START,parse(B_RAW),-90,0,OPTIONS,()->terrain(x->0.),
                    CorridorPlanner.Timing.defaults(),second,result->{resultsB.add(result);second.finish(result);});
            release.countDown();
            tickUntil(service,()->!resultsB.isEmpty());
            check(replacement.sequence()!=ticket.sequence()&&resultsA.size()==1&&resultsB.size()==1,"old completion cannot apply twice");
            check(resultsB.getFirst().status()==Status.ARRIVED&&second.issued.equals(B)&&second.target.equals(B)&&second.displayed.equals(B),
                    "B reached and request/display owner is B");
            first.log.awaitClose(3000);second.log.awaitClose(3000);
            validateJournal(root,A,A_RAW,first.trace.runId());
            validateJournal(root,B,B_RAW,second.trace.runId());
            System.out.println("PASS "+scenario+" -> new goal "+B);
        }
    }
    static void explicitRouting(Path root)throws Exception{
        Segment cold=routeProbe(root.resolve("cold"),null,false);
        check(cold.equals(routeProbe(root.resolve("paused-chat"),PathRequest.Source.CHAT_HOTKEY,false)),"chat B wins beside paused A");
        check(cold.equals(routeProbe(root.resolve("same-destination"),PathRequest.Source.MANUAL,true)),"same coordinate is a new run");
        check(cold.equals(routeProbe(root.resolve("feature"),PathRequest.Source.FEATURE,false)),"feature uses same start entry");
        System.out.println("PASS typed requests: cold/warm first-action equality, same destination, feature, stale callbacks");
    }
    static Segment routeProbe(Path root,PathRequest.Source warmSource,boolean sameDestination)throws Exception{
        try(var service=new PathService()){
            var input=new InputRequests();
            PathService.Ticket old=null;
            Driver oldDriver=null;
            var workerStarted=new CountDownLatch(1);
            var workerRelease=new CountDownLatch(1);
            if(warmSource!=null){
                var oldGoal=sameDestination?B:A;
                var a=new Driver(root,START,sameDestination?B_RAW:A_RAW,false,false,input);
                oldDriver=a;
                old=service.launch(PathRequest.start(PathRequest.Source.MANUAL,oldGoal),
                        ()->launch(a,()->{
                            workerStarted.countDown();
                            long until=System.nanoTime()+2_000_000_000L;
                            while(System.nanoTime()<until&&workerRelease.getCount()>0){
                                try{workerRelease.await(20,TimeUnit.MILLISECONDS);}catch(InterruptedException ignored){}
                            }
                            return terrain(x->0.);
                        }));
                check(workerStarted.await(2,TimeUnit.SECONDS),"A worker started");
            }
            PathRequest savedRelay=null;
            if(warmSource==PathRequest.Source.CHAT_HOTKEY){
                savedRelay=PathRequest.resume(old.destination(),old);
                service.cancel(old,"PLAYER_GUI_CHAT");
            }
            var b=new Driver(root,START,B_RAW,false,false,input);
            b.rehearseFixture=true;
            b.allowReplan=true;
            var oldTicket=old;
            var next=service.launch(PathRequest.start(warmSource==null?PathRequest.Source.MANUAL:warmSource,B),()->{
                if(oldTicket!=null)check(!input.has(oldTicket.owner()),"A input released before B samples");
                return launch(b,()->terrain(x->0.));
            });
            if(old!=null){
                var stale=PathRequest.rejoin(old,START);
                check(service.planRejoin(stale,null,null,0,Set.of())==null,"stale rejoin rejected before work");
                service.replan(PathRequest.replan(old,START),"LATE_A");
                check(!old.runId().equals(next.runId())&&next.destination().equals(B),"new identity for B");
            }
            if(savedRelay!=null)check(savedRelay.coordinate().equals(A)&&next.destination().equals(B),"saved relay cannot become the chat destination");
            workerRelease.countDown();
            tickUntil(service,()->b.first!=null);
            check(b.rehearsed&&B.equals(b.issued)&&B.equals(b.displayed),"B planning, rehearsal and handoff");
            var result=new Result(Status.ARRIVED,List.of(b.first),B,B,B,0,0,Set.of(),"FIXTURE");
            var corridor=new CorridorRepair(result,OPTIONS,CorridorPlanner.Timing.defaults(),terrain(x->0.));
            var recovery=service.planRejoin(PathRequest.rejoin(next,START),corridor,b.forecast(),0,Set.of());
            check(recovery!=null&&next.destination().equals(B),"rejoin origin does not replace destination");
            recovery.get(2,TimeUnit.SECONDS);
            service.replan(PathRequest.replan(next,START),"FIXTURE_LOCAL_RECOVERY_FAILED");
            tickUntil(service,()->b.routing.contains("REPLAN_SCHEDULED"));
            check(next.destination().equals(B),"replan preserves B");
            service.cancel(next);
            if(oldDriver!=null)oldDriver.log.awaitClose(3000);
            b.log.awaitClose(3000);
            check(b.routing.contains("STARTED_NEW")&&b.routing.contains("REJOIN_SCHEDULED")
                    &&(old==null||b.routing.contains("DROPPED_STALE")),"routing evidence");
            return b.first;
        }
    }
    static PathService.Launch launch(Driver driver,Supplier<Terrain> terrain){
        return new PathService.Launch(driver,START,-90,0,OPTIONS,terrain,CorridorPlanner.Timing.defaults(),driver,driver::finish);
    }
    static Point parse(String text){return TargetCoordinates.extract(text).orElseThrow();}
    static void validateJournal(Path root,Point target,String raw,String runId)throws Exception{
        String x=target.x()+"";
        Path file;
        try(var files=Files.list(root.resolve("logs/ultimategarden"))){
            file=files.filter(p->p.getFileName().toString().contains("_to_"+x+"_")).findFirst().orElseThrow();
        }
        var rows=Files.readAllLines(file).stream().map(line->JsonParser.parseString(line).getAsJsonObject()).toList();
        check(rows.getFirst().get("event").getAsString().equals("TASK_START"),"first start");
        check(rows.getFirst().getAsJsonObject("data").get("rawInput").getAsString().equals(raw),"raw parsed target");
        check(rows.getLast().get("event").getAsString().equals("TASK_END"),"last terminal");
        check(rows.stream().allMatch(r->r.get("runId").getAsString().equals(runId)),"only own run");
    }
    static void tickUntil(PathService service,BooleanSupplier done)throws Exception{
        long deadline=System.nanoTime()+4_000_000_000L;
        while(!done.getAsBoolean()&&System.nanoTime()<deadline){service.tick();Thread.sleep(2);}
        check(done.getAsBoolean(),"fixture timeout");
    }
    static final class Driver implements PathService.Driver{
        final Path root;
        final String raw;
        final boolean shouldFail,preparing;
        final InputRequests input;
        PathFailureTrace trace;
        PathTaskLog log;
        Point at,target,issued,displayed;
        PathService rejoinService;
        Future<CorridorRejoin.Search> rejoining;
        CorridorRepair corridor;
        CountDownLatch rehearsalEntered,rehearsalRelease;
        Segment action,first;
        boolean rehearseFixture,rehearsed,allowReplan;
        final List<String> routing=new ArrayList<>();
        PathService.Ticket ticket;
        Driver(Path root,Point start,String raw,boolean fail,boolean preparing,InputRequests input){
            this.root=root;this.at=start;this.raw=raw;this.shouldFail=fail;this.preparing=preparing;this.input=input;
        }
        public Point position(){return at;}
        public boolean usable(){return true;}
        public boolean needsPreparation(){return preparing;}
        public boolean prepare(){return false;}
        public void begin(Segment step){
            action=step;
            if(first==null){
                first=step;
                log.record(trace.add("FIRST_ACTION_DISPATCH","FIXTURE",0,0,map("action",step)));
            }
        }
        public void requestRouted(PathRequest request,PathService.Ticket old,PathService.Ticket next,Point goal,String disposition){
            routing.add(disposition);
            log.record(trace.add("PATH_REQUEST","FIXTURE",0,0,map("source",request.source(),"operation",request.operation(),
                    "coordinate",request.coordinate(),"coordinateRole",request.role(),"originalGoal",goal,"disposition",disposition,
                    "previousRunId",old==null?null:old.runId(),"nextRunId",next==null?null:next.runId())));
        }
        public void rehearsalReady(CorridorRepair.Rehearsal rehearsal){rehearsed=rehearsal.complete();}

        public boolean completed(){at=action.to();return true;}
        public boolean failed(){return shouldFail&&action!=null;}
        public void release(){input.remove(this);}
        public boolean gpuAcceleration(){return false;}
        public boolean canReplan(){return allowReplan;}
        public int candidateWorkers(){return 1;}
        public void sessionStarted(PathService.Ticket ticket,Point start,Point goal){
            this.ticket=ticket;
            trace=new PathFailureTrace("NORMAL","Hub",ticket.runId(),packet->log.record(packet));
            input.set(this,MOTION,KEYBOARD);
            target=goal;
            log=new PathTaskLog(root,TIME,"Hub",goal,trace.runId(),w->{});
            log.record(trace.add("TASK_START","FIXTURE",0,0,map("actualStart",start,"rawInput",raw,
                    "goal",goal,"normalizedTarget",TargetCoordinates.centered(goal),"evidenceSource","OFFLINE_FIXTURE")));
        }
        public void planningRequested(PathService.Ticket ticket,int attempt,Point start,Point goal){
            issued=goal;
            trace.planAttempt(attempt);
            log.record(trace.add("PLANNING_REQUEST","FIXTURE",0,0,map("goal",goal,"ticket",ticket.sequence())));
        }
        public void planReady(Result result){
            displayed=result.resolved();
            if(rejoinService!=null)corridor=new CorridorRepair(result,OPTIONS,CorridorPlanner.Timing.defaults(),terrain(x->0.));
        }
        public CorridorRepair.Start forecast(){
            return rehearsalEntered==null&&!rehearseFixture?null:new CorridorRepair.Start(new Motion(at,0,0,0,true),
                    -90,0,0,new Aim(300,15,60,.5,.25,.5,false,false,.5),new Ready(0,0,0));
        }
        public void prepareRehearsalOverlay(CorridorRepair.Rehearsal result,BooleanSupplier cancelled){
            if(rehearsalEntered==null)return;
            rehearsalEntered.countDown();
            try{rehearsalRelease.await(2,TimeUnit.SECONDS);}catch(InterruptedException expected){Thread.currentThread().interrupt();}
        }
        public boolean followsCorridor(){return corridor!=null;}
        public boolean advanceCorridor(){
            if(rejoining==null)rejoining=rejoinService.planRejoin(PathRequest.rejoin(ticket,at),corridor,new CorridorRepair.Start(new Motion(at,0,0,0,true),
                    -90,0,0,new Aim(300,15,60,.5,.25,.5,false,false,.5),new Ready(0,0,0)),0,Set.of());
            return false;
        }
        void finish(Result result){
            trace.export(result.reason());
            log.finish(trace.add("TASK_END","FIXTURE",0,0,map("status",result.status(),"reason",result.reason(),
                    "lastActualFeet",result.end(),"inputReleased",!input.has(this))));
        }
    }
    static void receiptsAndInput(){
        var receipts=new PathPositionReceipt();Object connection=new Object(),first=new Object(),second=new Object();
        Object world=new Object();
        receipts.context(connection,world);
        receipts.dispatch(first,A);
        receipts.retire(first);
        check(receipts.pending()&&receipts.retired(),"old cast fenced while waiting");
        check(receipts.accept(B)==null&&receipts.pending(),"unrelated correction does not drain old cast");
        check(receipts.accept(A)==null&&!receipts.pending(),"late packet drains without a recipient");
        receipts.dispatch(second,B);
        check(receipts.accept(B)==second,"only B owns its packet");
        receipts.dispatch(second,B);receipts.retire(second);
        receipts.context(connection,new Object());
        check(!receipts.pending(),"new world clears old position fence");
        check(PathHotkey.decide(false,true,GLFW.GLFW_PRESS)==PathHotkey.Decision.PASS
                &&PathHotkey.decide(true,true,GLFW.GLFW_PRESS)==PathHotkey.Decision.TOGGLE
                &&PathHotkey.decide(true,true,GLFW.GLFW_REPEAT)==PathHotkey.Decision.SUPPRESS_REPEAT
                &&PathHotkey.decide(true,true,GLFW.GLFW_RELEASE)==PathHotkey.Decision.PASS
                &&PathHotkey.decide(true,false,GLFW.GLFW_PRESS)==PathHotkey.Decision.PASS,"rebound physical press only");
        System.out.println("PASS receipt race, old-packet quarantine, hotkey press/repeat/release");
    }
    static void terminal(){
        for(Mode mode:Mode.values()){
            var floor=terrain(x->0.);
            var walk=terminalChoice(mode,START,B,floor);
            check(walk.decision().equals("ORDINARY")&&walk.choices().getFirst().actions().stream().noneMatch(a->a.movement()==Movement.TRANSMISSION),mode+" walking wins");
            var half=terrain(x->x>=1?.5:0.);
            var slab=terminalChoice(mode,START,new Point(2.5,.5,.5),half);
            check(slab.decision().equals("ORDINARY")&&slab.choices().getFirst().last().to().y()==.5,mode+" slab");
            var higher=terrain(x->x>=1?1.:0.);
            var jump=terminalChoice(mode,START,new Point(2.5,1,.5),higher);
            check(jump.decision().equals("ORDINARY")&&jump.choices().getFirst().actions().stream().anyMatch(a->a.movement()==Movement.JUMP),mode+" ordinary jump");
            var fall=terminalChoice(mode,new Point(.5,.5,.5),START,floor);
            check(fall.decision().equals("ORDINARY")&&fall.choices().getFirst().last().movement()==Movement.FALL,mode+" safe fall");
            var gap=terrain(x->x<1||x>=4?0.:null);
            var cast=terminalChoice(mode,START,B,gap);
            check(cast.decision().equals("TRANSMISSION")&&cast.choices().getFirst().last().movement()==Movement.TRANSMISSION,
                    mode+" gap cast ("+cast+")");
            var barrier=terrain(x->x>=1&&x<4?5.:0.);
            var blocked=terminalChoice(mode,START,B,barrier);
            check(blocked.choices().isEmpty()&&blocked.ordinaryReason().equals("ORDINARY_NO_PATH")
                    &&blocked.decision().equals("NO_LEGAL_TERMINAL_CAST"),mode+" specific dual failure");
            System.out.println("PASS "+mode+" terminal walk, slab, jump, fall, cast, blocked");
        }
    }
    static CorridorRepair.TerminalDecision terminalChoice(Mode mode,Point from,Point goal,Terrain terrain){
        var ref=new Segment(Movement.TRANSMISSION,from,goal,-90,0,0,1,Math.min(12,from.distance(goal)));
        var result=new Result(Status.ARRIVED,List.of(ref),goal,goal,goal,0,0,Set.of(),"FIXTURE");
        var opts=new Options(mode,true,false,12,1.25,60,Weights.defaults());
        var corridor=new CorridorRepair(result,opts,CorridorPlanner.Timing.defaults(),terrain);
        return corridor.terminalDecision(new CorridorRepair.Start(new Motion(from,0,0,0,terrain.supported(from)),
                -90,0,0,new Aim(300,15,60,.5,.25,.5,false,false,.5),new Ready(0,0,0)),0,terrain,7,()->false);
    }
    static Terrain terrain(DoubleFunction<Double> height){
        return new Terrain(){
            Double floor(Point p){if(p.x()<0||p.x()>14||p.z()<0||p.z()>1)return null;return height.apply(p.x());}
            public boolean clear(Point p){
                if(p.x()<0||p.x()>14||p.z()<0||p.z()>1)return false;
                var h=floor(p);return h==null||p.y()>=h-1e-6;
            }
            public boolean supported(Point p){var h=floor(p);return h!=null&&Math.abs(p.y()-h)<1e-6;}
            public boolean sweep(Point from,Point to){
                for(int i=0;i<=20;i++){
                    double t=i/20.;
                    if(!clear(new Point(from.x()+(to.x()-from.x())*t,from.y()+(to.y()-from.y())*t,from.z()+(to.z()-from.z())*t)))return false;
                }
                return true;
            }
            public Optional<Point> ground(Point p){var h=floor(p);return h!=null&&p.y()>=h?Optional.of(new Point(p.x(),h,p.z())):Optional.empty();}
            public Set<String> missing(){return Set.of();}
        };
    }
    static void collision(Path root)throws Exception{
        for(String id:List.of("aaaaaaaa-0000","bbbbbbbb-0000")){
            var log=new PathTaskLog(root,TIME,"Hub",B,id,w->{});
            log.record(map("event","TASK_START","runId",id));
            log.record(map("event","PLANNING_REQUEST","runId",id,"planAttempt",0));
            log.record(map("event","PLANNING_REQUEST","runId",id,"planAttempt",1));
            log.finish(map("event","TASK_END","runId",id));
            log.awaitClose(3000);
        }
        try(var files=Files.list(root.resolve("logs/ultimategarden"))){
            var all=files.toList();check(all.size()==2,"same ms exclusive filenames");
            for(var file:all){var rows=Files.readAllLines(file);check(rows.size()==4&&rows.getLast().contains("TASK_END"),"replan stays in its exclusive file");}
        }
        System.out.println("PASS exclusive names and same-file retry");
    }
}
