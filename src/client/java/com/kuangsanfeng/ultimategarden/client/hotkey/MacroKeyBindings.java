package com.kuangsanfeng.ultimategarden.client.hotkey;


import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import com.kuangsanfeng.ultimategarden.Ultimate_Garden;
import com.kuangsanfeng.ultimategarden.client.macro.farm.FarmController;
import com.kuangsanfeng.ultimategarden.client.macro.MacroManager;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;


// =========================================================
// Macro Key Bindings
// =========================================================

public final class MacroKeyBindings {
    public record Binding(String title, String description, KeyMapping mapping) { }
    public static java.util.List<Binding> getBindings() {
        return java.util.List.of(
            new Binding("Start / Pause", "Start or pause farming macro", MACRO_TOGGLE_KEY),
            new Binding("Reset", "Reset the current farm route.", MACRO_RESET_KEY),
            new Binding("Next line", "Advance one line while stopped.", NEXT_LINE_KEY),
            new Binding("Previous line", "Move back one line while stopped.", PREVIOUS_LINE_KEY),
            new Binding("Set spawn", "Execute /spawnpoint", SPAWN_KEY),
            new Binding("Warp Garden", "Execute /warp garden", WARP_KEY),
            new Binding("Pest timer", "Manually start the pest loadout timer in Pests mode.", PEST_LOADOUT_KEY),
            new Binding("Cancel pest timer", "Cancel the current pest loadout timer.", PEST_TIMER_CANCEL_KEY),
            new Binding("Loadout", "Execute /loadout", LOADOUT_KEY),
            new Binding("Select crop", "Choose crop and mode through chat.", CROP_CHANGE_KEY),
            new Binding("Toggle Zombie Shootout", "Start or stop Zombie Shootout.", SHOOTOUT_KEY),
            new Binding("Toggle HUD", "Show or hide the HUD.", HUD_TOGGLE_KEY),
            new Binding("Mouse focus", "Toggle cursor release.", WINDOW_FOCUS_KEY),
            new Binding("Open Ultimate Garden Menu", "Open menu; " + getMenuKeyName() + " closes it outside text input.", OPEN_MENU_KEY));
    }
    public static String getMenuKeyName() { return OPEN_MENU_KEY.getTranslatedKeyMessage().getString(); }
    public static boolean matchesShootoutMouse(int button) {
        var key=((com.kuangsanfeng.ultimategarden.client.mixin.KeyMappingAccessor)SHOOTOUT_KEY).getKey();
        return key.getType()==InputConstants.Type.MOUSE && key.getValue()==button;
    }
    public static boolean matchesShootoutKey(net.minecraft.client.input.KeyEvent event) { return SHOOTOUT_KEY.matches(event); }
    public static boolean matchesMenuKey(net.minecraft.client.input.KeyEvent event) { return OPEN_MENU_KEY.matches(event); }
    public static void clearMenuClicks() { while (OPEN_MENU_KEY.consumeClick()) { } }
    public static boolean isMenuKeyPhysicallyDown() {
        var key = ((com.kuangsanfeng.ultimategarden.client.mixin.KeyMappingAccessor)OPEN_MENU_KEY).getKey();
        if (key.getValue() < 0) return false;
        long handle = net.minecraft.client.Minecraft.getInstance().getWindow().handle();
        return key.getType() == InputConstants.Type.MOUSE
            ? GLFW.glfwGetMouseButton(handle, key.getValue()) == GLFW.GLFW_PRESS
            : GLFW.glfwGetKey(handle, key.getValue()) == GLFW.GLFW_PRESS;
    }



    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(
                    Ultimate_Garden.id("key_category")
            );


    // =========================================================
    // Key Mapping
    // =========================================================

    private static final KeyMapping SHOOTOUT_KEY = register("toggle_zombie_shootout", GLFW.GLFW_KEY_UNKNOWN);
    private static final KeyEdge SHOOTOUT_EDGE = new KeyEdge();
    private static final KeyMapping HUD_TOGGLE_KEY = register("toggle_hud", GLFW.GLFW_KEY_UNKNOWN);
    private static final KeyEdge HUD_TOGGLE_EDGE = new KeyEdge();
    private static final KeyMapping OPEN_MENU_KEY = register("open_menu", GLFW.GLFW_KEY_M);

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
            MacroManager manager
    ) {


        FarmController controller = manager.getFarmController();

        Ultimate_Garden.LOGGER.info(
                "Registering Ultimate Garden key bindings."
        );



        ClientTickEvents.END_CLIENT_TICK.register(
                client -> {


                    boolean hudToggle = HUD_TOGGLE_EDGE.rising(isPressed(HUD_TOGGLE_KEY));
                    while (HUD_TOGGLE_KEY.consumeClick()) { }
                    boolean hudInputAllowed = client.screen == null || client.screen instanceof com.kuangsanfeng.ultimategarden.client.ui.screen.UltimateGardenScreen
                            && !(client.screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox);
                    if (hudToggle && hudInputAllowed && !com.kuangsanfeng.ultimategarden.client.ui.hud.HudAdjustmentController.isActive()) {
                        var configs=Ultimate_GardenClient.getConfigManager();
                        if (!configs.isPendingRestart(com.kuangsanfeng.ultimategarden.client.config.ConfigManager.Module.HUD)) {
                            var hud=configs.getHudConfig(); hud.setShowHud(!hud.isShowHud());
                            configs.save(com.kuangsanfeng.ultimategarden.client.config.ConfigManager.Module.HUD);
                        }
                    }
                    boolean shootoutToggle=SHOOTOUT_EDGE.rising(isPressed(SHOOTOUT_KEY));
                    while(SHOOTOUT_KEY.consumeClick()){}
                    if(shootoutToggle && hudInputAllowed && !com.kuangsanfeng.ultimategarden.client.ui.hud.HudAdjustmentController.isActive())
                        com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController.INSTANCE.toggle();
                    // =====================================================
                    // Num+
                    // =====================================================

                    while (MACRO_TOGGLE_EDGE.rising(
                            isPressed(MACRO_TOGGLE_KEY)
                    )) {

                        if (controller.getState().getCropType() == 0) break;

                        if (controller.shouldPauseByHotkey()) {

                            manager.pauseByHotkey(MacroManager.FARM);

                        }
                        else {

                            manager.startByHotkey(MacroManager.FARM);

                        }
                    }



                    // =====================================================
                    // Num-
                    // =====================================================

                    while (MACRO_RESET_EDGE.rising(
                            isPressed(MACRO_RESET_KEY)
                    )) {

                        if (controller.getState().getCropType() == 0) break;

                        manager.resetByHotkey(MacroManager.FARM);

                    }



                    manager.tick();
                    boolean menuClick = false;
                    while (OPEN_MENU_KEY.consumeClick()) menuClick = true;
                    if (menuClick && client.screen == null) com.kuangsanfeng.ultimategarden.client.ui.MenuController.open();



                    if (controller.isMenuSessionActive()) return;

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

                        if (controller.getState().getCropType() == 0){
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