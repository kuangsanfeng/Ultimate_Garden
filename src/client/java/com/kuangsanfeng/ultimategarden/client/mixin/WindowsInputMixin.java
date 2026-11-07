package com.kuangsanfeng.ultimategarden.client.mixin;


import com.kuangsanfeng.ultimategarden.Ultimate_Garden;
import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import com.kuangsanfeng.ultimategarden.client.window.WindowsInputInterceptor;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;



/**
 * Block real player keyboard input while macro is running.
 *
 * Does not affect VirtualInput.
 */
@Mixin(KeyboardHandler.class)
public class WindowsInputMixin {

    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("RETURN"))
    private void clearUnusedInventoryRequest(long window, int action, KeyEvent event, CallbackInfo ci) {
        com.kuangsanfeng.ultimategarden.client.input.PlayerGuiInput.clearInventoryRequest();
    }


    @Inject(
            method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void interceptKeyboard(
            long window,
            int action,
            KeyEvent event,
            CallbackInfo ci
    ) {


        if (com.kuangsanfeng.ultimategarden.client.ui.hud.HudAdjustmentController.isActive()
                && !com.kuangsanfeng.ultimategarden.client.input.PlayerGuiInput.isVirtualDispatch()) {
            if (action == org.lwjgl.glfw.GLFW.GLFW_PRESS && event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
                net.minecraft.client.Minecraft.getInstance().execute(() -> com.kuangsanfeng.ultimategarden.client.ui.hud.HudAdjustmentController.finish(true));
            ci.cancel(); return;
        }
        var client = net.minecraft.client.Minecraft.getInstance();
        if (action == org.lwjgl.glfw.GLFW.GLFW_PRESS && client.screen == null) {
            com.kuangsanfeng.ultimategarden.client.input.PlayerGuiInput.Kind kind = null;
            if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                kind = com.kuangsanfeng.ultimategarden.client.input.PlayerGuiInput.Kind.MENU;
            } else if (client.options.keyInventory.matches(event)) {
                kind = com.kuangsanfeng.ultimategarden.client.input.PlayerGuiInput.Kind.INVENTORY;
            } else if (client.options.keyChat.matches(event) || client.options.keyCommand.matches(event)) {
                kind = com.kuangsanfeng.ultimategarden.client.input.PlayerGuiInput.Kind.CHAT;
            }
            if (kind != null && com.kuangsanfeng.ultimategarden.client.input.PlayerGuiInput.request(kind)) return;
        }

        WindowsInputInterceptor interceptor =
                Ultimate_GardenClient
                        .getWindowsInputInterceptor();


        if (interceptor == null) {

            return;
        }

        if (!interceptor.shouldBlockInput()) {
            return;
        }


        if (interceptor.blockKeyboard(
                event.key()
        )
        ) {

            ci.cancel();

        }

    }

}
