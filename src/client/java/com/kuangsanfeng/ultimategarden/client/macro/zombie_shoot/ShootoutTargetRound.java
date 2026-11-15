package com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot;

/** Per-entity attack-round bookkeeping, independent of equipment and age. */
public final class ShootoutTargetRound {
 public record Attempt(long round,long sequence,int retry){}
 private long round,sequence;private boolean handled,retry,retried;private Attempt pending;
 public boolean handled(){return handled;}
 public boolean retry(){return retry;}
 public boolean regularEligible(){return !handled||retry;}
 public boolean relockEligible(){return handled&&!retry&&pending==null;}
 public Attempt dispatched(){handled=true;retry=false;pending=new Attempt(round,++sequence,retried?1:0);return pending;}
 /** Only a matching, still-pending result may queue this round's single retry. */
 public boolean verify(Attempt attempt,boolean sameIdentity,boolean observedAlive){
  if(pending==null||!pending.equals(attempt))return false;
  pending=null;
  if(ShootoutPolicy.retryAllowed(attempt.retry(),retried,sameIdentity,observedAlive)){retry=true;retried=true;return true;}
  return false;
 }
 public void beginRelock(){
  if(!relockEligible())throw new IllegalStateException("Target round not ready for relock");
  round++;handled=false;retry=false;retried=false;
 }
}
