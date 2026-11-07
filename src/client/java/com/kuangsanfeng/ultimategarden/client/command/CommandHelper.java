package com.kuangsanfeng.ultimategarden.client.command;

import com.kuangsanfeng.ultimategarden.client.hint.HintManager;
import com.kuangsanfeng.ultimategarden.client.macro.farm.FarmController;


// =========================================================
// Helper Command
// =========================================================

public final class CommandHelper {


    private CommandHelper() {
    }


    public static void execute() {

        HintManager.printHint("Helper");

    }


    // =========================================================
    // Hotkey Command (formerly Hotkey.execute)
    // =========================================================
    public static void showHotkeys() {
        HintManager.printHint("Hotkey");
    }

    // =========================================================
    // Initialize Command (formerly Initialize.execute)
    // =========================================================
    public static void initialize(FarmController controller) {
        controller.changeCropType();
    }
}
