package com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot;
import com.kuangsanfeng.ultimategarden.client.function.collection.zombie_shoot.ZombieShootCoordinateSystem;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat.ZombieShootLogger;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat.ZombieShootSnapshot;


import java.util.*;import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootLogCategory;import java.nio.file.*;import java.time.Instant;
import static com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat.ZombieShootSnapshot.map;
public class ZombieShootSession {
 private final com.kuangsanfeng.ultimategarden.client.function.collection.CollectionSession<ZombieShootLogCategory> collection;
 public void select(Set<ZombieShootLogCategory> value){collection.select(value);}
 public boolean accepts(String folder){var category=ZombieShootLogCategory.folder(folder);return collection.accepts(category,folder.equals("sessions"));}

 private final java.util.concurrent.ConcurrentMap<String,String> trackTags=new java.util.concurrent.ConcurrentHashMap<>();
 public void registerTag(String track,String tag){if(trackTags.size()>=65536&&!trackTags.containsKey(track))throw new IllegalStateException("TAG_TRACK_INDEX_LIMIT");trackTags.put(track,tag);}
 public final String id;
 public final ZombieShootCoordinateSystem origin;public final String dimension;public final ZombieShootLogger logger;
 public final double stabilityTolerance=tolerance(), discontinuityDistance=4;public volatile boolean moved;
 private static double tolerance(){try{double v=Double.parseDouble(System.getProperty("ultimategarden.zombieShoot.originTolerance","0.1"));return Double.isFinite(v)&&v>=0?v:.1;}catch(RuntimeException e){return .1;}}
 public ZombieShootSession(Path root,ZombieShootCoordinateSystem origin,String dimension,String modVersion,Set<ZombieShootLogCategory> selected){
  collection=new com.kuangsanfeng.ultimategarden.client.function.collection.CollectionSession<>(selected);id=collection.id();
  this.origin=origin;this.dimension=dimension;logger=new ZombieShootLogger(root,id);
  emit("sessions",map("event","START","tagIdentityPolicy","sessionId + tag; monotonic unsigned 32-bit per session, no wrap; same UUID and live object required for recovery","purpose","passive developer logs; no macro control or Anti-Check trajectory recording","enabledCategories",selected.stream().map(Enum::name).sorted().toList(),"startTimestamp",Instant.now().toString(),"minecraftVersion","26.1.2","modVersion",modVersion,"originX",origin.x(),"originY",origin.y(),"originZ",origin.z(),"originEyeX",origin.eyeX(),"originEyeY",origin.eyeY(),"originEyeZ",origin.eyeZ(),"originYaw",origin.yaw(),"originPitch",origin.pitch(),"coordinateSystem","WORLD_PARALLEL_TRANSLATION_ONLY","sampleTickRate","one END_CLIENT_TICK while enabled; not wall-clock 20Hz guarantee","stabilityTolerance",stabilityTolerance,"discontinuityDistance",discontinuityDistance,"collectionRadius",ZombieShootCoordinateSystem.SCAN_RADIUS,"blockRangeCriterion","integer BlockPos anchor distance <=30 from fixed player origin; checked before world reads","rangeBoundary","sampling cutoff, not physical path endpoint","blockRule","floor(feet)+[-3,3]^3 intersect fixed-origin radius-30 sphere; scan budget 48 cells/tick; refresh 100 ticks","inGroundUnavailable","AbstractArrow.isInGround is protected; null","crossSessionAlignment","No cross-session merging"));
 }
 public boolean emit(String folder,Map<String,Object> data){if(!accepts(folder))return false;var copy=new LinkedHashMap<>(data);copy.put("sessionId",id);copy.putIfAbsent("timestamp",Instant.now().toString());copy.put("dimension",dimension);copy.put("coordinateOrigin",List.of(origin.x(),origin.y(),origin.z()));
 if(copy.get("targetBoundingBox") instanceof List<?> bb){var relative=new ArrayList<Double>();for(int i=0;i<6;i++)relative.add(((Number)bb.get(i)).doubleValue()-(i%3==0?origin.x():i%3==1?origin.y():origin.z()));copy.put("targetBoundingBoxRelative",relative);}
 if(copy.get("fittedCurve") instanceof List<?> curve){var expanded=new ArrayList<Object>();for(var v:curve){@SuppressWarnings("unchecked") var vertex=new LinkedHashMap<>((Map<String,Object>)v);var rel=(List<?>)vertex.get("relativePosition");vertex.put("worldPosition",List.of(origin.x()+((Number)rel.get(0)).doubleValue(),origin.y()+((Number)rel.get(1)).doubleValue(),origin.z()+((Number)rel.get(2)).doubleValue()));expanded.add(vertex);}copy.put("fittedCurve",expanded);}
 if(copy.get("turnRelativePosition") instanceof List<?> point){double x=((Number)point.get(0)).doubleValue(),y=((Number)point.get(1)).doubleValue(),z=((Number)point.get(2)).doubleValue();copy.putAll(map("turnWorldX",origin.x()+x,"turnWorldY",origin.y()+y,"turnWorldZ",origin.z()+z,"turnRelativeX",x,"turnRelativeY",y,"turnRelativeZ",z));copy.put("clientTick",copy.get("turnEventTick"));}
return collection.submit(ZombieShootLogCategory.folder(folder),folder.equals("sessions"),folder+"/"+(folder.equals("tags")?"session_":"")+id+".jsonl",tagged(new ZombieShootSnapshot(copy).data()),logger::offer);}
 private Map<String,Object> tagged(Map<String,Object> input){
  var out=new LinkedHashMap<String,Object>();var refs=new TreeSet<String>();
  input.forEach((k,v)->{Object value=v;if(v instanceof Map<?,?> m){@SuppressWarnings("unchecked") var typed=(Map<String,Object>)m;value=tagged(typed);}else if(v instanceof List<?> list)value=tagList(list);out.put(k,value);});
  Object track=out.containsKey("entityTrackId")?out.get("entityTrackId"):out.get("track");if(track!=null){var tag=trackTags.get(track.toString());if(tag!=null)out.put("tag",tag);}
  gather(out,refs);if(!refs.isEmpty()&&!out.containsKey("tag"))out.put("entityTags",List.copyOf(refs));return out;
 }
 private List<Object> tagList(List<?> input){var out=new ArrayList<Object>();for(var v:input){if(v instanceof Map<?,?> m){@SuppressWarnings("unchecked") var typed=(Map<String,Object>)m;out.add(tagged(typed));}else if(v instanceof List<?> l)out.add(tagList(l));else out.add(v);}return out;}
 private void gather(Object v,Set<String> refs){if(v instanceof Map<?,?> m){if(m.get("tag") instanceof String tag)refs.add(tag);m.values().forEach(x->gather(x,refs));}else if(v instanceof List<?> l)l.forEach(x->gather(x,refs));}
 public void stop(long tick){emit("sessions",map("event","END_REQUEST","clientTick",tick,"endTimestamp",Instant.now().toString(),"playerMoved",moved,"totalZombieSamples",logger.count("zombies/")+logger.count("diagnostics/zombies/"),"totalArrowSamples",logger.count("arrows/"),"totalPathSamples",logger.count("paths/raw/"),"totalBlockSamples",logger.count("environment/blocks/"),"droppedSamples",logger.dropped.get(),"collectionErrors",logger.errors.get(),"countsAt","end request; final counts in writer completion are authoritative"));collection.stop();logger.close();trackTags.clear();}
}

