package com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.model;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.targeting.ShootoutPolicy;

import com.google.gson.*;
import java.util.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.function.DoubleFunction;
import net.minecraft.world.phys.Vec3;
/** Immutable measured resource and pure numerical routines. No client controls. */
public final class ShootoutModel {
 public record Projection(Path path,double s,double residual,Vec3 offset){}
 public record Solution(double ticks,double yaw,double pitch,Vec3 target){}
 public static final class Path {
  public final String id;public final double[] arc;public final Vec3[] points;public final double length;
  Path(JsonObject json){id=json.get("pathId").getAsString();var a=json.getAsJsonArray("points");arc=new double[a.size()];points=new Vec3[a.size()];for(int i=0;i<a.size();i++){var p=a.get(i).getAsJsonObject();arc[i]=p.get("sBlocks").getAsDouble();points[i]=new Vec3(p.get("x").getAsDouble(),p.get("y").getAsDouble(),p.get("z").getAsDouble());if(!finite(points[i])||!Double.isFinite(arc[i])||i>0&&arc[i]<=arc[i-1])throw new IllegalArgumentException("Invalid path samples");}if(a.size()<2||arc[0]!=0)throw new IllegalArgumentException("Invalid path");length=arc[arc.length-1];}
  public Vec3 at(double s){s=Math.clamp(s,0,length);int k=Arrays.binarySearch(arc,s);if(k>=0)return points[k];int j=Math.clamp(-k-1,1,arc.length-1);double u=(s-arc[j-1])/(arc[j]-arc[j-1]);return points[j-1].lerp(points[j],u);}
  public Vec3 tangent(double s){return at(Math.min(length,s+.05)).subtract(at(Math.max(0,s-.05))).normalize();}
  public Projection project(Vec3 p){double best=Double.POSITIVE_INFINITY,s=0;Vec3 offset=Vec3.ZERO;for(int i=1;i<points.length;i++){var d=points[i].subtract(points[i-1]);double u=Math.clamp(p.subtract(points[i-1]).dot(d)/d.lengthSqr(),0,1);var q=points[i-1].add(d.scale(u));double r=p.distanceTo(q);if(r<best){best=r;s=arc[i-1]+u*(arc[i]-arc[i-1]);offset=p.subtract(q);}}return new Projection(this,s,best,offset);}
 }
 public final List<Path> paths;public final Map<String,Double> speeds;public final double drag,gravity,a,b;
 public ShootoutModel(InputStream input)throws IOException{if(input==null)throw new IOException("MODEL_RESOURCE_MISSING");try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){var json=JsonParser.parseReader(reader).getAsJsonObject();var list=new ArrayList<Path>();for(var p:json.getAsJsonArray("paths"))list.add(new Path(p.getAsJsonObject()));if(list.size()!=2||!list.get(0).id.equals("PATH_A")||list.get(0).points.length!=141||!list.get(1).id.equals("PATH_B")||list.get(1).points.length!=151)throw new IOException("MODEL_PATH_COUNTS_INVALID");paths=List.copyOf(list);var speed=new HashMap<String,Double>();for(var e:json.getAsJsonArray("zombieSpeedLookup")){var o=e.getAsJsonObject();double v=o.get("speedBlocksPerTick").getAsDouble();if(!Double.isFinite(v)||v<=0)throw new IOException("Invalid speed");speed.put(o.get("class").getAsString(),v);}if(!speed.keySet().equals(new HashSet<>(ShootoutPolicy.ORDER.subList(1,9))))throw new IOException("Invalid class table");speeds=Map.copyOf(speed);var arrow=json.getAsJsonObject("arrowModel");drag=arrow.get("dragPerTick").getAsDouble();gravity=arrow.get("gravityVelocityDecrementBlocksPerTick").getAsDouble();a=arrow.getAsJsonObject("empiricalInitialSpeed").get("a").getAsDouble();b=arrow.getAsJsonObject("empiricalInitialSpeed").get("b").getAsDouble();if(!(drag>0&&drag<1&&gravity>0&&Double.isFinite(gravity)&&Double.isFinite(a)&&Double.isFinite(b)&&a+Math.min(b,0)>0))throw new IOException("Invalid ballistics");}}
 public static ShootoutModel load()throws IOException{return new ShootoutModel(ShootoutModel.class.getResourceAsStream("/ultimategarden/zombie_shoot_theoretical_models.json"));}
 public Projection match(Vec3 relative){var p=paths.get(0).project(relative);var q=paths.get(1).project(relative);return Math.min(p.residual,q.residual)>1||Math.abs(p.residual-q.residual)<.35?null:p.residual<q.residual?p:q;}
 public static boolean finite(Vec3 v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
 public static double wrap(double x){return ((x+180)%360+360)%360-180;}
 public static double reflect(double s,double length){double m=((s%(2*length))+2*length)%(2*length);return length-Math.abs(m-length);}
 public double speed(double yaw){return a+b*Math.pow(Math.sin(2*Math.toRadians(yaw)),2);}
 public double sum(double t){return -Math.expm1(t*Math.log(drag))/(1-drag);}
 public double fall(double t){return gravity/(1-drag)*(t-sum(t));}
 public Vec3 velocity(double yaw,double pitch){double y=Math.toRadians(yaw),p=Math.toRadians(pitch),v=speed(yaw);return new Vec3(-v*Math.cos(p)*Math.sin(y),-v*Math.sin(p),v*Math.cos(p)*Math.cos(y));}
 public Vec3 arrow(Vec3 eye,double yaw,double pitch,double t){var v=velocity(yaw,pitch);return eye.add(v.scale(sum(t))).add(0,-fall(t),0);}
 private double equation(Vec3 eye,DoubleFunction<Vec3> target,double t){var d=target.apply(t).subtract(eye);double yaw=Math.toDegrees(Math.atan2(-d.x,d.z));return speed(yaw)*sum(t)-Math.hypot(Math.hypot(d.x,d.z),d.y+fall(t));}
 public Solution solve(Vec3 eye,DoubleFunction<Vec3> target){double previous=.001,fp=equation(eye,target,previous);for(double t=.125;t<=40;t+=.125){double f=equation(eye,target,t);if(!Double.isFinite(f))return null;if(fp*f<=0){double lo=previous,hi=t;for(int i=0;i<35;i++){double mid=(lo+hi)/2;if(equation(eye,target,mid)*fp>0)lo=mid;else hi=mid;}double root=(lo+hi)/2;var p=target.apply(root);var d=p.subtract(eye);double yaw=wrap(Math.toDegrees(Math.atan2(-d.x,d.z))),pitch=-Math.toDegrees(Math.atan2(d.y+fall(root),Math.hypot(d.x,d.z)));return finite(p)&&Double.isFinite(pitch)&&Math.abs(pitch)<90?new Solution(root,yaw,pitch,p):null;}previous=t;fp=f;}return null;}
}
