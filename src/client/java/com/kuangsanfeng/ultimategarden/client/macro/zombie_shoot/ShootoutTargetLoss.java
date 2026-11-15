package com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot;
/** Monotonic continuous-loss timer, owned by one target lock. */
public final class ShootoutTargetLoss {
 private long since;private boolean lost;
 public void clear(){lost=false;since=0;}
 public boolean expired(boolean valid,long now){if(valid){clear();return false;}if(!lost){lost=true;since=now;}return now-since>=100_000_000L;}
}
