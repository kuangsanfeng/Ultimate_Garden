package com.kuangsanfeng.ultimategarden.client.function.path_finding.execution;

import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.FlightTiming.Motion;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.CorridorRejoin;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.CorridorRepair;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.CorridorRepair.Choice;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.CorridorRepair.Start;
import java.util.*;
import java.util.function.Predicate;
import static com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.*;

public final class LocalCandidateFallback {
    public static final int LIMIT=7;

    private record StateKey(Motion motion,double yaw,double pitch,double travel,int cooldownTick,
                            long confirmationSequence,double progress,Double heightCorrection,
                            int consecutiveClimbs,boolean upwardTurnUsed) {
        StateKey(Start state,double progress,long confirmationSequence){
            this(state.motion(),state.yaw(),state.pitch(),state.airborneDistance(),
                    (int)Math.ceil(state.ready().cooldownMs()/50),confirmationSequence,progress,state.heightCorrection(),
                    state.consecutiveClimbs(),state.upwardTurnUsed());
        }
    }

    private record CandidateKey(List<Segment> actions,Motion castOrigin,int castTick,boolean jumpAssist) {
        CandidateKey(Choice choice){this(choice.actions(),choice.castOrigin(),choice.castTick(),choice.jumpAssist());}
    }
    private record AttemptKey(StateKey state,CandidateKey choice) {}

    public record Attempt(int rank,Choice choice,String reason,Start completion,int remaining,boolean verified) {}
    public record Decision(Choice choice,List<Attempt> attempts,int remaining,int generated,String reason) {}

    private final Set<AttemptKey> rejected=new HashSet<>();
    private int remaining=LIMIT;

    public void reset(){
        rejected.clear();
        remaining=LIMIT;
    }

    public Decision choose(CorridorRepair corridor,Start actual,double progress,long confirmationSequence,
                           boolean pending,Choice failed,Terrain terrain,Predicate<Choice> eligible){
        if(pending)return new Decision(null,List.of(),remaining,0,"POSITION_CONFIRMATION_PENDING");
        if(remaining==0)return new Decision(null,List.of(),0,0,"REVIEW_LIMIT");
        var state=new StateKey(actual,progress,confirmationSequence);
        if(failed!=null)rejected.add(new AttemptKey(state,new CandidateKey(failed)));
        var choices=corridor.choices(actual,progress,terrain,LIMIT,()->false);
        var attempts=new ArrayList<Attempt>();
        int rank=0;
        for(var candidate:choices){
            rank++;
            var key=new AttemptKey(state,new CandidateKey(candidate));
            if(rejected.contains(key)||!eligible.test(candidate)){
                attempts.add(new Attempt(rank,candidate,rejected.contains(key)?"UNCHANGED_REJECTED_CANDIDATE":
                        "INELIGIBLE_EXISTING_FILTER",null,remaining,false));
                continue;
            }
            if(remaining==0)return new Decision(null,List.copyOf(attempts),0,choices.size(),"REVIEW_LIMIT");
            remaining--;
            var first=candidate.actions().getFirst();
            String reason;
            Start end=null;
            boolean verified=false;
            if(candidate.progress()<=progress+.0001)reason="NO_PROGRESS";
            else if(first.movement()!=Movement.TRANSMISSION&&!CorridorRejoin.validateMotion(first,actual,terrain,corridor.jumpHeight()))
                reason="START_MOTION_REJECTED";
            else if((end=corridor.after(actual,candidate,terrain))==null)reason="CONFIRMATION_MOTION_UNKNOWN";
            else{
                // Cast choices already pass the N/N+1 aim, ray, landing and progress checks in CorridorRepair.
                // The end-to-next-action check uses the same unshifted continuation criterion as live dispatch.
                var continuation=corridor.continuation(end.withHeightCorrection(null),candidate.progress(),terrain);
                reason=continuation.reason();
                verified=continuation.connected();
            }
            attempts.add(new Attempt(rank,candidate,reason,end,remaining,verified));
            if(verified)return new Decision(candidate,List.copyOf(attempts),remaining,choices.size(),"VERIFIED_ALTERNATIVE");
            rejected.add(key);
        }
        String reason=remaining==0?"REVIEW_LIMIT":choices.size()==LIMIT?"GENERATOR_RETURN_LIMIT":"CANDIDATES_EXHAUSTED";
        return new Decision(null,List.copyOf(attempts),remaining,choices.size(),reason);
    }
}
