package com.kuangsanfeng.ultimategarden.client.function.path_finding.terrain;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.*;
import com.kuangsanfeng.ultimategarden.client.function.mapping.*;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import net.minecraft.core.*;import net.minecraft.core.registries.BuiltInRegistries;import net.minecraft.resources.Identifier;
import net.minecraft.world.level.BlockGetter;import net.minecraft.world.level.block.state.*;import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.*;import java.util.*;import java.io.*;
import java.util.concurrent.ConcurrentHashMap;
/** Per-plan immutable chunk snapshots, captured once on first use. Missing cells always remain unknown. */
public final class MapTerrain implements PathTypes.Terrain,BlockGetter {
 private final MapRepository repository;
 private final MapConfig config;
 private final Map<Long,Optional<ChunkSnapshot>> chunks=new ConcurrentHashMap<>();
 private final Map<ChunkSnapshot.State,BlockState> states=new ConcurrentHashMap<>();
 private final Map<BlockState,List<AABB>> fixedShapes=new ConcurrentHashMap<>();
 private final Set<String> missing=ConcurrentHashMap.newKeySet(), loadedFiles=ConcurrentHashMap.newKeySet();
 private final ThreadLocal<Query> query=ThreadLocal.withInitial(Query::new);
 private static final List<AABB> SOLID=List.of(new AABB(0,0,0,1,1,1));
 private static final class Query {
  long key;
  Optional<ChunkSnapshot> chunk;
  Map<String,Object> failure=Map.of();
 }
 public Set<String> loadedFiles(){return Set.copyOf(loadedFiles);}
 public Map<String,Object> diagnosticFailure(){return query.get().failure;}
 @Override public boolean parallelQueries(){return true;}
 private void failure(String reason,BlockPos pos){query.get().failure=Map.of("reason",reason,"blockX",pos.getX(),"blockY",pos.getY(),"blockZ",pos.getZ());}
 private static final class Unknown extends RuntimeException{
  static final Unknown VALUE=new Unknown();
  private Unknown(){super(null,null,false,false);}
 }
 public MapTerrain(MapRepository repository)throws IOException{this.repository=repository;config=Objects.requireNonNull(repository.load(),"Map not initialized");}
 public MapConfig config(){return config;}public Set<String> missing(){return Set.copyOf(missing);}
 public int getHeight(){return config.maxY()-config.origin().y();}public int getMinY(){return config.origin().y();}
 public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos p){return null;}
 public net.minecraft.world.level.material.FluidState getFluidState(BlockPos p){return getBlockState(p).getFluidState();}
 private static <T extends Comparable<T>> BlockState property(BlockState s,Property<T> p,String v){return s.setValue(p,p.getValue(v).orElseThrow());}
 public BlockState getBlockState(BlockPos p){
  int cx=Math.floorDiv(p.getX(),16),cz=Math.floorDiv(p.getZ(),16);long key=((long)cx<<32)^(cz&0xffffffffL);
  var q=query.get();
  if(q.chunk==null||q.key!=key){
   q.chunk=chunks.computeIfAbsent(key,k->loadChunk(cx,cz));q.key=key;
  }
  var chunk=q.chunk;
  if(chunk.isEmpty()){failure("MISSING_CHUNK",p);throw Unknown.VALUE;}
  var raw=chunk.get().state(Math.floorMod(p.getX(),16),p.getY()-getMinY(),Math.floorMod(p.getZ(),16));if(raw==null){failure("OUTSIDE_SCAN",p);missing.add("Y_OUTSIDE_SCAN:"+p.getY());throw Unknown.VALUE;}
  return states.computeIfAbsent(raw,v->{var id=Identifier.parse(v.name());if(!BuiltInRegistries.BLOCK.containsKey(id))throw Unknown.VALUE;var block=BuiltInRegistries.BLOCK.getValue(id);var state=block.defaultBlockState();for(var e:v.properties().entrySet()){var prop=block.getStateDefinition().getProperty(e.getKey());if(prop==null)throw Unknown.VALUE;state=property(state,prop,e.getValue());}return state;});
 }
 private Optional<ChunkSnapshot> loadChunk(int cx,int cz){
  try{
   var value=repository.read(config,config.selectedFestival(),cx,cz);
   var path=repository.chunkPath(config,config.selectedFestival(),cx,cz);
   if(value.isPresent()){
    if(!java.nio.file.Files.exists(path))path=repository.chunkPath(config,Festival.NONE,cx,cz);
    loadedFiles.add(path.toString());
   }else missing.add(path.toString());
   return value;
  }catch(IOException e){missing.add(e.toString());return Optional.empty();}
 }
 // Vanilla supplies a position-independent cached shape only for non-dynamic blocks.
 public boolean hasChunk(int cx,int cz){
  long key=((long)cx<<32)^(cz&0xffffffffL);
  return chunks.computeIfAbsent(key,k->loadChunk(cx,cz)).isPresent();
 }
 public List<AABB> collisionBoxes(BlockPos pos){
  try{return boxes(pos);}catch(Unknown unknown){return null;}
 }
 private List<AABB> boxes(BlockPos p){
  var state=getBlockState(p);
  if(!state.getFluidState().isEmpty())return SOLID;
  if(state.getBlock().hasDynamicShape())return state.getCollisionShape(this,p).toAabbs();
  return fixedShapes.computeIfAbsent(state,s->List.copyOf(s.getCollisionShape(this,p).toAabbs()));
 }
 private AABB body(Point p){return new AABB(p.x()-.2999,p.y()+.0001,p.z()-.2999,p.x()+.2999,p.y()+1.7999,p.z()+.2999);}
 private boolean occupied(AABB b){
  var pos=new BlockPos.MutableBlockPos();
  for(int x=(int)Math.floor(b.minX);x<=(int)Math.floor(b.maxX);x++)
   for(int z=(int)Math.floor(b.minZ);z<=(int)Math.floor(b.maxZ);z++)
    for(int y=(int)Math.floor(b.minY);y<=(int)Math.floor(b.maxY);y++)
     for(var shape:boxes(pos.set(x,y,z)))
      if(b.intersects(shape.minX+x,shape.minY+y,shape.minZ+z,shape.maxX+x,shape.maxY+y,shape.maxZ+z)){
       failure("OBSTACLE",pos);return true;
      }
  return false;
 }
 public boolean clear(Point p){query.get().failure=Map.of();try{return !occupied(body(p));}catch(Unknown e){return false;}}
 private AABB corridorBody(Point p){return new AABB(p.x()-.4999,p.y()+.0001,p.z()-.4999,p.x()+.4999,p.y()+1.9999,p.z()+.4999);}
 public boolean corridorClear(Point p){try{return !occupied(corridorBody(p));}catch(Unknown e){return false;}}
 public boolean corridorSweep(Point a,Point b){return sweep(a,b,.4999,1.9999);}
 public boolean supported(Point p){query.get().failure=Map.of();try{boolean support=occupied(new AABB(p.x()-.299,p.y()-.02,p.z()-.299,p.x()+.299,p.y(),p.z()+.299));if(!support)query.get().failure=Map.of("reason","NO_SUPPORT","feet",p);return support&&clear(p);}catch(Unknown e){return false;}}
 @Override public MotionCheck motionSweep(Point a,Point b) {
  if(sweep(a,b))return MotionCheck.CLEAR;
  return "OBSTACLE".equals(diagnosticFailure().get("reason"))?MotionCheck.BLOCKED:MotionCheck.UNKNOWN;
 }
 public boolean sweep(Point a,Point b){
  return sweep(a,b,.2999,1.7999);
 }
 private boolean sweep(Point a,Point b,double radius,double height){
  query.get().failure=Map.of();try{var start=new Vec3(a.x(),a.y(),a.z());var end=new Vec3(b.x(),b.y(),b.z());
   var bounds=new AABB(Math.min(a.x(),b.x())-radius,Math.min(a.y(),b.y())+.0001,Math.min(a.z(),b.z())-radius,
      Math.max(a.x(),b.x())+radius,Math.max(a.y(),b.y())+height,Math.max(a.z(),b.z())+radius);
   var pos=new BlockPos.MutableBlockPos();
   for(int x=(int)Math.floor(bounds.minX);x<=(int)Math.floor(bounds.maxX);x++)
    for(int z=(int)Math.floor(bounds.minZ);z<=(int)Math.floor(bounds.maxZ);z++)
     for(int y=(int)Math.floor(bounds.minY);y<=(int)Math.floor(bounds.maxY);y++)
      for(var shape:boxes(pos.set(x,y,z))){
       var expanded=new AABB(shape.minX+x-radius,shape.minY+y-height,shape.minZ+z-radius,
            shape.maxX+x+radius,shape.maxY+y-.0001,shape.maxZ+z+radius);
       if(expanded.contains(start)||expanded.contains(end)||expanded.clip(start,end).isPresent()){
        failure("OBSTACLE",pos);return false;
       }
      }
   return true;
  }catch(Unknown e){return false;}
 }
 public Optional<Point> ground(Point p){query.get().failure=Map.of("reason","NO_SAFE_LANDING","feet",p);try{int x=(int)Math.floor(p.x()),z=(int)Math.floor(p.z());for(int y=Math.min((int)Math.floor(p.y()),config.maxY()-1);y>=getMinY();y--){var pos=new BlockPos(x,y,z);for(var b:boxes(pos)){double top=b.maxY+y;if(top>p.y()+.001)continue;Point q=new Point(x+.5,top,z+.5);if(clear(q)&&safe(q))return Optional.of(q);}}return Optional.empty();}catch(Unknown e){return Optional.empty();}}
 private boolean safe(Point p){for(int x=(int)Math.floor(p.x())-2;x<=(int)Math.floor(p.x())+2;x++)for(int z=(int)Math.floor(p.z())-2;z<=(int)Math.floor(p.z())+2;z++){String name=BuiltInRegistries.BLOCK.getKey(getBlockState(new BlockPos(x,(int)Math.floor(p.y())-1,z)).getBlock()).toString();if(name.contains("portal")||name.equals("minecraft:slime_block"))return false;}return true;}
}
