package com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug;

/** RESET-only, single-use airborne gate. Time and player observations come from the client thread. */
public final class TransmissionResetJump {
 public enum Action {WAIT,CLICK,FAILED,IDLE}
 private boolean pending,airborne;
 private double startY;
 private long startTick,startNanos;
 public boolean begin(boolean onGround,double y,long tick,long nanos){
  clear();
  if(!onGround||!Double.isFinite(y))return false;
  startY=y;startTick=tick;startNanos=nanos;pending=true;return true;
 }
 public Action update(boolean onGround,double y,long tick,long nanos){
  if(!pending)return Action.IDLE;
  if(!Double.isFinite(y)||tick-startTick>=20||nanos-startNanos>=1_000_000_000L){pending=false;return Action.FAILED;}
  if(tick<=startTick)return Action.WAIT;
  if(!onGround){
   airborne=true;
   if(y-startY>=4.0){pending=false;return Action.CLICK;}
  }else if(airborne){pending=false;return Action.FAILED;}
  return Action.WAIT;
 }
 public Double takeoffY(){return pending?startY:null;}
 public Double relativeHeight(double currentY){return pending&&Double.isFinite(currentY)?currentY-startY:null;}
 public void clear(){pending=airborne=false;startY=0;startTick=startNanos=0;}
}
