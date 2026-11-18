package com.kuangsanfeng.ultimategarden.client.function.path_finding.planning;

import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import com.kuangsanfeng.ultimategarden.client.function.mapping.*;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.PathSettings;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.terrain.MapTerrain;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import java.nio.file.*;
import java.util.*;
import static com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.*;

public final class GpuPlanningRegression {
    private static void check(boolean value,String message){
        if(!value)throw new AssertionError(message);
    }

    public static void main(String[] args)throws Exception{
        var settings=new PathSettings();
        var doc=new JsonObject();
        doc.add("pathFinding",new JsonObject());
        check(!settings.load(doc)&&settings.gpuAcceleration()&&settings.cpuWorkers()==0&&
                settings.effectiveWorkers()==Runtime.getRuntime().availableProcessors(),"Missing performance fields");
        String prior=System.getProperty("ug.path.workers");
        System.setProperty("ug.path.workers","1");
        try{
            var legacy=new PathSettings();
            check(!legacy.load(doc)&&legacy.cpuWorkers()==1,"Explicit legacy JVM selection");
        }finally{
            if(prior==null)System.clearProperty("ug.path.workers");
            else System.setProperty("ug.path.workers",prior);
        }
        settings.setGpuAcceleration(false);
        settings.setCpuWorkers(1);
        doc.add("pathFinding",settings.toJson());
        var copy=new PathSettings();
        check(!copy.load(doc)&&!copy.gpuAcceleration()&&copy.cpuWorkers()==1&&copy.effectiveWorkers()==1,"Explicit performance fields");
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var root=Files.createTempDirectory(Path.of("build/path-audit"),"gpu-planning-fixture-");
        var repo=new MapRepository(root,"Gpu Test");
        var config=new MapConfig(1,"Gpu Test",new MapConfig.Origin(0,0,0),16,Set.of(Festival.NONE),Festival.NONE);
        repo.saveConfig(config);
        var palette=List.of(ChunkSnapshot.AIR,new ChunkSnapshot.State("minecraft:stone",Map.of()),
                new ChunkSnapshot.State("minecraft:oak_slab",Map.of("type","bottom","waterlogged","false")));
        for(int cz=0;cz<3;cz++)for(int cx=0;cx<3;cx++){
            int[] blocks=new int[16*16*16];
            for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                int wx=cx*16+x,wz=cz*16+z;
                if((wx==8||wx==40)&&wz>=8&&wz<=34||wz==34&&wx>=8&&wx<=40)blocks[z*16+x]=1;
                if(wx==15&&wz==8)blocks[256+z*16+x]=2;
                if(wx==16&&wz==10)blocks[256+z*16+x]=1;
            }
            LitematicIO.write(repo.chunkPath(config,Festival.NONE,cx,cz),new ChunkSnapshot(16,palette,blocks),
                    SharedConstants.getCurrentVersion().dataVersion().version());
        }
        var terrain=new MapTerrain(repo);
        var start=new Point(8.5,1,8.5);
        var target=new Point(40.5,1,8.5);
        var events=new ArrayList<Map.Entry<String,Map<String,Object>>>();
        var audit=new SearchAudit((event,data)->events.add(Map.entry(event,data)));
        var bounds=new SearchRegion(start,target,false);
        var added=new Point(4,1,50);
        var outsideCorner=new Point(-33,1,48);
        check(!bounds.contains(added)&&bounds.deferred(added),"Two-chunk extension membership");
        bounds.expand();
        check(bounds.contains(added)&&!bounds.contains(outsideCorner),"Euclidean mask clipping");
        var options=new Options(Mode.NORMAL,false,true,12,1,5000,Weights.defaults());
        var route=SegmentedPlanner.plan(terrain,options,4,CorridorPlanner.Timing.defaults(),()->false,audit,start,target,0,0,1,false);
        var extension=events.stream().filter(e->e.getKey().equals("SEARCH_MASK_EXPANDED")).findFirst().orElseThrow().getValue();
        check(((Number)extension.get("restored")).intValue()>0&&route.status()==Status.ARRIVED&&
                route.segments().stream().anyMatch(edge->edge.to().z()>=32),"Resume a real masked detour");

        var rays=new GpuCollision.Cast[48];
        for(int i=0;i<rays.length;i++){
            double y=new double[]{1.1,1.5,2.05,3}[i%4];
            double z=new double[]{8.5,10.5,11.5}[i%3];
            var a=new Point(i>=44?-17.5:13.5,y,z);
            var b=new Point(18.45,y,z);
            rays[i]=new GpuCollision.Cast(a,b,new Point(18.5,y,z));
        }
        boolean[] expected=new boolean[rays.length];
        for(int i=0;i<rays.length;i++){
            var ray=rays[i];
            expected[i]=terrain.clear(ray.landing())&&terrain.sweep(ray.start(),ray.raw())&&terrain.sweep(ray.raw(),ray.landing());
        }
        try(var gpu=new GpuCollision(true,terrain,audit)){
            var results=GpuCollision.result(gpu.submit(rays,rays.length));
            check(Arrays.equals(results,expected),"CPU/GPU agreement across partial blocks, boundaries and missing chunks");
        }
        System.setProperty("ug.path.forceGpuFallback","true");
        try(var gpu=new GpuCollision(true,terrain,audit)){
            check(GpuCollision.result(gpu.submit(rays,rays.length))==null,"Forced fallback returns to CPU");
        }finally{System.clearProperty("ug.path.forceGpuFallback");}
        check(events.stream().anyMatch(e->e.getKey().equals("GPU_FALLBACK")),"Fallback diagnostic");
        Files.writeString(root.resolve("passed.txt"),"PASS GPU 48 collision probes, CPU fallback, saved settings and radial mask recovery");
    }
}
