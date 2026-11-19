package com.kuangsanfeng.ultimategarden.client.function.path_finding.planning;

import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.terrain.MapTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import java.lang.foreign.MemorySegment;
import java.util.*;
import java.util.concurrent.*;
import static java.lang.foreign.ValueLayout.*;

/** A plan-local device snapshot with a shared serial submission thread. */
public final class GpuCollision implements AutoCloseable {
    record Cast(Point start,Point raw,Point landing) {}
    private static final int SLOTS=128, MAX_RAYS=512, HEADER=64, MAX_BOXES=131072;
    private static final ExecutorService DEVICE=Executors.newSingleThreadExecutor(task->{
        var t=new Thread(task,"ug-path-opencl");t.setDaemon(true);return t;
    });
    private static volatile String status="requested";
    private final MapTerrain map;
    private final boolean enabled;
    private final SearchAudit audit;
    private Session session;
    private volatile boolean failed;
    private boolean initialized;
    private long batches,queries,chunkUploads,bytesUploaded,uploadNs,readNs,totalNs,firstNs,warmNs,fallbacks;
    public static String status(){
        return status.equals("disabled")?"requested":status;
    }

    GpuCollision(boolean enabled,MapTerrain map,SearchAudit audit){
        this.enabled=enabled;
        this.map=map;
        this.audit=audit;
        status=enabled?(map==null?"fallback":"requested"):"disabled";
        emit("GPU_REQUEST",Map.of("enabled",enabled,"mapSupported",map!=null));
        if(enabled&&map==null)emit("GPU_FALLBACK",Map.of("reason","GPU_TERRAIN_UNSUPPORTED"));
    }

    Future<boolean[]> submit(Cast[] rays,int count){
        if(!enabled||map==null||count<18||rays.length>MAX_RAYS)return null;
        if(failed){fallbacks++;return null;}
        return DEVICE.submit(()->{
            try{
                long begin=System.nanoTime();
                if(session==null){
                    if(Boolean.getBoolean("ug.path.forceGpuFallback"))throw new IllegalStateException("FORCED_GPU_FALLBACK");
                    session=new Session();
                    initialized=true;status="ready";
                    emit("GPU_INITIALIZED",Map.of("device",session.cl.deviceName(),"initMs",(System.nanoTime()-begin)/1e6));
                    begin=System.nanoTime();
                }
                boolean[] result=session.check(rays);
                if(result==null){fallbacks++;return null;}
                long ns=System.nanoTime()-begin;
                if(batches==0)firstNs=ns;
                else warmNs+=ns;
                totalNs+=ns;
                batches++;queries+=count;
                status="active";
                if(batches==1||batches%4096==0)
                    emit("GPU_BATCH",Map.of("batch",batches,"candidates",count,"elapsedMs",ns/1e6,"chunkUploads",chunkUploads));
                return result;
            }catch(RuntimeException failure){
                failed=true;status="fallback";fallbacks++;
                emit("GPU_FALLBACK",Map.of("reason",failure.toString(),"completedBatches",batches));
                return null;
            }
        });
    }

    static boolean[] result(Future<boolean[]> batch){
        if(batch==null)return null;
        try{return batch.get();}
        catch(InterruptedException stop){
            batch.cancel(false);
            Thread.currentThread().interrupt();
            throw new CancellationException("GPU collision batch interrupted");
        }catch(ExecutionException failure){throw new IllegalStateException("GPU result delivery",failure.getCause());}
    }

    boolean enabled(){return enabled;}
    boolean used(){return batches>0;}
    private void emit(String event,Map<String,Object> details){if(audit!=null)audit.emit(event,details);}

    @Override public void close(){
        if(!enabled||map==null)return;
        // Cleanup is queued on the owner thread, including cancellation while a driver call is running.
        var cleanup=DEVICE.submit(()->{
            if(session!=null)session.cl.close();
            emit("GPU_SUMMARY",Map.ofEntries(Map.entry("enabled",enabled),Map.entry("initialized",initialized),
                    Map.entry("participated",batches>0),Map.entry("batches",batches),Map.entry("candidates",queries),
                    Map.entry("chunkUploads",chunkUploads),Map.entry("bytesUploaded",bytesUploaded),Map.entry("cpuFallbackBatches",fallbacks),
                    Map.entry("uploadMs",uploadNs/1e6),Map.entry("readbackMs",readNs/1e6),Map.entry("totalBatchMs",totalNs/1e6),
                    Map.entry("firstBatchMs",firstNs/1e6),Map.entry("subsequentBatchAvgMs",batches>1?warmNs/1e6/(batches-1):0)));
        });
        try{cleanup.get();}
        catch(InterruptedException stop){Thread.currentThread().interrupt();}
        catch(ExecutionException failure){emit("GPU_CLEANUP_ERROR",Map.of("reason",failure.getCause().toString()));}
    }

    private final class Session {
        final OpenClCompute cl;
        final long chunkBytes=(long)map.getHeight()*256*4;
        final MemorySegment cellBuffer,shapeBuffer,inputBuffer,resultBuffer,chunkHost,shapeHost,inputHost,resultHost;
        final LinkedHashMap<Long,Integer> slots=new LinkedHashMap<>(32,.75f,true);
        final Map<List<AABB>,Integer> shapeIndex=new HashMap<>();
        int shapeCount,uploadedShapes;
        Session(){
            cl=new OpenClCompute(GpuKernel.SOURCE);
            try{
                cellBuffer=cl.buffer(SLOTS*chunkBytes);
                shapeBuffer=cl.buffer(MAX_BOXES*48L);
                inputBuffer=cl.buffer(HEADER*4L+MAX_RAYS*72L);
                resultBuffer=cl.buffer(MAX_RAYS*4L);
                chunkHost=cl.host(chunkBytes);
                shapeHost=cl.host(MAX_BOXES*48L);
                inputHost=cl.host(inputBufferSize());
                resultHost=cl.host(MAX_RAYS*4L);
                cl.arg(0,inputBuffer);cl.arg(1,cellBuffer);cl.arg(2,shapeBuffer);cl.arg(3,resultBuffer);
                shapeIndex.put(List.of(),0);
            }catch(RuntimeException failure){cl.close();throw failure;}
        }
        private long inputBufferSize(){return HEADER*4L+MAX_RAYS*72L;}

