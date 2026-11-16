package com.kuangsanfeng.ultimategarden.client.function.mapping;

import com.kuangsanfeng.ultimategarden.client.function.world.WorldInfoService;
import com.kuangsanfeng.ultimategarden.client.platform.context.ClientContextEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.state.properties.Property;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Client-thread observation and commands; immutable chunk data crosses to one bounded IO worker. */
public final class MappingController {
    public static final MappingController INSTANCE=new MappingController();
    public record Chunk(int x,int z) {}
    public record Status(String worldName,MapConfig config,Chunk current,boolean recording,boolean busy,String message) {}
    private final Object gate=new Object();
    private final Set<Chunk> loaded=ConcurrentHashMap.newKeySet();
    private final Map<Chunk,Long> revisions=new ConcurrentHashMap<>();
    private final Set<Chunk> done=new HashSet<>(), suppressed=new HashSet<>();
    private final Map<Chunk,Long> pending=new HashMap<>();
    private final ThreadPoolExecutor io=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(8),r->{var t=new Thread(r,"ultimate-garden-mapping");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private volatile long epoch;
    private final Map<Chunk,Long> processing=new ConcurrentHashMap<>();
    private final Set<MapRepository.Coordinate> layerFiles=new HashSet<>(),baseFiles=new HashSet<>();
    private volatile MappingRecordingSnapshot recordingSnapshot=MappingRecordingSnapshot.EMPTY;
    public MappingRecordingSnapshot recordingSnapshot(){return recordingSnapshot;}
    private void onClient(Runnable task){Minecraft.getInstance().execute(()->{task.run();publishRecording();});}
    private void publishRecording(){
        var key=current();var cfg=config;
        java.util.function.Function<Chunk,MapRepository.Coordinate> relative=c->new MapRepository.Coordinate(cfg.relativeX(c.x),cfg.relativeZ(c.z));
        if(worldName.isBlank()){recordingSnapshot=MappingRecordingSnapshot.EMPTY;return;}
        var active=new HashSet<MapRepository.Coordinate>();var queued=new HashSet<MapRepository.Coordinate>();
        if(cfg!=null){for(var c:pending.keySet()){if(Objects.equals(processing.get(c),epoch))active.add(relative.apply(c));else queued.add(relative.apply(c));}}
        recordingSnapshot=new MappingRecordingSnapshot(worldName,cfg,cfg==null||key==null?null:relative.apply(key),recording,active,queued,layerFiles,baseFiles);
    }
    private record SavedFiles(Set<MapRepository.Coordinate> base,Set<MapRepository.Coordinate> layer,Set<Chunk> chunks){
        static SavedFiles empty(){return new SavedFiles(Set.of(),Set.of(),Set.of());}
    }
    private void acceptFiles(SavedFiles f){baseFiles.clear();baseFiles.addAll(f.base);layerFiles.clear();layerFiles.addAll(f.layer);done.addAll(f.chunks);}
    private abstract static class ProtectedMapEdit implements Runnable {}
    public record EditResult(java.util.List<String> failures,int deleted){}
    /** Replay edits bind an explicit validated local map, never the player's current coordinates. */
    public CompletableFuture<EditResult> editLocalMap(String name,Festival layer,Set<MapRepository.Coordinate> coordinates,boolean whole){
        var future=new CompletableFuture<EditResult>();boolean active=worldName.equals(name);boolean resume=active&&recording&&!whole;
        if(active){invalidate();for(var c:coordinates)if(config!=null)suppressed.add(new Chunk(Math.floorDiv(config.origin().x(),16)+c.x(),Math.floorDiv(config.origin().z(),16)+c.z()));busy=true;publishRecording();}
        long token=epoch;Path root=MappingPaths.root(Minecraft.getInstance().gameDirectory.toPath());
        try{io.execute(new ProtectedMapEdit(){public void run(){var failures=new ArrayList<String>();int deleted=0;MapConfig result=null;SavedFiles saved=SavedFiles.empty();
            try{var repo=new MapRepository(root,name);var cfg=repo.load();if(cfg==null)throw new java.io.IOException("MAP_NOT_INITIALIZED");
                if(whole){repo.deleteWorld();deleted=1;}else{for(var c:coordinates)try{int x=Math.addExact(Math.floorDiv(cfg.origin().x(),16),c.x()),z=Math.addExact(Math.floorDiv(cfg.origin().z(),16),c.z());if(repo.exists(cfg,layer,x,z)){repo.deleteChunk(cfg,layer,x,z);deleted++;}}catch(Exception e){failures.add(c+": "+e.getMessage());}result=repo.load();saved=savedChunks(repo,result);}
            }catch(Exception e){failures.add(String.valueOf(e.getMessage()));}
            final MapConfig finalConfig=result;final SavedFiles finalSaved=saved;var answer=new EditResult(List.copyOf(failures),deleted);
            onClient(()->{if(active&&epoch==token){config=finalConfig;acceptFiles(finalSaved);busy=false;recording=resume&&config!=null;message=whole?"NOT_INITIALIZED":"CHUNK_DELETED";}future.complete(answer);});
        }});}catch(RejectedExecutionException e){future.completeExceptionally(e);if(active)busy=false;}
        return future;
    }
    private ClientLevel observedWorld,cacheWorld;
    private Object observedConnection;
    private String worldName="",message="WAITING_WORLD";
    private MapRepository repository;
    private MapConfig config;
    private boolean recording,busy,registered;
    private Festival requested=Festival.NONE;
    private MappingController() {}
    public boolean running(){return recording;}
    public Festival requestedFestival(){return requested;}
    public Status snapshot(){return new Status(worldName,config,current(),recording,busy,message);}
    private Chunk current(){var c=Minecraft.getInstance();return c.player==null?null:new Chunk(c.player.chunkPosition().x(),c.player.chunkPosition().z());}
    public void register(){
        if(registered)return;registered=true;
        ClientChunkEvents.CHUNK_LOAD.register((level,chunk)->{
            synchronized(gate){if(cacheWorld!=level){invalidate();cacheWorld=level;loaded.clear();revisions.clear();}var p=chunk.getPos();var key=new Chunk(p.x(),p.z());revisions.merge(key,1L,Long::sum);loaded.add(key);}
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((level,chunk)->{
            synchronized(gate){if(cacheWorld!=level)return;var p=chunk.getPos();var key=new Chunk(p.x(),p.z());loaded.remove(key);revisions.merge(key,1L,Long::sum);}
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientContextEvents.onDisconnect((handler,client)->clearWorld());
        ClientContextEvents.onStopping(client->{clearWorld();io.shutdown();});
        ClientContextEvents.onChange(t->{if(t.change()==ClientContextEvents.Change.ACCOUNT_BEFORE_LOAD||t.change()==ClientContextEvents.Change.WORLD_CHANGED){invalidate();recordingSnapshot=MappingRecordingSnapshot.EMPTY;observedWorld=null;repository=null;config=null;}});
    }
    private void invalidate(){synchronized(gate){epoch++;}recording=false;busy=false;pending.clear();done.clear();processing.clear();io.getQueue().removeIf(task->!(task instanceof ProtectedMapEdit));}
    private void clearWorld(){invalidate();observedWorld=null;observedConnection=null;repository=null;config=null;worldName="";baseFiles.clear();layerFiles.clear();recordingSnapshot=MappingRecordingSnapshot.EMPTY;requested=Festival.NONE;suppressed.clear();synchronized(gate){cacheWorld=null;loaded.clear();revisions.clear();}message="WAITING_WORLD";}
    private void tick(Minecraft c){try{tickWork(c);}finally{publishRecording();}}
    private void tickWork(Minecraft c){
        String name=WorldInfoService.snapshot().island();if(name==null)name="";
        if(c.level==null||c.player==null||c.getConnection()==null) {if(observedWorld!=null||repository!=null)clearWorld();return;}
        if(observedWorld!=c.level||observedConnection!=c.getConnection()||!worldName.equals(name)){
            invalidate();observedWorld=c.level;observedConnection=c.getConnection();worldName=name;baseFiles.clear();layerFiles.clear();repository=null;config=null;suppressed.clear();requested=Festival.NONE;
            synchronized(gate){if(cacheWorld!=c.level){cacheWorld=c.level;loaded.clear();revisions.clear();}}
            if(name.isBlank()){message="WAITING_WORLD";return;}
            try{
                var repo=new MapRepository(MappingPaths.root(c.gameDirectory.toPath()),name);repository=repo;busy=true;long token=epoch;
                submit(token,()->{var result=repo.load();var saved=result==null?SavedFiles.empty():savedChunks(repo,result);onClient(()->{if(epoch!=token)return;config=result;acceptFiles(saved);requested=result==null?Festival.NONE:result.selectedFestival();busy=false;message=result==null?"NOT_INITIALIZED":"READY";});});
            }catch(Exception e){repository=null;error(e);}
            return;
        }
        if(!recording||busy||config==null||pending.size()>=2)return;
        MappingCandidates.next(loaded,current(),done,pending.keySet(),suppressed).ifPresent(this::capture);
    }
    private SavedFiles savedChunks(MapRepository repo,MapConfig cfg)throws Exception{
        var base=repo.index(cfg,Festival.NONE);var layer=cfg.selectedFestival()==Festival.NONE?base:repo.index(cfg,cfg.selectedFestival());var chunks=new HashSet<Chunk>();
        for(var c:layer)chunks.add(new Chunk(Math.addExact(Math.floorDiv(cfg.origin().x(),16),c.x()),Math.addExact(Math.floorDiv(cfg.origin().z(),16),c.z())));
        return new SavedFiles(base,layer,Set.copyOf(chunks));
    }
    @FunctionalInterface private interface Work {void run()throws Exception;}
    private void submit(long token,Work work){
        try{io.execute(()->{if(epoch!=token)return;try{work.run();}catch(Exception e){onClient(()->{if(epoch==token){invalidate();error(e);}});}});}catch(RejectedExecutionException e){invalidate();message="WRITE_QUEUE_FULL";}
    }
    private String localizedMessage(){
        var configs=com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient.getConfigManager();
        if(configs==null)return message;
        var language=new com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageManager(configs);
        String value=language.optional("ui.ultimategarden.mapping.status."+message);return value.isBlank()?message:value;
    }
    private void error(Exception e){message=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();var c=Minecraft.getInstance();if(c.player!=null)com.kuangsanfeng.ultimategarden.client.platform.hint.HintManager.print("Mapping: "+localizedMessage());}
    public void initialize(){
        if(config!=null||busy)return;
        var c=Minecraft.getInstance();if(repository==null||observedWorld!=c.level||worldName.isBlank()){message="WAITING_WORLD";return;}
        var key=current();if(key==null)return;
        try{
            var cfg=new MapConfig(1,worldName,new MapConfig.Origin(key.x*16,c.level.getMinY(),key.z*16),c.level.getMinY()+c.level.getHeight(),Set.of(Festival.NONE),Festival.NONE);
            var data=readChunk(key,cfg);if(data==null){message="CHUNK_NOT_LOADED";return;}
            busy=true;long token=epoch,revision=revisions.getOrDefault(key,0L);var repo=repository;int version=SharedConstants.getCurrentVersion().dataVersion().version();
            submit(token,()->{var result=repo.initialize(cfg,data,version,()->valid(token,key,revision),gate);var saved=result==null?SavedFiles.empty():savedChunks(repo,result);onClient(()->{if(epoch!=token)return;busy=false;config=result;if(result==null){message="CHUNK_UNLOADED";return;}requested=result.selectedFestival();acceptFiles(saved);recording=valid(token,key,revision);message=recording?"RECORDING":"READY";});});
        }catch(Exception e){error(e);}
    }
    public void toggleRecording(){
        if(busy)return;if(config==null){message="INITIALIZE_FIRST";notifyMessage();return;}
        if(recording){invalidate();message="STOPPED";}else{suppressed.clear();done.clear();recording=true;message="RECORDING";long token=epoch;var repo=repository;var cfg=config;busy=true;submit(token,()->{var saved=savedChunks(repo,cfg);onClient(()->{if(epoch==token){acceptFiles(saved);busy=false;}});});}
    }
    private void notifyMessage(){var c=Minecraft.getInstance();if(c.player!=null)com.kuangsanfeng.ultimategarden.client.platform.hint.HintManager.print("Mapping: "+localizedMessage());}
    public void redraw(){if(config==null){message="INITIALIZE_FIRST";notifyMessage();return;}if(!busy){var key=current();if(key!=null){suppressed.remove(key);synchronized(gate){revisions.merge(key,1L,Long::sum);}pending.remove(key);capture(key);}}}
    private boolean valid(long token,Chunk key,long revision){return epoch==token&&loaded.contains(key)&&revisions.getOrDefault(key,0L)==revision;}
    private void capture(Chunk key){
        var cfg=config;var repo=repository;if(cfg==null||repo==null)return;
        try{
            var data=readChunk(key,cfg);if(data==null){loaded.remove(key);return;}
            long token=epoch,revision=revisions.getOrDefault(key,0L);pending.put(key,revision);int version=SharedConstants.getCurrentVersion().dataVersion().version();
            submit(token,()->{processing.put(key,token);onClient(()->{});try{repo.write(cfg,cfg.selectedFestival(),key.x,key.z,data,version,()->valid(token,key,revision),gate);}finally{processing.remove(key,token);}boolean exists=repo.exists(cfg,cfg.selectedFestival(),key.x,key.z);onClient(()->{if(epoch!=token)return;pending.remove(key,revision);if(valid(token,key,revision)){done.add(key);var relative=new MapRepository.Coordinate(cfg.relativeX(key.x),cfg.relativeZ(key.z));if(exists)layerFiles.add(relative);else layerFiles.remove(relative);if(cfg.selectedFestival()==Festival.NONE){baseFiles.clear();baseFiles.addAll(layerFiles);}message="SAVED";}});});
        }catch(Exception e){error(e);done.add(key);}
    }
    private ChunkSnapshot readChunk(Chunk key,MapConfig cfg){
        var c=Minecraft.getInstance();if(c.level==null||c.level!=observedWorld||!loaded.contains(key))return null;
        LevelChunk chunk=c.level.getChunkSource().getChunk(key.x,key.z,ChunkStatus.FULL,false);
        if(chunk==null||chunk instanceof EmptyLevelChunk)return null;
        if(cfg.origin().y()<c.level.getMinY()||cfg.maxY()>c.level.getMinY()+c.level.getHeight())throw new IllegalStateException("MAP_HEIGHT_MISMATCH");
        int h=cfg.maxY()-cfg.origin().y();int[] indices=new int[256*h];var palette=new ArrayList<ChunkSnapshot.State>();var ids=new HashMap<ChunkSnapshot.State,Integer>();palette.add(ChunkSnapshot.AIR);ids.put(ChunkSnapshot.AIR,0);var pos=new BlockPos.MutableBlockPos();
        for(int y=0;y<h;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
            var state=chunk.getBlockState(pos.set(key.x*16+x,cfg.origin().y()+y,key.z*16+z));var properties=new TreeMap<String,String>();
            state.getValues().forEach(value->properties.put(value.property().getName(),propertyValue(value.property(),value.value())));
            var value=new ChunkSnapshot.State(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),properties);
            int id=ids.computeIfAbsent(value,v->{palette.add(v);return palette.size()-1;});indices[((y*16)+z)*16+x]=id;
        }
        return new ChunkSnapshot(h,palette,indices);
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private static String propertyValue(Property property,Comparable value){return property.getName(value);}
    public void chooseFestival(Festival f){requested=f;if(config!=null&&config.festivals().contains(f)&&f!=config.selectedFestival())changeLayer(f,false);}
    public void createFestival(){if(config!=null&&requested!=Festival.NONE&&!config.festivals().contains(requested))changeLayer(requested,true);}
    private void changeLayer(Festival f,boolean create){var cfg=config;var repo=repository;invalidate();suppressed.clear();busy=true;long token=epoch;submit(token,()->{var result=repo.select(cfg,f,create);var saved=savedChunks(repo,result);onClient(()->{if(epoch==token){config=result;acceptFiles(saved);busy=false;message="READY";}});});}
    public void deleteCurrent(){if(config==null||busy)return;var key=current();if(key==null)return;var cfg=config;var repo=repository;boolean resume=recording;invalidate();suppressed.add(key);busy=true;long token=epoch;submit(token,()->{repo.deleteChunk(cfg,cfg.selectedFestival(),key.x,key.z);var saved=savedChunks(repo,cfg);onClient(()->{if(epoch==token){acceptFiles(saved);busy=false;recording=resume;message="CHUNK_DELETED";}});});}
    public void resetOrigin(Chunk target){if(config==null||busy||target==null||!target.equals(current()))return;var cfg=config;var repo=repository;invalidate();busy=true;long token=epoch;submit(token,()->{var result=repo.resetOrigin(cfg,target.x,target.z,()->epoch==token,gate);var saved=savedChunks(repo,result);onClient(()->{if(epoch==token){config=result;acceptFiles(saved);busy=false;message="ORIGIN_RESET";}});});}
    public void deleteWorld(){if(repository==null||busy)return;var repo=repository;invalidate();busy=true;long token=epoch;submit(token,()->{repo.deleteWorld();onClient(()->{if(epoch==token){config=null;baseFiles.clear();layerFiles.clear();busy=false;requested=Festival.NONE;suppressed.clear();message="NOT_INITIALIZED";}});});}
    /** Runtime-only identity for dangerous confirmations; never persisted. */
    public Object worldIdentity(){return observedWorld;}
}
