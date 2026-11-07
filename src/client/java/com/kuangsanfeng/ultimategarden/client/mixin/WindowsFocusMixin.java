package com.kuangsanfeng.ultimategarden.client.mixin;


import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import com.kuangsanfeng.ultimategarden.client.window.WindowsState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(Minecraft.class)
public class WindowsFocusMixin {

    @Inject(method = "setScreen", at = @At("RETURN"))
    private void synchronizeMouseAfterScreenChange(net.minecraft.client.gui.screens.Screen screen, CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        var focus = Ultimate_GardenClient.getWindowsFocusController();
        if (focus != null && client.screen == null && client.player != null && client.level != null) {
            focus.reapplyCursorMode();
        }
    }


    @Inject(
            method = "isWindowActive",
            at = @At("HEAD"),
            cancellable = true
    )
    private void forceWindowActive(
            CallbackInfoReturnable<Boolean> cir
    ) {

        WindowsState state =
                Ultimate_GardenClient
                        .getWindowsState();


        if (state == null) {
            return;
        }


        if (state.isMouseReleased()) {

            cir.setReturnValue(true);

        }

    }

}
