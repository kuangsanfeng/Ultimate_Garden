package com.kuangsanfeng.ultimategarden.client.mixin;

import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only consume this module's tokens; vanilla/debug and other mods' responses remain untouched. */
@Mixin(ClientPacketListener.class)
public class ShootoutPongMixin {
 @Inject(method="handlePongResponse",at=@At("HEAD"),cancellable=true)
 private void shootout$pong(ClientboundPongResponsePacket packet,CallbackInfo ci){
  if(com.kuangsanfeng.ultimategarden.client.platform.network.ClientRttConnection.pong(this,packet.time()))ci.cancel();
 }
}
