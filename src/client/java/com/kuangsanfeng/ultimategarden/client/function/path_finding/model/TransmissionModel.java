package com.kuangsanfeng.ultimategarden.client.function.path_finding.model;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.*;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.model.TransmissionModel;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.PathPlanner;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
/** Path-finding contract. Deliberately separate from the legacy experimental FlightModel. */
public final class TransmissionModel {
 private TransmissionModel(){}
 public static Point raw(Point s,double yaw,double pitch,double distance){
  if(!Double.isFinite(yaw)||!Double.isFinite(pitch)||Math.abs(pitch)>90||!Double.isFinite(distance)||distance<=0)throw new IllegalArgumentException("Invalid transmission parameters");
  double y=Math.toRadians(yaw),p=Math.toRadians(pitch);return new Point(s.x()-distance*Math.sin(y)*Math.cos(p),s.y()-distance*Math.sin(p),s.z()+distance*Math.cos(y)*Math.cos(p));
 }
 public static Point landing(Point s,double yaw,double pitch,double distance){var p=raw(s,yaw,pitch,distance);return new Point(Math.floor(p.x())+.5,Math.round(p.y()),Math.floor(p.z())+.5);}
 public static double[] toward(Point a,Point b){double x=b.x()-a.x(),z=b.z()-a.z();return new double[]{Math.toDegrees(Math.atan2(-x,z)),-Math.toDegrees(Math.atan2(b.y()-a.y(),Math.hypot(x,z)))};}
 public static double angle(double ay,double ap,double by,double bp){double a=Math.toRadians(ap),b=Math.toRadians(bp);return Math.toDegrees(Math.acos(Math.clamp(Math.sin(a)*Math.sin(b)+Math.cos(a)*Math.cos(b)*Math.cos(Math.toRadians(ay-by)), -1,1)));}
}
