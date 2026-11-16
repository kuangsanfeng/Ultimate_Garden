package com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim;
import java.util.function.DoubleSupplier;
/** Hysteresis only. The random value never changes the aim direction. */
public final class ShootoutAimCorrection {
 private boolean active;private double threshold;private long started;
 public boolean active(){return active;}public double threshold(){return threshold;}
 public void clear(){active=false;threshold=0;started=0;}
 public boolean ready(Double error,double outer,double ratio,long now,DoubleSupplier random){
  if(error==null||!Double.isFinite(error)||!Double.isFinite(outer)||!Double.isFinite(ratio)||outer<0||ratio<0||ratio>.5)return false;
  if(!active&&error>outer+1e-9){double u=random.getAsDouble();if(!Double.isFinite(u)||u<0||u>1)throw new IllegalArgumentException("Invalid correction draw");threshold=u*ratio*outer;started=now;active=true;}
  if(active){if(error<=threshold+1e-9){clear();return error<=outer+1e-9;}if(now-started>=10_000_000_000L)throw new IllegalStateException("AIM_CORRECTION_UNREACHABLE");return false;}
  return error<=outer+1e-9;
 }
}
