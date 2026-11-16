package com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug;

/** RESET only. Coordinates are blocks; horizontal velocity is blocks/client Tick. */
public final class TransmissionCoastPrediction {
 public static final double RETENTION=.546,STOP_SPEED=.003;
 private TransmissionCoastPrediction(){}
 public record Stop(double x,double z,int ticks){
  public boolean inside(int baseX,int baseZ){return Math.floor(x)==baseX&&Math.floor(z)==baseZ;}
 }
 public static Stop predict(double x,double z,double vx,double vz){
  if(!Double.isFinite(x)||!Double.isFinite(z)||!Double.isFinite(vx)||!Double.isFinite(vz))throw new IllegalArgumentException("Non-finite return motion");
  int ticks=0;
  while(Math.hypot(vx,vz)>=STOP_SPEED){x+=vx;z+=vz;vx*=RETENTION;vz*=RETENTION;ticks++;}
  if(!Double.isFinite(x)||!Double.isFinite(z))throw new IllegalArgumentException("Non-finite stop point");
  return new Stop(x,z,ticks);
 }
 /** One fixed centre-line per return leg; distances are blocks along its unit direction. */
 public record Leg(double startX,double startZ,double ux,double uz,double yaw,double entry,double exit){
  public boolean overshot(Stop stop){return (stop.x()-startX)*ux+(stop.z()-startZ)*uz>=exit;}
 }
 public static Leg leg(double x,double z,int baseX,int baseZ){
  double dx=baseX+.5-x,dz=baseZ+.5-z,length=Math.hypot(dx,dz);
  if(length==0)return new Leg(x,z,0,0,0,0,0);
  double ux=dx/length,uz=dz/length,entry=Double.NEGATIVE_INFINITY,exit=Double.POSITIVE_INFINITY;
  if(ux!=0){double a=(baseX-x)/ux,b=(baseX+1.0-x)/ux;entry=Math.max(entry,Math.min(a,b));exit=Math.min(exit,Math.max(a,b));}
  if(uz!=0){double a=(baseZ-z)/uz,b=(baseZ+1.0-z)/uz;entry=Math.max(entry,Math.min(a,b));exit=Math.min(exit,Math.max(a,b));}
  double yaw=com.kuangsanfeng.ultimategarden.client.platform.input.view.ViewAngles.wrap(Math.toDegrees(Math.atan2(dx,-dz)));
  return new Leg(x,z,ux,uz,yaw,entry,exit);
 }
 public static double gap(double x,double z,int baseX,int baseZ){return Math.hypot(Math.max(Math.abs(x-(baseX+.5))-.5,0),Math.max(Math.abs(z-(baseZ+.5))-.5,0));}
}
