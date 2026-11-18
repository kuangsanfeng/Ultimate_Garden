package com.kuangsanfeng.ultimategarden.client.function.path_finding.planning;

import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import java.util.*;
import java.util.function.BooleanSupplier;
import static com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.*;

/** Resumes from verified forward actions and never dispatches partial routes. */
public final class SegmentedPlanner {
    public enum Phase { GROUNDED, AIRBORNE, DESCENDING }
    public record State(Point feet, double yaw, double pitch, Phase phase, double airborneDistance, double cost) {
        boolean grounded() { return phase==Phase.GROUNDED; }
    }
    private record Key(Point feet, double yaw, double pitch, Phase phase, double airborneDistance) {
        Key(State s) { this(s.feet, canonicalAngle(s.yaw), canonicalAngle(s.pitch), s.phase, s.airborneDistance); }
        private static double canonicalAngle(double angle) {
            double wrapped=Math.IEEEremainder(angle,360);
            return wrapped==0?0:wrapped;
        }
    }

    private SegmentedPlanner() {}

    public static Result plan(Terrain terrain, Options options, int maxSegments, CorridorPlanner.Timing timing,
                              BooleanSupplier cancelled, SearchAudit audit, Point start, Point goal, double yaw, double pitch) {
        return plan(terrain,options,maxSegments,timing,cancelled,audit,start,goal,yaw,pitch,1);
    }

    public static Result plan(Terrain terrain,Options options,int maxSegments,CorridorPlanner.Timing timing,
                              BooleanSupplier cancelled,SearchAudit audit,Point start,Point goal,double yaw,double pitch,int workers) {
        return plan(terrain,options,maxSegments,timing,cancelled,audit,start,goal,yaw,pitch,workers,false);
    }

    public static Result plan(Terrain terrain,Options options,int maxSegments,CorridorPlanner.Timing timing,
                              BooleanSupplier cancelled,SearchAudit audit,Point start,Point goal,double yaw,double pitch,int workers,
                              boolean gpuEnabled) {
        var map=terrain instanceof com.kuangsanfeng.ultimategarden.client.function.path_finding.terrain.MapTerrain terrainMap?terrainMap:null;
        try(var gpu=new GpuCollision(gpuEnabled,map,audit)){
            return plan(terrain,options,maxSegments,timing,cancelled,audit,start,goal,yaw,pitch,workers,gpu,map!=null);
        }
    }

