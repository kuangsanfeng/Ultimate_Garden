package com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight;
import java.util.*;
public record FlightResult(Status status,double yaw,double pitch,int clicks,List<Point> points,Point end,double error,Set<Confidence> confidence,boolean budgetExhausted,String reason){
 public enum Status{ALREADY_REACHED,HIT_TARGET,APPROXIMATE,OUT_OF_MODEL,UNSUPPORTED,INVALID,NO_SOLUTION}
 public enum Confidence{MEASURED,ESTIMATED,EXTRAPOLATED}
 public FlightResult{points=List.copyOf(points);confidence=Set.copyOf(confidence);}
}
