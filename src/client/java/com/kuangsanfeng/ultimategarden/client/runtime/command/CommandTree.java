package com.kuangsanfeng.ultimategarden.client.runtime.command;
import com.kuangsanfeng.ultimategarden.client.runtime.command.CommandHelper;



import com.kuangsanfeng.ultimategarden.client.macro.farm.FarmController;
import com.kuangsanfeng.ultimategarden.client.platform.hint.HintManager;
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
            FarmController controller
    ) {


        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> {


                    dispatcher.register(

                            literal("ultimategarden")


                                    // /ultimategarden
                                    .executes(context -> {

                                        CommandHelper.execute();

                                        return 1;

                                    })


                                    // /ultimategarden help
                                    .then(
                                            literal("help")
                                                    .executes(context -> {

                                                        CommandHelper.execute();

                                                        return 1;

                                                    })
                                    )


                                    // /ultimategarden helper
                                    .then(
                                            literal("helper")
                                                    .executes(context -> {

                                                        CommandHelper.execute();

                                                        return 1;

                                                    })
                                    )


                                    // /ultimategarden hotkey
                                    .then(
                                            literal("hotkey")
                                                    .executes(context -> {

                                                        CommandHelper.showHotkeys();

                                                        return 1;

                                                    })
                                    )


                                    // /ultimategarden initialize
                                    .then(
                                            literal("initialize")
                                                    .executes(context -> {

                                                        CommandHelper.initialize(
                                                                controller
                                                        );

                                                        return 1;

                                                    })
                                    )


                                    .then(literal("menu").executes(context -> {
                                        com.kuangsanfeng.ultimategarden.client.platform.ui.MenuController.open();
                                        return 1;
                                    }))

                                    // Unknown command fallback
                                    // /ultimategarden xxx
                                    .then(
                                            argument(
                                                    "unknown",
                                                    StringArgumentType.greedyString()
                                            )
                                                    .executes(context -> {

                                                        HintManager.printHint(
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