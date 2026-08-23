package com.kuangsanfeng.ultimategarden.client.macro.timer;

import com.kuangsanfeng.ultimategarden.client.macro.hint.MacroHint;

/**
 * 125 second pest loadout timer.
 */
public final class PestLoadoutTimer {

    private static final int DELAY_TICKS = 125 * 20;

    private int ticks;
    private boolean running;
    private boolean finished;

    public void start() {
        if (running) {
            return;
        }

        MacroHint.printHint("PestLoadoutTimerStart");
        ticks = DELAY_TICKS;
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

        MacroHint.printHint("PestLoadoutTimerReset");
        ticks = 0;
        running = false;
        finished = false;
    }
}