        boolean[] check(Cast[] rays){
            int minX=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
            for(var ray:rays){
                if(ray==null)continue;
                minX=Math.min(minX,index(Math.min(ray.landing.x(),Math.min(ray.start.x(),ray.raw.x()))-.2999));
                minZ=Math.min(minZ,index(Math.min(ray.landing.z(),Math.min(ray.start.z(),ray.raw.z()))-.2999));
                maxX=Math.max(maxX,index(Math.max(ray.landing.x(),Math.max(ray.start.x(),ray.raw.x()))+.2999));
                maxZ=Math.max(maxZ,index(Math.max(ray.landing.z(),Math.max(ray.start.z(),ray.raw.z()))+.2999));
            }
            int width=maxX-minX+1,depth=maxZ-minZ+1;
            if(width<1||depth<1||(long)width*depth>HEADER-8)return null;
            int[] metadata={minX,minZ,width,map.getMinY(),map.getHeight(),depth,0,0};
            for(int i=0;i<metadata.length;i++) inputHost.setAtIndex(JAVA_INT,i,metadata[i]);
            for(int z=0;z<depth;z++)for(int x=0;x<width;x++)
                inputHost.setAtIndex(JAVA_INT,8+z*width+x,slot(minX+x,minZ+z));
            if(shapeCount>uploadedShapes){
                long offset=uploadedShapes*48L, bytes=(shapeCount-uploadedShapes)*48L;
                upload(shapeBuffer,offset,shapeHost.asSlice(offset,bytes),bytes);
                uploadedShapes=shapeCount;
            }
            for(int i=0;i<rays.length;i++){
                var ray=rays[i];
                if(ray==null)continue;
                long offset=HEADER*4L+i*72L;
                point(offset,ray.start);point(offset+24,ray.raw);point(offset+48,ray.landing);
            }
            // Compress holes to avoid executing stale input for rejected candidates.
            int count=0;
            int[] order=new int[rays.length];
            for(int i=0;i<rays.length;i++)if(rays[i]!=null){
                order[count]=i;
                if(count!=i)MemorySegment.copy(inputHost,HEADER*4L+i*72L,inputHost,HEADER*4L+count*72L,72);
                count++;
            }
            upload(inputBuffer,0,inputHost,HEADER*4L+count*72L);
            cl.launch(count);
            long now=System.nanoTime();
            cl.read(resultBuffer,resultHost,count*4L);
            readNs+=System.nanoTime()-now;
            var result=new boolean[rays.length];
            for(int i=0;i<count;i++)result[order[i]]=resultHost.getAtIndex(JAVA_INT,i)!=0;
            return result;
        }

        private void point(long offset,Point p){
            inputHost.set(JAVA_DOUBLE,offset,p.x());
            inputHost.set(JAVA_DOUBLE,offset+8,p.y());
            inputHost.set(JAVA_DOUBLE,offset+16,p.z());
        }
        private int slot(int cx,int cz){
            long key=((long)cx<<32)^(cz&0xffffffffL);
            var cached=slots.get(key);
            if(cached!=null)return cached;
            if(!map.hasChunk(cx,cz))return -1;
            int slot=slots.size()<SLOTS?slots.size():slots.pollFirstEntry().getValue();
            var pos=new BlockPos.MutableBlockPos();
            for(int y=0;y<map.getHeight();y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                var boxes=map.collisionBoxes(pos.set(cx*16+x,map.getMinY()+y,cz*16+z));
                chunkHost.setAtIndex(JAVA_INT,((long)y*16+z)*16+x,boxes==null?-1:shapeIndex.computeIfAbsent(boxes,this::shape));
            }
            upload(cellBuffer,slot*chunkBytes,chunkHost,chunkBytes);
            slots.put(key,slot);
            chunkUploads++;
            return slot;
        }
        private int shape(List<AABB> boxes){
            int offset=shapeCount;
            if(boxes.size()>255||offset+boxes.size()>MAX_BOXES)
                throw new IllegalStateException("GPU_COLLISION_PALETTE_LIMIT");
            for(var box:boxes){
                long at=48L*shapeCount++;
                shapeHost.set(JAVA_DOUBLE,at,box.minX);shapeHost.set(JAVA_DOUBLE,at+8,box.minY);
                shapeHost.set(JAVA_DOUBLE,at+16,box.minZ);shapeHost.set(JAVA_DOUBLE,at+24,box.maxX);
                shapeHost.set(JAVA_DOUBLE,at+32,box.maxY);shapeHost.set(JAVA_DOUBLE,at+40,box.maxZ);
            }
            return offset*256+boxes.size();
        }
        private void upload(MemorySegment buffer,long offset,MemorySegment source,long bytes){
            long begin=System.nanoTime();
            cl.write(buffer,offset,source,bytes);
            uploadNs+=System.nanoTime()-begin;
            bytesUploaded+=bytes;
        }
        private int index(double x){return Math.floorDiv((int)Math.floor(x),16);}
    }
}