    private static Result plan(Terrain terrain,Options options,int maxSegments,CorridorPlanner.Timing timing,
                               BooleanSupplier cancelled,SearchAudit audit,Point start,Point goal,double yaw,double pitch,int workers,
                               GpuCollision gpu,boolean useBounds) {
        var checks=new CandidateWorkers(terrain.parallelQueries()?workers:1);
        if (maxSegments < 1) throw new IllegalArgumentException("Invalid segment limit");
        long totalBudget = Math.multiplyExact(options.budget(), maxSegments);
        State state = new State(start, yaw, pitch, terrain.supported(start)?Phase.GROUNDED:Phase.AIRBORNE, 0, 0);
        var visited = new HashSet<Key>();
        visited.add(new Key(state));
        var route = new ArrayList<Segment>();
        var saved = new ArrayDeque<ParentFrontier>();
        Alternative best=null;
        Alternative pending=null;
        long explored=0;
        CorridorPlanner planner=null;
        var region=useBounds?new SearchRegion(start,goal,audit!=null):null;
        if(region!=null) emit(audit,"SEARCH_MASK",region.mask());
        Point resolved=goal;
        String resume="INITIAL";
        for (int number=1;number<=maxSegments;number++) {
            long roundStart=explored, roundChecks=0;
            State roundOrigin=state;
            var activeMask=region==null?Map.of():region.mask();
            emit(audit,"SEGMENT_START",Map.of("segment",number,"start",state,"budget",options.budget(),"totalBudget",totalBudget,
                    "totalExpanded",explored,"resume",resume,"frontiers",planner==null?Map.of():planner.frontiers()));
            Result attempt;
            Status status;
            String reason;
            Alternative next;
            Map<String,Object> frontiers;
            boolean mayContinue;
            int count;
            for (;;) {
                boolean newOrigin=planner==null;
                if(newOrigin)planner=new CorridorPlanner(terrain,options,timing,cancelled,audit,checks,region,gpu);
                long before=planner.expanded(), checksBefore=planner.candidateChecks();
                if(audit!=null)audit.beginSegment(number,explored,before);
                attempt=newOrigin?planner.begin(state.feet,goal,state.yaw,state.pitch,state.airborneDistance,true):null;
                if(attempt==null)attempt=planner.advanceRound(options.budget()-(explored-roundStart),!saved.isEmpty());
                explored+=planner.expanded()-before;
                roundChecks+=planner.candidateChecks()-checksBefore;
                resolved=attempt.resolved();
                status=attempt.status();
                reason=attempt.reason();
                if(status==Status.ARRIVED) {
                    State end=apply(state,attempt.segments(),terrain,options.distance(),true,gpu.used());
                    if(end!=null){
                        next=new Alternative(end,attempt.segments());
                        route.addAll(attempt.segments());
                        break;
                    }
                    status=Status.NO_LOCAL_ACTION;
                    reason="SEGMENT_SEAM_REJECTED";
                }
                if(status!=Status.CANCELLED && !saved.isEmpty()
                        && (reason.equals("PREFIX_FIRST_ACTION_EXHAUSTED") || planner.frontiers().isEmpty())){
                    var parent=saved.pop();
                    emit(audit,"PREFIX_ROLLBACK",Map.of("segment",number,"rejected",state,"reason",reason,"restored",parent.state,
                            "totalExpanded",explored,"remainingInRound",options.budget()-(explored-roundStart),
                            "frontiers",parent.planner.frontiers()));
                    route.subList(parent.length,route.size()).clear();
                    state=parent.state;
                    planner=parent.planner;
                    pending=parent.pending;
                    resume="PREFIX_ROLLBACK";
                    if(explored-roundStart<options.budget()){
                        emit(audit,"PREFIX_FRONTIER_RESUMED",Map.of("segment",number,"state",state,
                                "totalExpanded",explored,"frontiers",planner.frontiers()));
                        continue;
                    }
                }
                next=null;
                break;
            }

            List<List<Segment>> candidates=status==Status.ARRIVED||status==Status.CANCELLED?List.of():planner.prefixes(resolved);
            count=candidates.size();
            if(status!=Status.ARRIVED && status!=Status.CANCELLED){
                next=choosePrefix(state,resolved,candidates,visited,terrain,options.distance(),gpu.used());
                if(next!=null){
                    var candidateRoute=new ArrayList<>(route);
                    candidateRoute.addAll(next.actions);
                    if(best==null||next.end.feet.distance(resolved)<best.end.feet.distance(resolved))
                        best=new Alternative(next.end,candidateRoute);
                }
            }
            var roundChunks=region==null?List.of():region.drain();
            boolean expansion=region!=null&&number==1&&maxSegments>1&&status!=Status.ARRIVED&&status!=Status.CANCELLED;
            if(expansion){
                var beforeMask=region.mask();
                region.expand();
                long priorChecks=planner.candidateChecks();
                int restored=planner.expandRegion();
                emit(audit,"SEARCH_MASK_EXPANDED",Map.of("before",beforeMask,"after",region.mask(),"restored",restored,
                        "candidateChecks",planner.candidateChecks()-priorChecks,"firstRoundStatus",status,"firstRoundReason",reason));
            }
            frontiers=planner.frontiers();
            mayContinue=status!=Status.CANCELLED && (status==Status.BUDGET_EXHAUSTED||expansion||resume.equals("PREFIX_ROLLBACK"))
                    && !frontiers.isEmpty();
            if(expansion && mayContinue && next!=null){
                pending=next;
                next=null;
            }
            if(!mayContinue && status!=Status.ARRIVED && status!=Status.CANCELLED && next==null && pending!=null
                    && !visited.contains(new Key(pending.end))){
                next=pending;
                pending=null;
            }
            if(status!=Status.ARRIVED && next!=null)mayContinue=true;
            boolean startsNext=mayContinue&&number<maxSegments;
            var info=new LinkedHashMap<String,Object>();
            info.put("segment",number);
            info.put("status",status);
            info.put("expanded",explored-roundStart);
            info.put("candidateChecks",roundChecks);
            info.put("totalExpanded",explored);
            info.put("unusedInRound",options.budget()-(explored-roundStart));
            info.put("unusedTotal",totalBudget-explored);
            info.put("termination",reason);
            info.put("verifiedPrefixes",count);
            info.put("selectedPrefix",next==null?List.of():next.actions);
            info.put("start",roundOrigin);
            info.put("end",next==null?null:next.end);
            info.put("missing",attempt.missing());
            info.put("frontiers",frontiers);
            info.put("next",startsNext?(next==null?"RETAINED_FRONTIER":"VERIFIED_PREFIX"):"NONE");
            info.put("segmentBudgetExhausted",explored-roundStart==options.budget());
            info.put("expandedChunks",roundChunks);
            info.put("searchMask",activeMask);
            emit(audit,"SEGMENT_END",info);
            if(status==Status.ARRIVED)return result(status,route,goal,resolved,next.end,explored,terrain,reason);
            if(!startsNext){
                if(best!=null && (route.isEmpty()||best.end.feet.distance(resolved)<state.feet.distance(resolved)))
                    return result(status,best.actions,goal,resolved,best.end,explored,terrain,mayContinue?"SEGMENT_LIMIT":reason);
                return result(status,route,goal,resolved,state,explored,terrain,mayContinue?"SEGMENT_LIMIT":reason);
            }
            resume=next==null?"RETAINED_FRONTIER":"VERIFIED_PREFIX";
            if(next!=null){
                saved.push(new ParentFrontier(state,planner,route.size(),pending));
                route.addAll(next.actions);
                state=next.end;
                visited.add(new Key(state));
                pending=null;
                planner=null;
            }
        }
        throw new AssertionError("Unreachable segment loop exit");
    }

