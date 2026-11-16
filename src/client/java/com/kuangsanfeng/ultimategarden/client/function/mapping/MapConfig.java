package com.kuangsanfeng.ultimategarden.client.function.mapping;

import java.util.*;

/** Only stable map storage data; never a copy of the live world-information snapshot. */
public record MapConfig(int schemaVersion, String worldName, Origin origin, int maxY,
                        Set<Festival> festivals, Festival selectedFestival) {
    public record Origin(int x, int y, int z) {}
    public MapConfig {
        MappingNames.key(worldName);
        if (schemaVersion != 1 || origin == null || origin.x % 16 != 0 || origin.z % 16 != 0
                || maxY <= origin.y || (long)maxY - origin.y > 4096) throw new IllegalArgumentException("INVALID_MAP_CONFIG");
        festivals = Set.copyOf(festivals);
        if (!festivals.contains(Festival.NONE) || !festivals.contains(selectedFestival)) throw new IllegalArgumentException("INVALID_MAP_LAYERS");
    }
    public MapConfig layer(Festival f, boolean create) {
        var layers = new HashSet<>(festivals); if (create) layers.add(f);
        return new MapConfig(1, worldName, origin, maxY, layers, f);
    }
    public MapConfig origin(int chunkX, int chunkZ) {
        return new MapConfig(1, worldName, new Origin(Math.multiplyExact(chunkX,16),origin.y,Math.multiplyExact(chunkZ,16)),maxY,festivals,selectedFestival);
    }
    public int relativeX(int chunkX) { return Math.subtractExact(chunkX, Math.floorDiv(origin.x,16)); }
    public int relativeZ(int chunkZ) { return Math.subtractExact(chunkZ, Math.floorDiv(origin.z,16)); }
}
