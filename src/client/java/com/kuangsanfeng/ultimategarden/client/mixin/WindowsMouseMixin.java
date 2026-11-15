package com.kuangsanfeng.ultimategarden.client.mixin;


import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import com.kuangsanfeng.ultimategarden.client.window.WindowsInputInterceptor;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;



/**
 * Block real player mouse input while macro is running.
 *
 * VirtualMouse is not affected.
 */
@Mixin(MouseHandler.class)
public class WindowsMouseMixin {
    @org.spongepowered.asm.mixin.Unique private boolean shootout$manualPressAdmitted;



    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "grabMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;setAll()V"))
    private void keepPhysicalKeysReleased(com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        if (!com.kuangsanfeng.ultimategarden.client.ui.hud.HudAdjustmentController.isActive()) original.call();
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method="onButton",at=@At(value="INVOKE",target="Lnet/minecraft/client/MouseHandler;grabMouse()V"))
    private void shootout$keepCursor(net.minecraft.client.MouseHandler handler,com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original){if(!com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ShootoutMouseInput.dispatching())original.call(handler);}

    // =========================================================
    // Mouse Button
    // =========================================================

    @Inject(
            method = "onButton",
            at = @At("HEAD"),
            cancellable = true
    )
    private void interceptMouseButton(
            long window,
            MouseButtonInfo buttonInfo,
            int action,
            CallbackInfo ci
    ) {


        shootout$manualPressAdmitted=false;
        if (com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ShootoutMouseInput.dispatching()) return;
        if (com.kuangsanfeng.ultimategarden.client.ui.hud.HudAdjustmentController.isActive()) { com.kuangsanfeng.ultimategarden.client.ui.hud.HudAdjustmentController.button(buttonInfo.button(), action); ci.cancel(); return; }
        if (action==org.lwjgl.glfw.GLFW.GLFW_PRESS && net.minecraft.client.Minecraft.getInstance().screen==null
                && com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController.INSTANCE.enabled()
                && com.kuangsanfeng.ultimategarden.client.hotkey.MacroKeyBindings.matchesShootoutMouse(buttonInfo.button())) {
            com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController.INSTANCE.stop("HOTKEY_STOP");
            ci.cancel(); return;
        }

        if(window==net.minecraft.client.Minecraft.getInstance().getWindow().handle()
                && com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController.INSTANCE.blockManualShot(buttonInfo.button(),action)) {
            ci.cancel();return;
        }

        WindowsInputInterceptor interceptor =
                Ultimate_GardenClient
                        .getWindowsInputInterceptor();


        if (interceptor == null) {
            return;
        }


        if (
                interceptor.blockMouseButton(buttonInfo.button(), action)
        ) {

            com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController.INSTANCE.onManualShotBlockedByInterceptor(buttonInfo.button(),action);
            ci.cancel();

        } else if(window==net.minecraft.client.Minecraft.getInstance().getWindow().handle()) {
            com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController.INSTANCE.onManualButton(buttonInfo.button(),action);
            shootout$manualPressAdmitted=buttonInfo.button()==1&&action==org.lwjgl.glfw.GLFW.GLFW_PRESS;
        }

    }



    @Inject(method="onButton",at=@At("RETURN"))
    private void shootout$manualProcessed(long window,MouseButtonInfo buttonInfo,int action,CallbackInfo ci){
        boolean admitted=shootout$manualPressAdmitted;shootout$manualPressAdmitted=false;
        if(admitted&&window==net.minecraft.client.Minecraft.getInstance().getWindow().handle()&&buttonInfo.button()==1&&action==org.lwjgl.glfw.GLFW.GLFW_PRESS&&!com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ShootoutMouseInput.dispatching())
            com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController.INSTANCE.manualInputProcessed();
    }

    // =========================================================
    // Mouse Scroll
    // =========================================================

    @Inject(
            method = "onScroll",
            at = @At("HEAD"),
            cancellable = true
    )
    private void interceptMouseScroll(
            long window,
            double xoffset,
            double yoffset,
            CallbackInfo ci
    ) {


        if (com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ShootoutMouseInput.dispatching()) return;
        if (com.kuangsanfeng.ultimategarden.client.ui.hud.HudAdjustmentController.isActive()) {  ci.cancel(); return; }

        WindowsInputInterceptor interceptor =
                Ultimate_GardenClient
                        .getWindowsInputInterceptor();


        if (interceptor == null) {
            return;
        }


        if (
                interceptor.blockMouseScroll()
        ) {

            ci.cancel();

        }

    }



    // =========================================================
    // Mouse Move
    // =========================================================

    @Inject(
            method = "onMove",
            at = @At("HEAD"),
            cancellable = true
    )
    private void interceptMouseMove(
            long window,
            double xpos,
            double ypos,
            CallbackInfo ci
    ) {


        if (com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ShootoutMouseInput.dispatching()) return;
        if (com.kuangsanfeng.ultimategarden.client.ui.hud.HudAdjustmentController.isActive()) { com.kuangsanfeng.ultimategarden.client.ui.hud.HudAdjustmentController.move(xpos, ypos); ci.cancel(); return; }

        WindowsInputInterceptor interceptor =
                Ultimate_GardenClient
                        .getWindowsInputInterceptor();


        if (interceptor == null) {
            return;
        }


        if (
                interceptor.blockMouseMove()
        ) {

            ci.cancel();

        }

    }


    // =========================================================
    // Additional Safeguard: Handle Accumulated Movement
    // =========================================================

    @Inject(
            method = "handleAccumulatedMovement",
            at = @At("HEAD"),
            cancellable = true
    )
    private void interceptHandleAccumulatedMovement(
            CallbackInfo ci
    ) {

        if (com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ShootoutMouseInput.dispatching()) return;
        if (com.kuangsanfeng.ultimategarden.client.ui.hud.HudAdjustmentController.isActive()) {  ci.cancel(); return; }

        WindowsInputInterceptor interceptor =
                Ultimate_GardenClient
                        .getWindowsInputInterceptor();


        if (interceptor == null) {
            return;
        }


        if (
                interceptor.blockMouseMove()
        ) {

            ci.cancel();

        }

    }

}
