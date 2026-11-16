package com.kuangsanfeng.ultimategarden.client.runtime.world.collection.zombie_shoot;
import com.kuangsanfeng.ultimategarden.client.runtime.world.collection.zombie_shoot.ZombieShootCoordinateSystem;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat.ZombieShootSession;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat.ZombieShootSnapshot;

import net.minecraft.core.BlockPos;import net.minecraft.client.multiplayer.ClientLevel;import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;
import net.minecraft.world.level.BlockGetter;
import static com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat.ZombieShootSnapshot.map;
public final class ZombieShootBlockCollector {
 public record Cell(long tick,List<List<Double>> boxes,Map<String,Object> data){}
 private record Job(BlockPos center,String track,String scan,int offset,List<Double> entityPosition){}
 private final LinkedHashMap<BlockPos,Cell> cells=new com.kuangsanfeng.ultimategarden.client.runtime.world.observation.query.ObservationCache<>(8192);
 private final LinkedHashMap<BlockPos,Long> centers=new com.kuangsanfeng.ultimategarden.client.runtime.world.observation.query.ObservationCache<>(512);private final ArrayDeque<Job> jobs=new ArrayDeque<>();
 public static BlockPos offset(int index){var p=com.kuangsanfeng.ultimategarden.client.runtime.world.observation.query.SpatialQueries.cubeOffset(index,3);return new BlockPos(p[0],p[1],p[2]);}
 public static BlockGetter boundedView(BlockGetter world,java.util.function.Predicate<BlockPos> loaded,ZombieShootCoordinateSystem origin){
  return new com.kuangsanfeng.ultimategarden.client.runtime.world.observation.query.LoadedWorldView(world,p->{
   if(!origin.contains(p.getX(),p.getY(),p.getZ()))throw new IllegalStateException("COLLISION_OUTSIDE_COLLECTION_RADIUS");
   if(!loaded.test(p))throw new IllegalStateException("COLLISION_NEIGHBOR_UNLOADED");
  });
 }
 private final com.kuangsanfeng.ultimategarden.client.runtime.world.observation.query.ScanBudget scanBudget=new com.kuangsanfeng.ultimategarden.client.runtime.world.observation.query.ScanBudget();
 public String request(BlockPos center,String track,List<Double> relative,long tick,ZombieShootSession session){
  center=center.immutable();String scan=session.id+":"+center.toShortString()+":"+tick;
  if(tick-centers.getOrDefault(center,-1000L)<100)return session.id+":"+center.toShortString()+":"+centers.get(center);
  if(jobs.size()>=128){session.logger.dropped.incrementAndGet();return null;}
  centers.put(center,tick);while(centers.size()>512)centers.remove(centers.keySet().iterator().next());
  session.emit("environment/scans",map("scanId",scan,"clientTick",tick,"entityTrackId",track,"centerWorldPosition",List.of(center.getX(),center.getY(),center.getZ()),"centerRelativePosition",session.origin.relative(center.getX(),center.getY(),center.getZ()),"extent",3,"cellCount",343,"status","QUEUED","cacheValidity","periodically re-observed; updates may be stale for 100 ticks"));
  jobs.add(new Job(center,track,scan,0,relative));return scan;
 }
 public void tick(ClientLevel level,long tick,ZombieShootSession s){
  var geometry=boundedView(level,p->level.hasChunkAt(p),s.origin);
  scanBudget.begin(48);
  while(!jobs.isEmpty()&&scanBudget.take()){
   Job j=jobs.removeFirst();int o=j.offset();var offset=offset(o);int dx=offset.getX(),dy=offset.getY(),dz=offset.getZ();BlockPos p=j.center().offset(dx,dy,dz);
   if(o<342)jobs.addFirst(new Job(j.center(),j.track(),j.scan(),o+1,j.entityPosition()));
   if(!s.origin.contains(p.getX(),p.getY(),p.getZ()))continue;
   if(!level.hasChunkAt(p)) {s.emit("diagnostics",map("clientTick",tick,"event","UNLOADED_BLOCK","entityTrackId",j.track(),"scanId",j.scan(),"blockWorldPosition",List.of(p.getX(),p.getY(),p.getZ()),"relativePosition",s.origin.relative(p.getX(),p.getY(),p.getZ())));cells.remove(p);continue;}
   try{
    var state=level.getBlockState(p);var shape=state.getCollisionShape(geometry,p);var boxes=new ArrayList<List<Double>>();
    for(var b:shape.toAabbs())boxes.add(List.of(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ));
    var props=new TreeMap<String,String>();state.getValues().forEach(v->props.put(v.property().getName(),v.value().toString()));
    var data=map("clientTick",tick,"scanId",j.scan(),"entityTrackId",j.track(),"entityRelativePosition",j.entityPosition(),"blockWorldX",p.getX(),"blockWorldY",p.getY(),"blockWorldZ",p.getZ(),"blockRelativeX",p.getX()-s.origin.x(),"blockRelativeY",p.getY()-s.origin.y(),"blockRelativeZ",p.getZ()-s.origin.z(),"blockRelativePosition",s.origin.relative(p.getX(),p.getY(),p.getZ()),"offsetX",dx,"offsetY",dy,"offsetZ",dz,"blockId",BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),"blockState",props,"isAir",state.isAir(),"isFluid",!state.getFluidState().isEmpty(),"fluidType",BuiltInRegistries.FLUID.getKey(state.getFluidState().getType()).toString(),"hasCollision",!shape.isEmpty(),"collisionShape",boxes,"shapeKind",shape.isEmpty()?"Empty":boxes.size()==1&&boxes.getFirst().equals(List.of(0.,0.,0.,1.,1.,1.))?"Full Cube":"Partial","hasVisualOcclusion",state.canOcclude(),"cacheValidity","observed at clientTick; changes unverified until low-frequency rescan");
    var old=cells.get(p);if(old==null||!old.boxes().equals(boxes)||!old.data().get("blockState").equals(props)||!old.data().get("blockId").equals(data.get("blockId")))s.emit("environment/blocks",data);
    cells.put(p,new Cell(tick,List.copyOf(boxes),Map.copyOf(data)));while(cells.size()>8192)cells.remove(cells.keySet().iterator().next());
   }catch(RuntimeException e){cells.remove(p);s.emit("diagnostics",map("clientTick",tick,"event","UNKNOWN_COLLISION","entityTrackId",j.track(),"blockPosition",p.toShortString(),"reason",e.toString()));}
  }
 }
 public Map<BlockPos,Cell> snapshot(){return Map.copyOf(cells);}
}

