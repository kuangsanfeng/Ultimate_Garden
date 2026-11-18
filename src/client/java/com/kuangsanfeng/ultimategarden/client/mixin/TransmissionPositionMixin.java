package com.kuangsanfeng.ultimategarden.client.mixin;
import net.minecraft.client.Minecraft;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.execution.ClientPathFinding;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.InstantTransmissionController;
@Mixin(ClientPacketListener.class)
public class TransmissionPositionMixin {
 @Unique private Vec3 transmission$before;
 @Inject(method="handleMovePlayer",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientPacketListener;setValuesFromPositionPacket(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;Lnet/minecraft/world/entity/Entity;Z)Z"))
 private void transmission$before(ClientboundPlayerPositionPacket packet,CallbackInfo ci){var c=Minecraft.getInstance();transmission$before=(InstantTransmissionController.INSTANCE.running()||ClientPathFinding.INSTANCE.observingPosition())&&c.player!=null?c.player.position():null;}
 @Inject(method="handleMovePlayer",at=@At("RETURN"))
 private void transmission$after(ClientboundPlayerPositionPacket packet,CallbackInfo ci){var before=transmission$before;transmission$before=null;var c=Minecraft.getInstance();if(before!=null&&c.player!=null){if(InstantTransmissionController.INSTANCE.running())InstantTransmissionController.INSTANCE.positionUpdate(before,c.player.position());if(ClientPathFinding.INSTANCE.observingPosition())ClientPathFinding.INSTANCE.positionUpdate(c.player.position());}}
}
