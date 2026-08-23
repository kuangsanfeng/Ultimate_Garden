package com.kuangsanfeng.ultimategarden.client.interaction.command;


import com.kuangsanfeng.ultimategarden.client.macro.MacroController;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;


// =========================================================
// Command Scheduler
// =========================================================

public final class CommandScheduler {


    private static MacroController controller;


    private CommandScheduler() {

    }



    public static void register(
            MacroController macroController
    ) {


        controller = macroController;


        ClientSendMessageEvents.ALLOW_CHAT.register(
                message -> {


                    if (controller != null &&
                            controller.handleInitializeInput(
                                    message
                            )) {


                        return false;

                    }


                    return true;

                }
        );

    }


}