package com.kuangsanfeng.ultimategarden.client.hotkey;


import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import com.kuangsanfeng.ultimategarden.Ultimate_Garden;
import com.kuangsanfeng.ultimategarden.client.macro.MacroController;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;


// =========================================================
// Macro Key Bindings
// =========================================================

public final class MacroKeyBindings {


    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(
                    Ultimate_Garden.id("key_category")
            );


    // =========================================================
    // Key Mapping
    // =========================================================

    private static final KeyMapping MACRO_TOGGLE_KEY =
            register(
                    "macro_toggle",
                    GLFW.GLFW_KEY_KP_ADD
            );


    private static final KeyMapping MACRO_RESET_KEY =
            register(
                    "macro_reset",
                    GLFW.GLFW_KEY_KP_SUBTRACT
            );


    private static final KeyMapping NEXT_LINE_KEY =
            register(
                    "next_line",
                    GLFW.GLFW_KEY_RIGHT_BRACKET
            );


    private static final KeyMapping PREVIOUS_LINE_KEY =
            register(
                    "previous_line",
                    GLFW.GLFW_KEY_LEFT_BRACKET
            );


    private static final KeyMapping SPAWN_KEY =
            register(
                    "spawn",
                    GLFW.GLFW_KEY_KP_MULTIPLY
            );


    private static final KeyMapping WARP_KEY =
            register(
                    "warp",
                    GLFW.GLFW_KEY_KP_DIVIDE
            );


    private static final KeyMapping PEST_LOADOUT_KEY =
            register(
                    "pest_loadout",
                    GLFW.GLFW_KEY_KP_7
            );


    private static final KeyMapping PEST_TIMER_CANCEL_KEY =
            register(
                    "pest_timer_cancel",
                    GLFW.GLFW_KEY_KP_4
            );


    private static final KeyMapping LOADOUT_KEY =
            register(
                    "loadout",
                    GLFW.GLFW_KEY_KP_9
            );


    private static final KeyMapping CROP_CHANGE_KEY =
            register(
                    "crop_change",
                    GLFW.GLFW_KEY_KP_DECIMAL
            );


    private static final KeyMapping WINDOW_FOCUS_KEY =
            register(
                    "window_focus",
                    GLFW.GLFW_KEY_U
            );


    // =========================================================
    // Rising Edge
    // =========================================================

    private static final KeyEdge MACRO_TOGGLE_EDGE =
            new KeyEdge();

    private static final KeyEdge MACRO_RESET_EDGE =
            new KeyEdge();

    private static final KeyEdge NEXT_LINE_EDGE =
            new KeyEdge();

    private static final KeyEdge PREVIOUS_LINE_EDGE =
            new KeyEdge();

    private static final KeyEdge SPAWN_EDGE =
            new KeyEdge();

    private static final KeyEdge WARP_EDGE =
            new KeyEdge();

    private static final KeyEdge PEST_LOADOUT_EDGE =
            new KeyEdge();

    private static final KeyEdge PEST_TIMER_CANCEL_EDGE =
            new KeyEdge();

    private static final KeyEdge LOADOUT_EDGE =
            new KeyEdge();

    private static final KeyEdge CROP_CHANGE_EDGE =
            new KeyEdge();

    private static final KeyEdge WINDOW_FOCUS_EDGE =
            new KeyEdge();


    private MacroKeyBindings() {
    }



    // =========================================================
    // Key Edge Detector
    // =========================================================

    private static final class KeyEdge {

        private boolean previous;


        public boolean rising(
                boolean current
        ) {

            boolean result =
                    current && !previous;


            previous = current;


            return result;
        }
    }



    private static boolean isPressed(
            KeyMapping key
    ) {

        return key.isDown();

    }



    // =========================================================
    // Key Name
    // =========================================================

    public static String getKeyName(
            String action
    ) {

        return switch (action) {

            case "toggle" ->
                    MACRO_TOGGLE_KEY
                            .getTranslatedKeyMessage()
                            .getString();


            case "reset" ->
                    MACRO_RESET_KEY
                            .getTranslatedKeyMessage()
                            .getString();


            case "next_line" ->
                    NEXT_LINE_KEY
                            .getTranslatedKeyMessage()
                            .getString();


            case "previous_line" ->
                    PREVIOUS_LINE_KEY
                            .getTranslatedKeyMessage()
                            .getString();


            case "spawn" ->
                    SPAWN_KEY
                            .getTranslatedKeyMessage()
                            .getString();


            case "warp" ->
                    WARP_KEY
                            .getTranslatedKeyMessage()
                            .getString();


            case "pest_loadout" ->
                    PEST_LOADOUT_KEY
                            .getTranslatedKeyMessage()
                            .getString();


            case "pest_timer_cancel" ->
                    PEST_TIMER_CANCEL_KEY
                            .getTranslatedKeyMessage()
                            .getString();


            case "loadout" ->
                    LOADOUT_KEY
                            .getTranslatedKeyMessage()
                            .getString();


            case "crop_change" ->
                    CROP_CHANGE_KEY
                            .getTranslatedKeyMessage()
                            .getString();


            case "windows_focus" ->
                    WINDOW_FOCUS_KEY
                            .getTranslatedKeyMessage()
                            .getString();


            default ->
                    "Unknown";
        };
    }



