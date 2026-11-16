package com.kuangsanfeng.ultimategarden.client.runtime.world.observation.query;
import java.util.*;
public final class SpatialQueries {
 private SpatialQueries(){}
 public static boolean inSphere(double x,double y,double z,double radius){return x*x+y*y+z*z<=radius*radius;}
 public static List<Double> relative(double x,double y,double z,double ox,double oy,double oz){return List.of(x-ox,y-oy,z-oz);}
 public static int[] cubeOffset(int index,int extent){int side=2*extent+1;if(index<0||index>=side*side*side)throw new IllegalArgumentException("Cell index");return new int[]{index/(side*side)-extent,index/side%side-extent,index%side-extent};}
 public static boolean intersects(double[] a,double[] b,double[] box){
  double lo=0,hi=1;for(int i=0;i<3;i++){double d=b[i]-a[i];if(Math.abs(d)<1e-10){if(a[i]<box[i]||a[i]>box[i+3])return false;}else{double x=(box[i]-a[i])/d,y=(box[i+3]-a[i])/d;lo=Math.max(lo,Math.min(x,y));hi=Math.min(hi,Math.max(x,y));if(lo>hi)return false;}}return true;
 }
}
