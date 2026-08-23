package com.kuangsanfeng.ultimategarden.client.interaction.command;


import com.kuangsanfeng.ultimategarden.client.macro.MacroController;
import com.kuangsanfeng.ultimategarden.client.macro.hint.MacroHint;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;


// =========================================================
// Command Tree
// =========================================================

public final class CommandTree {


    private CommandTree() {

    }



    public static void register(
            MacroController controller
    ) {


        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> {


                    dispatcher.register(

                            literal("ultimategarden")


                                    // /ultimategarden
                                    .executes(context -> {

                                        Helper.execute();

                                        return 1;

                                    })


                                    // /ultimategarden help
                                    .then(
                                            literal("help")
                                                    .executes(context -> {

                                                        Helper.execute();

                                                        return 1;

                                                    })
                                    )


                                    // /ultimategarden helper
                                    .then(
                                            literal("helper")
                                                    .executes(context -> {

                                                        Helper.execute();

                                                        return 1;

                                                    })
                                    )


                                    // /ultimategarden hotkey
                                    .then(
                                            literal("hotkey")
                                                    .executes(context -> {

                                                        Hotkey.execute();

                                                        return 1;

                                                    })
                                    )


                                    // /ultimategarden initialize
                                    .then(
                                            literal("initialize")
                                                    .executes(context -> {

                                                        Initialize.execute(
                                                                controller
                                                        );

                                                        return 1;

                                                    })
                                    )


                                    // Unknown command fallback
                                    // /ultimategarden xxx
                                    .then(
                                            argument(
                                                    "unknown",
                                                    StringArgumentType.greedyString()
                                            )
                                                    .executes(context -> {

                                                        MacroHint.printHint(
                                                                "WrongCommand"
                                                        );

                                                        return 1;

                                                    })
                                    )

                    );


                }
        );

    }


}