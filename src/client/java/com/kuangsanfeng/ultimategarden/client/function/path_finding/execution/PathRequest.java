package com.kuangsanfeng.ultimategarden.client.function.path_finding.execution;

import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import static java.util.Objects.requireNonNull;

/** A destination, recovery origin and saved destination are different request types. */
public record PathRequest(Source source, Operation operation, Point coordinate, CoordinateRole role,
                          PathService.Ticket ticket) {
    public enum Source { MANUAL, CHAT_HOTKEY, FEATURE, GUI_RELAY, LOCAL_RECOVERY, EXECUTION_RECOVERY }
    public enum Operation { START_NEW, RESUME_EXPLICIT, REJOIN_CURRENT, REPLAN_CURRENT }
    public enum CoordinateRole { DESTINATION, SAVED_DESTINATION, REJOIN_ORIGIN, REPLAN_ORIGIN }

    public PathRequest {
        requireNonNull(source);requireNonNull(coordinate);
        boolean valid=switch(operation){
            case START_NEW -> role==CoordinateRole.DESTINATION&&ticket==null
                    &&(source==Source.MANUAL||source==Source.CHAT_HOTKEY||source==Source.FEATURE);
            case RESUME_EXPLICIT -> role==CoordinateRole.SAVED_DESTINATION&&ticket!=null&&source==Source.GUI_RELAY;
            case REJOIN_CURRENT -> role==CoordinateRole.REJOIN_ORIGIN&&ticket!=null&&source==Source.LOCAL_RECOVERY;
            case REPLAN_CURRENT -> role==CoordinateRole.REPLAN_ORIGIN&&ticket!=null&&source==Source.EXECUTION_RECOVERY;
        };
        if(!valid)throw new IllegalArgumentException("Inconsistent path request");
    }

    public static PathRequest start(Source source,Point destination){
        return new PathRequest(source,Operation.START_NEW,destination,CoordinateRole.DESTINATION,null);
    }
    static PathRequest resume(Point destination,PathService.Ticket paused){
        return new PathRequest(Source.GUI_RELAY,Operation.RESUME_EXPLICIT,destination,CoordinateRole.SAVED_DESTINATION,paused);
    }
    static PathRequest rejoin(PathService.Ticket owner,Point origin){
        return new PathRequest(Source.LOCAL_RECOVERY,Operation.REJOIN_CURRENT,origin,CoordinateRole.REJOIN_ORIGIN,owner);
    }
    static PathRequest replan(PathService.Ticket owner,Point origin){
        return new PathRequest(Source.EXECUTION_RECOVERY,Operation.REPLAN_CURRENT,origin,CoordinateRole.REPLAN_ORIGIN,owner);
    }
}
