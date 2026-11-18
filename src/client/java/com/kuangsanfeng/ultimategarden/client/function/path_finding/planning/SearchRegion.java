package com.kuangsanfeng.ultimategarden.client.function.path_finding.planning;

import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import java.util.*;

/** The horizontal endpoint envelope uses the existing corridor clearance margin. */
final class SearchRegion {
    record Chunk(int x,int z) {
        static Chunk of(Point p){return new Chunk(index(p.x()),index(p.z()));}
    }
    private record Counts(long forward,long reverse,long local,long walking) {}
    private final int minX,minZ,maxX,maxZ;
    private final Map<Chunk,long[]> visited=new LinkedHashMap<>();
    private boolean expanded;
    private final boolean track;

    SearchRegion(Point a,Point b,boolean track) {
        minX=index(Math.min(a.x(),b.x())-CorridorPlanner.RADIUS);
        minZ=index(Math.min(a.z(),b.z())-CorridorPlanner.RADIUS);
        maxX=index(Math.max(a.x(),b.x())+CorridorPlanner.RADIUS);
        maxZ=index(Math.max(a.z(),b.z())+CorridorPlanner.RADIUS);
        this.track=track;
    }
    private static int index(double x){return Math.floorDiv((int)Math.floor(x),16);}
    private long distanceSquared(int x,int z) {
        long dx=Math.max(0,Math.max((long)minX-x,(long)x-maxX));
        long dz=Math.max(0,Math.max((long)minZ-z,(long)z-maxZ));
        return dx*dx+dz*dz;
    }
    boolean contains(Point p){return distanceSquared(index(p.x()),index(p.z()))<=(expanded?4:0);}
    boolean deferred(Point p){return !expanded&&distanceSquared(index(p.x()),index(p.z()))<=4;}
    void expand(){expanded=true;}
    boolean expanded(){return expanded;}
    Map<String,Object> mask(){
        return Map.of("minChunkX",minX,"maxChunkX",maxX,"minChunkZ",minZ,"maxChunkZ",maxZ,
                "radialPaddingChunks",expanded?2:0,"metric","EUCLIDEAN_CHUNK_CENTERS");
    }
    void visit(Point p,int stage){
        if(track) visited.computeIfAbsent(Chunk.of(p),k->new long[4])[stage]++;
    }
    List<Map<String,Object>> drain(){
        var rows=new ArrayList<Map<String,Object>>();
        visited.forEach((chunk,n)->rows.add(Map.of("chunk",chunk,"expanded",new Counts(n[0],n[1],n[2],n[3]),
                "addedByExpansion",distanceSquared(chunk.x,chunk.z)>0)));
        visited.clear();
        return rows;
    }
}
