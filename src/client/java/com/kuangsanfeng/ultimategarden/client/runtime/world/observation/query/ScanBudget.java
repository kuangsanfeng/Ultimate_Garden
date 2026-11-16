package com.kuangsanfeng.ultimategarden.client.runtime.world.observation.query;
/** Caller begins each scan phase; no timer or world ownership is implicit. */
public final class ScanBudget {
 private int remaining;
 public void begin(int limit){if(limit<0)throw new IllegalArgumentException("budget");remaining=limit;}
 public boolean take(){if(remaining==0)return false;remaining--;return true;}
}
