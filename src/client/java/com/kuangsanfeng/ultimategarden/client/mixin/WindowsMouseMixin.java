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


        WindowsInputInterceptor interceptor =
                Ultimate_GardenClient
                        .getWindowsInputInterceptor();


        if (interceptor == null) {
            return;
        }


        if (
                interceptor.blockMouseButton()
        ) {

            ci.cancel();

        }

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