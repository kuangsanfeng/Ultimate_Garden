package com.kuangsanfeng.ultimategarden.client.function.path_finding.execution;

import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;

/** Session-local telemetry. Samples are discarded whenever position is corrected. */
final class MovementMeasurements {
    private double speed = 4.3, jump = 1, walked;
    private long walkingTicks;
    private Point jumpStart;
    private double peak;
    boolean initialSpeedTried, initialJumpTried;
    private double initialDistance;
    private int initialTicks;

    double speed() { return speed; }
    double jump() { return jump; }
    void initialWalk(Point before,Point after) {
        double distance=Math.hypot(after.x()-before.x(),after.z()-before.z());
        if(distance>=.02 && distance<=.8 && Math.abs(after.y()-before.y())<=.08){initialDistance+=distance;initialTicks++;}
    }
    void finishInitialWalk() {if(initialTicks>=4)speed=20*initialDistance/initialTicks;}

    void walk(Point before, Point after, double threshold) {
        double distance = Math.hypot(after.x()-before.x(),after.z()-before.z());
        if (distance < .02 || distance > .8 || Math.abs(after.y()-before.y())>.08) return;
        walked += distance;
        if (++walkingTicks >= 2 && walked >= threshold) {
            speed=20*walked/walkingTicks;
            walked=0;
            walkingTicks=0;
        }
    }

    void beginJump(Point feet) { jumpStart=feet;peak=0; }
    void observeJump(Point feet) { if (jumpStart!=null) peak=Math.max(peak,feet.y()-jumpStart.y()); }
    void finishJump() {
        if (jumpStart!=null && peak>=.4 && peak<=6) jump=peak;
        jumpStart=null;
    }
    void rejectJump() { jumpStart=null; }
}
