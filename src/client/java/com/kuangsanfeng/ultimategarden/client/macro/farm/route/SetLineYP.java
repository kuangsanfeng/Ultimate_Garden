package com.kuangsanfeng.ultimategarden.client.macro.farm.route;

import com.kuangsanfeng.ultimategarden.client.input.VirtualInput;
import com.kuangsanfeng.ultimategarden.client.scheduler.ActionScheduler;
import com.kuangsanfeng.ultimategarden.client.config.RandomDelayConfig;
import static com.kuangsanfeng.ultimategarden.client.config.RandomDelayConfig.Key.*;

public final class SetLineYP {

    private final java.util.function.Supplier<RandomDelayConfig> randomDelays;
    private final VirtualInput input;
    private final ActionScheduler scheduler;


    public SetLineYP(
            VirtualInput input,
            ActionScheduler scheduler, java.util.function.Supplier<RandomDelayConfig> randomDelays
    ) {

        this.input = input;
        this.randomDelays = randomDelays;

        this.scheduler = scheduler;
    }


    // =========================================================
    // Normal Crop
    // key 3
    // =========================================================

    public void setNormal(boolean running) {

        setView(3, null);
    }


    // =========================================================
    // SugarCane / Mushroom
    //
    //key 3
    //key 4
    // =========================================================

    public void setAlternate(int eventCount, int slot1, int slot2, Runnable after) {
        setView((eventCount % 2 == 0) ? slot1 : slot2, after);
    }

    // =========================================================
    // YawPitch
    // =========================================================

    private void setView(int slotNum, Runnable after) {
        input.slot(slotNum);

        scheduler.scheduleDelayTicks(
                randomDelays.get().sample(VIEW_ATTACK),
                () -> {
                    input.actionMouseDown();
                }
        );

        scheduler.scheduleDelayTicks(
                randomDelays.get().sample(VIEW_RELEASE),
                () -> {
                    input.actionMouseUp();
                }
        );

        scheduler.scheduleDelayTicks(
                randomDelays.get().sample(VIEW_TOOL_SLOT),
                () -> {
                    input.slot(1);
                }
        );

        scheduler.scheduleDelayTicks(
                randomDelays.get().sample(VIEW_RESUME_ATTACK),
                () -> {
                    input.actionMouseDown();
                    if (after != null) after.run();
                }
        );
    }



}
