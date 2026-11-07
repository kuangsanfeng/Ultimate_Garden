package com.kuangsanfeng.ultimategarden.client.macro.farm;

import com.kuangsanfeng.ultimategarden.client.input.VirtualInput;
import com.kuangsanfeng.ultimategarden.client.window.WindowsFocusController;
import com.kuangsanfeng.ultimategarden.client.window.WindowsInputInterceptor;
import com.kuangsanfeng.ultimategarden.client.macro.farm.action.FarmCommandController;
import com.kuangsanfeng.ultimategarden.client.macro.farm.crop.CropType;
import com.kuangsanfeng.ultimategarden.client.macro.farm.route.RouteController;
import com.kuangsanfeng.ultimategarden.client.macro.MacroManager;
import com.kuangsanfeng.ultimategarden.client.macro.farm.hint.FarmHints;
import net.minecraft.client.Minecraft;
import com.kuangsanfeng.ultimategarden.client.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.config.FarmConfig;
import net.minecraft.client.gui.screens.ChatScreen;


// =========================================================
// Farm Controller
// =========================================================

public final class FarmController implements MacroManager.Module {

    private final FarmState state;
    private final Minecraft client;
    private final com.kuangsanfeng.ultimategarden.client.macro.GuiPauseState menuPause = new com.kuangsanfeng.ultimategarden.client.macro.GuiPauseState();
    public enum MenuAction { PREVIOUS_LINE, NEXT_LINE, SET_SPAWN, WARP_GARDEN, LOADOUT }
    private final java.util.Deque<MenuAction> menuActions = new java.util.ArrayDeque<>();
    private VirtualInput.HeldActions heldActions;
    private Object menuWorld;
    private Object lastWorld;
    private boolean actionsRestored;
    private String stopReason = "Stopped";


    private final FarmConfig config;
    private final ConfigManager configManager;

    private final VirtualInput input;

    private final FarmCommandController command;

    private final RouteController route;

    private final WindowsInputInterceptor windowsInputInterceptor;

    private final WindowsFocusController windowsFocusController;

    private boolean waitingCropSelect;
    private boolean waitingModeSelect;
    private boolean waitingSimulateCropSelect;

    private boolean isTimeTesterMode = false;


    public FarmController(
            Minecraft client,
            VirtualInput input,
            WindowsInputInterceptor windowsInputInterceptor,
            WindowsFocusController windowsFocusController,
            ConfigManager configManager

    ) {

        this.client = client;
        this.lastWorld = client.level;
        this.input = input;

        this.windowsInputInterceptor =
                windowsInputInterceptor;

        this.windowsFocusController =
                windowsFocusController;

        this.state = new FarmState();

        this.configManager = configManager;
        this.config = configManager.getFarmConfig();

        this.command =
                new FarmCommandController(
                        client,
                        input,
                        state,
                        this::pauseMacro,
                        this::startMacro,
                        windowsFocusController, configManager.getPestConfig()
                );

        this.route = new RouteController(
                state, input, command, windowsFocusController, this::resetMacro, configManager.getCropConfig()
        );


        route.setRouteAllowed(() -> !menuPause.isActive());
        isTimeTesterMode = config.getSelectedCropTypeId() == CropType.TIMETESTER.getValue();
        state.setCropType(isTimeTesterMode ? config.getSelectedSimulatedCropTypeId() : config.getSelectedCropTypeId());
        state.setModType(config.getSelectedFarmModeId());

    }


    public FarmState getState() {

        return state;
    }


    public FarmConfig getConfig() {

        return config;
    }


    public FarmCommandController getCommand() {

        return command;
    }


    // =========================================================
    // Macro Control
    // =========================================================

    public void startMacroByHotkey(){

        if (windowsFocusController != null) {
            windowsFocusController.releaseFocusWithHint();
        }

        startMacro();
    }
    public void startMacro() {
        if (menuPause.isActive()) return;
        if (state.getCropType() == 0 || !configManager.getCropConfig().get(state.getCropType()).isExecutable()) return;

        if (state.isRunning()) {
            return;
        }


        state.setRunning(true);
        stopReason = "Running";

        windowsInputInterceptor.enable();

        input.mouseDown();

        FarmHints.printHint("MacroStart");

        runCurrentMovement();
    }

