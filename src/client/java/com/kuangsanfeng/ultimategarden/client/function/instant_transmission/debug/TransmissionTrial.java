package com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug;
/** Tick-based bounded observation; never equates click count with successful teleports. */
public final class TransmissionTrial {
 public record Sample(long tick,long nanos,double x,double y,double z,double vy,boolean ground){}
 public final int planned;public final long firstTick;public int actual,updates,packets;public boolean anomaly;
 public long lastClick=-1,lastResponse=-1;public Sample endpoint,landing;private Sample previous,stableStart;private int stable;
 public TransmissionTrial(int count,long tick){planned=count;firstTick=tick;}
 public int due(long tick){long relative=tick-firstTick;if(relative<0||relative>2L*(planned-1)||relative%2!=0)return -1;return (int)(relative/2);}
 public void clicked(long tick){actual++;lastClick=tick;lastResponse=-1;endpoint=null;landing=null;stableStart=null;stable=0;}
 public void response(long tick){response(tick,true);}
 public void response(long tick,boolean identifiable){previous=null;packets++;if(identifiable)updates++;if(lastClick>=0&&tick>=lastClick){lastResponse=tick;stable=0;stableStart=null;endpoint=null;landing=null;}}
 public void observe(Sample s){
  if(lastResponse>=lastClick&&lastClick>=0&&s.tick()>lastResponse&&s.tick()>firstTick+2L*(planned-1)){
   if(previous!=null&&s.tick()-previous.tick()==1&&Math.hypot(s.x()-previous.x(),s.z()-previous.z())<=.01){if(stableStart==null)stableStart=previous;if(++stable>=2&&endpoint==null)endpoint=stableStart;}
   else {stable=0;stableStart=null;endpoint=null;landing=null;}
   if(endpoint!=null&&s.ground()&&landing==null)landing=s;
  }previous=s;
 }
 public boolean timedOut(long tick){return tick-firstTick>=200;}
 public boolean complete(){return landing!=null;}
}
