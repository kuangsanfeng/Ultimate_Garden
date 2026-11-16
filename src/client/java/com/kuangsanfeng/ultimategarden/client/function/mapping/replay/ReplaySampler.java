package com.kuangsanfeng.ultimategarden.client.function.mapping.replay;

import com.kuangsanfeng.ultimategarden.client.function.mapping.ChunkSnapshot;
import java.util.*;

/** Every sampling space uses Y descending, X ascending, Z ascending after excluding air. */
public final class ReplaySampler {
    private ReplaySampler(){}
    public record Sample(int x,int y,int z,ChunkSnapshot.State state){}
    /** XZ maxima exclusive; Y bounds inclusive. The returned XYZ is the actual source block. */
    public static Sample sample(ChunkSnapshot chunk,int x0,int z0,int x1,int z1,int y0,int y1){
        for(int y=Math.min(y1,chunk.height()-1);y>=Math.max(0,y0);y--)
            for(int x=Math.max(0,x0);x<Math.min(16,x1);x++)for(int z=Math.max(0,z0);z<Math.min(16,z1);z++){
                var state=chunk.state(x,y,z);if(!ChunkSnapshot.air(state))return new Sample(x,y,z,state);
            }
        return null;
    }
    /** Interior depth candidates; exposed boundary geometry is retained independently at its full height. */
    public static List<Sample> depthSamples(ChunkSnapshot chunk,int x0,int z0,int x1,int z1,ReplayMesh.Mode mode){
        var top=sample(chunk,x0,z0,x1,z1,0,chunk.height()-1);if(top==null)return List.of();
        var result=new ArrayList<Sample>();result.add(top);int bottom=mode==ReplayMesh.Mode.ALL?0:mode==ReplayMesh.Mode.TWO_LAYERS?Math.max(0,top.y()-2):top.y();
        for(int y=top.y()-1;y>=bottom;y--){var value=sample(chunk,x0,z0,x1,z1,y,y);if(value!=null)result.add(value);}return List.copyOf(result);
    }
}
