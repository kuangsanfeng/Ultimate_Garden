package com.kuangsanfeng.ultimategarden.client.interaction.command;


import com.kuangsanfeng.ultimategarden.client.macro.MacroController;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
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


        // 拦截并处理自己发送的聊天（如初始化输入）
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


        // 纯监听服务器发来的聊天/系统消息（不拦截，消息正常显示）
        ClientReceiveMessageEvents.CHAT.register(
                (message, signedMessage, sender, params, receptionTimestamp) -> {
                    if (controller != null) {
                        // 将 Text 组件转成 String 并交由MacroController的函数automateTaskByMonitorMessage处理
                        String text = message.getString();
                        controller.automateTaskByMonitorMessage(text);
                    }
                }
        );

    }


}