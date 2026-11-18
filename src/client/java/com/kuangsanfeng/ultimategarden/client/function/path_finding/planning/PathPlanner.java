package com.kuangsanfeng.ultimategarden.client.function.path_finding.planning;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.*;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.TransmissionModel;
import static com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.*;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import java.util.*;import java.util.function.BooleanSupplier;
/** Bounded heading-aware Dijkstra. No walking-distance heuristic is used with cheap teleport edges. */
public final class PathPlanner {
 private final SearchAudit audit;private Node closest;private long stale;private int peak;private final Set<Key> expandedStates=new HashSet<>();private final Set<Point> positions=new HashSet<>(),expandedPositions=new HashSet<>();
 public PathPlanner(){this(null);}private PathPlanner(SearchAudit audit){this.audit=audit;}
 public Result plan(Point start,Point requested,double yaw,double pitch,Options options,Terrain terrain,BooleanSupplier cancelled,SearchAudit audit){return new PathPlanner(audit).plan(start,requested,yaw,pitch,options,terrain,cancelled);}

 private record Key(Point p,double yaw,double pitch,int travel){}
 private record Node(Key key,double cost,Node previous,Segment edge,long order){}
 private record PendingEdge(Node parent,Segment edge){}
 public Result plan(Point start,Point requested,double yaw,double pitch,Options options,Terrain terrain,BooleanSupplier cancelled){
  return new GraphSearch(start,requested,yaw,pitch,options,terrain,cancelled,false,null).advance(options.budget());
 }

 GraphSearch groundSearch(Point start,Point goal,double yaw,double pitch,Options options,Terrain terrain,BooleanSupplier cancelled,SearchRegion region){
  return new GraphSearch(start,goal,yaw,pitch,options,terrain,cancelled,true,region);
 }

 final class GraphSearch {
  private final Point start,requested,goal;
  private final Options options;
  private final Terrain terrain;
  private final BooleanSupplier cancelled;
  private final boolean air;
  private final SearchRegion region;
  private final ArrayList<PendingEdge> masked=new ArrayList<>();
  private final PriorityQueue<Node> queue;
  private final Map<Key,Double> best=new HashMap<>();
  private long explored,order,checks;
  private java.util.function.Consumer<GraphProgress> observe;
  private Result terminal;

  GraphSearch(Point start,Point requested,double yaw,double pitch,Options options,Terrain terrain,BooleanSupplier cancelled,boolean groundAStar,SearchRegion region){
   this.region=region;
   this.start=start;
   this.requested=requested;
   this.options=options;
   this.cancelled=cancelled;
   this.terrain=audit==null?terrain:audit.wrap(terrain);
   terrain=this.terrain;
   var previousMissing=terrain.missing();
   auditMode=options.mode();
   Point destination=new Point(Math.floor(requested.x())+.5,requested.y(),Math.floor(requested.z())+.5);
   boolean airTarget=!terrain.supported(destination);
   String rejected=null;
   if(!terrain.clear(start)||!terrain.clear(destination)) rejected="OCCUPIED_ENDPOINT_OR_UNKNOWN";
   else if(airTarget&&options.projectAir()){
    var ground=terrain.ground(destination);
    if(ground.isEmpty()) rejected="NO_SAFE_PROJECTION";
    else {destination=ground.get();airTarget=false;}
   }
   goal=destination;
   air=airTarget;
   queue=new PriorityQueue<>(Comparator.comparingDouble(
     (Node n)->groundAStar?n.cost+(Math.abs(n.key.p.x()-goal.x())+Math.abs(n.key.p.z()-goal.z()))*options.weights().walk():
       options.mode()==Mode.WEIGHTED?n.cost:n.key.p.distance(goal)+n.cost*.01).thenComparingLong(Node::order));
   if(rejected!=null) terminal=terminalResult(terrain.missing().isEmpty()?Status.UNREACHABLE:Status.MAP_MISSING,null,start,rejected);
   else if(terrain.missing().stream().anyMatch(m->!previousMissing.contains(m)))
    terminal=terminalResult(Status.MAP_MISSING,null,start,"ENDPOINT_MAP_MISSING");
   else if(air&&!options.transmission()) terminal=terminalResult(Status.UNREACHABLE,null,start,"AIR_TARGET_REQUIRES_TRANSMISSION");
   if(terminal!=null)return;
   if(audit!=null)audit.goal(goal);
   if(options.mode()==Mode.NORMAL&&options.transmission()){
    var direct=direct(start,goal,yaw,pitch,options,terrain,cancelled);
    if(direct!=null){terminal=terminalResult(Status.ARRIVED,direct,direct.key.p,"NORMAL_SAME_SIGHT_DIRECT");return;}
   }
   var first=new Node(new Key(start,yaw,pitch,0),0,null,null,order++);
   queue.add(first);
   best.put(first.key,0.);
   closest=first;
   if(audit!=null)positions.add(start);
   peak=1;
  }

