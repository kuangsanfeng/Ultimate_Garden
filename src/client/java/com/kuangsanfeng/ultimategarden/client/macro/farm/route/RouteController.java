package com.kuangsanfeng.ultimategarden.client.macro.farm.route;
import com.kuangsanfeng.ultimategarden.client.macro.farm.route.SetLineYP;


import com.kuangsanfeng.ultimategarden.client.platform.input.virtual.VirtualInput;
import com.kuangsanfeng.ultimategarden.client.platform.window.WindowsFocusController;
import com.kuangsanfeng.ultimategarden.client.macro.farm.FarmState;
import com.kuangsanfeng.ultimategarden.client.macro.farm.action.FarmCommandController;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.farm.CropDefinition;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.farm.CropConfig;

import com.kuangsanfeng.ultimategarden.client.macro.farm.hint.FarmHints;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.RandomDelayConfig;
import com.kuangsanfeng.ultimategarden.client.macro.farm.crop.CropType;

// =========================================================
// Farm Route Controller
// =========================================================
public final class RouteController {

    private final FarmState state;
    private java.util.function.BooleanSupplier routeAllowed = () -> true;
    public void setRouteAllowed(java.util.function.BooleanSupplier value) { routeAllowed = value; }
    private final CropConfig crops;
    private final VirtualInput input;
    private final FarmCommandController command;
    private final SetLineYP setLineYP;
    private final WindowsFocusController windowsFocusController;
    private final Runnable resetFarm;

    public RouteController(FarmState state, VirtualInput input,
                           FarmCommandController command,
                           WindowsFocusController windowsFocusController,
                           Runnable resetFarm, CropConfig crops) {
        this.crops = crops;
        this.state = state;
        this.input = input;
        this.command = command;
        this.windowsFocusController = windowsFocusController;
        this.resetFarm = resetFarm;
        this.setLineYP = new SetLineYP(input, command.getScheduler(), command::getRandomDelays);
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


    public void stopKeyboardMouse() {

        stopKeyboard();

        input.mouseUp();
    }


    // =========================================================
    // Movement
    // =========================================================

    public void runCurrentMovement() {
        if (!routeAllowed.getAsBoolean() || state.isCommandRunning()) return;

        CropDefinition data =
                crops.get(
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
    // Tick
    // =========================================================

    public void tick(boolean isTimeTesterMode) {




        if (!state.isRunning()) {
            return;
        }


        if (state.isCommandRunning()) {
            return;
        }

        state.incrementTick();


        CropDefinition data =
                crops.get(
                        state.getCropType()
                );


        long tickLimit = isTimeTesterMode ? crops.getCrop(CropType.TIMETESTER).getTickLimit() : data.getTickLimit();
        tickLimit = tickLimit + command.getRandomDelays().sample(RandomDelayConfig.Key.FARM_ROW_JITTER);

        if (state.getTick()
                >= tickLimit) {

            onLineFinished();
        }
    }


    // =========================================================
    // Line Change
    // =========================================================

    private void onLineFinished() {

        CropDefinition data =
                crops.get(
                        state.getCropType()
                );


        state.resetTick();

        stopMovement();


        state.setEventSelect(
                !state.isEventSelect()
        );


        state.setEventChange(true);

        state.incrementEventCount();

        FarmHints.printHint("LineFinished", state.getEventCount());


        if (data.lineChangeMovement().enabled()) {

            command.lineChangeMovement(
                    data.lineChangeMovement().durationTicks(), this::continueLine
            );

            return;
        }


        continueLine();
    }


    private void continueLine() {

        CropDefinition data =
                crops.get(
                        state.getCropType()
                );


        if (state.getEventCount()
                >= data.getLines()) {

            //if pestMode
            if (state.isPestsMode()) {

                if (windowsFocusController != null) {
                    windowsFocusController.restoreFocusWithHint();
                }

                resetFarm.run();

                return;
            }

            //else-if farmMode
            finishFarmRoute();

            return;
        }


        var view = data.lineChangeYawPitch();
        if (view.enabled()) {
            state.setCommandRunning(true);
            state.setLineChangePhase(FarmState.LineChangePhase.YAW_PITCH);
            // Queue the stage behind existing work; do not select a slot early.
            command.getScheduler().schedule(0, () -> setLineYP.setAlternate(
                    state.getEventCount(), view.slot1(), view.slot2(), this::startNextLine));
        } else startNextLine();
    }

    private void startNextLine() {
        state.setCommandRunning(false);
        runCurrentMovement();
        state.setEventChange(false);
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

                                if (routeAllowed.getAsBoolean()) input.mouseDown();

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


        CropDefinition data =
                crops.get(
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

        FarmHints.printHint("LineFinished", state.getEventCount());
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

        FarmHints.printHint("LineFinished", state.getEventCount());
    }


}
