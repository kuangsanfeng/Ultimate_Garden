package com.kuangsanfeng.ultimategarden.client.macro;

import com.kuangsanfeng.ultimategarden.client.input.VirtualInput;
import com.kuangsanfeng.ultimategarden.client.interaction.windows.WindowsFocusController;
import com.kuangsanfeng.ultimategarden.client.interaction.windows.WindowsInputInterceptor;
import com.kuangsanfeng.ultimategarden.client.macro.command.CommandController;
import com.kuangsanfeng.ultimategarden.client.macro.crop.CropData;
import com.kuangsanfeng.ultimategarden.client.macro.crop.CropDatabase;
import com.kuangsanfeng.ultimategarden.client.macro.crop.CropType;
import com.kuangsanfeng.ultimategarden.client.macro.route.SetLineYP;
import com.kuangsanfeng.ultimategarden.client.macro.hint.MacroHint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import java.util.concurrent.ThreadLocalRandom;


// =========================================================
// Macro Controller
// =========================================================

public final class MacroController {

    private final MacroState state;

    private final MacroConfig config;

    private final VirtualInput input;

    private final CommandController command;

    private final SetLineYP setLineYP;

    private final WindowsInputInterceptor windowsInputInterceptor;

    private final WindowsFocusController windowsFocusController;

    private boolean waitingCropSelect;
    private boolean waitingModeSelect;
    private boolean waitingSimulateCropSelect;

    private boolean isTimeTesterMode = false;


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

    public MacroController(
            Minecraft client,
            VirtualInput input,
            WindowsInputInterceptor windowsInputInterceptor,
            WindowsFocusController windowsFocusController

    ) {

        this.input = input;

        this.windowsInputInterceptor =
                windowsInputInterceptor;

        this.windowsFocusController =
                windowsFocusController;

        this.state = new MacroState();

        this.config = new MacroConfig();

        this.command =
                new CommandController(
                        client,
                        input,
                        state,
                        this::pauseMacro,
                        this::startMacro
                );

        this.setLineYP =
                new SetLineYP(
                        input,
                        command.getScheduler()
                );


        config.load();

        /*
        state.setCropType(
                config.getCropType().getValue()
        );

        state.setModType(
                config.getModType()
        );

         */

    }


    public MacroState getState() {

        return state;
    }


    public MacroConfig getConfig() {

        return config;
    }


    public CommandController getCommand() {

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

        if (state.isRunning()) {
            return;
        }


        state.setRunning(true);

        windowsInputInterceptor.enable();

        input.mouseDown();

        MacroHint.printHint("MacroStart");

        runCurrentMovement();
    }

    public void pauseMacroByHotkey(){

        if (windowsFocusController != null) {
            windowsFocusController.restoreFocusWithHint();
        }

        pauseMacro();
    }
    public void pauseMacro() {

        state.setRunning(false);

        windowsInputInterceptor.disable();

        stopKeyboardMouse();

        command.cancel();


        MacroHint.printHint("ProgramPause");

        if (isTimeTesterMode) {
            MacroHint.printHint("TimeTesterCount", state.getTick());
        }
    }


    public void resetMacroByHotkey(){

        if (windowsFocusController != null) {
            windowsFocusController.restoreFocusWithHint();
        }

        resetMacro();
    }
    public void resetMacro() {

        state.reset();

        windowsInputInterceptor.disable();

        stopKeyboardMouse();

        command.cancel();


        MacroHint.printHint("ProgramReset");
    }


    // =========================================================
    // Input Stop
    // =========================================================

    private void stopKeyboard() {

        input.keyUp("W");

        input.keyUp("S");

        input.keyUp("A");

        input.keyUp("D");
    }


    private void stopKeyboardMouse() {

        stopKeyboard();

        input.mouseUp();
    }


    // =========================================================
    // Movement
    // =========================================================

    public void runCurrentMovement() {

        CropData data =
                CropDatabase.get(
                        state.getCropType()
                );


        boolean[] keys =
                data.getMovementKeys(
                        state.isEventSelect()
                );

        input.slot(1);

        if (keys[0]) {
            input.keyDown("W");
        }

        if (keys[1]) {
            input.keyDown("S");
        }

        if (keys[2]) {
            input.keyDown("A");
        }

        if (keys[3]) {
            input.keyDown("D");
        }
    }


    public void stopMovement() {

        stopKeyboard();
    }

    // =========================================================
    // Restore Action When Focus Change
    // =========================================================

    public void restoreInputState() {

        if (!state.isRunning()) {
            return;
        }


        input.mouseDown();

        runCurrentMovement();

    }

    // =========================================================
    // Tick
    // =========================================================

    public void tick() {

        command.tick();


        if (!state.isRunning()) {
            return;
        }


        if (state.isCommandRunning()) {
            return;
        }

        state.incrementTick();


        CropData data =
                CropDatabase.get(
                        state.getCropType()
                );


        int tickLimit = isTimeTesterMode ? 99999 : data.getTickLimit();
        tickLimit = tickLimit + random(0, 10);

        if (state.getTick()
                >= tickLimit) {

            onLineFinished();
        }
    }


