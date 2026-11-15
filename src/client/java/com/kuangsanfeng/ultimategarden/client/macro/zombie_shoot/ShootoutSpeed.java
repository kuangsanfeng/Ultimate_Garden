package com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot;
import java.util.*;
/** Signed arc-length observations. Default exits exactly when the window matures. */
public final class ShootoutSpeed {
 private record Sample(long tick,double speed){}
 private final ArrayDeque<Sample> samples=new ArrayDeque<>();private long first=-1,last=-1;private double previous;private String path;private boolean defaultRetired;
 public enum Direction { FORWARD,BACKWARD,STATIONARY,UNKNOWN }
 public record Estimate(double signedSpeed,boolean reliable,int count,double speedMagnitude,Direction direction){}
 /** Mean of valid measured arc-length speeds only; no initial/default sample. */
 public Double measuredSpeed(){return samples.isEmpty()?null:samples.stream().mapToDouble(v->Math.abs(v.speed)).average().orElseThrow();}
 public Estimate observe(long tick,String path,double s,double initial,int window){
  if(first<0)first=tick;
  if(last>=0&&tick-last==1&&Objects.equals(this.path,path)&&Math.abs(s-previous)<=1){samples.add(new Sample(tick,s-previous));}
  else if(last>=0){samples.clear();}
  last=tick;previous=s;this.path=path;while(!samples.isEmpty()&&samples.getFirst().tick<=tick-window)samples.removeFirst();
  double sum=samples.stream().mapToDouble(v->Math.abs(v.speed)).sum();int count=samples.size();if(tick-first>=window)defaultRetired=true;boolean mature=defaultRetired;
  Direction direction=count==0?Direction.UNKNOWN:Math.abs(samples.getLast().speed)<1e-8?Direction.STATIONARY:samples.getLast().speed>0?Direction.FORWARD:Direction.BACKWARD;
  double magnitude=mature?(count==0?0:sum/count):(sum+initial)/(count+1);
  double signed=direction==Direction.FORWARD?magnitude:direction==Direction.BACKWARD?-magnitude:0;
  return new Estimate(signed,mature?count>=Math.min(window,Math.max(2,window/2)):count>0,count,magnitude,direction);
 }
}
