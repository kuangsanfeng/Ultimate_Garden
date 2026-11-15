package com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot;
import java.util.*;
public record ZombieShootCoordinateSystem(double x,double y,double z,double eyeX,double eyeY,double eyeZ,float yaw,float pitch) {
 public static final double SCAN_RADIUS=30;
 public static boolean insideRelative(double x,double y,double z){return x*x+y*y+z*z<=SCAN_RADIUS*SCAN_RADIUS;}
 public boolean contains(double a,double b,double c){return insideRelative(a-x,b-y,c-z);}
 public List<Double> relative(double a,double b,double c){return List.of(a-x,b-y,c-z);}
 public double displacement(double a,double b,double c){return Math.sqrt((a-x)*(a-x)+(b-y)*(b-y)+(c-z)*(c-z));}
}

