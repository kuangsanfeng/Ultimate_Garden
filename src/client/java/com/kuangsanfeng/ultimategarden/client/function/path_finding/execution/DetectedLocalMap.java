package com.kuangsanfeng.ultimategarden.client.function.path_finding.execution;

import com.google.gson.JsonParser;
import com.kuangsanfeng.ultimategarden.client.function.mapping.MapRepository;
import com.kuangsanfeng.ultimategarden.client.function.mapping.MappingNames;
import com.kuangsanfeng.ultimategarden.client.function.world.WorldInfoSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

/** Resolves current world identifiers against the name recorded in the map metadata. */
public record DetectedLocalMap(Status status,String name) {
    public enum Status { FOUND, UNKNOWN, MISSING, AMBIGUOUS }
    public static DetectedLocalMap resolve(Path root,WorldInfoSnapshot world,boolean trusted) {
        if (!trusted) return new DetectedLocalMap(Status.UNKNOWN,null);
        var matches=new LinkedHashMap<String,String>();
        String identifier=null;
        // Subareas may be ordinary landmarks. An exact local dataset makes one actionable.
        for (String value:new String[]{world.map(),world.subArea(),world.island()}) {
            if (value==null || value.isBlank()) continue;
            if (identifier==null) identifier=value;
            try {
                String key=MappingNames.key(value.strip());
                var folder=root.resolve(key);
                if (Files.isSymbolicLink(folder) || Files.isSymbolicLink(folder.resolve("map.json"))) continue;
                try (var reader=Files.newBufferedReader(folder.resolve("map.json"))) {
                    var metadata=JsonParser.parseReader(reader).getAsJsonObject();
                    String name=metadata.get("worldName").getAsString();
                    if (!MappingNames.key(name).equals(key)) continue;
                    var repository=new MapRepository(root,name);
                    var config=repository.load();
                    if (config!=null && !repository.index(config,config.selectedFestival()).isEmpty())
                        matches.put(key,name);
                    else if(config!=null && !repository.index(config,com.kuangsanfeng.ultimategarden.client.function.mapping.Festival.NONE).isEmpty())
                        matches.put(key,name);
                }
            } catch (IOException | RuntimeException ignored) {
                // A missing or invalid catalog entry cannot be a planning dataset.
            }
        }
        if (matches.size()>1) return new DetectedLocalMap(Status.AMBIGUOUS,null);
        if (matches.size()==1) return new DetectedLocalMap(Status.FOUND,matches.values().iterator().next());
        return new DetectedLocalMap(identifier==null?Status.UNKNOWN:Status.MISSING,identifier);
    }
}