    private record Alternative(State end,List<Segment> actions){}
    private record ParentFrontier(State state,CorridorPlanner planner,int length,Alternative pending){}

    private static Alternative choosePrefix(State state,Point resolved,List<List<Segment>> candidates,Set<Key> visited,
                                            Terrain terrain,double range,boolean validateCasts) {
        for(var steps:candidates){
            State end=apply(state,steps,terrain,range,false,validateCasts);
            if(end!=null&&!visited.contains(new Key(end))&&end.feet.distance(resolved)<state.feet.distance(resolved))
                return new Alternative(end,steps);
        }
        return null;
    }



    private static State apply(State start, List<Segment> actions, Terrain terrain, double range, boolean complete,boolean validateCasts) {
        State current=start;
        for (int i=0; i<actions.size(); i++) {
            Segment action=actions.get(i);
            if (!current.feet.equals(action.from())) return null;
            boolean grounded=terrain.supported(action.to());
            double travel=current.grounded() ? 0 : current.airborneDistance;
            if (action.movement()==Movement.TRANSMISSION) {
                if (action.castDistance()<=0 || action.castDistance()>range) return null;
                if(validateCasts){
                    Point raw=com.kuangsanfeng.ultimategarden.client.function.path_finding.model.TransmissionModel.raw(
                            action.from(),action.yaw(),action.pitch(),action.castDistance());
                    if(!terrain.clear(action.to())||!terrain.sweep(action.from(),raw)||!terrain.sweep(raw,action.to()))return null;
                }
                travel+=action.castDistance();
                if (travel>180 && !grounded && !(complete && i==actions.size()-1)) return null;
            }
            Phase phase=grounded ? Phase.GROUNDED : action.movement()==Movement.FALL ? Phase.DESCENDING : Phase.AIRBORNE;
            current=new State(action.to(), action.yaw(), action.pitch(), phase, grounded?0:travel, current.cost+action.cost());
        }
        return current;
    }

    private static Result result(Status status,List<Segment> route,Point goal,Point resolved,State end,long explored,Terrain terrain,String reason) {
        return new Result(status, route, goal, resolved, end.feet, end.cost, explored, terrain.missing(), reason);
    }
    private static void emit(SearchAudit audit,String event,Map<String,Object> data) {
        if (audit!=null) audit.emit(event,data);
    }
}
