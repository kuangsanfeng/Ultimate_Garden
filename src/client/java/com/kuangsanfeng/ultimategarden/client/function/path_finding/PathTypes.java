package com.kuangsanfeng.ultimategarden.client.function.path_finding;
import java.util.*;import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
public final class PathTypes {
 private PathTypes(){}
 public enum Mode{NORMAL,WEIGHTED,CORRIDOR} public enum Movement{WALK,JUMP,FALL,TRANSMISSION}
 public enum Status{ARRIVED,UNREACHABLE,NO_SPACE_CORRIDOR,NO_LOCAL_ACTION,MAP_MISSING,BUDGET_EXHAUSTED,EXECUTION_FAILED,CANCELLED}
 public record Weights(double transmission,double walk,double jump,double fall,double turn,double exponent){
  public Weights{for(double d:new double[]{transmission,walk,jump,fall,turn,exponent})if(!Double.isFinite(d)||d<0)throw new IllegalArgumentException("Invalid weight");if(exponent==0)throw new IllegalArgumentException("Invalid exponent");}
  public static Weights defaults(){return new Weights(.01,.1,1,0,1,2);}
 }
 public static final long DEFAULT_NODES_PER_ROUND=2_000_000;
 public static final int DEFAULT_SEARCH_ROUNDS=2;
 public record Options(Mode mode,boolean transmission,boolean projectAir,double distance,double jumpHeight,long budget,Weights weights){
  public Options{Objects.requireNonNull(mode);Objects.requireNonNull(weights);if(!Double.isFinite(distance)||distance<=0||!Double.isFinite(jumpHeight)||jumpHeight<0||jumpHeight>6||budget<1)throw new IllegalArgumentException("Invalid path limits");}
  /** Offline 12-block defaults; live callers must supply a confirmed equipment capability. */
  public static Options defaults(){return new Options(Mode.NORMAL,true,true,12,1,DEFAULT_NODES_PER_ROUND,Weights.defaults());}
 }
 public record Segment(Movement movement,Point from,Point to,double yaw,double pitch,double ascent,double cost,double castDistance){
  public Segment{Objects.requireNonNull(movement);Objects.requireNonNull(from);Objects.requireNonNull(to);}
  public Segment(Movement movement,Point from,Point to,double yaw,double pitch,double ascent,double cost){this(movement,from,to,yaw,pitch,ascent,cost,0);}
 }
 public record Result(Status status,List<Segment> segments,Point requested,Point resolved,Point end,double cost,long explored,Set<String> missing,String reason){public Result{segments=List.copyOf(segments);missing=Set.copyOf(missing);}}
 public interface Terrain {
  enum MotionCheck {CLEAR,BLOCKED,UNKNOWN}
  boolean clear(Point feet);boolean supported(Point feet);boolean sweep(Point a,Point b);
  Optional<Point> ground(Point p);Set<String> missing();
  default Optional<Point> localSurface(Point top,double maxDrop) {
   return ground(top).filter(p->p.y()>=top.y()-maxDrop && p.y()<=top.y()+.001);
  }
  default MotionCheck motionSweep(Point a,Point b) {
   if(sweep(a,b)) return MotionCheck.CLEAR;
   Object reason=diagnosticFailure().get("reason");
   return "MISSING_CHUNK".equals(reason)||"OUTSIDE_SCAN".equals(reason)||"UNKNOWN".equals(reason)?MotionCheck.UNKNOWN:MotionCheck.BLOCKED;
  }
  default boolean parallelQueries(){return false;}
  default boolean corridorClear(Point feet){return clear(feet);}
  default boolean corridorSweep(Point from,Point to){return sweep(from,to);}
  default Map<String,Object> diagnosticFailure(){return Map.of();}
 }
}