    public void pauseMacroByHotkey(){

        if (windowsFocusController != null) {
            windowsFocusController.restoreFocusWithHint();
        }

        pauseMacro();
    }
    public void pauseMacro() {
        if (menuPause.isActive()) { menuPause.pause(false); heldActions = null; menuActions.clear(); }
        stopReason = "User paused";

        state.setRunning(false);

        windowsInputInterceptor.disable();

        stopKeyboardMouse();

        command.cancel();


        FarmHints.printHint("ProgramPause");

        if (isTimeTesterMode) {
            FarmHints.printHint("TimeTesterCount", state.getTick());
        }
    }


    public void resetMacroByHotkey(){

        if (windowsFocusController != null) {
            windowsFocusController.restoreFocusWithHint();
        }

        resetMacro();
    }
    public void resetMacro() {
        if (menuPause.isActive()) { menuPause.pause(true); heldActions = null; menuActions.clear(); }
        stopReason = "Reset";

        state.reset();

        windowsInputInterceptor.disable();

        stopKeyboardMouse();

        command.cancel();


        FarmHints.printHint("ProgramReset");
    }


    // =========================================================
    // Input Stop / Movement: route owns directional input.
    // =========================================================
    private void stopKeyboardMouse() {
        route.stopKeyboardMouse();
    }

    public void runCurrentMovement() {
        route.runCurrentMovement();
    }

    public void stopMovement() {
        route.stopMovement();
    }

    // =========================================================
    // Restore Action When Focus Change
    // =========================================================

    public void restoreInputState() {
        if (menuPause.isActive()) return;

        if (!state.isRunning()) {
            return;
        }


        input.mouseDown();

        runCurrentMovement();

    }

    // =========================================================
    // Tick: commands/timer must advance before route state, even while paused.
    // =========================================================
    public void tick() {
        if (lastWorld != null && lastWorld != client.level) abortForWorldChange();
        lastWorld = client.level;
        if (client.player == null || client.level == null) {
            if (menuPause.isActive() || state.isRunning() || command.hasPendingWork()) abortForWorldChange();
            return;
        }
        if (menuPause.isActive()) {
            tickMenuSession();
            return;
        }
        command.tick();
        route.tick(isTimeTesterMode);
    }

