package com.kuangsanfeng.ultimategarden.client.mixin;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.InstantTransmissionController;
import net.minecraft.client.Minecraft;import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;
/** Observes vanilla key consumption; never cancels input or writes KeyMapping / player state. */
@Mixin(KeyboardInput.class)
public abstract class TransmissionMovementInputMixin {
 @Inject(method="tick",at=@At("RETURN"))
 private void transmission$readInput(CallbackInfo ci){
  var controller=InstantTransmissionController.INSTANCE;if(!controller.resetDiagnosticsActive())return;
  var c=Minecraft.getInstance();if(c.player==null||c.player.input!=(Object)this)return;
  var input=((KeyboardInput)(Object)this).keyPresses;var vector=((KeyboardInput)(Object)this).getMoveVector();
  controller.resetNativeInput(Map.of("W",input.forward(),"S",input.backward(),"A",input.left(),"D",input.right(),
   "SPACE",input.jump(),"SHIFT",input.shift(),"moveX",vector.x,"moveForward",vector.y,"playerTick",c.player.tickCount));
 }
}
