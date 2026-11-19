package com.kuangsanfeng.ultimategarden.client.platform.input.view;



/** One shared-budget mouse step; contains no controller progress or target history. */
public class ViewRotationPlanner {
 public record Rotation(double yawDelta,double pitchDelta,double error,double step){}
 public static double step(double error,double peak,double curvature,double dt){
  if(!Double.isFinite(error)||error<0||!Double.isFinite(peak)||peak<=0||!Double.isFinite(curvature)||curvature<=0||!Double.isFinite(dt)||dt<0)throw new IllegalArgumentException("Invalid aim curve input");
  return Math.min(error,peak*Math.tanh(error/curvature)*Math.min(dt,.05));
 }
 public static Rotation rotation(double yaw,double pitch,double targetYaw,double targetPitch,double peak,double curvature,double dt){
  Double error=ViewAngles.error(yaw,pitch,targetYaw,targetPitch);
  if(error==null)throw new IllegalArgumentException("Invalid view angles");
  double dy=Math.abs(targetPitch)==90?0:ViewAngles.wrap(ViewAngles.wrap(targetYaw)-ViewAngles.wrap(yaw));
  double dp=targetPitch-pitch;
  double distance=Math.hypot(dy,dp);
  double step=step(distance,peak,curvature,dt);
  double fraction=distance==0?0:step/distance;
  return new Rotation(dy*fraction,dp*fraction,error,step);
 }

 public static double rawPitchDelta(float pitch,double delta,double sensitivity,boolean inverted){
  return rawDelta(pitch,Math.clamp(delta,-90.0-pitch,90.0-pitch),sensitivity,inverted);
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
