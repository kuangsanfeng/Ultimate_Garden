
import com.kuangsanfeng.ultimategarden.client.function.mapping.*;
import com.kuangsanfeng.ultimategarden.client.function.world.*;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.player.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Temporary harness: real Mapping controller/store; synthetic native client cache, no game/server. */
public class RecorderHudIntegration {
 static final Class<?> C=MappingController.class;
 static final MappingController M=MappingController.INSTANCE;
 static final sun.misc.Unsafe U;
 static {try {var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);U=(sun.misc.Unsafe)f.get(null);}catch(Exception e){throw new RuntimeException(e);}}
 static void field(Object target,Class<?> owner,String name,Object value)throws Exception {var f=owner.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
 static Object get(Object target,Class<?> owner,String name)throws Exception {var f=owner.getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 static void call(String name,Class<?>[] types,Object...args)throws Exception {var f=C.getDeclaredMethod(name,types);f.setAccessible(true);f.invoke(M,args);}
 static <T>T blank(Class<T> c)throws Exception{return c.cast(U.allocateInstance(c));}
 static final MappingController.Chunk A=new MappingController.Chunk(-1,0), B=new MappingController.Chunk(0,0);
 static class Cache extends ClientChunkCache {
  Map<MappingController.Chunk,LevelChunk> entries;Map<MappingController.Chunk,Integer> queries;
  private Cache(){super(null,0);}
  @Override public LevelChunk getChunk(int x,int z,ChunkStatus status,boolean load){
   if(load)throw new AssertionError("Loading query");var k=new MappingController.Chunk(x,z);queries.merge(k,1,Integer::sum);return entries.get(k);
  }
 }
 static class World extends ClientLevel {
  Cache cache;
  private World(){super(null,null,null,null,0,0,null,false,0L,0);}
  @Override public ClientChunkCache getChunkSource(){return cache;}
  @Override public int getMinY(){return 0;}
  @Override public int getHeight(){return 1;}
 }
 static class Chunk extends LevelChunk {
  ChunkPos pos;int blockReads;
  private Chunk(){super((Level)null,new ChunkPos(0,0));}
  @Override public ChunkPos getPos(){return pos;}
  @Override public BlockState getBlockState(BlockPos p){blockReads++;return Blocks.STONE.defaultBlockState();}
 }
 static Queue<Runnable> callbacks;
 static void drain(){Runnable r;while((r=callbacks.poll())!=null)r.run();}
 static void settle()throws Exception {long end=System.nanoTime()+10_000_000_000L;do{drain();var io=(ThreadPoolExecutor)get(M,C,"io");if(!M.snapshot().busy()&&((Map<?,?>)get(M,C,"pending")).isEmpty()&&io.getActiveCount()==0&&io.getQueue().isEmpty()&&callbacks.isEmpty())return;Thread.sleep(5);}while(System.nanoTime()<end);throw new AssertionError("Worker timeout "+M.snapshot());}
 static void ticks(Minecraft client,int count)throws Exception{for(int i=0;i<count;i++){call("tick",new Class[]{Minecraft.class},client);drain();}settle();}
 static Chunk load(World world,MappingController.Chunk k)throws Exception{var ch=blank(Chunk.class);ch.pos=new ChunkPos(k.x(),k.z());world.cache.entries.put(k,ch);ClientChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(world,ch);return ch;}
 static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
 static void report(String stage,Path a,FileTime initial,int reads,int blocks)throws Exception{
  int n=world.cache.queries.getOrDefault(A,0)-reads;int b=chunkA.blockReads-blocks;var time=Files.getLastModifiedTime(a);
  require(n==0&&b==0&&time.equals(initial),stage+" repeated A query="+n+" blocks="+b+" time="+time);
  System.out.println(stage+" A.chunkQueries="+n+" A.blockReads="+b+" A.mtime="+time+" unchanged=true");
 }
 static void waitFuture(java.util.concurrent.CompletableFuture<?> f)throws Exception{long end=System.nanoTime()+10_000_000_000L;while(!f.isDone()&&System.nanoTime()<end){drain();Thread.sleep(5);}require(f.isDone(),"management future timeout");}
 static World world;static Chunk chunkA;
 public static void main(String[] args)throws Exception{
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  Path instance=Files.createTempDirectory(Path.of("build/mapping-audit").toAbsolutePath(),"instance-");
  Minecraft client=blank(Minecraft.class);field(null,Minecraft.class,"instance",client);field(client,Minecraft.class,"gameDirectory",instance.toFile());field(client,Minecraft.class,"gameThread",Thread.currentThread());
  callbacks=new ConcurrentLinkedQueue<>();field(client,net.minecraft.util.thread.BlockableEventLoop.class,"pendingRunnables",callbacks);
  world=blank(World.class);world.cache=blank(Cache.class);world.cache.entries=new HashMap<>();world.cache.queries=new HashMap<>();client.level=world;
  LocalPlayer player=blank(LocalPlayer.class);client.player=player;field(player,Entity.class,"chunkPosition",new ChunkPos(-1,0));field(player,LocalPlayer.class,"connection",blank(ClientPacketListener.class));
  var info=(WorldInfoState)get(null,WorldInfoService.class,"state");field(info,WorldInfoState.class,"snapshot",new WorldInfoSnapshot(null,null,"SKYBLOCK","Once Audit",null,null,null,null,null,null));
  MapRepository repo=new MapRepository(MappingPaths.root(instance),"Once Audit");var cfg=new MapConfig(1,"Once Audit",new MapConfig.Origin(-16,0,0),1,Set.of(Festival.NONE),Festival.NONE);
  var snap=new ChunkSnapshot(1,List.of(ChunkSnapshot.AIR),new int[256]);repo.initialize(cfg,snap,net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version(),()->true,new Object());
  Path fileA=repo.chunkPath(cfg,Festival.NONE,-1,0),fileB=repo.chunkPath(cfg,Festival.NONE,0,0);Files.setLastModifiedTime(fileA,FileTime.fromMillis(1234567890000L));FileTime original=Files.getLastModifiedTime(fileA);
  // A contains air on disk, but the synthetic live cache contains stone. If recaptured, both hash and time would change.
  byte[] before=Files.readAllBytes(fileA);M.register();chunkA=load(world,A);call("tick",new Class[]{Minecraft.class},client);settle();M.toggleRecording();settle();
  int reads=world.cache.queries.getOrDefault(A,0),blocks=chunkA.blockReads;ticks(client,100);report("STAY_100_TICKS",fileA,original,reads,blocks);
  ClientChunkEvents.CHUNK_UNLOAD.invoker().onChunkUnload(world,chunkA);world.cache.entries.remove(A);require(!((Set<?>)get(M,C,"loaded")).contains(A),"unload did not remove observed A");ticks(client,20);
  chunkA=load(world,A);require(((Set<?>)get(M,C,"loaded")).contains(A),"reload did not observe A");blocks=chunkA.blockReads;ticks(client,100);report("UNLOAD_AND_NEW_OBJECT_RELOAD_100_TICKS",fileA,original,reads,blocks);
  M.toggleRecording();require(!M.running(),"stop");M.toggleRecording();settle();ticks(client,100);report("STOP_START_100_TICKS",fileA,original,reads,blocks);
  var chunkB=load(world,B);Object commitGate=get(M,C,"gate");
  synchronized(commitGate){call("tick",new Class[]{Minecraft.class},client);long until=System.nanoTime()+5_000_000_000L;while(((Map<?,?>)get(M,C,"processing")).isEmpty()&&System.nanoTime()<until)Thread.onSpinWait();drain();
    var extra=load(world,new MappingController.Chunk(1,0));call("tick",new Class[]{Minecraft.class},client);var counts=M.recordingSnapshot();
    require(counts.processing().size()==1&&counts.queued().size()==1&&java.util.Collections.disjoint(counts.processing(),counts.queued()),"processing and queued must count distinct actual tasks");
    require(counts.currentState()==MappingRecordingSnapshot.ChunkState.SAVED,"saved current chunk while other chunks process");
    System.out.println("PASS: active=1 queued=1 distinct chunks; current A still saved");
  }settle();require(Files.isRegularFile(fileB),"B not saved");require(world.cache.queries.getOrDefault(B,0)==1&&chunkB.blockReads==256,"B not captured once");require(repo.read(cfg,Festival.NONE,0,0).orElseThrow().palette().stream().anyMatch(s->s.name().equals("minecraft:stone")),"B did not contain actual observed state");
  ticks(client,100);require(world.cache.queries.getOrDefault(B,0)==1,"B repeated after save");System.out.println("NEW_B_AND_100_MORE_TICKS B.chunkQueries=1 B.blockReads="+chunkB.blockReads+" B.exists=true B.mtime="+Files.getLastModifiedTime(fileB));
  require(Arrays.equals(before,Files.readAllBytes(fileA)),"A changed bytes");report("FINAL",fileA,original,reads,blocks);
  call("tick",new Class[]{Minecraft.class},client);var hud=M.recordingSnapshot();require(hud.saved().size()==3&&hud.currentState()==MappingRecordingSnapshot.ChunkState.SAVED,"base HUD index or current saved state");
  M.chooseFestival(Festival.SPOOKY);M.createFestival();settle();hud=M.recordingSnapshot();require(hud.config().selectedFestival()==Festival.SPOOKY&&hud.saved().isEmpty()&&hud.baseSaved().size()==3&&hud.currentState()==MappingRecordingSnapshot.ChunkState.BASE_FALLBACK,"empty festival reuses base and shows zero covers");
  M.toggleRecording();settle();ticks(client,1);settle();hud=M.recordingSnapshot();require(hud.saved().size()==1&&hud.currentState()==MappingRecordingSnapshot.ChunkState.SAVED,"overlay submission updates current and layer count");
  var edit=M.editLocalMap("Once Audit",Festival.SPOOKY,Set.of(new MapRepository.Coordinate(0,0)),false);waitFuture(edit);settle();require(edit.isDone()&&edit.join().failures().isEmpty(),"explicit replay deletion failed");
  hud=M.recordingSnapshot();require(hud.saved().isEmpty()&&hud.baseSaved().size()==3&&hud.currentState()==MappingRecordingSnapshot.ChunkState.BASE_FALLBACK,"overlay deletion refreshes index and fallback");
  var other=new MapRepository(MappingPaths.root(instance),"Other Map");other.initialize(new MapConfig(1,"Other Map",new MapConfig.Origin(0,0,0),1,Set.of(Festival.NONE),Festival.NONE),snap,4811,()->true,new Object());
  var remove=M.editLocalMap("Other Map",Festival.NONE,Set.of(),true);waitFuture(remove);settle();require(remove.isDone()&&remove.join().failures().isEmpty()&&!Files.exists(other.directory())&&Files.isRegularFile(fileA),"replay deletion must target selected offline map only");
  M.chooseFestival(Festival.NONE);settle();field(player,Entity.class,"chunkPosition",new ChunkPos(-2,-1));ticks(client,1);hud=M.recordingSnapshot();require(hud.current().equals(new MapRepository.Coordinate(-1,-1)),"negative relative HUD coordinates");
  System.out.println("PASS: real recorder HUD origin/layer/file indices, overlay commit/delete/fallback, negative coordinates and offline selected-map deletion isolation");
  require(!Files.exists(instance.resolve("config/map")),"recorder, HUD indices and management must not create legacy map root");
  require(Files.isRegularFile(MappingPaths.root(instance).resolve("once-audit/map.json")),"existing new-root map remains readable");
  System.out.println("TEMP_INSTANCE="+instance);System.out.println("PASS: real Mapping lifecycle/store with controlled client cache; no actual game unload claimed");
  call("clearWorld",new Class[]{});((ThreadPoolExecutor)get(M,C,"io")).shutdownNow();
 }
}
