package com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight;
import java.util.*;
import static com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.FlightResult.*;
/** Pure fixed-angle packet displacement. Falling is deliberately excluded. */
public final class FlightModel {
 private static final int[] UP={0,1,2,3,4,5,6,7,8,9,9,10,11,11,11,12,12,12,12};
 public record Vector(double yaw,double pitch,Point delta){}
 public static double wrap(double d){double a=d%360;if(a>180)a-=360;else if(a<=-180)a+=360;return a;}
 public Point displacement(double yaw,double pitch,double distance){
  if(!Double.isFinite(yaw)||!Double.isFinite(pitch)||Math.abs(pitch)>90||!Double.isFinite(distance)||distance<=0)throw new IllegalArgumentException("Unsupported angles / distance");
  double y=Math.toRadians(wrap(yaw)),p=Math.toRadians(pitch),h=distance*Math.cos(p);double dy;
  if(distance==12&&pitch<=0){double index=-pitch/5;dy=Math.abs(index-Math.rint(index))<1e-10?UP[(int)Math.rint(index)]:Math.floor(-12*Math.sin(p)+.62);}
  else dy=Math.floor(-distance*Math.sin(p)+.5);
  return new Point(Math.floor(-h*Math.sin(y)+.5),dy,Math.floor(h*Math.cos(y)+.5));
 }
 public FlightResult predict(Point start,Point target,double yaw,double pitch,int clicks,Capability cap){
  if(clicks<0||clicks>cap.maxClicks())return new FlightResult(Status.OUT_OF_MODEL,yaw,pitch,0,List.of(),start,start.distance(target),Set.of(),false,"CLICK_LIMIT");
  if(!Double.isFinite(yaw)||!Double.isFinite(pitch)||Math.abs(pitch)>90)return new FlightResult(Status.UNSUPPORTED,0,0,0,List.of(),start,start.distance(target),Set.of(),false,"UNSUPPORTED_ANGLE");
  Point d=displacement(yaw,pitch,cap.distance()),end=start;var points=new ArrayList<Point>();for(int k=0;k<clicks;k++){end=end.add(d);points.add(end);}
  var confidence=EnumSet.noneOf(Confidence.class);double w=wrap(yaw);
  boolean measured=cap.distance()==12&&start.centred()&&w>=0&&w<=45&&pitch<=0&&Math.abs(pitch/5-Math.rint(pitch/5))<1e-10;
  confidence.add(measured?Confidence.MEASURED:Confidence.ESTIMATED);if(clicks>3)confidence.add(Confidence.EXTRAPOLATED);
  double error=end.distance(target);return new FlightResult(clicks==0&&error<=cap.tolerance()?Status.ALREADY_REACHED:error<=cap.tolerance()?Status.HIT_TARGET:Status.APPROXIMATE,wrap(yaw),pitch,clicks,points,end,error,confidence,false,"FIXED_ANGLE_PACKET_MODEL");
 }
 public static double[] toward(Point start,Point target){double dx=target.x()-start.x(),dy=target.y()-start.y(),dz=target.z()-start.z();return new double[]{wrap(Math.toDegrees(Math.atan2(-dx,dz))),-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)))};}
 /** Finite checked candidates, deduplicated by integer displacement; yaw cell boundaries and midpoints are included. */
 public record Candidates(List<Vector> vectors,boolean truncated){public Candidates{vectors=List.copyOf(vectors);}}
 public List<Vector> vectors(Capability cap,Point start,Point target){return candidates(cap,start,target).vectors();}
 public Candidates candidates(Capability cap,Point start,Point target){
  var out=new LinkedHashMap<Point,Vector>();var direct=toward(start,target);add(out,direct[0],direct[1],cap.distance());
  var pitches=new TreeSet<Double>();for(int p=-90;p<=90;p++)pitches.add((double)p);pitches.add(direct[1]);
  for(int n=0;n<=(int)Math.ceil(cap.distance());n++){double v=(n-.62)/cap.distance();if(v>=0&&v<=1)pitches.add(-Math.toDegrees(Math.asin(v)));}
  int work=0;for(double p:pitches){double h=cap.distance()*Math.cos(Math.toRadians(p));var angles=new TreeSet<Double>();angles.add(-180.);angles.add(180.);
   if(h>1e-10)for(int n=-(int)Math.ceil(h);n<=(int)Math.ceil(h);n++){double v=(n+.5)/h;if(v>=-1&&v<=1){double a=Math.toDegrees(Math.asin(v));for(double q:new double[]{a,180-a,90-a,90+a})angles.add(wrap(q));}}
   var list=new ArrayList<>(angles);for(int i=0;i<list.size();i++){if(++work>cap.budget())return new Candidates(List.copyOf(out.values()),true);double a=list.get(i);add(out,a,p,cap.distance());if(i+1<list.size())add(out,(a+list.get(i+1))/2,p,cap.distance());}
  }return new Candidates(List.copyOf(out.values()),false);
 }
 private void add(Map<Point,Vector> out,double yaw,double pitch,double distance){Point d=displacement(yaw,pitch,distance);out.putIfAbsent(d,new Vector(wrap(yaw),pitch,d));}
 private static int clickLimit(Point start,Point target,Point delta,Capability cap){double length=delta.distance(new Point(0,0,0));return length==0?0:(int)Math.min(cap.maxClicks(),Math.ceil((start.distance(target)+cap.tolerance())/length)+1);}
 public FlightResult solve(Point start,Point target,Capability cap){
  if(start.distance(target)<=cap.tolerance())return predict(start,target,0,0,0,cap);
  FlightResult best=null;int checked=0;var candidates=candidates(cap,start,target);boolean exhausted=candidates.truncated();
  outer:for(var v:candidates.vectors())for(int k=1,limit=clickLimit(start,target,v.delta(),cap);k<=limit;k++){
   if(++checked>cap.budget()){exhausted=true;break outer;}var r=predict(start,target,v.yaw(),v.pitch(),k,cap);
   if(best==null||r.error()<best.error()-1e-10||Math.abs(r.error()-best.error())<=1e-10&&r.clicks()<best.clicks())best=r;
  }
  if(best==null)return new FlightResult(Status.NO_SOLUTION,0,0,0,List.of(),start,start.distance(target),Set.of(),exhausted,"NO_CHECKED_CANDIDATE");
  return new FlightResult(start.distance(target)>(cap.distance()+Math.sqrt(3))*cap.maxClicks()?Status.OUT_OF_MODEL:best.status(),best.yaw(),best.pitch(),best.clicks(),best.points(),best.end(),best.error(),best.confidence(),exhausted,exhausted?"SEARCH_BUDGET_EXHAUSTED":"BEST_CHECKED_CANDIDATE_NOT_PROOF_OF_GLOBAL_OPTIMUM");
 }
}
