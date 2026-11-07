package com.kuangsanfeng.ultimategarden.client.macro.farm.action;

import com.kuangsanfeng.ultimategarden.client.window.WindowsFocusController;
import com.kuangsanfeng.ultimategarden.client.input.VirtualInput;
import com.kuangsanfeng.ultimategarden.client.scheduler.ActionScheduler;
import com.kuangsanfeng.ultimategarden.client.macro.farm.FarmState;
import com.kuangsanfeng.ultimategarden.client.macro.farm.hint.FarmHints;
import com.kuangsanfeng.ultimategarden.client.macro.farm.pest.PestLoadoutTimer;
import net.minecraft.client.Minecraft;

import java.util.concurrent.ThreadLocalRandom;


// =========================================================
// Command Controller
// =========================================================

public final class FarmCommandController {

    private final Minecraft client;

    private final WindowsFocusController windowsFocusController;

    private final VirtualInput input;

    private final ActionScheduler scheduler;

    // Owned here, advanced after scheduler.tick(), and cancelled only explicitly.
    // Farm pause/reset intentionally clears actions without cancelling this timer.

    private final PestLoadoutTimer pestLoadoutTimer;
    private final com.kuangsanfeng.ultimategarden.client.config.PestConfig pestConfig;

    private Runnable pestLoadoutTimerFinished;
    private boolean pendingPestLoadout;
    private boolean deferredPestInFlight;
    private boolean actionFailed;
    private boolean expectingLoadoutScreen;
    private boolean businessResumePending;
    public boolean isExpectingLoadoutScreen() { return expectingLoadoutScreen; }
    public boolean isBusinessResumePending() { return businessResumePending; }
    public boolean isPestLoadoutPending() { return pendingPestLoadout || deferredPestInFlight; }
    public void discardDeferredActions() { pendingPestLoadout = false; deferredPestInFlight = false; businessResumePending = false; scheduler.clear(); }
    public int getPestLoadoutRemainingTicks() { return pestLoadoutTimer.getRemainingTicks(); }
    public boolean hasPendingWork() { return scheduler.isRunning() || pendingPestLoadout || deferredPestInFlight; }
    public boolean hasActionFailed() { return actionFailed; }
    public void clearActionFailure() { actionFailed = false; }
    private void failAction() { actionFailed = true; deferredPestInFlight = false; businessResumePending = false; }
    public void abortWorld() {
        scheduler.clear(); scheduler.setPaused(false);
        pestLoadoutTimer.cancel(); pendingPestLoadout = false; deferredPestInFlight = false; expectingLoadoutScreen = false; businessResumePending = false;
    }


    private final FarmState macroState;
    private final Runnable pauser;
    private final Runnable resumer;


    public FarmCommandController(
            Minecraft client,
            VirtualInput input,
            FarmState macroState,
            Runnable pauser,
            Runnable resumer,
            WindowsFocusController windowsFocusController,
            com.kuangsanfeng.ultimategarden.client.config.PestConfig pestConfig
    ) {

        this.client = client;
        this.pestConfig = pestConfig;
        this.windowsFocusController = windowsFocusController;

        this.input = input;

        this.macroState = macroState;

        this.pauser = pauser;

        this.resumer = resumer;

        this.scheduler =
                new ActionScheduler();

        this.pestLoadoutTimer =
                new PestLoadoutTimer();
    }


    public ActionScheduler getScheduler() {

        return scheduler;
    }


    public boolean isRunning() {

        return scheduler.isRunning();
    }


    public void tick() { tick(false, false); }

