package com.kuangsanfeng.ultimategarden.client.function.path_finding.model;

import com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.Terrain;

import static com.kuangsanfeng.ultimategarden.client.function.path_finding.model.FlightTiming.TICK_MS;

/** Shared by the NORMAL/WEIGHTED predictor and client dispatch. */
public final class RouteCastTiming {
    private RouteCastTiming() {}

    public static int castTick(FlightTiming.Ready ready,double aimMs,boolean correcting) {
        int plannedAimTick=aimTick(aimMs);
        int gateTick=(int)Math.ceil(Math.max(ready.cooldownMs(),ready.confirmationMs())/TICK_MS);
        return Math.max(ready.earliestTick(),Math.max(plannedAimTick+(correcting?0:1),gateTick));
    }

    public static int aimTick(double aimMs){
        return (int)Math.ceil(aimMs/TICK_MS-1e-9);
    }

    public enum ConfirmationStage { PACKET_APPLIED, CONFIRMATION_CONSUMED }

    public record Handoff(FlightTiming.Motion motion,ConfirmationStage inputStage,int physicsSteps) {}

    /**
     * The isolated and native drivers select the next leg at END_CLIENT_TICK.
     * The normal model assumes a packet applied before that tick's motion update.
     * A supported zero-velocity packet is already at rest for this predictor.
     */
    public static Handoff expectedHandoff(FlightTiming.Motion packet,Terrain terrain){
        int steps=packet.grounded()&&packet.vx()==0&&packet.vy()==0&&packet.vz()==0&&terrain.supported(packet.feet())?0:1;
        return handoff(packet,ConfirmationStage.PACKET_APPLIED,steps,terrain);
    }

    public static Handoff handoff(FlightTiming.Motion motion,ConfirmationStage stage,int interveningPhysics,Terrain terrain){
        int steps=stage==ConfirmationStage.PACKET_APPLIED?interveningPhysics:0;
        return new Handoff(steps==0?motion:FlightTiming.predict(motion,steps,terrain),stage,steps);
    }

    public static String aimScheduleBlocker(boolean scheduled,long tick,long started,int expected,boolean aligned){
        if(!aligned)return "AIM_NOT_READY";
        return scheduled&&tick-started<expected?"SCHEDULE_NOT_READY":"NONE";
    }

    public static boolean ready(long tick,long started,int plannedTick,boolean aligned) {
        return aligned&&tick-started>=plannedTick;
    }

    public static boolean validView(double yaw,double pitch) {
        return Double.isFinite(yaw)&&Double.isFinite(pitch)&&pitch>=-90&&pitch<=90;
    }

    public static FlightTiming.AimTime estimate(double yaw,double pitch,double targetYaw,double targetPitch,
                                                 FlightTiming.Aim aim) {
        return validView(yaw,pitch)&&validView(targetYaw,targetPitch)?
                FlightTiming.estimate(yaw,pitch,targetYaw,targetPitch,aim):null;
    }
}