    public void beginMenuPause() {
        boolean reopening = menuPause.isActive();
        menuPause.open(state.isRunning() || command.isBusinessResumePending());
        menuWorld = client.level;
        state.setRunning(false);
        windowsInputInterceptor.disable();
        if (!reopening || actionsRestored || heldActions == null) heldActions = input.suspendForMenu();
        actionsRestored = false;
        command.getScheduler().setPaused(true);
        if (!reopening) command.clearActionFailure();
    }
    public void endMenuPause() {
        if (menuPause.isActive()) menuPause.close(configManager.getGeneralConfig().isAutoResumeFarmAfterGuiClose());
    }
    public boolean isMenuSessionActive() { return menuPause.isActive(); }
    public boolean isMenuOpen() { return menuPause.isOpen(); }
    public String getRunStatus() {
        if (menuPause.isActive()) {
            if (menuPause.hasFailed()) return "Paused: action failed";
            if (menuPause.getIntent() == com.kuangsanfeng.ultimategarden.client.macro.GuiPauseState.Intent.START) return "Start requested (after menu)";
            if (menuPause.getIntent() == com.kuangsanfeng.ultimategarden.client.macro.GuiPauseState.Intent.PAUSE) return "User paused";
            if (menuPause.getIntent() == com.kuangsanfeng.ultimategarden.client.macro.GuiPauseState.Intent.RESET) return "Reset";
            return menuPause.isOpen() ? menuPause.wasRunningAtOpen() ? "Temporarily paused for menu" : "Menu open - " + stopReason : "Waiting for business actions";
        }
        return state.isRunning() ? "Running" : command.isBusinessResumePending() ? "Paused for business action" : stopReason;
    }
    public boolean canEditRouteConfiguration() {
        return menuPause.canChangeRoute() && !command.hasPendingWork() && !command.isPestLoadoutTimerRunning() && menuActions.isEmpty();
    }
    public void requestMenuStart() {
        if (!menuPause.isOpen()) throw new IllegalStateException("Open the menu before requesting a deferred start.");
        if (state.getCropType() == 0 || !configManager.getCropConfig().get(state.getCropType()).isExecutable()) throw new IllegalStateException("Select an implemented, enabled crop first.");
        command.clearActionFailure();
        menuPause.start();
    }
    public void queueMenuAction(MenuAction action) {
        if (!menuPause.isOpen() || state.getCropType() == 0 || !menuActions.isEmpty()) throw new IllegalStateException("Select a crop; only one menu action can be queued at a time.");
        menuActions.add(action);
    }
    public int getQueuedMenuActions() { return menuActions.size(); }
    public void applySavedSelection() {
        if (!canEditRouteConfiguration()) throw new IllegalStateException("Pause or Reset first; pending actions must be finished or cancelled.");
        isTimeTesterMode = config.getSelectedCropTypeId() == CropType.TIMETESTER.getValue();
        state.setCropType(isTimeTesterMode ? config.getSelectedSimulatedCropTypeId() : config.getSelectedCropTypeId());
        state.setModType(config.getSelectedFarmModeId());
        state.resetRoute();
        waitingCropSelect = false; waitingModeSelect = false; waitingSimulateCropSelect = false;
    }
    private void tickMenuSession() {
        if (client.level != menuWorld) { abortForWorldChange(); return; }
        boolean ownedBusinessScreen = actionsRestored && command.isRunning() && command.isExpectingLoadoutScreen()
                && client.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
        boolean held = menuPause.isOpen() || (client.screen != null && !ownedBusinessScreen) || client.getOverlay() != null;
        if (!actionsRestored && org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(client.getWindow().handle(), org.lwjgl.glfw.GLFW.GLFW_FOCUSED) == 0) held = true;
        if (held && actionsRestored) {
            heldActions = input.suspendForMenu();
            actionsRestored = false;
            windowsInputInterceptor.disable();
        }
        if (!held && !actionsRestored) {
            held = !windowsFocusController.restoreGameMouseAfterMenu();
            if (!held) {
                if (command.hasPendingWork()) windowsInputInterceptor.enable();
                input.resumeAfterMenu(heldActions, command.isRunning());
                heldActions = null;
                actionsRestored = true;
            }
        }
        try { command.tick(held || menuPause.hasFailed(), true); }
        catch (RuntimeException e) {
            com.kuangsanfeng.ultimategarden.Ultimate_Garden.LOGGER.error("Menu action failed; farming remains paused", e);
            command.cancel(); menuPause.fail(); menuActions.clear();
        }
        if (command.hasActionFailed() || menuPause.hasFailed()) {
            menuPause.fail(); menuActions.clear(); command.discardDeferredActions();
            input.releaseAll(); heldActions = null;
        }
        if (held || command.hasPendingWork()) return;
        if (!menuActions.isEmpty()) {
            switch (menuActions.remove()) {
                case PREVIOUS_LINE -> previousLine();
                case NEXT_LINE -> nextLine();
                case SET_SPAWN -> spawn();
                case WARP_GARDEN -> warpGarden();
                case LOADOUT -> loadout();
            }
            return;
        }
        if (client.screen != null) return;
        input.releaseAll();
        boolean resume = menuPause.finish();
        if (menuPause.hasFailed()) stopReason = "Paused: action failed";
        else if (menuPause.getIntent() == com.kuangsanfeng.ultimategarden.client.macro.GuiPauseState.Intent.PAUSE) stopReason = "User paused";
        else if (menuPause.getIntent() == com.kuangsanfeng.ultimategarden.client.macro.GuiPauseState.Intent.RESET) stopReason = "Reset";
        else stopReason = "Paused after menu";
        if (resume) startMacro();
        else windowsInputInterceptor.disable();
    }
    public void abortForWorldChange() {
        input.resumeAfterMenu(null, false); input.releaseAll();
        command.abortWorld(); menuActions.clear(); heldActions = null;
        menuPause.abort(); state.reset(); windowsInputInterceptor.disable();
        waitingCropSelect = false; waitingModeSelect = false; waitingSimulateCropSelect = false;
        isTimeTesterMode = config.getSelectedCropTypeId() == CropType.TIMETESTER.getValue();
        state.setCropType(isTimeTesterMode ? config.getSelectedSimulatedCropTypeId() : config.getSelectedCropTypeId());
        state.setModType(config.getSelectedFarmModeId());
        stopReason = "Stopped: game context changed";
    }

