package com.kuangsanfeng.ultimategarden.client.function.mapping;

import java.util.*;

/** Select from the observed cache only: never invent coordinates around a render distance. */
public final class MappingCandidates {
    private MappingCandidates() {}
    public static Optional<MappingController.Chunk> next(Collection<MappingController.Chunk> loaded,
            MappingController.Chunk current,Set<MappingController.Chunk> done,
            Set<MappingController.Chunk> pending,Set<MappingController.Chunk> suppressed){
        return loaded.stream().filter(k->!done.contains(k)&&!pending.contains(k)&&!suppressed.contains(k))
            .min(Comparator.comparingInt((MappingController.Chunk k)->k.equals(current)?0:1)
                .thenComparingInt(MappingController.Chunk::x).thenComparingInt(MappingController.Chunk::z));
    }
}
