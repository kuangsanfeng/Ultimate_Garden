package com.kuangsanfeng.ultimategarden.client.function.world;
/** Immutable current world information. Missing fields are null; contains no game objects. */
public record WorldInfoSnapshot(String server,String dimension,String gameType,String island,String subArea,String instance,String mode,String map,String lobbyName,String environment){
  public WorldInfoSnapshot(String server,String dimension){this(server,dimension,null,null,null,null,null,null,null,null);}
  public String value(String field){return switch(field){case "server"->server;case "dimension"->dimension;case "game_type"->gameType;case "island"->island;case "sub_area"->subArea;case "instance"->instance;default->null;};}
 }
