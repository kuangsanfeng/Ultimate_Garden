package com.kuangsanfeng.ultimategarden.client.mixin;


import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Minecraft;


@Mixin(Minecraft.class)
public class WindowsPauseMixin {


    @Inject(
            method = "pauseIfInactive",
            at = @At("HEAD"),
            cancellable = true
    )
    private void blockPauseWhenWindowsReleased(
            CallbackInfo ci
    ) {

        if (Ultimate_GardenClient
                .getWindowsState()
                .isMouseReleased()) {


            ci.cancel();

        }

    }

}