package com.kuangsanfeng.ultimategarden.client.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.kuangsanfeng.ultimategarden.client.runtime.hotkey.HotkeySwitches;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(KeyBindsList.class)
public abstract class HotkeyListMixin {
 @Shadow @Final private net.minecraft.client.gui.screens.options.controls.KeyBindsScreen keyBindsScreen;
 @Unique private long ug$revision=com.kuangsanfeng.ultimategarden.client.runtime.hotkey.HotkeySwitches.revision();
 @Inject(method="refreshEntries",at=@At("HEAD"),cancellable=true) private void ug$refresh(CallbackInfo ci){if(ug$revision!=HotkeySwitches.revision()){ug$revision=HotkeySwitches.revision();((ScreenRebuildAccess)keyBindsScreen).ug$rebuildWidgets();ci.cancel();}}

 @ModifyExpressionValue(method="<init>",at=@At(value="FIELD",target="Lnet/minecraft/client/Options;keyMappings:[Lnet/minecraft/client/KeyMapping;"))
 private KeyMapping[] ug$visible(KeyMapping[] keys){return HotkeySwitches.visible(keys);}
}
