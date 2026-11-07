package com.kuangsanfeng.ultimategarden.client.mixin;

import com.kuangsanfeng.ultimategarden.client.input.PlayerGuiInput;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep input provenance aligned with vanilla's inventory click queue. */
@Mixin(KeyMapping.class)
public class InventoryInputMixin {
    private static boolean isInventory(KeyMapping mapping) {
        var client = Minecraft.getInstance();
        return client.options != null && mapping == client.options.keyInventory;
    }

    @Inject(method = "click", at = @At("HEAD"))
    private static void recordClick(InputConstants.Key key, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (client.options != null
                && ((KeyMappingAccessor) client.options.keyInventory).getKey().equals(key)) {
            PlayerGuiInput.inventoryClick();
        }
    }

    @Inject(method = "consumeClick", at = @At("RETURN"))
    private void consumeClick(CallbackInfoReturnable<Boolean> cir) {
        if (isInventory((KeyMapping) (Object) this)) PlayerGuiInput.inventoryConsumed(cir.getReturnValue());
    }

    @Inject(method = "release", at = @At("HEAD"))
    private void release(CallbackInfo ci) {
        // setScreen releases queued clicks before Screen.init; keep the active opening.
        if (isInventory((KeyMapping) (Object) this)) PlayerGuiInput.inventoryReleased();
    }
}
