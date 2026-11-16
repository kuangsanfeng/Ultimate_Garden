package com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight;
public record Point(double x,double y,double z){
 public Point {if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("Non-finite point");}
 public Point add(Point d){return new Point(x+d.x,y+d.y,z+d.z);}
 public double distance(Point p){return Math.hypot(Math.hypot(x-p.x,z-p.z),y-p.y);}
 public boolean sameColumn(Point p){return Math.floor(x)==Math.floor(p.x)&&Math.floor(z)==Math.floor(p.z);}
 public boolean sameBlock(Point p){return sameColumn(p)&&Math.floor(y)==Math.floor(p.y);}
 public boolean centred(){return Math.abs(x-Math.floor(x)-.5)<1e-9&&Math.abs(z-Math.floor(z)-.5)<1e-9;}
}
