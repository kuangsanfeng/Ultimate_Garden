package com.kuangsanfeng.ultimategarden.client.function.mapping.replay;

import com.google.gson.JsonParser;
import com.kuangsanfeng.ultimategarden.client.function.mapping.*;
import com.kuangsanfeng.ultimategarden.client.function.mapping.MapRepository.Coordinate;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Validated local-only map index. Never consumes a live client world or server instance. */
public final class LocalMapLibrary {
    public record World(MapRepository repository,MapConfig config){}
    public record Index(List<World> worlds,List<String> errors){public Index{worlds=List.copyOf(worlds);errors=List.copyOf(errors);}}
    public static Index list(Path root)throws IOException{
        if(!Files.exists(root))return new Index(List.of(),List.of());if(Files.isSymbolicLink(root))throw new IOException("MAP_SYMLINK_REJECTED");
        var worlds=new ArrayList<World>();var errors=new ArrayList<String>();
        try(var dirs=Files.list(root)){for(Path dir:dirs.sorted().toList()){
            if(!Files.isDirectory(dir)||Files.isSymbolicLink(dir)||dir.getFileName().toString().startsWith("."))continue;
            try(var reader=Files.newBufferedReader(dir.resolve("map.json"))){String name=JsonParser.parseReader(reader).getAsJsonObject().get("worldName").getAsString();var repo=new MapRepository(root,name);if(!repo.directory().equals(dir.toAbsolutePath().normalize()))throw new IOException("MAP_DIRECTORY_NAME_MISMATCH");var cfg=repo.load();if(cfg!=null)worlds.add(new World(repo,cfg));}
            catch(Exception e){errors.add(dir.getFileName()+": "+e.getMessage());}
        }}return new Index(worlds,errors);
    }
    public static Set<Coordinate> composed(World world,Festival layer)throws IOException{var coords=new HashSet<>(world.repository.index(world.config,Festival.NONE));if(layer!=Festival.NONE)coords.addAll(world.repository.index(world.config,layer));return Set.copyOf(coords);}
}
