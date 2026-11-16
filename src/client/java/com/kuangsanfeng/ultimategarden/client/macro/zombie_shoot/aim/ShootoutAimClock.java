package com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim;

/** Client-frame scheduler. No catch-up loop; missed deadlines are discarded. */
public final class ShootoutAimClock {
 private boolean active;private int rate;private long last;private double next;
 public void reset(){active=false;last=0;next=0;}
 public double step(long now,int hz){
  if(hz<20||hz>120)throw new IllegalArgumentException("Aim update rate must be 20..120 Hz");
  double period=1e9/hz;
  if(!active||rate!=hz||now<last){active=true;rate=hz;last=now;next=now+period;return 0;}
  if(now+.5<next)return 0; // Half-nanosecond rounding tolerance for fractional periods (60/120 Hz).
  double dt=Math.min(.05,(now-last)/1e9);last=now;
  next+=Math.max(1,Math.floor((now-next)/period)+1)*period;
  return dt;
 }
}
