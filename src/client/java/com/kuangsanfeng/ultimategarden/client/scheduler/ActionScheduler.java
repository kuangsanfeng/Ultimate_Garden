package com.kuangsanfeng.ultimategarden.client.scheduler;

import java.util.ArrayDeque;
import java.util.Queue;


// =========================================================
// Action Scheduler
// =========================================================

public final class ActionScheduler {

    // Each owner controls tick() and clear(); this queue does not register events.
    // FarmCommandController owns the active farm queue; SetLineYP shares it.
    // Delay/countdown/FIFO behavior is intentionally unchanged.

    private final Queue<ScheduledAction> queue =
            new ArrayDeque<>();

    private ScheduledAction current;

    private long remainingTicks;
    private boolean paused;
    public void setPaused(boolean value) { paused = value; }
    public boolean isPaused() { return paused; }
    public long getRemainingTicks() { return remainingTicks; }


    public boolean isRunning() {

        return current != null ||
                !queue.isEmpty();

    }


    public void tick() {
        if (paused) return;

        if (current == null) {

            current = queue.poll();

            if (current == null) {
                return;
            }

            remainingTicks =
                    current.delayTicks;
        }


        if (remainingTicks > 0) {

            remainingTicks--;

            return;
        }


        Runnable action =
                current.action;

        current = null;


        if (action != null) {
            action.run();
        }
    }


    public void schedule(
            long delayMs,
            Runnable action
    ) {

        long ticks =
                Math.max(
                        0,
                        (delayMs + 49) / 50
                );

        queue.add(
                new ScheduledAction(
                        ticks,
                        action
                )
        );
    }


    public void clear() {

        queue.clear();

        current = null;

        remainingTicks = 0;
    }


    // =========================================================
    // Scheduled Action
    // =========================================================

    private static final class ScheduledAction {

        private final long delayTicks;

        private final Runnable action;


        private ScheduledAction(
                long delayTicks,
                Runnable action
        ) {

            this.delayTicks = delayTicks;

            this.action = action;
        }
    }
}
