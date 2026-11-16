package com.kuangsanfeng.ultimategarden.client.platform.context;
import com.kuangsanfeng.ultimategarden.client.platform.context.ContextSignal;


import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Fabric bridges register once. Listener order is the original module registration order. */
public final class ClientContextEvents {
 public enum Change { ACCOUNT_BEFORE_LOAD, ACCOUNT_AFTER_LOAD, WORLD_CHANGED }
 public record Transition(Change change, Object previous, Object current) {}
 private record Disconnect(ClientPacketListener handler, Minecraft client) {}
 private static final ContextSignal<Disconnect> DISCONNECT = new ContextSignal<>();
 private static final ContextSignal<Minecraft> STOP = new ContextSignal<>();
 private static final ContextSignal<Transition> CHANGES = new ContextSignal<>();
 private static boolean disconnectInstalled, stopInstalled;
 private static Object observedWorld;
 private static boolean worldObserved;
 private ClientContextEvents() {}
 public static void onDisconnect(BiConsumer<ClientPacketListener,Minecraft> listener) {
  DISCONNECT.subscribe(e -> listener.accept(e.handler(),e.client()));
  if (!disconnectInstalled) {
   disconnectInstalled=true;
   ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{try{DISCONNECT.publish(new Disconnect(handler,client));}finally{observedWorld=null;worldObserved=false;}});
  }
 }
 public static void onStopping(Consumer<Minecraft> listener) {
  STOP.subscribe(listener);
  if (!stopInstalled) {
   stopInstalled=true;
   ClientLifecycleEvents.CLIENT_STOPPING.register(client->{try{STOP.publish(client);}finally{observedWorld=null;worldObserved=false;}});
  }
 }
 public static void onChange(Consumer<Transition> listener) { CHANGES.subscribe(listener); }
 public static void accountBefore(Object previous,Object current) { CHANGES.publish(new Transition(Change.ACCOUNT_BEFORE_LOAD,previous,current)); }
 public static void accountAfter(Object previous,Object current) { CHANGES.publish(new Transition(Change.ACCOUNT_AFTER_LOAD,previous,current)); }
 /** Called at the existing Farm world-observation phase, without adding a tick driver. */
 public static void observeWorld(Object world) {
  if(worldObserved && observedWorld != world) CHANGES.publish(new Transition(Change.WORLD_CHANGED,observedWorld,world));
  observedWorld=world;worldObserved=true;
 }
}
