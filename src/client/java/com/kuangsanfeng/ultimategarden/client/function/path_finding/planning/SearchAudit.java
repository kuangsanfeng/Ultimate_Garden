package com.kuangsanfeng.ultimategarden.client.function.path_finding.planning;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.*;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import java.util.*;import java.util.function.BiConsumer;
/** Optional bounded diagnostic sink. Rejections count failed checks, not mutually exclusive candidates. */
public final class SearchAudit {
 private final BiConsumer<String,Map<String,Object>> sink;
 private final Map<String,Long> rejections=new TreeMap<>(),candidates=new TreeMap<>();
 private final Map<String,List<Map<String,Object>>> samples=new TreeMap<>();
 private Point goal;public void goal(Point p){goal=p;}
 public void context(String action,Point from,Point to,double yaw,double pitch){this.action=action;this.from=from;this.to=to;this.yaw=yaw;this.pitch=pitch;}
 private String action="ENDPOINT";private Point from,to;private double yaw,pitch;private long generated;
 private Map<String,Object> last=Map.of();
 public SearchAudit(BiConsumer<String,Map<String,Object>> sink){this.sink=Objects.requireNonNull(sink);}
 public void candidate(String action,Point from,Point to,double yaw,double pitch){context(action,from,to,yaw,pitch);generated++;candidates.merge(action,1L,Long::sum);}
 public long generated(){return generated;}
 public void reject(String reason,Map<String,Object> detail){
  String key=action+":"+reason+":"+detail.getOrDefault("reason","CHECK");rejections.merge(key,1L,Long::sum);
  var list=samples.computeIfAbsent(key,k->new ArrayList<>());double distance=goal==null||from==null?-1:from.distance(goal);long count=rejections.get(key);if(list.size()>=2&&distance>=((Number)list.get(1).get("fromDistanceToGoal")).doubleValue()&&count%1024!=0)return;var e=new LinkedHashMap<String,Object>();e.put("from",from);e.put("to",to);e.put("yaw",yaw);e.put("pitch",pitch);e.put("detail",detail);e.put("fromDistanceToGoal",distance);e.put("occurrence",count);
  if(list.isEmpty()){e.put("sampleKind","FIRST");list.add(e);}
  else if(list.size()==1){e.put("sampleKind","NEAREST");list.add(e);}
  else {double best=((Number)list.get(1).get("fromDistanceToGoal")).doubleValue();if(distance<best){e.put("sampleKind","NEAREST");list.set(1,e);}var last=new LinkedHashMap<>(e);last.put("sampleKind","LATEST_SAMPLED");if(list.size()==2)list.add(last);else list.set(2,last);}
 }
 public void dominated(Segment e){String old=action;action=e.movement().name();from=e.from();to=e.to();yaw=e.yaw();pitch=e.pitch();reject("DOMINATED_STATE",Map.of());action=old;}
 public Terrain wrap(Terrain t){return new Terrain(){
  public boolean clear(Point p){boolean yes=t.clear(p);if(!yes)reject("CLEARANCE_REJECTED",t.diagnosticFailure());return yes;}
  public boolean supported(Point p){boolean yes=t.supported(p);if(!yes)reject("SUPPORT_REJECTED",t.diagnosticFailure());return yes;}
  public boolean sweep(Point a,Point b){boolean yes=t.sweep(a,b);if(!yes)reject("SEGMENT_REJECTED",t.diagnosticFailure());return yes;}
  public Optional<Point> ground(Point p){var result=t.ground(p);if(result.isEmpty())reject("NO_LANDING",t.diagnosticFailure());return result;}
  public Set<String> missing(){return t.missing();}public Map<String,Object> diagnosticFailure(){return t.diagnosticFailure();}
 };}
 private long segment,baseExpanded,originExpanded,lastSample;
 void beginSegment(long number,long expanded,long origin){
  segment=number;baseExpanded=expanded;originExpanded=origin;lastSample=0;
 }
 void sample(String phase,long originTotal,long candidates,int open,Point at){
  long now=System.nanoTime();
  if(lastSample!=0&&now-lastSample<1_000_000_000L)return;
  lastSample=now;
  emit("WORK_PROGRESS",Map.of("segment",segment,"stage",phase,"totalExpanded",baseExpanded+originTotal-originExpanded,
      "open",open,"candidateChecksAtOrigin",candidates,"node",at,"progressNanos",now));
 }
 public void emit(String event,Map<String,Object> data){sink.accept(event,data);}
 public void progress(Map<String,Object> data,boolean emit){last=new LinkedHashMap<>(data);last.put("generatedCandidates",generated);if(emit)this.emit("SEARCH_PROGRESS",last);}
 public Map<String,Object> latest(){return last;}
 public void summary(){emit("REJECTION_SUMMARY",Map.of("counts",rejections,"samples",samples,"candidateCounts",candidates,"countMeaning","failed predicate checks, not mutually exclusive rejected candidates","unimplemented",List.of("coarse_planning","teleport_collision_truncation","six_block_fall_reset","confirmed_distance_landing_controller")));}
}
