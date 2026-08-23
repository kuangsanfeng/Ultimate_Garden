package com.kuangsanfeng.ultimategarden.client.macro.command;

import java.util.ArrayDeque;
import java.util.Queue;


// =========================================================
// Command Scheduler
// =========================================================

public final class CommandScheduler {

    private final Queue<ScheduledAction> queue =
            new ArrayDeque<>();

    private ScheduledAction current;

    private long remainingTicks;


    public boolean isRunning() {

        return current != null ||
                !queue.isEmpty();

    }


    public void tick() {

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