package com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot;

import net.minecraft.world.phys.Vec3;

/** One whole-view great-circle step; contains no controller progress or target history. */
public final class ShootoutTanhAim {
 public record Rotation(double yawDelta,double pitchDelta,double error,double step){}
 public static double step(double error,double peak,double curvature,double dt){
  if(!Double.isFinite(error)||error<0||!Double.isFinite(peak)||peak<=0||!Double.isFinite(curvature)||curvature<=0||!Double.isFinite(dt)||dt<0)throw new IllegalArgumentException("Invalid aim curve input");
  return Math.min(error,peak*Math.tanh(error/curvature)*Math.min(dt,.05));
 }
 private static Vec3 direction(double yaw,double pitch){
  double y=Math.toRadians(ShootoutModel.wrap(yaw)),p=Math.toRadians(pitch);
  return new Vec3(-Math.cos(p)*Math.sin(y),-Math.sin(p),Math.cos(p)*Math.cos(y));
 }
 public static Rotation rotation(double yaw,double pitch,double targetYaw,double targetPitch,double peak,double curvature,double dt){
  Double error=ShootoutAimCompletion.error(yaw,pitch,targetYaw,targetPitch);
  if(error==null)throw new IllegalArgumentException("Invalid view angles");
  double step=step(error,peak,curvature,dt);
  if(error==0||step==0)return new Rotation(0,0,error,step);
  if(step>=error)return new Rotation(ShootoutModel.wrap(targetYaw-yaw),targetPitch-pitch,error,step);
  var from=direction(yaw,pitch);var to=direction(targetYaw,targetPitch);
  var tangent=to.subtract(from.scale(Math.clamp(from.dot(to),-1,1)));
  if(tangent.lengthSqr()<1e-20){
   double y=Math.toRadians(ShootoutModel.wrap(yaw)),p=Math.toRadians(pitch);
   double dy=ShootoutModel.wrap(targetYaw-yaw),dp=targetPitch-pitch;
   tangent=Math.abs(dp)>Math.abs(dy)?new Vec3(Math.sin(p)*Math.sin(y),-Math.cos(p),-Math.sin(p)*Math.cos(y)).scale(Math.signum(dp))
     :new Vec3(-Math.cos(y),0,-Math.sin(y)).scale(dy<0?-1:1);
  }
  double radians=Math.toRadians(step);
  var next=from.scale(Math.cos(radians)).add(tangent.scale(Math.sin(radians)/Math.sqrt(tangent.lengthSqr())));
  double horizontal=Math.hypot(next.x,next.z);
  double nextYaw=horizontal<1e-12?yaw:Math.toDegrees(Math.atan2(-next.x,next.z));
  double nextPitch=Math.clamp(-Math.toDegrees(Math.atan2(next.y,horizontal)),-90,90);
  return new Rotation(ShootoutModel.wrap(nextYaw-yaw),nextPitch-pitch,error,step);
 }
 /** Respect vanilla float precision without accumulating residuals or crossing the planned endpoint. */
 public static double rawDelta(float current,double delta,double sensitivity,boolean inverted){
  double bound=current+delta;float endpoint=(float)bound;
  if(delta>0&&endpoint>bound)endpoint=Math.nextDown(endpoint);
  if(delta<0&&endpoint<bound)endpoint=Math.nextUp(endpoint);
  if(endpoint==current)return 0;
  double ss=sensitivity*.6F+.2F,factor=ss*ss*ss*8,sign=inverted?-1:1;
  float units=(float)((endpoint-current)/.15F);
  for(int i=0;i<8;i++){
   double raw=units/factor*sign;
   float actual=current+(float)(raw*factor*sign)*.15F;
   if(actual==current)return 0;
   if(delta>0?actual<=bound:actual>=bound)return raw;
   units=Math.nextAfter(units,0);
  }
  return 0;
 }
}
