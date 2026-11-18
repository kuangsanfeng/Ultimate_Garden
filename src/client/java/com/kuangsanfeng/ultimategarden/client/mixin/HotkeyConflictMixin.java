package com.kuangsanfeng.ultimategarden.client.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.kuangsanfeng.ultimategarden.client.runtime.hotkey.HotkeySwitches;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(targets="net.minecraft.client.gui.screens.options.controls.KeyBindsList$KeyEntry")
public abstract class HotkeyConflictMixin {
 @ModifyExpressionValue(method="refreshEntry",at=@At(value="FIELD",target="Lnet/minecraft/client/Options;keyMappings:[Lnet/minecraft/client/KeyMapping;"))
 private KeyMapping[] ug$visible(KeyMapping[] keys){return HotkeySwitches.visible(keys);}
}
