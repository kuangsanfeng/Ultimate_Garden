package com.kuangsanfeng.ultimategarden.client.scheduler;


/**
 * Minecraft tick scheduler.
 *
 * 20 TPS
 * 1 tick = 50ms
 *
 * Replaces short delays from the original C++.
 */
public class InputScheduler {


    public static final long TICK_MS = 50L;


    private int remainingTicks = 0;



    // =========================================================
    // Delay
    // =========================================================


    /**
     * Delay by milliseconds.
     *
     * Milliseconds are rounded up to complete ticks.
     *
     * 1ms  -> 1 tick
     * 50ms -> 1 tick
     * 51ms -> 2 ticks
     */
    public void delayMillis(
            long milliseconds
    ) {

        if (milliseconds <= 0) {

            return;

        }


        int ticks =
                (int) Math.ceil(
                        (double) milliseconds
                                / TICK_MS
                );


        delayTicks(ticks);

    }



    /**
     * Delay by ticks.
     */
    public void delayTicks(
            int ticks
    ) {

        if (ticks <= 0) {

            return;

        }


        remainingTicks =
                Math.max(
                        remainingTicks,
                        ticks
                );

    }



    // =========================================================
    // Tick
    // =========================================================


    /**
     * Update once per Minecraft tick.
     *
     * Returns true while the scheduler is still waiting.
     */
    public boolean tick() {

        if (remainingTicks <= 0) {

            return false;

        }


        remainingTicks--;


        return remainingTicks > 0;

    }



    public boolean isWaiting() {

        return remainingTicks > 0;

    }



    public int getRemainingTicks() {

        return remainingTicks;

    }



    public void clear() {

        remainingTicks = 0;

    }

}