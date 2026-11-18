package com.kuangsanfeng.ultimategarden.client.mixin;

import com.kuangsanfeng.ultimategarden.client.function.path_finding.execution.ClientPathFinding;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
abstract class PathChatObservationMixin {
    @Inject(method="addMessage", at=@At(value="INVOKE",
            target="Lnet/minecraft/client/gui/components/ChatComponent;logChatMessage(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V"))
    private void ug$observeVisibleChat(Component contents, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        ClientPathFinding.INSTANCE.observeChat(contents);
    }
}