    // =========================================================
    // Register Key
    // =========================================================

    private static KeyMapping register(
            String name,
            int key
    ) {

        return KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        "key.ultimate_garden." + name,
                        InputConstants.Type.KEYSYM,
                        key,
                        CATEGORY
                )
        );
    }



    // =========================================================
    // Register
    // =========================================================


    public static void register(
            MacroController controller
    ) {


        Ultimate_Garden.LOGGER.info(
                "Registering Ultimate Garden key bindings."
        );



        ClientTickEvents.END_CLIENT_TICK.register(
                client -> {


                    controller.tick();



                    // =====================================================
                    // Num+
                    // =====================================================

                    while (MACRO_TOGGLE_EDGE.rising(
                            isPressed(MACRO_TOGGLE_KEY)
                    )) {

                        if (controller.getState().getCropType() == 0){
                            return;
                        }

                        if (controller.getState().isRunning()) {

                            controller.pauseMacroByHotkey();

                        }
                        else {

                            controller.startMacroByHotkey();

                        }
                    }



                    // =====================================================
                    // Num-
                    // =====================================================

                    while (MACRO_RESET_EDGE.rising(
                            isPressed(MACRO_RESET_KEY)
                    )) {

                        if (controller.getState().getCropType() == 0){
                            return;
                        }

                        controller.resetMacroByHotkey();

                    }



                    // =====================================================
                    // ]
                    // =====================================================

                    while (NEXT_LINE_EDGE.rising(
                            isPressed(NEXT_LINE_KEY)
                    )) {

                        if (controller.getState().isRunning()){
                            return;
                        }

                        controller.nextLine();

                    }



                    // =====================================================
                    // [
                    // =====================================================

                    while (PREVIOUS_LINE_EDGE.rising(
                            isPressed(PREVIOUS_LINE_KEY)
                    )) {

                        if (controller.getState().isRunning()){
                            return;
                        }

                        controller.previousLine();

                    }



                    // =====================================================
                    // Num*
                    // =====================================================

                    while (SPAWN_EDGE.rising(
                            isPressed(SPAWN_KEY)
                    )) {

                        if (controller.getState().getCropType() == 0){
                            return;
                        }

                        controller.spawnByHotkey();

                    }



                    // =====================================================
                    // Num/
                    // =====================================================

                    while (WARP_EDGE.rising(
                            isPressed(WARP_KEY)
                    )) {

                        if (controller.getState().getCropType() == 0){
                            return;
                        }

                        controller.warpGarden();

                    }



                    // =====================================================
                    // Num7
                    // =====================================================

                    while (PEST_LOADOUT_EDGE.rising(
                            isPressed(PEST_LOADOUT_KEY)
                    )) {

                        if (controller.getState().getCropType() == 0 && !controller.getState().isPestsMode()){
                            return;
                        }

                        controller.getCommand()
                                .startPestLoadoutTimer();

                    }



                    // =====================================================
                    // Num4
                    // =====================================================

                    while (PEST_TIMER_CANCEL_EDGE.rising(
                            isPressed(PEST_TIMER_CANCEL_KEY)
                    )) {

                        controller.getCommand()
                                .cancelPestLoadoutTimer();

                    }



                    // =====================================================
                    // Num9
                    // =====================================================

                    while (LOADOUT_EDGE.rising(
                            isPressed(LOADOUT_KEY)
                    )) {

                        if (controller.getState().getCropType() == 0){
                            return;
                        }

                        controller.loadout();

                    }



                    // =====================================================
                    // Num.
                    // =====================================================

                    while (CROP_CHANGE_EDGE.rising(
                            isPressed(CROP_CHANGE_KEY)
                    )) {

                        controller.changeCropType();

                    }



                    // =====================================================
                    // U
                    // =====================================================

                    if (WINDOW_FOCUS_EDGE.rising(
                            isPressed(WINDOW_FOCUS_KEY)
                    )) {


                        Ultimate_GardenClient
                                .getWindowsFocusController()
                                .toggle();

                    }

                }
        );
    }
}