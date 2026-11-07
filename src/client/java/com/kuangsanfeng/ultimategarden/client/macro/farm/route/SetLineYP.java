package com.kuangsanfeng.ultimategarden.client.macro.farm.route;

import com.kuangsanfeng.ultimategarden.client.input.VirtualInput;
import com.kuangsanfeng.ultimategarden.client.scheduler.ActionScheduler;

public final class SetLineYP {

    private final VirtualInput input;
    private final ActionScheduler scheduler;


    public SetLineYP(
            VirtualInput input,
            ActionScheduler scheduler
    ) {

        this.input = input;

        this.scheduler = scheduler;
    }


    // =========================================================
    // Normal Crop
    // key 3
    // =========================================================

    public void setNormal(boolean running) {

        setView(
                "3",
                running
        );
    }


    // =========================================================
    // SugarCane / Mushroom
    //
    //key 3
    //key 4
    // =========================================================

    public void setAlternate(
            int eventCount,
            boolean running
    ) {

        String key =
                (eventCount % 2 == 0)
                        ? "3"
                        : "4";


        setView(
                key,
                running
        );
    }


    // =========================================================
    // YawPitch
    // =========================================================

    private void setView(
            String key,
            boolean running
    ) {

        int slotNum = Integer.parseInt(key);
        input.slot(slotNum);

        scheduler.schedule(
                random(100,150),
                () -> {
                    input.actionMouseDown();
                }
        );

        scheduler.schedule(
                random(100,150),
                () -> {
                    input.actionMouseUp();
                }
        );

        scheduler.schedule(
                random(100,150),
                () -> {
                    input.slot(1);
                }
        );

        scheduler.schedule(
                random(100,150),
                () -> {
                    input.actionMouseDown();
                }
        );
    }



    private int random(
            int base,
            int range
    ) {

        if (range <= 0) {

            return base;

        }


        return base +
                (int)(Math.random() * range);
    }
}