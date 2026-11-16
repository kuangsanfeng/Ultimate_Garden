package com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.shot;
import java.util.function.DoubleSupplier;
/** Monotonic deadline. Exactly one uniform jitter draw per submitted shot. */
public final class ShootoutShotClock {
 private long deadline;
 public void reset(){deadline=0;}
 public boolean ready(long now){return now>=deadline;}
 public double remainingTicks(long now){return Math.max(0,(deadline-now)/50_000_000.);}
 public void fired(long now,double base,double min,double max,DoubleSupplier uniform){if(!ready(now))throw new IllegalStateException("SHOT_COOLDOWN_ACTIVE");if(!Double.isFinite(base)||!Double.isFinite(min)||!Double.isFinite(max)||base<.5||min<0||max<min)throw new IllegalArgumentException("Invalid shot interval");double u=uniform.getAsDouble();if(!Double.isFinite(u)||u<0||u>1)throw new IllegalArgumentException("Invalid uniform draw");deadline=now+(long)((base+min+(max-min)*u)*1e9);}
}