    public void nextLine() {
        route.nextLine();
    }

    public void previousLine() {
        route.previousLine();
    }

    // =========================================================
    // Initialize Crop and Mode
    // =========================================================

    public void changeCropType() {

        if (state.isRunning()) {
            return;
        }

        state.setCropType(0);
        state.setModType(0);

        waitingSimulateCropSelect = false;
        waitingCropSelect = true;
        waitingModeSelect = false;


        FarmHints.printHint("CropSelect");

        Minecraft.getInstance().setScreen(
                new ChatScreen("", false)
        );
    }

    private void saveSelection() {
        config.setSelectedCropType(FarmConfig.cropNameFromId(isTimeTesterMode ? CropType.TIMETESTER.getValue() : state.getCropType()));
        config.setSelectedFarmMode(FarmConfig.farmModeFromId(state.getModType()));
        if (isTimeTesterMode) config.setSelectedSimulatedCropType(FarmConfig.cropNameFromId(state.getCropType()));
        configManager.saveFarmConfig();
    }

    public boolean handleInitializeInput(
            String message
    ) {

        // 1. 选择作物阶段
        if (waitingCropSelect) {
            try {
                int value =
                        Integer.parseInt(message);
                CropType crop =
                        CropType.fromValue(value);
                if (crop.isCustom()) {
                    com.kuangsanfeng.ultimategarden.client.hint.HintManager.printRequired("Not implemented / Coming soon");
                    return true;
                }
                if (!configManager.getCropConfig().getCrop(crop).isExecutable()) return true;
                state.setCropType(
                        crop.getValue()
                );


                if (crop == CropType.TIMETESTER) {
                    this.isTimeTesterMode = true;
                } else {
                    this.isTimeTesterMode = false;
                }

                state.resetTick();
                state.setEventCount(0);
                state.setEventSelect(true);
                state.setEventChange(true);

                waitingCropSelect = false;
                waitingModeSelect = true;

                //MacroHint.printHint("CropInfo", crop);
                FarmHints.printHint("ModeSelect");

                Minecraft.getInstance()
                        .execute(() -> {

                            Minecraft.getInstance()
                                    .setScreen(
                                            new ChatScreen("", false)
                                    );

                        });

                return true;
            }
            catch (Exception e) {
                return true;
            }
        }


        // 2. 选择模式阶段（Farm = 0, Pests = 1）
        if (waitingModeSelect) {
            try {
                int value =
                        Integer.parseInt(message);
                CropType crop =
                        CropType.fromValue(state.getCropType());

                // 模式输入不匹配则不处理
                if (value != 0 && value != 1) {

                    return true;
                }

                state.setModType(
                        value
                );


                waitingModeSelect = false;

                if (isTimeTesterMode) {
                    waitingSimulateCropSelect = true;
                    FarmHints.printHint("SimulateCropSelect");

                    Minecraft.getInstance()
                            .execute(() -> {

                                Minecraft.getInstance()
                                        .setScreen(
                                                new ChatScreen("", false)
                                        );

                            });
                    return true;
                }

                saveSelection();
                FarmHints.printHint("InitializeDone");
                FarmHints.printHint("CropInfo", crop);

                return true;
            }
            catch (Exception e) {
                return true;
            }
        }



        if (waitingSimulateCropSelect) {
            try {
                int value =
                        Integer.parseInt(message);
                CropType crop =
                        CropType.fromValue(value);
                if (crop.isCustom()) {
                    com.kuangsanfeng.ultimategarden.client.hint.HintManager.printRequired("Not implemented / Coming soon");
                    return true;
                }
                if (!configManager.getCropConfig().getCrop(crop).isExecutable()) return true;

                if (crop != CropType.TIMETESTER && !crop.isCustom()) {
                    state.setCropType(
                            crop.getValue()
                    );

                    waitingSimulateCropSelect = false;

                    saveSelection();
                    FarmHints.printHint("InitializeDone");
                    FarmHints.printHint("CropInfo", crop);
                    return true;
                }
                return true;
            }
            catch (Exception e) {
                return true;
            }
        }

        return false;
    }


