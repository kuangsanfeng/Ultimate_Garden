package com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing.strategy;
import java.util.*;import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.*;import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing.*;
public final class DirectAimStrategy implements PlanningStrategy {
 public RouteResult plan(Point start,Point target,Capability cap,Terrain terrain){
  var model=new FlightModel();var segments=new ArrayList<FlightResult>();var points=new ArrayList<Point>();var seen=new HashSet<Point>();seen.add(start);Point current=start;boolean unknown=false;String reason="WITHIN_SIX_BLOCKS";RouteResult.Status status=RouteResult.Status.REACHED;
  for(int i=0;current.distance(target)>6;i++){
   if(i>=Math.min(cap.maxClicks(),cap.budget())){status=RouteResult.Status.LIMIT;reason="DIRECT_STEP_LIMIT";break;}
   var angles=FlightModel.toward(current,target);var step=model.predict(current,target,angles[0],angles[1],1,cap);
   if(current.equals(step.end())){status=RouteResult.Status.NO_DISPLACEMENT;reason="ZERO_PACKET_DISPLACEMENT";break;}
   if(!seen.add(step.end())){status=RouteResult.Status.REPEATED_POINT;reason="REPEATED_PACKET_POSITION";break;}
   var check=terrain.transfer(current,step.end());if(check.knowledge()==Terrain.Knowledge.BLOCKED){status=RouteResult.Status.BLOCKED;reason=check.reason();break;}unknown|=check.knowledge()==Terrain.Knowledge.UNKNOWN;
   segments.add(step);points.add(step.end());current=step.end();
  }
  if(status==RouteResult.Status.REACHED&&unknown)status=RouteResult.Status.GEOMETRIC_UNKNOWN;
  return new RouteResult(RouteStrategy.DIRECT_AIM,status,segments,points,current,null,current.distance(target),status==RouteResult.Status.LIMIT,unknown&&status==RouteResult.Status.GEOMETRIC_UNKNOWN?"TERRAIN_UNKNOWN":reason);
 }
}
