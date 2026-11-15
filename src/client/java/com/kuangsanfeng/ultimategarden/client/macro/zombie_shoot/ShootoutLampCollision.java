package com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot;

import net.minecraft.world.phys.*;
import java.util.List;

/** A stationary lamp terminates flight at its first valid surface hit. */
public final class ShootoutLampCollision {
 private static double entry(AABB box,Vec3 from,Vec3 to){
  if(box.contains(from))return 0;
  var hit=box.clip(from,to);return hit.isEmpty()?Double.POSITIVE_INFINITY:from.distanceToSqr(hit.get());
 }
 public static boolean hits(ShootoutModel model,Vec3 eye,double yaw,double pitch,double flightTicks,double radius,List<AABB> target,List<AABB> obstacles){
  return hits(model,eye,yaw,pitch,flightTicks,radius,target,obstacles,null);
 }
 public static boolean hits(ShootoutModel model,Vec3 eye,double yaw,double pitch,double flightTicks,double radius,List<AABB> target,List<AABB> obstacles,java.util.function.Consumer<String> outcome){
  Vec3 previous=eye;
  for(int i=1;i<=Math.ceil(flightTicks+1);i++){
   var next=model.arrow(eye,yaw,pitch,Math.min(i,flightTicks+.5));
   double targetHit=Double.POSITIVE_INFINITY,blockHit=Double.POSITIVE_INFINITY;
   for(var box:target)targetHit=Math.min(targetHit,entry(box.inflate(radius),previous,next));
   for(var box:obstacles)blockHit=Math.min(blockHit,entry(box.inflate(radius),previous,next));
   if(Double.isFinite(blockHit)&&blockHit<=targetHit){if(outcome!=null)outcome.accept("OBSTACLE_HIT_FIRST");return false;}
   if(Double.isFinite(targetHit))return true;
   previous=next;
  }
  if(outcome!=null)outcome.accept("TARGET_VOLUME_MISS");return false;
 }
}