    // =========================================================
    // Line Change
    // =========================================================

    private void onLineFinished() {

        CropData data =
                CropDatabase.get(
                        state.getCropType()
                );


        state.resetTick();

        stopMovement();


        state.setEventSelect(
                !state.isEventSelect()
        );


        state.setEventChange(true);

        state.incrementEventCount();

        MacroHint.printHint("LineFinished", state.getEventCount());


        if (state.getCropType() == 7) {

            command.cactusLineChange(
                    this::continueLine
            );

            return;
        }


        continueLine();
    }


    private void continueLine() {

        CropData data =
                CropDatabase.get(
                        state.getCropType()
                );


        if (state.getEventCount()
                >= data.getLines()) {

            //if pestMode
            if (state.isPestsMode()) {

                resetMacro();

                return;
            }

            //else-if farmMode
            finishFarmRoute();

            return;
        }


        changeLineView();

        runCurrentMovement();

        state.setEventChange(false);
    }


    private void changeLineView() {

        if (state.getCropType() == 5 ||
                state.getCropType() == 6) {

            setLineYP.setAlternate(
                    state.getEventCount(),
                    true
            );

        }
        else {
            /*
            setLineYP.setNormal(
                    //true
                    false
            );

            */
        }
    }


    // =========================================================
    // Farm Route
    // =========================================================

    private void finishFarmRoute() {

        stopKeyboardMouse();

        command.getScheduler().schedule(
                500,
                () -> {
                    command.warpGarden(
                            () -> {

                                state.resetRoute();

                                input.mouseDown();

                                runCurrentMovement();

                                state.setEventChange(false);
                            }
                    );
                }
        );
    }


    // =========================================================
    // Manual Line Control
    // =========================================================

    public void nextLine() {

        if (state.isRunning()) {
            return;
        }


        CropData data =
                CropDatabase.get(
                        state.getCropType()
                );


        if (state.getEventCount()
                >= data.getLines()) {

            return;
        }


        state.incrementEventCount();

        state.resetTick();


        state.setEventSelect(
                !state.isEventSelect()
        );


        state.setEventChange(true);


        //changeManualLineView();

        MacroHint.printHint("LineFinished", state.getEventCount());
    }


    public void previousLine() {

        if (state.isRunning()) {
            return;
        }


        if (state.getEventCount() <= 0) {
            return;
        }


        state.decrementEventCount();

        state.resetTick();


        state.setEventSelect(
                !state.isEventSelect()
        );


        state.setEventChange(true);


        //changeManualLineView();

        MacroHint.printHint("LineFinished", state.getEventCount());
    }


    private void changeManualLineView() {

        if (state.getCropType() == 5 ||
                state.getCropType() == 6) {

            setLineYP.setAlternate(
                    state.getEventCount(),
                    false
            );

        }
        else {

            /*
            setLineYP.setNormal(
                    false
            );

             */
        }
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

        waitingCropSelect = true;
        waitingModeSelect = false;


        MacroHint.printHint("CropSelect");

        Minecraft.getInstance().setScreen(
                new ChatScreen("", false)
        );
    }

    public boolean handleInitializeInput(
            String message
    ) {


        if (waitingCropSelect) {


            try {

                int value =
                        Integer.parseInt(message);


                CropType crop =
                        CropType.fromValue(value);


                state.setCropType(
                        crop.getValue()
                );


                config.setCropType(
                        crop
                );

                if (crop.getValue() == 8) {
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


                MacroHint.printHint("ModeSelect");


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



        if (waitingModeSelect) {


            try {

                int value =
                        Integer.parseInt(message);

                CropType crop =
                        CropType.fromValue(value);


                if (value != 0 && value != 1) {

                    return true;
                }


                state.setModType(
                        value
                );


                config.setModType(
                        value
                );


                waitingModeSelect = false;

                if (isTimeTesterMode) {
                    waitingSimulateCropSelect = true;

                    MacroHint.printHint("SimulateCropSelect");

                    Minecraft.getInstance()
                            .execute(() -> {

                                Minecraft.getInstance()
                                        .setScreen(
                                                new ChatScreen("", false)
                                        );

                            });
                    return true;
                }


                MacroHint.printHint("InitializeDone");

                MacroHint.printHint("CropInfo", crop);

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

                if (crop.getValue() >= 1 && crop.getValue() <= 7) {

                    state.setCropType(
                            crop.getValue()
                    );

                    waitingSimulateCropSelect = false;

                    MacroHint.printHint("InitializeDone");

                    MacroHint.printHint("CropInfo", crop);

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
        // 步骤 1：如果是蘑菇 (CropType 6) 且在害虫模式
        if (state.getCropType() == 6) {
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


    private void restartAfterCommand(
            boolean wasRunning
    ) {

        if (!wasRunning) {
            return;
        }


        startMacro();
    }
}