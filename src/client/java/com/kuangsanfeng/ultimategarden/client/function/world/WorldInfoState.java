package com.kuangsanfeng.ultimategarden.client.function.world;
import java.util.*;
/** Client-thread state. Official location is connection scoped; text is world/instance scoped. */
public final class WorldInfoState {
 public record Location(String serverName,String serverType,String mode,String map,String lobbyName){public Location(String serverName,String serverType,String mode,String map){this(serverName,serverType,mode,map,null);}}
 public record TextFrame(List<String> tab,List<String> sidebar){public TextFrame{tab=List.copyOf(tab);sidebar=List.copyOf(sidebar);}public static final TextFrame EMPTY=new TextFrame(List.of(),List.of());}
 public static final WorldInfoSnapshot UNKNOWN=new WorldInfoSnapshot(null,null);
 private Object world,connection;private volatile WorldInfoSnapshot snapshot=UNKNOWN;private Location location;private String environment;private boolean helloReceived;
 private TextFrame last=TextFrame.EMPTY,barrier=TextFrame.EMPTY;private boolean tabWaiting,sidebarWaiting;
 private void publish(WorldInfoSnapshot value){if(!snapshot.equals(value))snapshot=value;}
 private static String present(String value){return value==null||value.isBlank()?null:value;}
 public WorldInfoSnapshot snapshot(){return snapshot;}
 public boolean acceptHello(Object source,Object current,long capturedGeneration,long currentGeneration,String value){if(source==null||source!=current||capturedGeneration!=currentGeneration)return false;hello(source,value);return true;}
 public void hello(Object connection,String value){if(connection==null)return;if(this.connection!=connection){clear();this.connection=connection;}environment=value;helloReceived=true;publish(new WorldInfoSnapshot(snapshot.server(),snapshot.dimension(),snapshot.gameType(),snapshot.island(),snapshot.subArea(),snapshot.instance(),snapshot.mode(),snapshot.map(),snapshot.lobbyName(),environment));}
 public boolean hasHypixel(Object connection){return connection!=null&&this.connection==connection&&(helloReceived||location!=null);}
 public boolean acceptLocation(Object source,Object current,long capturedGeneration,long currentGeneration,Location value){if(source==null||source!=current||capturedGeneration!=currentGeneration)return false;location(source,value);return true;}
 public void location(Object connection,Location value){
  if(connection==null)return;if(this.connection!=connection){clear();this.connection=connection;}
  boolean changed=location==null||!Objects.equals(location.serverName(),value.serverName())||!Objects.equals(location.serverType(),value.serverType())||!Objects.equals(location.mode(),value.mode());location=value;
  if(changed){barrier=last;tabWaiting=sidebarWaiting=true;}publish(new WorldInfoSnapshot(snapshot.server(),snapshot.dimension(),present(value.serverType()),changed?null:snapshot.island(),changed?null:snapshot.subArea(),present(value.serverName()),value.mode(),value.map(),value.lobbyName(),environment));
 }
 /** Suppress the previous world's text immediately while retaining the latest authoritative connection event. */
 public void invalidateWorld(){world=null;barrier=last;tabWaiting=sidebarWaiting=true;publish(new WorldInfoSnapshot(null,null,null,null,null,null,null,null,null,environment));}
 public void update(Object world,Object connection,String server,String dimension){update(world,connection,server,dimension,false,TextFrame.EMPTY);}
 public void update(Object world,Object connection,String server,String dimension,boolean hypixel,TextFrame text){
  if(world==null||connection==null){clear();return;}
  if(this.connection!=connection){clear();this.connection=connection;}
  if(this.world!=null&&this.world!=world)invalidateWorld();this.world=world;
  if(!hypixel){environment=null;helloReceived=false;location=null;tabWaiting=sidebarWaiting=false;last=TextFrame.EMPTY;publish(new WorldInfoSnapshot(server,dimension));return;}
  String type=location==null?null:present(location.serverType());
  String serverName=location==null?null:present(location.serverName());
  if(tabWaiting&&!text.tab().equals(barrier.tab()))tabWaiting=false;
  if(sidebarWaiting&&!text.sidebar().equals(barrier.sidebar()))sidebarWaiting=false;
  boolean regions=location!=null&&(type==null||"SKYBLOCK".equals(type));String area=null,sub=null;
  if(regions){area=tabWaiting?null:WorldInfoText.area(text.tab());if(area==null)area=WorldInfoText.modeName(location.mode());sub=sidebarWaiting?null:WorldInfoText.subArea(text.sidebar());}
  publish(new WorldInfoSnapshot(server,dimension,type,area,sub,serverName,location==null?null:location.mode(),location==null?null:location.map(),location==null?null:location.lobbyName(),environment));last=text;
 }
 public boolean hasLocation(Object connection){return connection!=null&&this.connection==connection&&location!=null;}
 public WorldInfoSnapshot read(Object world,Object connection){return world!=null&&connection!=null&&this.world==world&&this.connection==connection?snapshot:UNKNOWN;}
 public void clear(){world=connection=null;location=null;environment=null;helloReceived=false;snapshot=UNKNOWN;last=barrier=TextFrame.EMPTY;tabWaiting=sidebarWaiting=false;}
}
