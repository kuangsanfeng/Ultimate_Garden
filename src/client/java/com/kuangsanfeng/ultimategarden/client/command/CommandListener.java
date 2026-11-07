package com.kuangsanfeng.ultimategarden.client.command;


import com.kuangsanfeng.ultimategarden.client.macro.MacroManager;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;


// =========================================================
// Command Listener
// =========================================================

public final class CommandListener {


    private static MacroManager controller;


    private CommandListener() {

    }



    public static void register(
            MacroManager macroManager
    ) {


        controller = macroManager;


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
        ClientReceiveMessageEvents.GAME.register(
                (message, overlay) -> {
                    if (controller != null) {
                        // 将 Text 组件转成 String，经 MacroManager 分发到各模块的消息处理函数。
                        String text = message.getString();
                        controller.automateTaskByMonitorMessage(text);
                    }
                }
        );

    }


}
