package com.kuangsanfeng.ultimategarden.client.mixin;


import com.kuangsanfeng.ultimategarden.client.function.path_finding.execution.ClientPathFinding;
import com.kuangsanfeng.ultimategarden.client.platform.gui.PlayerGuiInput;
import com.kuangsanfeng.ultimategarden.client.platform.input.PhysicalGameInput;
import static com.kuangsanfeng.ultimategarden.client.platform.input.InputRequests.Channel.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import com.kuangsanfeng.ultimategarden.client.platform.window.WindowsInputInterceptor;
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
    @WrapMethod(method = "keyPress")
    private void filterPhysicalKeyMappings(long window, int action, KeyEvent event, Operation<Void> original) {
        var client = net.minecraft.client.Minecraft.getInstance();
        boolean filter = window == client.getWindow().handle() && !PlayerGuiInput.isVirtualDispatch()
                && PhysicalGameInput.inGame(client,KEYBOARD);
        PhysicalGameInput.keyboardDispatch(filter, () -> original.call(window, action, event));
    }

    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("RETURN"))
    private void clearUnusedInventoryRequest(long window, int action, KeyEvent event, CallbackInfo ci) {
        com.kuangsanfeng.ultimategarden.client.platform.gui.PlayerGuiInput.clearInventoryRequest();
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


        if (com.kuangsanfeng.ultimategarden.client.platform.ui.hud.HudAdjustmentController.isActive()
                && !com.kuangsanfeng.ultimategarden.client.platform.gui.PlayerGuiInput.isVirtualDispatch()) {
            if (action == org.lwjgl.glfw.GLFW.GLFW_PRESS && event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
                net.minecraft.client.Minecraft.getInstance().execute(() -> com.kuangsanfeng.ultimategarden.client.platform.ui.hud.HudAdjustmentController.finish(true));
            ci.cancel(); return;
        }
        var client = net.minecraft.client.Minecraft.getInstance();
        if (window != client.getWindow().handle() || PlayerGuiInput.isVirtualDispatch()) return;
        if (action == org.lwjgl.glfw.GLFW.GLFW_PRESS && client.screen == null) {
            com.kuangsanfeng.ultimategarden.client.platform.gui.PlayerGuiInput.Kind kind = null;
            if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                kind = com.kuangsanfeng.ultimategarden.client.platform.gui.PlayerGuiInput.Kind.MENU;
            } else if (client.options.keyInventory.matches(event)) {
                kind = com.kuangsanfeng.ultimategarden.client.platform.gui.PlayerGuiInput.Kind.INVENTORY;
            } else if (client.options.keyChat.matches(event) || client.options.keyCommand.matches(event)) {
                kind = com.kuangsanfeng.ultimategarden.client.platform.gui.PlayerGuiInput.Kind.CHAT;
            }
            if (kind != null && PlayerGuiInput.request(kind)) {
                ClientPathFinding.INSTANCE.stopForPlayerGui(kind);
                return;
            }
        }

        if (com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController.INSTANCE.enabled()
                && com.kuangsanfeng.ultimategarden.client.runtime.hotkey.MacroKeyBindings.matchesShootoutKey(event)) return;

        if (com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController.INSTANCE.enabled() && client.screen==null) {
            if (com.kuangsanfeng.ultimategarden.client.runtime.hotkey.MacroKeyBindings.matchesMenuKey(event)) return;
            if (client.options.keyUp.matches(event) || client.options.keyDown.matches(event)
                    || client.options.keyLeft.matches(event) || client.options.keyRight.matches(event)
                    || client.options.keyJump.matches(event) || client.options.keyShift.matches(event)
                    || client.options.keySprint.matches(event)) { ci.cancel(); return; }
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
