package com.kuangsanfeng.ultimategarden.client.macro.farm.action;

import com.kuangsanfeng.ultimategarden.client.window.WindowsFocusController;
import com.kuangsanfeng.ultimategarden.client.input.VirtualInput;
import com.kuangsanfeng.ultimategarden.client.scheduler.ActionScheduler;
import com.kuangsanfeng.ultimategarden.client.macro.farm.FarmState;
import com.kuangsanfeng.ultimategarden.client.macro.farm.hint.FarmHints;
import com.kuangsanfeng.ultimategarden.client.macro.farm.pest.PestLoadoutTimer;
import net.minecraft.client.Minecraft;

import com.kuangsanfeng.ultimategarden.client.config.RandomDelayConfig;
import static com.kuangsanfeng.ultimategarden.client.config.RandomDelayConfig.Key.*;


// =========================================================
// Command Controller
// =========================================================

public final class FarmCommandController {
    public enum Workflow { IDLE, SPAWN, WARP, LOADOUT, PEST_LOADOUT, SPAWN_LOADOUT, LINE_CHANGE, OTHER }
    private Workflow workflow = Workflow.IDLE;
    public Workflow getWorkflow() {
        if (workflow != Workflow.IDLE) return workflow;
        if (macroState.getLineChangePhase() != FarmState.LineChangePhase.IDLE) return Workflow.LINE_CHANGE;
        if (pendingPestLoadout || deferredPestInFlight) return Workflow.PEST_LOADOUT;
        return hasPendingWork() ? Workflow.OTHER : Workflow.IDLE;
    }
    public void markSpawnScheduled() { if (!hasPendingWork()) workflow = Workflow.SPAWN; }
    private Runnable trackWorkflow(Workflow value, Runnable after) {
        workflow = value;
        return () -> {
            if (workflow == value) workflow = Workflow.IDLE;
            if (after != null) after.run();
        };
    }


    private RandomDelayConfig randomDelays = new RandomDelayConfig();
    public RandomDelayConfig getRandomDelays() { return randomDelays; }
    public void setRandomDelays(RandomDelayConfig config) { randomDelays = java.util.Objects.requireNonNull(config); }
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
    public void discardDeferredActions() { workflow = Workflow.IDLE; pendingPestLoadout = false; deferredPestInFlight = false; businessResumePending = false; macroState.setCommandRunning(false); scheduler.clear(); }
    public int getPestLoadoutRemainingTicks() { return pestLoadoutTimer.getRemainingTicks(); }
    public boolean hasPendingWork() { return scheduler.isRunning() || pendingPestLoadout || deferredPestInFlight; }
    public boolean hasActionFailed() { return actionFailed; }
    public void clearActionFailure() { actionFailed = false; }
    private void failAction() { workflow = Workflow.IDLE; actionFailed = true; deferredPestInFlight = false; businessResumePending = false; }
    public void abortWorld() {
        workflow = Workflow.IDLE;
        macroState.setCommandRunning(false);
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
        if (!hasPendingWork()) workflow = Workflow.IDLE;
    }


    public void cancel() {
        workflow = Workflow.IDLE;
        macroState.setCommandRunning(false);
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

        Runnable completed = trackWorkflow(Workflow.WARP, after);


        sendCommand("warp garden");


        scheduler.scheduleDelayTicks(
                randomDelays.sample(WARP),
                completed
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

        Runnable completed = trackWorkflow(Workflow.SPAWN, after);


        sendCommand("spawnlocation");


        scheduler.scheduleDelayTicks(
                randomDelays.sample(SPAWN_POINT),
                completed
        );
    }


    public void spawnMushroomMovement(
            boolean moveRight,
            Runnable after
    ) {

        if (isRunning()) {
            return;
        }

        Runnable completed = trackWorkflow(Workflow.SPAWN, after);


        String direction =
                moveRight ? "D" : "A";


        input.actionKeyDown(direction);

        input.actionKeyDown("S");


        scheduler.scheduleDelayTicks(
                randomDelays.sample(SPAWN_MUSHROOM),
                () -> {

                    input.actionKeyUp(direction);

                    input.actionKeyUp("S");


                    completed.run();
                }
        );
    }


    // =========================================================
    // Cactus
    // =========================================================

    public void lineChangeMovement(int durationTicks, Runnable after) {

        if (durationTicks < 1) throw new IllegalArgumentException("Invalid movement duration");

        // Freeze row time until the movement and its completion wait finish.
        macroState.setCommandRunning(true);
        macroState.setLineChangePhase(FarmState.LineChangePhase.MOVEMENT);

        scheduler.schedule(
                100,
                () -> input.actionKeyDown("W")
        );


        scheduler.scheduleAfterTicks(
                durationTicks,
                () -> input.actionKeyUp("W")
        );


        scheduler.schedule(
                100,
                () -> {
                    macroState.setCommandRunning(false);
                    if (after != null) after.run();
                }
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

        Runnable completed = trackWorkflow(Workflow.LOADOUT, after);


        sendCommand("loadout");

        boolean currentlyReleased = windowsFocusController.isReleased();

        if (currentlyReleased) {
            windowsFocusController.releaseFocus();
        } else {
            windowsFocusController.restoreFocus();
        }


        scheduler.schedule(
                400,
                completed
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

        Runnable completed = trackWorkflow(Workflow.PEST_LOADOUT, after);


        scheduler.schedule(
                0,
                () -> sendCommand("loadout")
        );


        scheduler.scheduleDelayTicks(
                randomDelays.sample(PEST_OPEN_WAIT),
                () -> pestLoadoutStep2(0, completed)
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
                        scheduler.scheduleDelayTicks(
                                randomDelays.sample(PEST_SELECT),
                                () -> input.slot(4)
                        );

                        scheduler.scheduleDelayTicks(
                                randomDelays.sample(PEST_SETTLE),
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

        Runnable completed = trackWorkflow(Workflow.SPAWN_LOADOUT, after);


        scheduler.schedule(
                100,
                () -> input.slot(7)
        );


        scheduler.schedule(
                250,
                () -> sendCommand("loadout")
        );



        scheduler.scheduleDelayTicks(
                randomDelays.sample(SPAWN_LOADOUT_OPEN_WAIT),
                () -> spawnPestsStep2(0, completed)
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
                        scheduler.scheduleDelayTicks(
                                randomDelays.sample(SPAWN_LOADOUT_SELECT),
                                () -> input.slot(5)
                        );

                        scheduler.scheduleDelayTicks(
                                randomDelays.sample(SPAWN_LOADOUT_SETTLE),
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
        scheduler.scheduleDelayTicks(
                randomDelays.sample(SPAWN_INVENTORY_KEY),
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

}
