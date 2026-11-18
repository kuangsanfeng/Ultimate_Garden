package com.kuangsanfeng.ultimategarden.client.function.path_finding.execution;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.*;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.CorridorRepair;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.SegmentedPlanner;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.SearchAudit;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.CorridorPlanner;
import java.util.concurrent.*;import java.util.concurrent.atomic.*;import java.util.function.*;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import static com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.*;
/** Independent owner-scoped planning and confirmed-action execution. Call tick on the client thread. */
public final class PathService implements AutoCloseable {
 public enum Stage {IDLE,PREPARING,PLANNING,PLAN_READY,EXECUTING}
 public synchronized Stage stage(){return current==null?Stage.IDLE:preparing?Stage.PREPARING:planning!=null?Stage.PLANNING:driver!=null?Stage.EXECUTING:Stage.PLAN_READY;}
 public interface Driver {
  Point position();boolean usable();void begin(Segment action);boolean completed();boolean failed();void release();
  default String failureReason(){return "INPUT_OR_ACTION_FAILED";}
  default boolean canReplan(){return false;}
  default double yaw(){return 0;}
  default double pitch(){return 0;}
  default void planReady(Result result){}
  default CorridorRepair.Start forecast(){return null;}
  default void rehearsalReady(CorridorRepair.Rehearsal rehearsal){}
  default void prepareRehearsalOverlay(CorridorRepair.Rehearsal rehearsal,BooleanSupplier cancelled){}
  default boolean followsCorridor(){return false;}
  default boolean advanceCorridor(){return false;}
  default void replanning(String reason,int attempt){}
  default CorridorContinuation takeCorridorContinuation(){return null;}
  default boolean accepts(Segment action){return position().distance(action.to())<=.75;}
  default boolean needsPreparation(){return false;}
  default boolean prepare(){return true;}
  default boolean readyForReplan(){return true;}
  default int maximumSearchSegments(){return DEFAULT_SEARCH_ROUNDS;}
  default SearchAudit planningAudit(int attempt){return null;}
  default int candidateWorkers(){return Runtime.getRuntime().availableProcessors();}
  default boolean gpuAcceleration(){return true;}
  default int rehearsalLimit(){return CorridorRepair.DEFAULT_SEARCH_LIMIT;}
  default void rehearsalStarting(CorridorRepair.Start initial,int limit){}
  default void terminalPosition(Point measured,Point planned){}
  default Options updateOptions(Options original){return original;}
  default CorridorPlanner.Timing updateTiming(CorridorPlanner.Timing original){return original;}
 }
 public record Ticket(Object owner,long sequence){}
 public record CorridorContinuation(CorridorRepair corridor,double progress){}
 private static final AtomicReference<PathService> INPUT_LEASE=new AtomicReference<>();
 private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"ug-path-planning");t.setDaemon(true);return t;});
 private final AtomicLong revision=new AtomicLong();private Ticket current;private Future<Planned> planning;private Result result;private Driver driver;private int index;private boolean active,closed;private Consumer<Result> callback;
 private Driver autoDriver;
 private Point lastMeasured;
 private record Request(Point goal,Options options,Supplier<Terrain> terrain,CorridorPlanner.Timing timing,int segments){}
 private record Planned(Result result,Terrain geometry,CorridorRepair.Rehearsal rehearsal){}
 private Request request;
 private int replans;private boolean preparing;
 private boolean waitingForReplan;
 public synchronized Ticket plan(Object owner,Point start,Point goal,double yaw,double pitch,Options options,Supplier<Terrain> terrain,Consumer<Result> completion){
  return plan(owner,start,goal,yaw,pitch,options,terrain,CorridorPlanner.Timing.defaults(),completion);
 }
 public synchronized Ticket plan(Object owner,Point start,Point goal,double yaw,double pitch,Options options,Supplier<Terrain> terrain,CorridorPlanner.Timing timing,Consumer<Result> completion){
  return plan(owner,start,goal,yaw,pitch,options,terrain,timing,DEFAULT_SEARCH_ROUNDS,completion);
 }
 public synchronized Ticket plan(Object owner,Point start,Point goal,double yaw,double pitch,Options options,Supplier<Terrain> terrain,CorridorPlanner.Timing timing,int maxSegments,Consumer<Result> completion){
  if(maxSegments<1)throw new IllegalArgumentException("Invalid segment limit");
  Math.multiplyExact(options.budget(),maxSegments);
  if(closed)throw new IllegalStateException("PATH_SERVICE_CLOSED");if(current!=null)throw new IllegalStateException("PATH_INPUT_OWNED");long id=revision.incrementAndGet();current=new Ticket(java.util.Objects.requireNonNull(owner),id);callback=completion;
  result=null;lastMeasured=null;request=new Request(goal,options,terrain,timing,maxSegments);replans=0;submit(start,yaw,pitch,id);return current;
 }
 public synchronized Ticket start(Object owner,Point start,Point goal,double yaw,double pitch,Options options,Supplier<Terrain> terrain,CorridorPlanner.Timing timing,Driver driver,Consumer<Result> completion){
  java.util.Objects.requireNonNull(driver);
  Math.multiplyExact(options.budget(),driver.maximumSearchSegments());
  if(closed||current!=null||!INPUT_LEASE.compareAndSet(null,this))throw new IllegalStateException("PATH_INPUT_OWNED");
  current=new Ticket(java.util.Objects.requireNonNull(owner),revision.incrementAndGet());
  lastMeasured=start;
  this.driver=driver;request=new Request(goal,options,terrain,timing,driver.maximumSearchSegments());callback=completion;result=null;replans=0;preparing=driver.needsPreparation();
  if(!preparing){autoDriver=driver;this.driver=null;submit(start,yaw,pitch,revision.get());}
  return current;
 }
 public synchronized void execute(Ticket ticket,Driver driver){require(ticket);if(planning!=null||result==null||result.status()!=Status.ARRIVED)throw new IllegalStateException("NO_EXECUTABLE_PLAN");java.util.Objects.requireNonNull(driver);if(INPUT_LEASE.get()!=this&&!INPUT_LEASE.compareAndSet(null,this))throw new IllegalStateException("PATH_INPUT_OWNED");this.driver=driver;lastMeasured=driver.position();index=0;active=false;}
 private void submit(Point start,double yaw,double pitch,long id) {
  var args=request;
  SearchAudit audit=autoDriver==null?null:autoDriver.planningAudit(replans);
  int threads=autoDriver==null?Runtime.getRuntime().availableProcessors():autoDriver.candidateWorkers();
  boolean gpu=autoDriver==null||autoDriver.gpuAcceleration();
  long queued=System.nanoTime();
  if(audit!=null)audit.emit("ENQUEUED",java.util.Map.of("start",start,"queuedNanos",queued,"workers",threads,"gpuEnabled",gpu));
  planning=worker.submit(()->{
   long began=System.nanoTime();
   if(audit!=null)audit.emit("WORKER_START",java.util.Map.of("start",start,"queueMs",(began-queued)/1e6,
           "thread",Thread.currentThread().getName(),"workers",threads));
   try {
    var geometry=args.terrain.get();
    var route=SegmentedPlanner.plan(geometry,args.options,args.segments,args.timing,
            ()->cancelled(id),audit,start,args.goal,yaw,pitch,threads,gpu);
    if(audit!=null)audit.emit("WORKER_END",java.util.Map.of("status",route.status(),"reason",route.reason(),
            "expanded",route.explored(),"plannedEnd",route.end(),"workMs",(System.nanoTime()-began)/1e6));
    return new Planned(route,geometry,null);
   } catch(RuntimeException failure) {
    if(audit!=null)audit.emit("WORKER_ERROR",java.util.Map.of("reason",failure.toString(),"start",start));
    throw failure;
   }
  });
 }
 private boolean cancelled(long id){return revision.get()!=id||Thread.currentThread().isInterrupted();}
 private void rehearse(Planned plan,CorridorRepair.Start initial){
  long id=revision.get();
  var args=request;
  rehearseSuffix(plan.result(),()->plan.geometry(),initial,null,args,id);
 }
 private void rehearseSuffix(Result plan,Supplier<Terrain> geometry,CorridorRepair.Start initial,
                             CorridorContinuation continuation,Request args,long id){
  var clientDriver=autoDriver;
  int limit=clientDriver.rehearsalLimit();
  clientDriver.rehearsalStarting(initial,limit);
  planning=worker.submit(()->{
   BooleanSupplier cancelled=()->cancelled(id);
   Terrain terrain=geometry.get();
   var corridor=continuation==null?new CorridorRepair(plan,args.options,args.timing,terrain):continuation.corridor();
   double progress=continuation==null?0:continuation.progress();
   var rehearsal=corridor.rehearse(initial,terrain,cancelled,limit,progress);
   if(rehearsal.complete()&&!cancelled.getAsBoolean())clientDriver.prepareRehearsalOverlay(rehearsal,cancelled);
   return new Planned(plan,null,rehearsal);
  });
 }

 public synchronized Result snapshot(){return result;}
 public synchronized void tick(){try{advance();}catch(RuntimeException e){finish(Status.EXECUTION_FAILED,"DRIVER_EXCEPTION:"+e.getClass().getSimpleName());}}
 private void advance(){
  if(current==null)return;
  var measuring=driver!=null?driver:autoDriver;
  if(measuring!=null&&measuring.usable()) lastMeasured=measuring.position();
  if(waitingForReplan){
   if(!driver.usable()){finish(Status.EXECUTION_FAILED,driver.failureReason());return;}
   if(!driver.readyForReplan())return;
   waitingForReplan=false;
   var options=driver.updateOptions(request.options);
   if(replans==2&&options.mode()==Mode.WEIGHTED)
    options=new Options(Mode.NORMAL,options.transmission(),options.projectAir(),options.distance(),options.jumpHeight(),options.budget(),options.weights());
   request=new Request(request.goal,options,request.terrain,driver.updateTiming(request.timing),request.segments);
   var at=driver.position();double yaw=driver.yaw(),pitch=driver.pitch();
   autoDriver=driver;driver=null;submit(at,yaw,pitch,revision.incrementAndGet());return;
  }
  if(preparing){
   if(!driver.usable()){finish(Status.EXECUTION_FAILED,driver.failureReason());return;}
   if(!driver.prepare())return;
   preparing=false;
   var at=driver.position();double yaw=driver.yaw(),pitch=driver.pitch();
   request=new Request(request.goal,driver.updateOptions(request.options),request.terrain,driver.updateTiming(request.timing),request.segments);
   driver.release();autoDriver=driver;driver=null;submit(at,yaw,pitch,revision.get());return;
  }
  if (autoDriver!=null && !autoDriver.usable()) {finish(Status.EXECUTION_FAILED,autoDriver.failureReason());return;}
  if(planning!=null&&planning.isDone()) {
   try {
    var planned=planning.get();
    result=planned.result();
    planning=null;
    if(result.status()!=Status.ARRIVED)finish(result.status(),result.reason());
    else if(autoDriver!=null) {
     var next=autoDriver;
     if(planned.geometry()!=null) {
      next.planReady(result);
      var initial=next.forecast();
      if(initial!=null){rehearse(planned,initial);return;}
     }
     autoDriver=null;
     if(planned.rehearsal()!=null) {
      next.rehearsalReady(planned.rehearsal());
      if(!planned.rehearsal().complete()) {
       driver=next;
       retry(planned.rehearsal().reason());
       return;
      }
     }
     execute(current,next);
    } else if(callback!=null)callback.accept(result);
   }catch(InterruptedException e){
    Thread.currentThread().interrupt();finish(Status.CANCELLED,"PLANNING_INTERRUPTED");
   }catch(ExecutionException e){
    finish(Status.EXECUTION_FAILED,"PLANNING_EXCEPTION:"+e.getCause());
   }
   return;
  }
  if(driver==null)return;
  if(!driver.usable()){finish(Status.EXECUTION_FAILED,driver.failureReason());return;}
  if(driver.followsCorridor()) {
   boolean arrived=driver.advanceCorridor();
   if(driver.failed())retry(driver.failureReason());
   else if(arrived)finish(Status.ARRIVED,"EXECUTION_CONFIRMED");
   else {
    var continuation=driver.takeCorridorContinuation();
    if(continuation!=null){
     var initial=driver.forecast();
     driver.release();
     autoDriver=driver;
     driver=null;
     rehearseSuffix(result,request.terrain,initial,continuation,request,revision.incrementAndGet());
    }
   }
   return;
  }
  if(index==result.segments().size()){finish(Status.ARRIVED,"EXECUTION_CONFIRMED");return;}
  if(!active){driver.begin(result.segments().get(index));active=true;}
  if(driver.failed()){retry(driver.failureReason());return;}
  if(driver.completed()){
   var action=result.segments().get(index);
   if(driver.failed()||!driver.accepts(action)){retry(driver.failed()?driver.failureReason():"ACTUAL_POSITION_DIFFERS_REPLAN_REQUIRED");return;}
   driver.release();index++;active=false;
   if(index<result.segments().size()){
    driver.begin(result.segments().get(index));active=true;
    // Start aligning immediately after a packet. The monotonic cast gate still governs dispatch.
    driver.completed();
   }
  }
 }
 private void retry(String reason){
  if(!driver.canReplan()||replans>=2){finish(Status.EXECUTION_FAILED,reason);return;}
  driver.release();
  driver.replanning(reason,++replans);
  waitingForReplan=true;
 }
 public synchronized void cancel(Ticket ticket){cancel(ticket,"OWNER_CANCELLED");}
 synchronized void cancel(Ticket ticket,String reason){require(ticket);finish(Status.CANCELLED,reason);}
 public synchronized void contextChanged(){contextChanged("CONTEXT_CHANGED");}
 public synchronized void contextChanged(String reason){if(current!=null)finish(Status.CANCELLED,reason);}
 private void require(Ticket t){if(current==null||!current.equals(t))throw new IllegalArgumentException("NOT_PATH_OWNER");}
 private void finish(Status status,String why){
  revision.incrementAndGet();if(planning!=null)planning.cancel(true);planning=null;
  Point planned=result==null?null:result.end();
  var last=driver!=null?driver:autoDriver;
  try{if(last!=null&&last.usable())lastMeasured=last.position();}catch(RuntimeException e){status=Status.EXECUTION_FAILED;why="POSITION_READ_FAILED";}
  Point p=lastMeasured!=null?lastMeasured:planned!=null?planned:new Point(0,0,0);
  try{if(last!=null){last.release();last.terminalPosition(lastMeasured,planned);}}catch(RuntimeException e){status=Status.EXECUTION_FAILED;why="INPUT_RELEASE_FAILED";}
  finally{driver=null;autoDriver=null;current=null;request=null;lastMeasured=null;preparing=false;waitingForReplan=false;INPUT_LEASE.compareAndSet(this,null);}
  result=new Result(status,result==null?java.util.List.of():result.segments(),result==null?p:result.requested(),result==null?p:result.resolved(),p,result==null?0:result.cost(),result==null?0:result.explored(),result==null?java.util.Set.of():result.missing(),why);
  if(callback!=null){var cb=callback;callback=null;cb.accept(result);}
 }
 public synchronized void close(){closed=true;contextChanged("CLIENT_STOPPING");worker.shutdownNow();}
}
