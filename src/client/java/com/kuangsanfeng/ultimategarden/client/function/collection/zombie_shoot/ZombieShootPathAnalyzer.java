package com.kuangsanfeng.ultimategarden.client.function.collection.zombie_shoot;
import com.kuangsanfeng.ultimategarden.client.function.collection.zombie_shoot.ZombieShootCoordinateSystem;
import com.kuangsanfeng.ultimategarden.client.function.collection.zombie_shoot.ZombieShootPathCollector;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat.ZombieShootSnapshot;

import java.util.*;
import static com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat.ZombieShootSnapshot.map;
/** Conservative session-local observed polyline model; no extrapolation or control output. */
public final class ZombieShootPathAnalyzer {
 private static final double TURN_BOUNDARY_MARGIN=1;
 private List<ZombieShootPathCollector.Point> anchorA,anchorB;
 private double match(List<ZombieShootPathCollector.Point> a,List<ZombieShootPathCollector.Point> b){double d=0;for(var p:a)d+=distance(p,b.get(nearest(p,b)));return d/a.size();}
 public record Result(String status,List<Map<String,Object>> models,List<Map<String,Object>> turns){}
 private double distance(ZombieShootPathCollector.Point a,ZombieShootPathCollector.Point b){return Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2));}
 private int nearest(ZombieShootPathCollector.Point p,List<ZombieShootPathCollector.Point> line){int best=0;double d=Double.MAX_VALUE;for(int i=0;i<line.size();i++){double n=distance(p,line.get(i));if(n<d){d=n;best=i;}}return best;}
 // A sampling cutoff is not evidence of a physical endpoint.
 public static boolean turnInterior(ZombieShootPathCollector.Point point){double radius=ZombieShootCoordinateSystem.SCAN_RADIUS-TURN_BOUNDARY_MARGIN;return point.x()*point.x()+point.y()*point.y()+point.z()*point.z()<radius*radius;}
 public Result analyze(List<List<ZombieShootPathCollector.Point>> source){
  var groups=new ArrayList<List<List<ZombieShootPathCollector.Point>>>();
  for(var raw:source){if(raw.stream().anyMatch(p->!ZombieShootCoordinateSystem.insideRelative(p.x(),p.y(),p.z())))continue;if(raw.size()<12)continue;var line=raw.stream().filter(p->p.heading()!=null).toList();if(line.size()<12)continue;
   List<List<ZombieShootPathCollector.Point>> match=null;
   for(var g:groups){var reference=g.getFirst();long near=line.stream().filter(p->distance(p,reference.get(nearest(p,reference)))<=.75).count();if(near/(double)line.size()>=.8){match=g;break;}}
   if(match==null){match=new ArrayList<>();groups.add(match);}match.add(line);
  }
  var reliable=groups.stream().filter(g->g.stream().map(l->l.getFirst().track()).distinct().count()>=2).sorted(Comparator.comparingDouble(g->g.getFirst().stream().mapToDouble(ZombieShootPathCollector.Point::z).average().orElse(0))).toList();
  if(reliable.size()!=2)return new Result("Insufficient Data",List.of(),List.of());
  if(anchorA==null){anchorA=List.copyOf(reliable.get(0).getFirst());anchorB=List.copyOf(reliable.get(1).getFirst());}
  else{
   double forward=match(reliable.get(0).getFirst(),anchorA)+match(reliable.get(1).getFirst(),anchorB);
   double reverse=match(reliable.get(1).getFirst(),anchorA)+match(reliable.get(0).getFirst(),anchorB);
   if(Math.abs(forward-reverse)<.5||Math.min(forward,reverse)>3)return new Result("Insufficient Data",List.of(),List.of());
   if(reverse<forward)reliable=List.of(reliable.get(1),reliable.get(0));
  }
  var models=new ArrayList<Map<String,Object>>();var turns=new ArrayList<Map<String,Object>>();
  for(int n=0;n<2;n++){
   var group=reliable.get(n);var reference=new ArrayList<>(group.stream().max(Comparator.comparingInt(List::size)).orElseThrow());
   // Canonical orientation compares observed endpoints, never the cluster discovery order.
   var first=reference.getFirst();var last=reference.getLast();if(first.x()>last.x()||first.x()==last.x()&&first.z()>last.z())Collections.reverse(reference);
   // Stop the reference at its first sustained local reversal, retaining raw input separately.
   for(int i=3;i+3<reference.size();i++){
    var a=reference.get(i-3);var b=reference.get(i);var c=reference.get(i+3);
    double ux=b.x()-a.x(),uz=b.z()-a.z(),vx=c.x()-b.x(),vz=c.z()-b.z();
    double norm=Math.hypot(ux,uz)*Math.hypot(vx,vz);
    if(norm>.001&&(ux*vx+uz*vz)/norm<-.8){reference=new ArrayList<>(reference.subList(0,i+1));break;}
   }
   var rawReference=List.copyOf(reference);
   for(int i=1;i+1<reference.size();i++){
    var a=rawReference.get(i-1);var b=rawReference.get(i);var c=rawReference.get(i+1);
    reference.set(i,new ZombieShootPathCollector.Point(b.track(),b.segment(),b.tick(),b.classification(),(a.x()+b.x()+c.x())/3,(a.y()+b.y()+c.y())/3,(a.z()+b.z()+c.z())/3,b.speed(),b.heading()));
   }
   double[] arc=new double[reference.size()];for(int i=1;i<arc.length;i++)arc[i]=arc[i-1]+distance(reference.get(i-1),reference.get(i));
   if(arc[arc.length-1]<2)return new Result("Insufficient Data",List.of(),List.of());
   String id=n==0?"PATH_A":"PATH_B";
   var curve=new ArrayList<Object>();for(int i=0;i<arc.length;i++){var p=reference.get(i);curve.add(map("s",arc[i],"relativePosition",List.of(p.x(),p.y(),p.z())));}
   long supported=0;for(var vertex:reference){if(group.stream().filter(line->line.stream().anyMatch(p->distance(p,vertex)<=.75)).map(line->line.getFirst().track()).distinct().count()>=2)supported++;}
   long samples=0,inliers=0;double squared=0;var index=new ArrayList<Object>();var assignments=new ArrayList<Object>();
   for(var trajectory:group){
    long trajectoryInliers=0;
    double[] projected=new double[trajectory.size()];
    for(int i=0;i<trajectory.size();i++){var p=trajectory.get(i);int j=nearest(p,reference);double residual=distance(p,reference.get(j));samples++;squared+=residual*residual;if(residual<=.75){inliers++;trajectoryInliers++;}projected[i]=arc[j];
     index.add(map("entityTrackId",p.track(),"segment",p.segment(),"clientTick",p.tick(),"pathId",id,"arcLength",arc[j],"classification",p.classification(),"observedSpeed",p.speed(),"observedDirection",p.heading(),"relativePosition",List.of(p.x(),p.y(),p.z()),"projectionResidual",residual,"coverageStatus","OBSERVED"));
    }
    assignments.add(map("entityTrackId",trajectory.getFirst().track(),"segment",trajectory.getFirst().segment(),"pathId",id,"availableDataCount",trajectory.size(),"classificationConfidence",trajectoryInliers/(double)trajectory.size(),"criterion","fraction within 0.75 block of assigned reference"));
    for(int i=3;i+3<trajectory.size();i++){
     double before=projected[i]-projected[i-3],after=projected[i+3]-projected[i];
     boolean continuous=true;for(int k=i-3;k<i+3;k++)continuous&=trajectory.get(k+1).tick()-trajectory.get(k).tick()==1;
     for(int k=i-3;k<=i+3;k++)continuous&=turnInterior(trajectory.get(k));
     if(continuous&&Math.abs(before)>.08&&Math.abs(after)>.08&&before*after<0&&Math.min(projected[i],arc[arc.length-1]-projected[i])<Math.max(.5,arc[arc.length-1]*.05)){
      var p=trajectory.get(i);turns.add(map("event","TURN_CANDIDATE","entityTrackId",p.track(),"pathId",id,"turnEventTick",p.tick(),"turnRelativePosition",List.of(p.x(),p.y(),p.z()),"directionBefore",Math.signum(before),"directionAfter",Math.signum(after),"speedBefore",Math.abs(before)/3,"speedAfter",Math.abs(after)/3,"turnDetectionMethod","three-tick projected arc displacement reversal near observed endpoint","turnConfidence",null,"evidence","candidate only; nearest-point projection and incomplete endpoint observations cannot confirm a turnaround"));
     }
    }
   }
   models.add(map("pathId",id,"modelVersion",1,"sampleCount",samples,"trajectoryCount",group.stream().map(l->l.getFirst().track()).distinct().count(),"observedPoints",group,"fittedCurve",curve,"fitMethod","three-point moving-average observed polyline; endpoints preserved; no extrapolation","fitResidual",Math.sqrt(squared/samples),"observedStart",curve.getFirst(),"observedEnd",curve.getLast(),"coverage",supported/(double)reference.size(),"coverageDefinition","fraction of reference vertices supported within 0.75 block by at least two distinct entity tracks; unobserved continuation UNKNOWN","confidence",inliers/(double)samples,"confidenceDefinition","fraction of assigned points within 0.75 block of reference vertices; not a calibrated probability","pathNumbering","Initial PATH_A has lower representative mean relative Z; later models match fixed session anchors with >=0.5 cost margin and <=3 total mean-distance cost; ambiguous assignments withheld; no cross-session identity claim","observationIndex",index,"trajectoryAssignments",assignments,"collectionRadius",ZombieShootCoordinateSystem.SCAN_RADIUS,"rangeBoundaryIsPathEndpoint",false,"turnBoundaryExclusionMargin",TURN_BOUNDARY_MARGIN,"unknownRegions","all unobserved continuation; radius-30 truncation is not a physical endpoint"));
  }
  return new Result("Available",models,turns);
 }
}

