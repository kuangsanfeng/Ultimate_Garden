package com.kuangsanfeng.ultimategarden.client.macro.farm.pest;

import com.kuangsanfeng.ultimategarden.client.macro.farm.hint.FarmHints;

/**
 * 125 second pest loadout timer.
 */
public final class PestLoadoutTimer {

    // Duration is captured when a new countdown starts.

    private int ticks;
    private boolean running;
    private boolean finished;

    public void start(int delayTicks) {
        if (delayTicks < 1) throw new IllegalArgumentException("Invalid pest delay");
        if (running) {
            return;
        }

        FarmHints.printHint("PestLoadoutTimerStart");
        ticks = delayTicks;
        running = true;
        finished = false;
    }

    public void tick() {
        if (!running) {
            return;
        }

        ticks--;

        if (ticks <= 0) {
            ticks = 0;
            running = false;
            finished = true;
        }
    }

    public int getRemainingTicks() { return ticks; }

    public boolean isRunning() {
        return running;
    }

    public boolean isFinished() {
        return finished;
    }

    public void clearFinished() {
        finished = false;
    }

    public void cancel() {
        if (!running) {
            return;
        }

        FarmHints.printHint("PestLoadoutTimerReset");
        ticks = 0;
        running = false;
        finished = false;
    }
}