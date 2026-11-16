package com.kuangsanfeng.ultimategarden.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.kuangsanfeng.ultimategarden.client.platform.ui.screen.MappingReplayScreen;

@Mixin(GameRenderer.class)
public abstract class MappingReplayBackgroundMixin {
    @Inject(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
    private void mapping$filterCurrentWorld(DeltaTracker tracker,boolean advanceGameTime,CallbackInfo ci){
        var client=Minecraft.getInstance();
        if(advanceGameTime&&client.level!=null&&client.screen instanceof MappingReplayScreen screen)screen.processWorldBackground();
    }
}
