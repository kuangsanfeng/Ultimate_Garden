package com.kuangsanfeng.ultimategarden.client.mixin;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.gen.Accessor;
/** Read-only identity shared by CONFIGURATION and PLAY, including Hello before a player exists. */
@Mixin(ClientCommonPacketListenerImpl.class)
public interface WorldInfoConnectionAccessor {@Accessor("connection") Connection worldInfo$connection();}
