package com.kuangsanfeng.ultimategarden.client.mixin;
import net.minecraft.client.MouseHandler;import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.gen.*;
@Mixin(MouseHandler.class)
public interface ShootoutMouseAccessor {
 @Accessor("accumulatedDX") double shootout$getDX(); @Accessor("accumulatedDX") void shootout$setDX(double v);
 @Accessor("accumulatedDY") double shootout$getDY(); @Accessor("accumulatedDY") void shootout$setDY(double v);
 @Accessor("isRightPressed") void shootout$setRightPressed(boolean pressed);
 @Invoker("turnPlayer") void shootout$turn(double elapsed);
 @Invoker("onButton") void shootout$button(long window,MouseButtonInfo info,int action);
}
