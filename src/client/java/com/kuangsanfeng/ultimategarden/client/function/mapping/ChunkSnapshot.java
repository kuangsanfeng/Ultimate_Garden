package com.kuangsanfeng.ultimategarden.client.function.mapping;

import java.util.*;

/** Immutable palette and X-fastest, Z-next, Y-last indices. Null/missing is never air. */
public final class ChunkSnapshot {
    public record State(String name, Map<String,String> properties) {
        public State { if(name==null || !name.contains(":"))throw new IllegalArgumentException("INVALID_BLOCK_NAME"); properties=Map.copyOf(properties); }
    }
    public static final State AIR = new State("minecraft:air",Map.of());
    private final int height;
    private final List<State> palette;
    private final int[] indices;
    public ChunkSnapshot(int height,List<State> palette,int[] indices) {
        if(height<1 || height>4096 || palette.isEmpty() || !palette.getFirst().equals(AIR)
                || indices.length!=Math.multiplyExact(256,height))throw new IllegalArgumentException("INVALID_CHUNK_SNAPSHOT");
        this.height=height;this.palette=List.copyOf(palette);this.indices=indices.clone();
        for(int i:indices)if(i<0||i>=palette.size())throw new IllegalArgumentException("INVALID_PALETTE_INDEX");
    }
    public int height(){return height;}
    public List<State> palette(){return palette;}
    public int[] indices(){return indices.clone();}
    public State state(int x,int y,int z){
        if(x<0||x>=16||z<0||z>=16||y<0||y>=height)return null;
        return palette.get(indices[((y*16)+z)*16+x]);
    }
    public static boolean air(State state){return state!=null&&Set.of("minecraft:air","minecraft:cave_air","minecraft:void_air").contains(state.name());}
    public boolean sameBlocks(ChunkSnapshot other){
        if(height!=other.height)return false;
        for(int i=0;i<indices.length;i++)if(!palette.get(indices[i]).equals(other.palette.get(other.indices[i])))return false;
        return true;
    }
}
