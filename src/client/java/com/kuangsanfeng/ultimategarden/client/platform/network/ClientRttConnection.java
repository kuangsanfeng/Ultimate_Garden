package com.kuangsanfeng.ultimategarden.client.platform.network;
import com.kuangsanfeng.ultimategarden.client.platform.network.NativeRttService;

import net.minecraft.client.Minecraft;
import java.util.function.DoubleConsumer;
/** One native connection service. No tick scheduling or business compensation policy. */
public final class ClientRttConnection {
 private static final NativeRttService SERVICE=new NativeRttService();
 private static DoubleConsumer sample=value->{};
 static {com.kuangsanfeng.ultimategarden.client.platform.context.ClientContextEvents.onDisconnect((handler,client)->SERVICE.stop());}
 public static java.util.function.LongConsumer sender(net.minecraft.client.multiplayer.ClientPacketListener connection){
  return token->{if(connection!=null)connection.send(new net.minecraft.network.protocol.ping.ServerboundPingRequestPacket(token));};
 }
 private ClientRttConnection(){}
 public static NativeRttService service(){return SERVICE;}
 public static void onSample(DoubleConsumer listener){sample=java.util.Objects.requireNonNull(listener);}
 public static boolean pong(Object listener,long token){
  if(!SERVICE.owns(token))return false;
  long received=System.nanoTime();var client=Minecraft.getInstance();
  client.execute(()->{if(client.getConnection()==listener&&SERVICE.pong(listener,token,received))sample.accept(SERVICE.milliseconds());});
  return true;
 }
}