  boolean hasFrontier(){
   while(!queue.isEmpty()&&queue.peek().cost>best.getOrDefault(queue.peek().key,Double.POSITIVE_INFINITY)+1e-10){
    queue.remove();stale++;
   }
   return terminal==null&&!queue.isEmpty();
  }
  long expanded(){return explored;}
  int openSize(){return queue.size();}
  List<Segment> partialPath(){return path(closest);}

  void observe(java.util.function.Consumer<GraphProgress> observer){observe=observer;}
 long checks(){return checks;}
 Result advance(long allowance){
   if(terminal!=null)return terminal;
   long initial=explored;
   while(hasFrontier()){
    if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())
     return terminal=terminalResult(Status.CANCELLED,null,start,"CANCELLED");
    if(explored-initial>=allowance){
     var node=queue.peek();
     progress(explored,best.size(),queue,node,goal,false);
     if(audit!=null)audit.emit("SEARCH_LIMIT",Map.of("limit","HEADING_GRAPH_BUDGET","budget",allowance,"triggerPosition",node.key.p));
     return terminalResult(Status.BUDGET_EXHAUSTED,null,start,"HEADING_GRAPH_BUDGET");
    }
    var node=queue.remove();
   explored++;if(audit!=null){expandedPositions.add(node.key.p);expandedStates.add(node.key);}if(node.key.p.distance(goal)<closest.key.p.distance(goal))closest=node;
   if(explored%1000==0)progress(explored,best.size(),queue,node,goal,true);
   if(observe!=null&&(explored&63)==1)observe.accept(new GraphProgress(explored,checks,queue.size(),node.key.p));
   var p=node.key.p;
   if(region!=null)region.visit(p,3);
   if(p.distance(goal)<.1||air&&p.distance(goal)<=options.distance()/2){progress(explored,best.size(),queue,node,goal,false);return terminal=terminalResult(Status.ARRIVED,node,p,"BEST_CHECKED_HEADING_GRAPH");}
   var edges=new ArrayList<Segment>();if(audit!=null)audit.context("NODE_CLASSIFICATION",p,p,node.key.yaw,node.key.pitch);boolean grounded=terrain.supported(p);
   if(grounded){for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){if(dx==0&&dz==0)continue;Point q=new Point(Math.floor(p.x())+.5+dx,p.y(),Math.floor(p.z())+.5+dz);countCandidate("WALK",p,q);
    if(terrain.clear(q)&&terrain.supported(q)&&terrain.sweep(p,q)&&terrain.supported(new Point((p.x()+q.x())/2,p.y(),(p.z()+q.z())/2)))add(edges,Movement.WALK,p,q,0,options,node);
    else for(int rise=1;rise<=Math.floor(options.jumpHeight());rise++){q=new Point(Math.floor(p.x())+.5+dx,p.y()+rise,Math.floor(p.z())+.5+dz);Point apex=new Point(p.x(),q.y()+.1,p.z());countCandidate("JUMP",p,q);if(terrain.clear(q)&&terrain.supported(q)&&terrain.sweep(p,apex)&&terrain.sweep(apex,q)){add(edges,Movement.JUMP,p,q,rise,options,node);break;}}
    countCandidate("FALL",p,new Point(Math.floor(p.x())+.5+dx,p.y(),Math.floor(p.z())+.5+dz));var drop=terrain.ground(new Point(Math.floor(p.x())+.5+dx,p.y(),Math.floor(p.z())+.5+dz));if(drop.isPresent()&&drop.get().y()<p.y()&&terrain.sweep(p,new Point(drop.get().x(),p.y(),drop.get().z()))&&terrain.sweep(new Point(drop.get().x(),p.y(),drop.get().z()),drop.get()))add(edges,Movement.FALL,p,drop.get(),0,options,node);
   }}else{countCandidate("FALL",p,p);var drop=terrain.ground(p);if(drop.isPresent()&&drop.get().y()<p.y()&&terrain.sweep(p,drop.get()))add(edges,Movement.FALL,p,drop.get(),0,options,node);}
   if(options.transmission()&&node.key.travel<180){var target=TransmissionModel.toward(p,goal);var angles=new ArrayList<double[]>();angles.add(target);
    for(int y=0;y<360;y+=45)for(int a:new int[]{-90,-60,-30,0,30,60,90})angles.add(new double[]{y,a});
    var ends=new HashSet<Point>();for(var a:angles){checks++;Point q=TransmissionModel.landing(p,a[0],a[1],options.distance());if(audit!=null)audit.candidate("TRANSMISSION",p,q,a[0],a[1]);if(q.equals(p)||!ends.add(q)){if(audit!=null)audit.reject("DUPLICATE_QUANTIZED_ENDPOINT",Map.of());}else if(terrain.clear(q)&&terrain.sweep(p,q))add(edges,Movement.TRANSMISSION,p,q,0,options,node,a);}
    // Terminal guidance may use the remaining continuous distance, but only if unobstructed.
    if(p.distance(goal)<=options.distance()){countCandidate("TERMINAL_TRANSMISSION",p,goal);if(terrain.clear(goal)&&terrain.sweep(p,goal))add(edges,Movement.TRANSMISSION,p,goal,0,options,node);}
   }else if(options.transmission()&&audit!=null){candidate("TRANSMISSION",p,p);audit.reject("TRAVEL_COUNTER_180_GUARD",Map.of("travelCounter",node.key.travel,"check","counter-only; landing refresh controller unimplemented"));}
   for(var e:edges) {
    if(region==null||region.contains(e.to()))offer(node,e);
    else if(region.deferred(e.to()))masked.add(new PendingEdge(node,e));
   }
  }
  progress(explored,best.size(),queue,closest,goal,false);if(audit!=null)audit.emit("OPEN_LIST_EXHAUSTED",Map.of("expanded",explored));
   return terminal=terminalResult(terrain.missing().isEmpty()?Status.UNREACHABLE:Status.MAP_MISSING,null,start,"NO_PATH_IN_CHECKED_MOVEMENT_GRAPH");
  }
  int restoreRegion(){
   int before=queue.size();
   for(var item:masked)if(region.contains(item.edge.to()))offer(item.parent,item.edge);
   masked.clear();
   if(queue.size()>before)terminal=null;
   return queue.size()-before;
  }
  private void offer(Node node,Segment edge){
   if(audit!=null)audit.context("TRAVEL_STATE_CLASSIFICATION",edge.from(),edge.to(),edge.yaw(),edge.pitch());
   int travel=edge.movement()==Movement.TRANSMISSION?node.key.travel+(int)Math.ceil(options.distance()):terrain.supported(edge.to())?0:node.key.travel;
   var key=new Key(edge.to(),edge.yaw(),edge.pitch(),travel);
   double cost=node.cost+edge.cost();
   if(cost+1e-10<best.getOrDefault(key,Double.POSITIVE_INFINITY)){
    best.put(key,cost);
    if(audit!=null)positions.add(edge.to());
    queue.add(new Node(key,cost,node,edge,order++));
    peak=Math.max(peak,queue.size());
   }else if(audit!=null)audit.dominated(edge);
  }
  private void countCandidate(String action,Point from,Point to){checks++;candidate(action,from,to);}
  private Result terminalResult(Status status,Node node,Point end,String reason){
   return result(status,node,requested,goal,end,explored,terrain,reason);
  }
 }
 record GraphProgress(long expanded,long checks,int open,Point point){}
 private void candidate(String action,Point from,Point to){if(audit!=null){var a=TransmissionModel.toward(from,to);audit.candidate(action,from,to,a[0],a[1]);}}
 private void progress(long expanded,int states,PriorityQueue<Node> queue,Node current,Point goal,boolean emit){
  if(audit==null)return;var data=new LinkedHashMap<String,Object>();data.put("expanded",expanded);data.put("completeStates",states);data.put("uniquePositions",positions.size());data.put("expandedUniquePositions",expandedPositions.size());data.put("expandedFullStates",expandedStates.size());data.put("reexpandedStateVisits",expanded-expandedStates.size());data.put("openSize",queue.size());data.put("openPeak",peak);data.put("staleQueueEntries",stale);data.put("currentPosition",current.key.p);data.put("currentCost",current.cost);data.put("queueHeadCost",queue.isEmpty()?null:queue.peek().cost);data.put("queueHeadPriority",queue.isEmpty()?null:priority(queue.peek(),goal));data.put("closestPosition",closest.key.p);data.put("closestDistance",closest.key.p.distance(goal));data.put("closestCost",closest.cost);data.put("closestState",closest.key);data.put("currentState",current.key);audit.progress(data,emit);
 }
 private double priority(Node n,Point goal){return auditMode==Mode.WEIGHTED?n.cost:n.key.p.distance(goal)+n.cost*.01;}
 private Mode auditMode;
 public Result planGround(Point start,Point requested,double yaw,double pitch,Options options,Terrain terrain,BooleanSupplier cancelled){
  return groundSearch(start,requested,yaw,pitch,options,terrain,cancelled,null).advance(options.budget());
 }
 private List<Segment> path(Node n){var result=new ArrayList<Segment>();while(n!=null&&n.edge!=null){result.add(n.edge);n=n.previous;}Collections.reverse(result);return result;}
 private Node direct(Point start,Point goal,double yaw,double pitch,Options o,Terrain t,BooleanSupplier cancelled) {
  var n=new Node(new Key(start,yaw,pitch,0),0,null,null,0);var a=TransmissionModel.toward(start,goal);var seen=new HashSet<Point>();
  for(int k=0;k<Math.min(o.budget(),1000);k++) {
   if(cancelled.getAsBoolean())return null;var p=n.key.p;if(p.distance(goal)<.1)return n;
   Point q=p.distance(goal)<=o.distance()?goal:TransmissionModel.landing(p,a[0],a[1],o.distance());
   candidate("DIRECT_TRANSMISSION",p,q);if(!seen.add(q)){if(audit!=null)audit.reject("DUPLICATE_QUANTIZED_ENDPOINT",Map.of());return null;}if(!t.clear(q)||!t.sweep(p,q))return null;if(n.key.travel>=180){if(audit!=null)audit.reject("TRAVEL_COUNTER_180_GUARD",Map.of("travelCounter",n.key.travel));return null;}
   var edges=new ArrayList<Segment>();add(edges,Movement.TRANSMISSION,p,q,0,o,n);var e=edges.getFirst();
   e=new Segment(e.movement(),p,q,a[0],a[1],0,o.weights().transmission()+o.weights().turn()*Math.pow(TransmissionModel.angle(n.key.yaw,n.key.pitch,a[0],a[1])/30,o.weights().exponent()));
   n=new Node(new Key(q,a[0],a[1],n.key.travel+(int)Math.ceil(o.distance())),n.cost+e.cost(),n,e,k+1);
  }return null;
 }
 private void add(List<Segment> out,Movement m,Point p,Point q,double ascent,Options o,Node n){add(out,m,p,q,ascent,o,n,null);}
 private void add(List<Segment> out,Movement m,Point p,Point q,double ascent,Options o,Node n,double[] actualAngles){var a=actualAngles==null?TransmissionModel.toward(p,q):actualAngles.clone();if(m==Movement.WALK||m==Movement.JUMP||m==Movement.FALL)a[1]=0;
  var w=o.weights();double cost=m==Movement.TRANSMISSION?w.transmission():w.walk()*(Math.abs(q.x()-p.x())+Math.abs(q.z()-p.z()))+w.jump()*ascent+w.fall()*Math.max(0,p.y()-q.y());
  cost+=w.turn()*Math.pow(TransmissionModel.angle(n.key.yaw,n.key.pitch,a[0],a[1])/30,w.exponent());out.add(new Segment(m,p,q,a[0],a[1],ascent,cost));
 }
 List<Segment> partialPath(){return path(closest);}
 private Result result(Status s,Node n,Point wanted,Point resolved,Point end,long explored,Terrain t,String why){if(audit!=null){audit.summary();audit.emit("SEARCH_TERMINATION",Map.of("status",s,"reason",why,"expanded",explored,"closestProgressPath",path(closest),"latest",audit.latest()));}var path=new ArrayList<Segment>();double cost=n==null?0:n.cost;while(n!=null&&n.edge!=null){path.add(n.edge);n=n.previous;}Collections.reverse(path);return new Result(s,path,wanted,resolved,end,cost,explored,t.missing(),why);}
}
