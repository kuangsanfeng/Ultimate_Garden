import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.*;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.*;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.terrain.*;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.execution.*;
import static com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.*;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.*;import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public final class PathRegression {
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("Path check "+checks);}
 static Terrain flat=new Terrain(){public boolean clear(Point p){return p.y()>=0;}public boolean supported(Point p){return p.y()==0;}public boolean sweep(Point a,Point b){return clear(a)&&clear(b);}public Optional<Point> ground(Point p){return Optional.of(new Point(p.x(),0,p.z()));}public Set<String> missing(){return Set.of();}};
 public static void main(String[] args)throws Exception {
  Point s=new Point(.5,0,.5);check(TransmissionModel.landing(s,0,0,12).equals(new Point(.5,0,12.5)));check(TransmissionModel.landing(s,45,-45,12).equals(new Point(-5.5,8,6.5)));
  check(TransmissionModel.landing(new Point(-.2,1.49,-.2),0,0,12).equals(new Point(-.5,1,11.5)));check(TransmissionModel.landing(new Point(.5,1.5,.5),0,-90,12).y()==14);
  var planner=new PathPlanner();var r=planner.plan(s,new Point(.5,0,18.5),0,0,Options.defaults(),flat,()->false);check(r.status()==Status.ARRIVED&&r.segments().size()==2);check(r.segments().get(0).to().z()==12.5&&r.end().z()==18.5);check(r.segments().stream().allMatch(e->e.yaw()==0&&e.pitch()==0));
  var walk=new Options(Mode.WEIGHTED,false,true,12,1,2000,new Weights(.01,.1,1,0,0,2));r=planner.plan(s,new Point(2.5,0,.5),-90,0,walk,flat,()->false);check(r.status()==Status.ARRIVED&&Math.abs(r.cost()-.2)<1e-8);check(r.segments().stream().noneMatch(e->e.movement()==Movement.TRANSMISSION));
  check(planner.plan(s,new Point(2.5,0,.5),0,0,walk,flat,()->true).status()==Status.CANCELLED);
  var budget=new Options(Mode.WEIGHTED,false,true,12,1,1,Weights.defaults());check(planner.plan(s,new Point(20.5,0,.5),0,0,budget,flat,()->false).status()==Status.BUDGET_EXHAUSTED);
  var cheap=new Options(Mode.WEIGHTED,true,true,12,1,2000,new Weights(.01,.1,1,0,0,2));var expensive=new Options(Mode.WEIGHTED,true,true,12,1,2000,new Weights(10,.1,1,0,0,2));
  check(planner.plan(s,new Point(2.5,0,.5),0,0,cheap,flat,()->false).segments().getFirst().movement()==Movement.TRANSMISSION);
  check(planner.plan(s,new Point(2.5,0,.5),0,0,expensive,flat,()->false).segments().stream().noneMatch(e->e.movement()==Movement.TRANSMISSION));
  var unprojected=new Options(Mode.WEIGHTED,true,false,12,1,2000,Weights.defaults());check(planner.plan(s,new Point(.5,6,.5),0,0,unprojected,flat,()->false).status()==Status.ARRIVED);
  var noAir=new Options(Mode.WEIGHTED,false,false,12,1,2000,Weights.defaults());check(planner.plan(s,new Point(.5,5,.5),0,0,noAir,flat,()->false).status()==Status.UNREACHABLE);
  check(planner.plan(s,new Point(.5,6,.5),0,0,cheap,flat,()->false).resolved().y()==0);
  try(var service=new PathService()) {var got=new AtomicReference<Result>();Object owner=new Object();var ticket=service.plan(owner,s,new Point(.5,0,18.5),0,0,Options.defaults(),()->flat,got::set);
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(got.get()==null&&System.nanoTime()<deadline){service.tick();Thread.yield();}check(got.get()!=null&&got.get().status()==Status.ARRIVED);
   var released=new AtomicInteger();service.execute(ticket,new PathService.Driver(){public Point position(){return s;}public boolean usable(){return true;}public void begin(Segment x){}public boolean completed(){return false;}public boolean failed(){return false;}public void release(){released.incrementAndGet();}});service.tick();service.cancel(ticket);check(released.get()==1&&got.get().status()==Status.CANCELLED);
  }
  try(var service=new PathService()) {
   var outcome=new AtomicReference<Result>();
   var retryCount=new AtomicInteger();
   service.start(new Object(),s,new Point(.5,0,18.5),0,0,Options.defaults(),()->flat,CorridorPlanner.Timing.defaults(),
      new PathService.Driver(){
       private Point at=s,goal=s;private boolean failed;private int beginCount;
       public Point position(){return at;}
       public boolean usable(){return true;}
       public boolean canReplan(){return true;}
       public void begin(Segment action){goal=action.to();failed=++beginCount==1;}
       public boolean completed(){if(failed)return false;at=goal;return true;}
       public boolean failed(){return failed;}
       public void release(){}
       public void replanning(String reason,int attempt){retryCount.incrementAndGet();}
      },outcome::set);
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
   while(outcome.get()==null&&System.nanoTime()<deadline){service.tick();Thread.yield();}
   check(outcome.get()!=null&&outcome.get().status()==Status.ARRIVED&&retryCount.get()==1);
  }
  cancellationDuringReplan(s);
  sameParallelRoute(s);
  System.out.println("PASS "+checks+" path model/18=12+6/ground-only cost/budget/owner cancellation checks; controlled driver, not actual game execution");
 }
 static void cancellationDuringReplan(Point start)throws Exception {
  var target=new Point(.5,0,18.5);
  var measured=new Point(.5,0,1.5);
  var secondJob=new CountDownLatch(1);
  var jobs=new AtomicInteger();
  var callbacks=new AtomicInteger();
  var result=new AtomicReference<Result>();
  var planPoint=new AtomicReference<Point>();
  try(var service=new PathService()){
   service.start(new Object(),start,target,0,0,Options.defaults(),()->{
    if(jobs.incrementAndGet()==2){
     secondJob.countDown();
     try {new CountDownLatch(1).await();}catch(InterruptedException stop){Thread.currentThread().interrupt();}
    }
    return flat;
   },CorridorPlanner.Timing.defaults(),new PathService.Driver(){
    private Point at=start;
    private boolean failed;
    public Point position(){return at;}
    public boolean usable(){return true;}
    public boolean canReplan(){return true;}
    public void begin(Segment action){at=measured;failed=true;}
    public boolean completed(){return false;}
    public boolean failed(){return failed;}
    public void release(){}
    public void terminalPosition(Point actual,Point planned){check(actual.equals(measured));planPoint.set(planned);}
   },r->{result.set(r);callbacks.incrementAndGet();});
   long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
   while(secondJob.getCount()!=0&&System.nanoTime()<limit){service.tick();Thread.sleep(1);}
   check(secondJob.getCount()==0);
   service.contextChanged("WORLD_CHANGED");
   check(result.get().status()==Status.CANCELLED&&result.get().reason().equals("WORLD_CHANGED"));
   check(result.get().end().equals(measured)&&planPoint.get().equals(target));
   var next=new AtomicReference<Result>();
   service.plan(new Object(),start,target,0,0,Options.defaults(),()->flat,next::set);
   limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
   while(next.get()==null&&System.nanoTime()<limit){service.tick();Thread.sleep(1);}
   check(next.get()!=null&&next.get().status()==Status.ARRIVED&&callbacks.get()==1);
  }
 }

 static void sameParallelRoute(Point start){
  var used=ConcurrentHashMap.<String>newKeySet();
  Terrain safe=new Terrain(){
   public boolean parallelQueries(){return true;}
   public boolean clear(Point p){return flat.clear(p);}
   public boolean supported(Point p){return flat.supported(p);}
   public boolean sweep(Point a,Point b){
    used.add(Thread.currentThread().getName());
    return flat.sweep(a,b);
   }
   public Optional<Point> ground(Point p){return flat.ground(p);}
   public Set<String> missing(){return Set.of();}
  };
  var config=new Options(Mode.WEIGHTED,true,true,12,1,4000,Weights.defaults());
  var end=new Point(.5,0,36.5);
  var one=SegmentedPlanner.plan(safe,config,4,CorridorPlanner.Timing.defaults(),()->false,null,start,end,0,0,1);
  int available=Runtime.getRuntime().availableProcessors();
  for(int workers:new int[]{Math.min(4,available),Math.min(8,available)}){
   var many=SegmentedPlanner.plan(safe,config,4,CorridorPlanner.Timing.defaults(),()->false,null,start,end,0,0,workers);
   check(one.status()==Status.ARRIVED&&one.segments().equals(many.segments())&&one.explored()==many.explored());
  }
  check(available==1||used.stream().anyMatch(n->n.startsWith("ug-path-candidate-")));
 }

}