    // =========================================================
    // Spawn
    // =========================================================

    public void spawnByHotkey(){

        spawn();
    }
    public void spawn() {

        if (state.isRunning()) {
            pauseMacro();
        }


        // 使用 command 的调度器延迟 200ms 后再执行后续 spawn 动作
        command.getScheduler().schedule(
                200,
                () -> {
                    executeSpawnSequence();
                }
        );
    }


    private void executeSpawnSequence() {
        // Mushroom movement precedes the spawn sequence.
        if (state.getCropType() == CropType.MUSHROOM.getValue()) {
            // 先执行蘑菇位移，位移彻底完成后，通过方法引用交给下一步
            command.spawnMushroomMovement(
                    state.getEventCount() / 2 != 0,
                    this::executeSpawnPointAndPests
            );
        } else {
            // 不是蘑菇则直接跳过位移，进入下一步
            executeSpawnPointAndPests();
        }
    }

    private void executeSpawnPointAndPests() {
        // 步骤 2：执行 spawnlocation (spawnPoint)
        command.spawnPoint(
                () -> {
                    // 步骤 3：当 spawnlocation 完成后，如果开启了害虫模式，才严格串行执行 spawnPests
                    if (windowsFocusController != null) {
                        windowsFocusController.restoreFocusWithHint();
                    }

                    if (state.isPestsMode()) {
                        command.spawnPests(null);
                    }
                }
        );
    }


    // =========================================================
    // Loadout
    // =========================================================

    public void loadout() {

        if (state.isRunning()) {
            pauseMacro();
        }


        command.loadout(null);
    }


    // =========================================================
    // Warp Garden
    // =========================================================

    public void warpGarden() {

        stopKeyboardMouse();

        input.slot(1);

        command.warpGarden(
                () -> {

                    // mode1: warp then select slot1 and continue macro
                    if (state.isPestsMode()) {

                        if (windowsFocusController != null) {
                            windowsFocusController.releaseFocusWithHint();;
                        }

                        startMacro();
                    }
                }
        );
    }


    // =========================================================
    // Monitor Message to Automate Task
    // =========================================================

    public boolean automateTaskByMonitorMessage(String message) {
        if (!configManager.getPestConfig().isAutoPestLoadout()) return false;
        String cleanMessage = message.replaceAll("§[0-9a-fk-or]", "");
        //System.out.println("[Debug Chat Monitor] " + cleanMessage);

        if (message != null && cleanMessage.contains("Pest have spawned in Plot")) {
            // 检测到害虫生成提示，启动 pestLoadout 计时
            command.getScheduler().schedule(
                    100,
                    () -> {
                        if (configManager.getPestConfig().isAutoPestLoadout()) command.startPestLoadoutTimer();
                    }
            );

            return true;
        }
        return false;
    }


    private void restartAfterCommand(
            boolean wasRunning
    ) {

        if (!wasRunning) {
            return;
        }


        startMacro();
    }
}