    public void tick(boolean holdActions, boolean menuSession) {
        scheduler.setPaused(holdActions);

        scheduler.tick();

        pestLoadoutTimer.tick();

        if (pestLoadoutTimer.isFinished()) {

            pestLoadoutTimer.clearFinished();
            if (menuSession) {
                pendingPestLoadout = true;
                FarmHints.printHint("PestLoadoutTimeUp");
            } else {

            FarmHints.printHint("PestLoadoutTimeUp");

            boolean wasRunning = macroState.isRunning();

            if (wasRunning && pauser != null) {
                pauser.run();
            }

            businessResumePending = wasRunning;
            pestLoadout(
                    () -> {
                        businessResumePending = false;
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
        if (menuSession && !holdActions && pendingPestLoadout && !scheduler.isRunning()) {
            pendingPestLoadout = false;
            deferredPestInFlight = true;
            pestLoadout(() -> {
                deferredPestInFlight = false;
                if (pestLoadoutTimerFinished != null) pestLoadoutTimerFinished.run();
            });
        }
        if (!scheduler.isRunning() && !deferredPestInFlight) expectingLoadoutScreen = false;
    }


    public void cancel() {
        businessResumePending = false;
        if (deferredPestInFlight) failAction();

        scheduler.clear();
    }


    public void startPestLoadoutTimer() {

        if (!macroState.isPestsMode()) {
            return;
        }

        pestLoadoutTimer.start(pestConfig.getPestLoadoutDelayTicks());

    }


    public void cancelPestLoadoutTimer() {
        if (pendingPestLoadout) failAction();
        pendingPestLoadout = false;

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


        input.actionKeyDown(direction);

        input.actionKeyDown("S");


        scheduler.schedule(
                random(150,150),
                () -> {

                    input.actionKeyUp(direction);

                    input.actionKeyUp("S");


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
                () -> input.actionKeyDown("W")
        );


        scheduler.schedule(
                random(500,150),
                () -> input.actionKeyUp("W")
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

        boolean currentlyReleased = windowsFocusController.isReleased();

        if (currentlyReleased) {
            windowsFocusController.releaseFocus();
        } else {
            windowsFocusController.restoreFocus();
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


        scheduler.schedule(
                random(50,50),
                () -> pestLoadoutStep2(0, after)
        );
    }

    private void pestLoadoutStep2(int counter, Runnable after) {
        // 如果检测次数超过 40 次(40 x 50ms == 2000ms)，直接退出，放弃本次动作
        if (counter >= 40) {
            failAction();
            return;
        }

        scheduler.schedule(
                50, // 每隔 50ms 检查一次
                () -> {
                    // 判断当前是否已经打开了界面 (client.screen 不为 null 代表界面已弹出)
                    if (client.screen != null) {
                        // 1. 执行 slot 4
                        scheduler.schedule(
                                random(150,200),
                                () -> input.slot(4)
                        );

                        scheduler.schedule(
                                random(100,50),
                                () -> pestLoadoutStep3(0, after)
                        );
                    } else {
                        // 如果还没打开界面，递归自己，继续等 50ms 检查
                        pestLoadoutStep2(counter + 1, after);
                    }
                }
        );
    }

    private void pestLoadoutStep3(int counter, Runnable after){
        // 如果检测次数超过 20 次(20 x 100ms == 2000ms)，直接退出，放弃本次动作
        if (counter >= 20) {
            failAction();
            return;
        }

        scheduler.schedule(
                100, // 每隔 100ms 检查一次
                () -> {
                    // 判断当前是否已经关闭了界面 (client.screen 为 null 代表界面已关闭)
                    if (client.screen == null) {

                        // 安排对焦状态保持
                        boolean currentlyReleased = windowsFocusController.isReleased();
                        if (currentlyReleased) {
                            windowsFocusController.releaseFocus();
                        } else {
                            windowsFocusController.restoreFocus();
                        }

                        // 最终回调
                        scheduler.schedule(
                                300,
                                after
                        );
                    } else {
                        // 如果还没打开界面
                        // 关闭界面逻辑
                        client.setScreen(null);

                        // 递归自己，继续等 100ms 检查
                        pestLoadoutStep3(counter + 1, after);
                    }
                }
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



        scheduler.schedule(
                random(50,50),
                () -> spawnPestsStep2(0, after)
        );
    }

    private void spawnPestsStep2(int counter, Runnable after) {
        // 如果检测次数超过 40 次(40 x 50ms == 2000ms)，直接退出，放弃本次动作
        if (counter >= 40) {
            failAction();
            return;
        }

        scheduler.schedule(
                50, // 每隔 50ms 检查一次
                () -> {
                    // 判断当前是否已经打开了界面 (client.screen 不为 null 代表界面已弹出)
                    if (client.screen != null) {
                        // 1. 执行 slot 5
                        scheduler.schedule(
                                random(150,200),
                                () -> input.slot(5)
                        );

                        scheduler.schedule(
                                random(100,50),
                                () -> spawnPestsStep3(0, after)
                        );
                    } else {
                        // 如果还没打开界面，递归自己，继续等 50ms 检查
                        spawnPestsStep2(counter + 1, after);
                    }
                }
        );
    }

    private void spawnPestsStep3(int counter, Runnable after) {
        // 如果检测次数超过 20 次(20 x 100ms == 2000ms)，直接退出，放弃本次动作
        if (counter >= 20) {
            failAction();
            return;
        }

        scheduler.schedule(
                100, // 每隔 100ms 检查一次
                () -> {
                    // 判断当前是否已经关闭了界面 (client.screen 为 null 代表界面已关闭)
                    if (client.screen == null) {
                        // 安排对焦状态保持
                        boolean currentlyReleased = windowsFocusController.isReleased();
                        if (currentlyReleased) {
                            windowsFocusController.releaseFocus();
                        } else {
                            windowsFocusController.restoreFocus();
                        }

                        spawnPestsStep4(after);
                    } else {
                        // 如果还没打开界面
                        // 关闭界面逻辑
                        client.setScreen(null);

                        // 递归自己，继续等 100ms 检查
                        spawnPestsStep3(counter + 1, after);
                    }
                }
        );
    }

    private void spawnPestsStep4(Runnable after) {
        // 安排按下 E 键
        scheduler.schedule(
                random(150, 250),
                () -> input.keyClick("E")
        );

        // 最终回调
        scheduler.schedule(
                300,
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


        if (command.equals("loadout")) expectingLoadoutScreen = true;
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
