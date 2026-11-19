package com.kuangsanfeng.ultimategarden.client.function.world;
import com.kuangsanfeng.ultimategarden.client.platform.context.ClientContextEvents;
import com.kuangsanfeng.ultimategarden.client.mixin.WorldInfoConnectionAccessor;
import net.minecraft.client.Minecraft;import net.minecraft.network.Connection;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.hypixel.data.type.ServerType;import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundHelloPacket;
import net.minecraft.world.scores.*;import java.util.*;
/** Client-thread collection/merging; no UI dependency and no queries or requests in snapshot(). */
public final class WorldInfoService {
 private static final WorldInfoState state=new WorldInfoState();private static boolean registered;
 private static volatile long generation;private static volatile Connection connection;
 private WorldInfoService(){}
 public static WorldInfoSnapshot snapshot(){return state.snapshot();}
 public static boolean trustedLocation(){var source=connection;return source!=null&&source.isConnected()&&state.hasHypixel(source); }
 private static void clear(){generation++;connection=null;state.clear();}
 private static void bind(Connection value){if(connection!=value){generation++;connection=value;state.clear();}}
 public static void register(){if(registered)return;registered=true;
  ClientConfigurationConnectionEvents.INIT.register((handler,c)->{bind(((WorldInfoConnectionAccessor)handler).worldInfo$connection());state.invalidateWorld();});
  ClientConfigurationConnectionEvents.DISCONNECT.register((handler,c)->{if(connection==((WorldInfoConnectionAccessor)handler).worldInfo$connection())clear();});
  ClientPlayConnectionEvents.INIT.register((handler,c)->bind(handler.getConnection()));
  HypixelModAPI.getInstance().createHandler(ClientboundHelloPacket.class,packet->{
   var client=Minecraft.getInstance();var source=connection;long epoch=generation;String environment=packet.getEnvironment()==null?null:packet.getEnvironment().name();
   client.execute(()->{if(source!=null&&source.isConnected())state.acceptHello(source,connection,epoch,generation,environment);});
  });
  HypixelModAPI.getInstance().createHandler(ClientboundLocationPacket.class,packet->{
   var client=Minecraft.getInstance();var source=connection;long epoch=generation;
   var data=new WorldInfoState.Location(packet.getServerName(),packet.getServerType().map(ServerType::name).orElse(null),packet.getMode().orElse(null),packet.getMap().orElse(null),packet.getLobbyName().orElse(null));
   client.execute(()->{if(source!=null&&source.isConnected())state.acceptLocation(source,connection,epoch,generation,data);});
  });
  HypixelModAPI.getInstance().subscribeToEventPacket(ClientboundLocationPacket.class);
  ClientContextEvents.onDisconnect((handler,c)->{if(connection==handler.getConnection())clear();});
  ClientContextEvents.onChange(e->{if(e.change()==ClientContextEvents.Change.WORLD_CHANGED)state.invalidateWorld();else{generation++;state.invalidateWorld();}});
  ClientContextEvents.onStopping(c->clear());ClientTickEvents.END_CLIENT_TICK.register(WorldInfoService::tick);
 }
 private static void tick(Minecraft c){
  var listener=c.getConnection();if(c.level==null||listener==null){if(connection==null)state.clear();else state.invalidateWorld();return;}
  var raw=listener.getConnection();if(!raw.isConnected()){clear();return;}bind(raw);
  var server=c.getCurrentServer();boolean hypixel=server!=null&&state.hasHypixel(raw);
  var tab=new ArrayList<String>();var sidebar=new ArrayList<String>();
  if(hypixel){
   for(var info:listener.getListedOnlinePlayers())tab.add(WorldInfoText.clean(c.gui.getTabList().getNameForDisplay(info).getString()));
   var board=c.level.getScoreboard();Objective objective=null;
   var team=c.player==null?null:board.getPlayersTeam(c.player.getScoreboardName());if(team!=null){var slot=DisplaySlot.teamColorToSlot(team.getColor());if(slot!=null)objective=board.getDisplayObjective(slot);}
   if(objective==null)objective=board.getDisplayObjective(DisplaySlot.SIDEBAR);
   if(objective!=null)board.listPlayerScores(objective).stream().filter(entry->!entry.isHidden()).sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed().thenComparing(PlayerScoreEntry::owner,String.CASE_INSENSITIVE_ORDER)).limit(15).forEach(entry->sidebar.add(WorldInfoText.clean(PlayerTeam.formatNameForTeam(board.getPlayersTeam(entry.owner()),entry.ownerName()).getString())));
  }
  state.update(c.level,raw,server==null?null:server.ip,c.level.dimension().identifier().toString(),hypixel,new WorldInfoState.TextFrame(tab,sidebar));
 }
}
