package com.kuangsanfeng.ultimategarden.client.function.mapping;

import java.nio.file.Path;

/** Shared instance-local root for recording, replay, indices and map management. No legacy fallback. */
public final class MappingPaths {
    private MappingPaths() {}
    public static Path root(Path gameDirectory) {
        return gameDirectory.toAbsolutePath().normalize().resolve("config").resolve("ultimategarden").resolve("map");
    }
}
