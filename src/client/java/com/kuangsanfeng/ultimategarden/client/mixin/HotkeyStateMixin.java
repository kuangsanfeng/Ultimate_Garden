package com.kuangsanfeng.ultimategarden.client.mixin;
import com.kuangsanfeng.ultimategarden.client.runtime.hotkey.HotkeySwitches;
import com.kuangsanfeng.ultimategarden.client.platform.input.PhysicalGameInput;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(KeyMapping.class)
public abstract class HotkeyStateMixin {
 @Shadow private int clickCount;
 @Redirect(method="forAllKeyMappings",at=@At(value="INVOKE",target="Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"))
 private static void ug$dispatch(java.util.function.Consumer<KeyMapping> action,Object value){var mapping=(KeyMapping)value;if(HotkeySwitches.enabled(mapping)&&!PhysicalGameInput.blocksMapping(mapping))action.accept(mapping);}

 @WrapOperation(method="setAll", at=@At(value="INVOKE",target="Lnet/minecraft/client/KeyMapping;setDown(Z)V"))
 private static void ug$restorePhysicalKey(KeyMapping mapping, boolean down, Operation<Void> original) {
  var client=Minecraft.getInstance();
  if(!PhysicalGameInput.blocksGame(client)||!PhysicalGameInput.isBlockedMapping(client.options,mapping))
   original.call(mapping,down);
 }

 private boolean ug$enabled(){return HotkeySwitches.enabled((KeyMapping)(Object)this);}
 @Inject(method="isDown",at=@At("HEAD"),cancellable=true) private void ug$down(CallbackInfoReturnable<Boolean> ci){if(!ug$enabled())ci.setReturnValue(false);}
 @Inject(method="setDown",at=@At("HEAD"),cancellable=true) private void ug$set(boolean down,CallbackInfo ci){if(down&&!ug$enabled())ci.cancel();}
 @Inject(method="consumeClick",at=@At("HEAD"),cancellable=true) private void ug$consume(CallbackInfoReturnable<Boolean> ci){if(!ug$enabled()){clickCount=0;ci.setReturnValue(false);}}
 @Inject(method="same",at=@At("HEAD"),cancellable=true) private void ug$same(KeyMapping other,CallbackInfoReturnable<Boolean> ci){if(!ug$enabled()||!HotkeySwitches.enabled(other))ci.setReturnValue(false);}
 @Inject(method="matches",at=@At("HEAD"),cancellable=true) private void ug$key(KeyEvent e,CallbackInfoReturnable<Boolean> ci){if(!ug$enabled())ci.setReturnValue(false);}
 @Inject(method="matchesMouse",at=@At("HEAD"),cancellable=true) private void ug$mouse(MouseButtonEvent e,CallbackInfoReturnable<Boolean> ci){if(!ug$enabled())ci.setReturnValue(false);}
}
