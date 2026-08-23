package com.kuangsanfeng.ultimategarden.client.mixin;

import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class WindowsMissTimeMixin {

    @Shadow
    @Final
    private Minecraft minecraft; // MouseHandler 内部自带的 minecraft 实例字段

    @Inject(
            method = "grabMouse",
            at = @At("TAIL")
    )
    private void clearMissTimeOnGrab(CallbackInfo ci) {
        if (Ultimate_GardenClient.getWindowsState() != null &&
                !Ultimate_GardenClient.getWindowsState().isMouseReleased()) {

            // 直接通过强转修改 Minecraft 的 missTime，完全不需要额外的 Accessor 文件
            this.minecraft.missTime = 0;
        }
    }
}