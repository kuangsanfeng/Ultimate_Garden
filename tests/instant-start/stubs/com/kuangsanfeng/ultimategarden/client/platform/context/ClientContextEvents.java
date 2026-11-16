package com.kuangsanfeng.ultimategarden.client.platform.context;
import net.minecraft.client.Minecraft;import java.util.function.*;
public final class ClientContextEvents {public enum Change{ACCOUNT_BEFORE_LOAD,ACCOUNT_AFTER_LOAD,WORLD_CHANGED}public record Transition(Change change){}public static void onChange(Consumer<Transition> c){}public static void onDisconnect(BiConsumer<Object,Minecraft> c){}public static void onStopping(Consumer<Minecraft> c){}}
