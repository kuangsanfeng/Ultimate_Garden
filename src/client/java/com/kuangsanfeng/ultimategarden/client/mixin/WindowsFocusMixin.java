package com.kuangsanfeng.ultimategarden.client.mixin;


import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import com.kuangsanfeng.ultimategarden.client.interaction.windows.WindowsState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(Minecraft.class)
public class WindowsFocusMixin {


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