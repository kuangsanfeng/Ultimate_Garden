package com.kuangsanfeng.ultimategarden.client.anti_check;
/** One monotonic warning window, no threads or queued playback jobs. */
public final class ViewWarningClock {
 private long start,next,duration;private boolean active;
 public void begin(long now,double seconds){start=now;next=now;duration=(long)(seconds*1e9);active=true;}
 public boolean active(){return active;}
 public int pulse(long now){if(!active)return 0;if(now-start>=duration){clear();return -1;}if(now>=next){next=now+400_000_000L;return 1;}return 0;}
 public void clear(){active=false;start=0;next=0;duration=0;}
}
