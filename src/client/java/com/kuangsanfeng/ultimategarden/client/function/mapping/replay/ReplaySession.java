package com.kuangsanfeng.ultimategarden.client.function.mapping.replay;

import com.kuangsanfeng.ultimategarden.client.function.mapping.*;
import com.kuangsanfeng.ultimategarden.client.function.mapping.MapRepository.Coordinate;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** Bounded, cancellable offline IO/geometry/raster jobs. No decoding or mesh construction in draw(). */
public final class ReplaySession implements AutoCloseable {
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(8),r->{var t=new Thread(r,"mapping-replay");t.setDaemon(true);return t;});
    private final Path root;private volatile long generation;private volatile boolean closed;
    public volatile LocalMapLibrary.Index index=new LocalMapLibrary.Index(List.of(),List.of());
    public volatile ReplayMesh mesh=new ReplayMesh(List.of(),0,List.of());
    public volatile ReplayRaster.Image image;public volatile String error="";public volatile boolean loading;
    private final AtomicReference<Request> latest=new AtomicReference<>();private final AtomicBoolean drawing=new AtomicBoolean();
    private final Map<Path,ChunkSnapshot> cache=new LinkedHashMap<>(32,.75f,true){protected boolean removeEldestEntry(Map.Entry<Path,ChunkSnapshot> e){return size()>128;}};
    public record Request(ReplayCamera camera,int cx,int cz,int width,int height,Set<Coordinate> selected,boolean showChunkBorders,long generation){}
    public ReplaySession(Path root){this.root=root;}
    private abstract static class IndexTask implements Runnable {}
    public void refresh(Consumer<LocalMapLibrary.Index> ready){enqueue(new IndexTask(){public void run(){try{index=LocalMapLibrary.list(root);if(!closed)ready.accept(index);}catch(Exception e){error=e.getMessage();}}});}
    private boolean enqueue(Runnable task){if(closed)return false;try{worker.execute(task);return true;}catch(RejectedExecutionException e){error="REPLAY_BUSY";return false;}}
    private final class BuildJob {
        final LocalMapLibrary.World world;final Festival layer;final int step;final ReplayMesh.Mode mode;final ReplayMesh.Materials materials;final long token;
        final List<String> errors=new ArrayList<>();final Map<Coordinate,ChunkSnapshot> data=new HashMap<>();final List<Coordinate> selected=new ArrayList<>();
        final Map<ChunkSnapshot.State,ReplayMesh.Material> materialCache=new HashMap<>();
        ReplayMesh.Material resolve(ChunkSnapshot.State state){return materialCache.computeIfAbsent(state,materials::resolve);}
        void validate(ChunkSnapshot snapshot){for(var state:snapshot.palette())resolve(state);}
        List<Coordinate> sorted;int cursor;
        BuildJob(LocalMapLibrary.World world,Festival layer,int step,ReplayMesh.Mode mode,ReplayMesh.Materials materials,long token){this.world=world;this.layer=layer;this.step=step;this.mode=mode;this.materials=materials;this.token=token;}
        boolean stale(){return closed||generation!=token;}
        void batch(){if(stale())return;try{
            long start=System.nanoTime();int processed=0;
            while(cursor<sorted.size()){if(stale())return;var coord=sorted.get(cursor++);
                try{var snapshot=read(world,layer,coord);if(snapshot!=null){validate(snapshot);data.put(coord,snapshot);selected.add(coord);}}catch(Exception e){errors.add(coord+": "+e.getMessage());}
                if(++processed>=4||System.nanoTime()-start>=8_000_000L)break;
            }
            if(cursor<sorted.size()){enqueue(this::batch);return;}
            var result=ReplayMesh.build(data,selected,step,mode,this::resolve,this::stale,errors);if(!stale()){mesh=result;loading=false;}
        }catch(CancellationException ignored){}catch(Exception e){if(!stale()){error=String.valueOf(e.getMessage());loading=false;}}}
    }
    public void build(LocalMapLibrary.World world,Festival layer,int cx,int cz,String renderDistance,int step,ReplayMesh.Mode mode,ReplayMesh.Materials materials){
        long token=++generation;drawing.set(false);error="";latest.set(null);worker.getQueue().removeIf(task->!(task instanceof IndexTask));loading=true;image=null;mesh=new ReplayMesh(List.of(),0,List.of());
        var job=new BuildJob(world,layer,step,mode,materials,token);
        enqueue(()->{try{var all=LocalMapLibrary.composed(world,layer);job.sorted=ReplaySelection.withinDistance(all,cx,cz,renderDistance);job.batch();}catch(Exception e){if(!job.stale()){error=String.valueOf(e.getMessage());loading=false;}}});
    }
    private ChunkSnapshot read(LocalMapLibrary.World w,Festival layer,Coordinate c)throws Exception{
        int cx=Math.addExact(Math.floorDiv(w.config().origin().x(),16),c.x()),cz=Math.addExact(Math.floorDiv(w.config().origin().z(),16),c.z());
        var path=w.repository().chunkPath(w.config(),layer,cx,cz);if(!Files.isRegularFile(path))path=w.repository().chunkPath(w.config(),Festival.NONE,cx,cz);if(!Files.isRegularFile(path))return null;
        var stored=cache.get(path);if(stored!=null)return stored;var result=LitematicIO.read(path);if(result.height()!=w.config().maxY()-w.config().origin().y())throw new java.io.IOException("CHUNK_HEIGHT_MISMATCH");cache.put(path,result);return result;
    }
    public void clear(){generation++;latest.set(null);drawing.set(false);worker.getQueue().removeIf(task->!(task instanceof IndexTask));image=null;mesh=new ReplayMesh(List.of(),0,List.of());loading=false;}
    public void invalidateFiles(){enqueue(new IndexTask(){public void run(){cache.clear();}});}
    public void frame(ReplayCamera camera,int cx,int cz,int width,int height,Set<Coordinate> selected){
        frame(camera,cx,cz,width,height,selected,true);
    }
    public void frame(ReplayCamera camera,int cx,int cz,int width,int height,Set<Coordinate> selected,boolean showChunkBorders){
        if(closed||loading||mesh.parts().isEmpty())return;latest.set(new Request(camera.copy(),cx,cz,width,height,Set.copyOf(selected),showChunkBorders,generation));
        startDrawing();
    }
    private void startDrawing(){
        if(!drawing.compareAndSet(false,true))return;
        if(!enqueue(()->{try{Request r;while((r=latest.getAndSet(null))!=null&&!closed){final Request req=r;if(req.generation!=generation)continue;try{var result=ReplayRaster.render(mesh,req.camera,req.cx,req.cz,req.width,req.height,req.selected,req.showChunkBorders,()->closed||generation!=req.generation);if(generation==req.generation)image=result;}catch(CancellationException ignored){}}}finally{drawing.set(false);if(latest.get()!=null&&!closed)startDrawing();}}))drawing.set(false);
    }
    @Override public void close(){closed=true;generation++;latest.set(null);worker.shutdownNow();image=null;}
}
