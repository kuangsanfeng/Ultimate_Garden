package com.kuangsanfeng.ultimategarden.client.function.collection.zombie_shoot;
import java.util.*;
public record ZombieShootCoordinateSystem(double x,double y,double z,double eyeX,double eyeY,double eyeZ,float yaw,float pitch) {
 public static final double SCAN_RADIUS=30;
 public static boolean insideRelative(double x,double y,double z){return com.kuangsanfeng.ultimategarden.client.function.observation.query.SpatialQueries.inSphere(x,y,z,SCAN_RADIUS);}
 public boolean contains(double a,double b,double c){return insideRelative(a-x,b-y,c-z);}
 public List<Double> relative(double a,double b,double c){return com.kuangsanfeng.ultimategarden.client.function.observation.query.SpatialQueries.relative(a,b,c,x,y,z);}
 public double displacement(double a,double b,double c){return Math.sqrt((a-x)*(a-x)+(b-y)*(b-y)+(c-z)*(c-z));}
}

