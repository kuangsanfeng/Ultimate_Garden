package com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.model;

import java.util.ArrayDeque;

/** Per-entity, bounded, continuous X/Z observations. Seconds are measured with a monotonic clock. */
public final class ShootoutBabyStall {
 private record Sample(long nanos,double x,double z,double distance){}
 private final com.kuangsanfeng.ultimategarden.client.runtime.world.observation.sampling.TimedWindow<Sample> samples=new com.kuangsanfeng.ultimategarden.client.runtime.world.observation.sampling.TimedWindow<>(256,Sample::nanos);
 private long lastTick=-1;private double windowSeconds=-1;private boolean stalled,ready;
 public boolean stalled(){return ready&&stalled;}
 public boolean ready(){return ready;}
 public void clear(){samples.clear();lastTick=-1;stalled=false;ready=false;windowSeconds=-1;}
 public void observe(long tick,long nanos,double x,double z,double defaultSpeed,double seconds,double ratio){
  if(!Double.isFinite(x)||!Double.isFinite(z)||!Double.isFinite(defaultSpeed)||defaultSpeed<=0||!Double.isFinite(seconds)||seconds<=0||!Double.isFinite(ratio)||ratio<0){clear();return;}
  if(seconds!=windowSeconds){clear();windowSeconds=seconds;}
  var previous=samples.peekLast();
  if(previous!=null&&tick==lastTick)return; // A same-tick empty-queue rescan is not another movement sample.
  double distance=previous==null?0:Math.hypot(x-previous.x,z-previous.z);
  if(previous!=null&&(tick-lastTick!=1||nanos<=previous.nanos||nanos-previous.nanos>250_000_000L||distance>1)){
   samples.clear();stalled=false;ready=false;distance=0;
  }
  lastTick=tick;samples.addLast(new Sample(nanos,x,z,distance));

  double start=nanos-seconds*1e9;
  // Keep the segment crossing the window boundary; include only its in-window fraction.
  samples.retainBoundary(start);
  ready=samples.size()>1&&samples.getFirst().nanos<=start;
  if(!ready)return;
  double measured=0;Sample before=null;
  for(var sample:samples){if(before!=null){double fraction=Math.min(1,(sample.nanos-start)/(sample.nanos-before.nanos));measured+=sample.distance*Math.max(0,fraction);}before=sample;}
  double threshold=defaultSpeed*20*seconds*ratio; // Resource speeds are blocks/tick; 20 ticks per second is the reference speed unit.
  if(measured<threshold)stalled=true;else if(measured>threshold)stalled=false;
 }
}
