package com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot;
public final class ZombieShootoutController {
 public static final ZombieShootoutController INSTANCE=new ZombieShootoutController();public enum State{STOPPED,AIMING}
 public record HudSnapshot(boolean running,State state,String classification,String tag,Double measuredSpeed,Double predictionSpeed,Double targetYaw,Double targetPitch,Double aimError,boolean aimReady,double tolerance,Double correctionThreshold,String aimMode,double cooldownSeconds,double remainingSeconds,boolean confirmationEnabled,String confirmationState,String attemptState,Double ping,Double latencyTicks){}
 public HudSnapshot hudSnapshot(){return new HudSnapshot(true,State.AIMING,"LEATHER_ADULT","z1",.1,.12,10.,20.,1.,true,2.,.5,"TANH",.55,.1,true,"WAITING","IDLE",100.,2.);}
 public boolean enabled(){return true;}public String pingStatus(){return "AVAILABLE";}
}
