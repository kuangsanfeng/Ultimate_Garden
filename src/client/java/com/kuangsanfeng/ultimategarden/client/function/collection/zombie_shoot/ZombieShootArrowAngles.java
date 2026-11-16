package com.kuangsanfeng.ultimategarden.client.function.collection.zombie_shoot;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat.ZombieShootSnapshot;

import java.util.*;
import static com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat.ZombieShootSnapshot.map;
/** Passive angle comparisons; entity orientation is distinct from velocity direction. */
public final class ZombieShootArrowAngles {
 private static final double EPS=1e-8;
 public static Double wrap(double value){return Double.isFinite(value)?((value+180)%360+360)%360-180:null;}
 private static Double difference(Double a,double b,boolean yaw){if(a==null||!Double.isFinite(a)||!Double.isFinite(b))return null;return yaw?wrap(a-b):a-b;}
 public static Double[] direction(double x,double y,double z){
  if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return new Double[]{null,null};
  double horizontal=Math.hypot(x,z);if(Math.hypot(horizontal,y)<=EPS)return new Double[]{null,null};
  return new Double[]{horizontal<=EPS?null:wrap(Math.toDegrees(Math.atan2(-x,z))),-Math.toDegrees(Math.atan2(y,horizontal))};
 }
 public static Map<String,Object> sample(double ay,double ap,double py,double pp,double oy,double op,double vx,double vy,double vz,double ex,double ey,double ez,double ax,double arrowY,double az){
  var velocity=direction(vx,vy,vz);double dx=ax-ex,dy=arrowY-ey,dz=az-ez;var spatial=direction(dx,dy,dz);
  var out=map("arrowYaw",ay,"arrowPitch",ap,"playerYaw",py,"playerPitch",pp,"originYaw",oy,"originPitch",op,
   "relativeYawToPlayer",difference(ay,py,true),"relativePitchToPlayer",difference(ap,pp,false),"relativeYawToOrigin",difference(ay,oy,true),"relativePitchToOrigin",difference(ap,op,false),
   "velocityX",vx,"velocityY",vy,"velocityZ",vz,"velocityYaw",velocity[0],"velocityPitch",velocity[1],
   "velocityRelativeYawToPlayer",difference(velocity[0],py,true),"velocityRelativePitchToPlayer",difference(velocity[1],pp,false),"velocityRelativeYawToOrigin",difference(velocity[0],oy,true),"velocityRelativePitchToOrigin",difference(velocity[1],op,false),
   "playerEyeX",ex,"playerEyeY",ey,"playerEyeZ",ez,"arrowDirectionFromPlayerYaw",spatial[0],"arrowDirectionFromPlayerPitch",spatial[1],"arrowDirectionRelativeYaw",difference(spatial[0],py,true),"arrowDirectionRelativePitch",difference(spatial[1],pp,false),"distanceFromPlayer",Math.hypot(Math.hypot(dx,dz),dy));
  return new ZombieShootSnapshot(out).data();
 }
}
