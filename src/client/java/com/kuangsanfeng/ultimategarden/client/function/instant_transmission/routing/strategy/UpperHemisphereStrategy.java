package com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing.strategy;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.*;import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing.*;
public final class UpperHemisphereStrategy implements PlanningStrategy {public RouteResult plan(Point a,Point b,Capability c,Terrain t){return FixedRouteSearch.search(a,b,c,t,false);}}
