package com.kuangsanfeng.ultimategarden.client.hint;

import com.kuangsanfeng.ultimategarden.client.hotkey.MacroKeyBindings;
import net.minecraft.client.Minecraft;
import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import net.minecraft.network.chat.Component;

// =========================================================
// Generic Mod Hint Output
// =========================================================
public final class HintManager {
    private static final String PREFIX = "[UltimateGarden] ";

    private HintManager() {
    }

    public static boolean isEnabled(String event) {
        var configs=Ultimate_GardenClient.getConfigManager();
        if (configs==null) return true;
        var general=configs.getGeneralConfig();
        if (!general.isShowModChatHints()) return false;
        if (event.equals("PestLoadoutTimeUp")) return general.hintEnabled(com.kuangsanfeng.ultimategarden.client.config.GeneralConfig.Hint.pestTimerDone)
                || general.hintEnabled(com.kuangsanfeng.ultimategarden.client.config.GeneralConfig.Hint.pestLoadoutChange);
        String key=switch(event) {
            case "MacroStart" -> "macroStart";
            case "ProgramPause" -> "macroPause";
            case "ProgramReset" -> "macroReset";
            case "InitializeDone" -> "macroInitialized";
            case "LineFinished" -> "lineCompleted";
            case "TimeTesterCount" -> "timeTesterTicks";
            case "PestLoadoutTimerStart" -> "pestTimerStart";
            case "PestLoadoutTimerReset" -> "pestTimerReset";
            case "PestTimerDone" -> "pestTimerDone";
            case "PestLoadoutChange" -> "pestLoadoutChange";
            case "FocusRelease" -> "focusReleased";
            case "FocusRestore" -> "focusRestored";
            case "CropSelect", "ModeSelect", "SimulateCropSelect", "CropInfo" -> "cropSelectionInitialization";
            case "Hotkey" -> "hotkeyHelp";
            case "Helper", "WrongCommand" -> "commandHelpErrors";
            default -> null;
        };
        return key==null || general.hintEnabled(com.kuangsanfeng.ultimategarden.client.config.GeneralConfig.Hint.valueOf(key));
    }
    public static void printFor(String event, String text) { if (isEnabled(event)) print(text); }
    public static void printHint(String type) {
        if (!isEnabled(type)) return;
        printNull();
        switch (type) {
            case "Hotkey" -> {
                printHotkeyHint();
                printNull();
            }
            case "Helper" -> {
                printModeHelperHint();
                printNull();
            }
            case "WrongCommand" -> {
                printWrongCommandHint();
                printNull();
            }
            case "FocusRelease" -> printFocusReleaseHint();
            case "FocusRestore" -> printFocusRestoreHint();
            default -> {
            }
        }
        printNull();
    }

    // =========================================================
    private static void printHotkeyHint() {

        print(
                MacroKeyBindings.getKeyName("toggle")
                        + " -> Pause/Start macro"
        );

        print(
                MacroKeyBindings.getKeyName("reset")
                        + " -> Reset macro"
        );

        print(
                MacroKeyBindings.getKeyName("next_line")
                        + " -> Move to next line"
        );

        print(
                MacroKeyBindings.getKeyName("previous_line")
                        + " -> Move to previous line"
        );

        print(
                MacroKeyBindings.getKeyName("spawn")
                        + " -> Set spawn"
        );

        print(
                MacroKeyBindings.getKeyName("warp")
                        + " -> /warp garden"
        );

        print(
                MacroKeyBindings.getKeyName("pest_loadout")
                        + " -> Auto Pest Loadout"
        );

        print(
                MacroKeyBindings.getKeyName("pest_timer_cancel")
                        + " -> Cancel pest loadout timer"
        );

        print(
                MacroKeyBindings.getKeyName("loadout")
                        + " -> /loadout"
        );

        print(
                MacroKeyBindings.getKeyName("crop_change")
                        + " -> Change crop type"
        );

        print(
                MacroKeyBindings.getKeyName("windows_focus")
                        + " -> Shift Windows focus state"
        );
    }

    // =========================================================
    private static void printFocusReleaseHint() {

        print("Focus Released");

    }

    // =========================================================
    private static void printFocusRestoreHint() {

        print("Focus Restored");

    }

    // =========================================================
    private static void printModeHelperHint() {

        print("/ultimategarden help(er): Show help list");
        print("/ultimategarden hotkey: Show hotkeys");
        print("/ultimategarden initialize: Initialize farming crop and mod switch");
        print("/ultimategarden menu: Open UI");

    }

    // =========================================================
    private static void printWrongCommandHint() {

        printRequired("Command Not Found.");
        printRequired("Use '/ultimategarden help(er)' to show help list");

    }

    // =========================================================
    private static boolean ordinaryHintsEnabled() {
        var configs = Ultimate_GardenClient.getConfigManager();
        return configs == null || configs.getGeneralConfig().isShowModChatHints();
    }

    public static void print(String text) {
        if (ordinaryHintsEnabled()) printRequired(text);
    }

    public static void printRequired(
            String text
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();


        if (minecraft.player == null) {
            return;
        }


        minecraft.gui
                .getChat()
                .addClientSystemMessage(
                        Component.literal(
                                PREFIX + text
                        )
                );

    }

    // =========================================================
    public static void printNull() {
        if (!ordinaryHintsEnabled()) return;

        Minecraft minecraft =
                Minecraft.getInstance();


        if (minecraft.player == null) {
            return;
        }


        minecraft.gui
                .getChat()
                .addClientSystemMessage(
                        Component.literal("")
                );

    }

}
