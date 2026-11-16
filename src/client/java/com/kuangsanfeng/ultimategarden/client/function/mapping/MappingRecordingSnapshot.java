package com.kuangsanfeng.ultimategarden.client.function.mapping;

import com.kuangsanfeng.ultimategarden.client.function.mapping.MapRepository.Coordinate;
import java.util.Set;

/** One consistent, immutable recorder context. Rendering never enumerates the filesystem. */
public record MappingRecordingSnapshot(String worldName,MapConfig config,Coordinate current,boolean recording,
        Set<Coordinate> processing,Set<Coordinate> queued,Set<Coordinate> saved,Set<Coordinate> baseSaved) {
    public static final MappingRecordingSnapshot EMPTY=new MappingRecordingSnapshot("",null,null,false,Set.of(),Set.of(),Set.of(),Set.of());
    public MappingRecordingSnapshot{processing=Set.copyOf(processing);queued=Set.copyOf(queued);saved=Set.copyOf(saved);baseSaved=Set.copyOf(baseSaved);}
    public enum ChunkState { PROCESSING, QUEUED, SAVED, BASE_FALLBACK, UNRECORDED, UNKNOWN }
    public ChunkState currentState(){if(config==null||current==null)return ChunkState.UNKNOWN;if(processing.contains(current))return ChunkState.PROCESSING;if(queued.contains(current))return ChunkState.QUEUED;if(saved.contains(current))return ChunkState.SAVED;if(config.selectedFestival()!=Festival.NONE&&baseSaved.contains(current))return ChunkState.BASE_FALLBACK;return ChunkState.UNRECORDED;}
}
