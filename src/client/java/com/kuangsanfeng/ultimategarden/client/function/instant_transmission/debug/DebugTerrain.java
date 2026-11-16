package com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing.*;
public final class DebugTerrain {private DebugTerrain(){} public static Terrain current(){return new ClientTerrain(net.minecraft.client.Minecraft.getInstance().level);}}
