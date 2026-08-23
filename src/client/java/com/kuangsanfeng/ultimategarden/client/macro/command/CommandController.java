package com.kuangsanfeng.ultimategarden.client.macro.command;

import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import com.kuangsanfeng.ultimategarden.client.input.VirtualInput;
import com.kuangsanfeng.ultimategarden.client.macro.MacroState;
import com.kuangsanfeng.ultimategarden.client.macro.hint.MacroHint;
import com.kuangsanfeng.ultimategarden.client.macro.timer.PestLoadoutTimer;
import net.minecraft.client.Minecraft;

import java.util.concurrent.ThreadLocalRandom;


// =========================================================
// Command Controller
// =========================================================

public final class CommandController {

    private final Minecraft client;

    private final VirtualInput input;

    private final CommandScheduler scheduler;

    private final PestLoadoutTimer pestLoadoutTimer;

    private Runnable pestLoadoutTimerFinished;

    private final MacroState macroState;
    private final Runnable pauser;
    private final Runnable resumer;


    public CommandController(
            Minecraft client,
            VirtualInput input,
            MacroState macroState,
            Runnable pauser,
            Runnable resumer
    ) {

        this.client = client;

        this.input = input;

        this.macroState = macroState;

        this.pauser = pauser;

        this.resumer = resumer;

        this.scheduler =
                new CommandScheduler();

        this.pestLoadoutTimer =
                new PestLoadoutTimer();
    }


    public CommandScheduler getScheduler() {

        return scheduler;
    }


    public boolean isRunning() {

        return scheduler.isRunning();
    }


    public void tick() {

        scheduler.tick();

        pestLoadoutTimer.tick();

        if (pestLoadoutTimer.isFinished()) {

            pestLoadoutTimer.clearFinished();

            MacroHint.printHint("PestLoadoutTimeUp");

            boolean wasRunning = macroState.isRunning();

            if (wasRunning && pauser != null) {
                pauser.run();
            }

            pestLoadout(
                    () -> {
                        if (wasRunning && resumer != null) {
                            resumer.run();
                        }
                        if (pestLoadoutTimerFinished != null) {

                            pestLoadoutTimerFinished.run();

                        }

                    }
            );
        }
    }


    public void cancel() {

        scheduler.clear();
    }


    public void startPestLoadoutTimer() {

        pestLoadoutTimer.start();

    }


    public void cancelPestLoadoutTimer() {

        pestLoadoutTimer.cancel();

    }


    public boolean isPestLoadoutTimerRunning() {

        return pestLoadoutTimer.isRunning();
    }


    public void setPestLoadoutTimerFinished(
            Runnable callback
    ) {

        this.pestLoadoutTimerFinished = callback;

    }


    // =========================================================
    // Warp Garden
    // =========================================================

    public void warpGarden(
            Runnable after
    ) {

        if (isRunning()) {
            return;
        }


        sendCommand("warp garden");


        scheduler.schedule(
                random(800, 800),
                after
        );
    }


    // =========================================================
    // Spawn
    // =========================================================

    public void spawnPoint(
            Runnable after
    ) {

        if (isRunning()) {
            return;
        }


        sendCommand("spawnlocation");


        scheduler.schedule(
                random(300,150),
                after
        );
    }


    public void spawnMushroomMovement(
            boolean moveRight,
            Runnable after
    ) {

        if (isRunning()) {
            return;
        }


        String direction =
                moveRight ? "D" : "A";


        input.keyDown(direction);

        input.keyDown("S");


        scheduler.schedule(
                random(150,150),
                () -> {

                    input.keyUp(direction);

                    input.keyUp("S");


                    if (after != null) {
                        after.run();
                    }
                }
        );
    }


    // =========================================================
    // Cactus
    // =========================================================

    public void cactusLineChange(
            Runnable after
    ) {

        if (isRunning()) {
            return;
        }


        scheduler.schedule(
                100,
                () -> input.keyDown("W")
        );


        scheduler.schedule(
                random(500,150),
                () -> input.keyUp("W")
        );


        scheduler.schedule(
                100,
                after
        );
    }


    // =========================================================
    // Loadout
    // =========================================================

    public void loadout(
            Runnable after
    ) {

        if (isRunning()) {
            return;
        }


        sendCommand("loadout");

        if (Ultimate_GardenClient.getWindowsFocusController() != null) {
            var focusController = Ultimate_GardenClient.getWindowsFocusController();
            if (!focusController.isReleased()) {
                focusController.releaseFocus();
            }
            else{
                focusController.restoreFocus();
            }
        }


        scheduler.schedule(
                400,
                after
        );
    }


    // =========================================================
    // Pest Loadout
    // =========================================================

    public void pestLoadout(
            Runnable after
    ) {

        if (isRunning()) {
            return;
        }


        scheduler.schedule(
                0,
                () -> sendCommand("loadout")
        );

        if (Ultimate_GardenClient.getWindowsFocusController() != null) {
            var focusController = Ultimate_GardenClient.getWindowsFocusController();
            if (!focusController.isReleased()) {
                focusController.releaseFocus();
            }
            else{
                focusController.restoreFocus();
            }
        }


        scheduler.schedule(
                random(250,150),
                () -> input.slot(4)
        );


        scheduler.schedule(
                random(200,100),
                () -> {
                    if (client.screen != null) {
                        client.setScreen(null);
                    }
                }
        );


        scheduler.schedule(
                500,
                after
        );
    }


    // =========================================================
    // Pest Spawn
    // =========================================================

    public void spawnPests(
            Runnable after
    ) {

        if (isRunning()) {
            return;
        }


        scheduler.schedule(
                100,
                () -> input.slot(7)
        );


        scheduler.schedule(
                250,
                () -> sendCommand("loadout")
        );

        if (Ultimate_GardenClient.getWindowsFocusController() != null) {
            var focusController = Ultimate_GardenClient.getWindowsFocusController();
            if (!focusController.isReleased()) {
                focusController.releaseFocus();
            }
            else{
                focusController.restoreFocus();
            }
        }


        scheduler.schedule(
                random(250,150),
                () -> input.slot(5)
        );


        scheduler.schedule(
                random(200,100),
                () ->{
                    if (client.screen != null) {
                        client.setScreen(null);
                    }
                }
        );


        scheduler.schedule(
                random(150,100),
                () -> input.keyClick("E")
        );


        scheduler.schedule(
                100,
                after
        );
    }


    // =========================================================
    // Server Command
    // =========================================================

    private void sendCommand(
            String command
    ) {

        if (client.player == null) {
            return;
        }


        if (client.player.connection == null) {
            return;
        }


        client.player.connection.sendCommand(
                command
        );
    }


    // =========================================================
    // Random Delay
    // =========================================================

    private int random(
            int base,
            int range
    ) {

        if (range <= 0) {
            return base;
        }


        return base +
                ThreadLocalRandom
                        .current()
                        .nextInt(range);
    }
}