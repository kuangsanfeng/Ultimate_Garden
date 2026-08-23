package com.kuangsanfeng.ultimategarden.client.interaction.command;

import com.kuangsanfeng.ultimategarden.client.macro.MacroController;


// =========================================================
// Initialize Command
// =========================================================

public final class Initialize {


    private Initialize() {
    }


    public static void execute(
            MacroController controller
    ) {

        controller.changeCropType();

    }

}