package com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing;
import java.util.*;import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.*;
public record RouteResult(RouteStrategy strategy,Status status,List<FlightResult> segments,List<Point> points,Point lastTransmission,Point landing,double error,boolean budgetExhausted,String reason){
 public enum Status{REACHED,GEOMETRIC_UNKNOWN,NO_SOLUTION,BLOCKED,LIMIT,NO_DISPLACEMENT,REPEATED_POINT}
 public RouteResult{segments=List.copyOf(segments);points=List.copyOf(points);}
}
