package com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight;
public record Capability(double distance,int maxClicks,int budget,double tolerance){
 public Capability{if(!Double.isFinite(distance)||distance<=0||distance>128||maxClicks<1||maxClicks>128||budget<1||budget>2000000||!Double.isFinite(tolerance)||tolerance<0)throw new IllegalArgumentException("Invalid capability / search bounds");}
 public static Capability standard(){return new Capability(12,32,200000,.001);}
}
