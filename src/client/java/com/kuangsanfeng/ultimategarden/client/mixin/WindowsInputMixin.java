package com.kuangsanfeng.ultimategarden.client.mixin;


import com.kuangsanfeng.ultimategarden.Ultimate_Garden;
import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import com.kuangsanfeng.ultimategarden.client.interaction.windows.WindowsInputInterceptor;
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