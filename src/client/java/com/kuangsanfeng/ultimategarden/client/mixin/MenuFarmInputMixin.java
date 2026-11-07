package com.kuangsanfeng.ultimategarden.client.mixin;

import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import com.kuangsanfeng.ultimategarden.client.ui.screen.UltimateGardenScreen;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only the non-pausing Ultimate Garden UI may pass virtual farming input. */
@Mixin(Minecraft.class)
public abstract class MenuFarmInputMixin {
    @Shadow private void handleKeybinds() { }
    private boolean keepsInput() {
        return ((Minecraft) (Object) this).screen instanceof UltimateGardenScreen menu && menu.keepsFarmingInput();
    }
    @WrapMethod(method = "setScreen")
    private void preserveVirtualHolds(Screen next, Operation<Void> original) {
        Minecraft client = (Minecraft) (Object) this;
        boolean fromRunningMenu = keepsInput();
        original.call(next);
        if ((keepsInput() || fromRunningMenu && client.screen == null) && client.player != null && client.level != null) {
            Ultimate_GardenClient.getFarmController().reapplyMenuInput();
            client.missTime = 0;
        }
    }
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;tick()V", shift = At.Shift.AFTER))
    private void tickVirtualFarming(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        if (!keepsInput() || client.player == null || client.level == null || client.getOverlay() != null) return;
        client.missTime = 0;
        Ultimate_GardenClient.getFarmController().reapplyMenuInput();
        handleKeybinds();
    }
    @WrapOperation(method = "handleKeybinds", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;"))
    private Screen allowVirtualAttack(Minecraft client, Operation<Screen> original) {
        return keepsInput() ? null : original.call(client);
    }
    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z"))
    private boolean allowVirtualHold(MouseHandler mouse, Operation<Boolean> original) {
        return keepsInput() || original.call(mouse);
    }
}
