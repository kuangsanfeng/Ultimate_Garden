package com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.*;
public interface PlanningStrategy {RouteResult plan(Point start,Point target,Capability capability,Terrain terrain);}
