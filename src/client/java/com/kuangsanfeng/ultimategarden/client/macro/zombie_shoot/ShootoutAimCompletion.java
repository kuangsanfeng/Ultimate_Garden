package com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot;
/** Actual view direction comparison, independent of mouse easing progress. */
public final class ShootoutAimCompletion {
 public static Double error(double yaw,double pitch,double targetYaw,double targetPitch){
  if(!Double.isFinite(yaw)||!Double.isFinite(pitch)||!Double.isFinite(targetYaw)||!Double.isFinite(targetPitch)||Math.abs(pitch)>90||Math.abs(targetPitch)>90)return null;
  double dy=Math.toRadians(ShootoutModel.wrap(ShootoutModel.wrap(targetYaw)-ShootoutModel.wrap(yaw)));
  if(dy==0&&pitch==targetPitch)return 0.;
  double p=Math.toRadians(pitch),t=Math.toRadians(targetPitch);
  double dot=Math.sin(p)*Math.sin(t)+Math.cos(p)*Math.cos(t)*Math.cos(dy);
  double result=Math.toDegrees(Math.acos(Math.clamp(dot,-1,1)));
  return Double.isFinite(result)?result:null;
 }
 public static boolean aligned(double yaw,double pitch,double targetYaw,double targetPitch,double tolerance){
  return aligned(yaw,pitch,targetYaw,targetPitch,tolerance,null);
 }
 public static boolean aligned(double yaw,double pitch,double targetYaw,double targetPitch,double tolerance,java.util.function.Consumer<Double> observed){
  Double error=error(yaw,pitch,targetYaw,targetPitch);
  if(observed!=null)observed.accept(error);
  // Only a numerical comparison allowance, not a gameplay tolerance.
  return error!=null&&Double.isFinite(tolerance)&&tolerance>=0&&error<=tolerance+1e-9;
 }
}
