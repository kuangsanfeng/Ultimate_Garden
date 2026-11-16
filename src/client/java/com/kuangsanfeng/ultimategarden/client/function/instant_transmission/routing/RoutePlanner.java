package com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.*;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing.strategy.*;
public final class RoutePlanner {
 public RouteResult plan(Point start,Point target,Capability capability,RouteStrategy strategy,Terrain terrain){
  if(terrain==null)terrain=Terrain.UNKNOWN;
  PlanningStrategy impl=switch(strategy){case DROP_ALIGN->new DropAlignStrategy();case UPPER_HEMISPHERE_NEAREST->new UpperHemisphereStrategy();case DIRECT_AIM->new DirectAimStrategy();};
  return impl.plan(start,target,capability,terrain);
 }
}
