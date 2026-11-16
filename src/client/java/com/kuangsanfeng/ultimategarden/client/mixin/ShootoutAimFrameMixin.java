package com.kuangsanfeng.ultimategarden.client.mixin;

import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Once per client frame, after client ticks and before native mouse handling/camera extraction. */
@Mixin(Minecraft.class)
public abstract class ShootoutAimFrameMixin {
 @Inject(method="runTick",at=@At(value="INVOKE",target="Lnet/minecraft/client/MouseHandler;handleAccumulatedMovement()V"))
 private void shootout$aimFrame(boolean advanceGameTime,CallbackInfo ci){ZombieShootoutController.INSTANCE.aimFrame();com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.InstantTransmissionController.INSTANCE.frame();}
}
