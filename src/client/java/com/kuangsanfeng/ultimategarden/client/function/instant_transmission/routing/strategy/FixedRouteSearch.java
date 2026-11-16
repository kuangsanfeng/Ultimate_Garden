package com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing.strategy;
import java.util.*;import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.*;import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing.*;
final class FixedRouteSearch {
 private static int limit(Point a,Point b,Point delta,Capability c,boolean drop){double length=delta.distance(new Point(0,0,0));return length==0?0:(int)Math.min(c.maxClicks(),Math.floor((a.distance(b)+(drop?12+Math.sqrt(2):6))/length));}
 static RouteResult search(Point start,Point target,Capability cap,Terrain terrain,boolean drop){
  var model=new FlightModel();RouteStrategy strategy=drop?RouteStrategy.DROP_ALIGN:RouteStrategy.UPPER_HEMISPHERE_NEAREST;
  FlightResult best=null;Point landing=null;boolean bestUnknown=false,exhausted=false;int checked=0;String failure="NO_CHECKED_DIRECT_CANDIDATE";
  var candidates=model.candidates(cap,start,target);var vectors=candidates.vectors();exhausted=candidates.truncated();
  outer:for(int vi=-1;vi<vectors.size();vi++)for(int k=vi<0?0:1;k<=(vi<0?0:limit(start,target,vectors.get(vi).delta(),cap,drop));k++){
   if(++checked>cap.budget()){exhausted=true;break outer;}var vector=vi<0?null:vectors.get(vi);Point end=vi<0?start:new Point(start.x()+k*vector.delta().x(),start.y()+k*vector.delta().y(),start.z()+k*vector.delta().z());
   if(drop?(!end.sameColumn(target)||end.y()<target.y()||end.y()>target.y()+12):(end.y()<target.y()||end.distance(target)>6))continue;
   var candidate=model.predict(start,target,vi<0?0:vector.yaw(),vi<0?0:vector.pitch(),k,cap);boolean unknown=false,blocked=false;Point previous=start;
   if(k==0){var check=terrain.transfer(start,start);if(check.knowledge()==Terrain.Knowledge.BLOCKED){failure=check.reason();continue;}unknown=check.knowledge()==Terrain.Knowledge.UNKNOWN;}
   for(Point point:candidate.points()){var check=terrain.transfer(previous,point);if(check.knowledge()==Terrain.Knowledge.BLOCKED){failure=check.reason();blocked=true;break;}unknown|=check.knowledge()==Terrain.Knowledge.UNKNOWN;previous=point;}
   if(blocked)continue;
   Point fall=drop?new Point(end.x(),target.y(),end.z()):null;
   if(drop){var check=terrain.descent(end,fall);if(check.knowledge()==Terrain.Knowledge.BLOCKED){failure=check.reason();continue;}unknown|=check.knowledge()==Terrain.Knowledge.UNKNOWN;}
   double score=drop?end.y()-target.y():candidate.error(),bestScore=best==null?Double.POSITIVE_INFINITY:drop?best.end().y()-target.y():best.error();
   if(best==null||score<bestScore-1e-10||Math.abs(score-bestScore)<=1e-10&&k<best.clicks()){best=candidate;landing=fall;bestUnknown=unknown;}
  }
  if(best==null)return new RouteResult(strategy,exhausted?RouteResult.Status.LIMIT:RouteResult.Status.NO_SOLUTION,List.of(),List.of(),start,null,start.distance(target),exhausted,exhausted?"SEARCH_BUDGET_EXHAUSTED":failure);
  return new RouteResult(strategy,bestUnknown?RouteResult.Status.GEOMETRIC_UNKNOWN:RouteResult.Status.REACHED,List.of(best),best.points(),best.end(),landing,(landing==null?best.end():landing).distance(target),exhausted,exhausted?"BEST_CHECKED_BUDGET_EXHAUSTED":bestUnknown?"GEOMETRIC_CANDIDATE_TERRAIN_UNKNOWN":"BEST_CHECKED_DIRECT_CANDIDATE");
 }
}
