package com.kuangsanfeng.ultimategarden.client.runtime.world.collection.zombie_shoot;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootLogCategory;
import com.kuangsanfeng.ultimategarden.client.runtime.world.collection.zombie_shoot.ZombieShootArrowAngles;
import com.kuangsanfeng.ultimategarden.client.runtime.world.collection.zombie_shoot.ZombieShootBlockCollector;
import com.kuangsanfeng.ultimategarden.client.runtime.world.collection.zombie_shoot.ZombieShootClassifier;
import com.kuangsanfeng.ultimategarden.client.runtime.world.collection.zombie_shoot.ZombieShootCoordinateSystem;
import com.kuangsanfeng.ultimategarden.client.runtime.world.collection.zombie_shoot.compat.ZombieShootLogGate;
import com.kuangsanfeng.ultimategarden.client.runtime.world.collection.zombie_shoot.ZombieShootOcclusionAnalyzer;
import com.kuangsanfeng.ultimategarden.client.runtime.world.collection.zombie_shoot.ZombieShootPathAnalyzer;
import com.kuangsanfeng.ultimategarden.client.runtime.world.collection.zombie_shoot.ZombieShootPathCollector;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat.ZombieShootSession;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat.ZombieShootSnapshot;


import java.util.*;
import static com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootLogCategory.*;import java.util.concurrent.*;
import net.minecraft.client.Minecraft;import net.minecraft.world.entity.Entity;import net.minecraft.world.entity.monster.zombie.Zombie;import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.core.registries.BuiltInRegistries;import net.fabricmc.loader.api.FabricLoader;
import static com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat.ZombieShootSnapshot.map;
/** Passive client-thread reader. Analysis receives immutable observations only. */
public class ZombieShootCollector {
 public static final ZombieShootCollector INSTANCE=new ZombieShootCollector();
 private final ZombieShootLogGate gate=new ZombieShootLogGate();private boolean failed,previousMacroRunning;
 private Runnable gateChanged=()->{};
 public void setGateChanged(Runnable listener){gateChanged=Objects.requireNonNull(listener);}
 public boolean logsEnabled(){return gate.enabled();}
 public void setLogsEnabled(boolean value){if(value&&!gate.enabled())failed=false;gate.setEnabled(value);gateChanged.run();if(!value)stop();}
 private boolean pathSelection(Set<ZombieShootLogCategory> categories){return categories.contains(PATH_POINTS)||categories.contains(PATH_MODELS)||categories.contains(TURN_EVENTS);}
 public void optionsChanged(Set<ZombieShootLogCategory> categories){
  categories=categories.stream().filter(ZombieShootLogCategory::legacy).collect(java.util.stream.Collectors.toUnmodifiableSet());
  boolean resumePaths=!pathSelection(gate.categories())&&pathSelection(categories);
  gate.categories(categories);if(session!=null)session.select(categories);
  // A disabled path interval is unobserved, never a straight connection to the old point list.
  if(resumePaths&&enabled()){paths=new ZombieShootPathCollector();pathAnalyzer=new ZombieShootPathAnalyzer();}
  if(categories.isEmpty())stop();
 }
 public void update(boolean macroRunning){
  try{
  optionsChanged(com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient.getConfigManager().getZombieShootoutConfig().logCategories());
  if(macroRunning&&!previousMacroRunning)failed=false;previousMacroRunning=macroRunning;
  if(!gate.collecting(macroRunning)){stop();return;}
  if(failed)return;if(!enabled())startSession();if(enabled())tick();
  }catch(RuntimeException ex){
   failed=true;try{stop();}catch(RuntimeException cleanup){if(session!=null)session.logger.close();}
   status="Error";com.kuangsanfeng.ultimategarden.Ultimate_Garden.LOGGER.error("Zombie Shoot developer logs stopped",ex);
  }
 }
 private boolean selected(ZombieShootLogCategory category){return gate.categories().contains(category);}
 private boolean selectedZombie(String classification){var category=ZombieShootLogCategory.folder("zombies/"+classification.toLowerCase(Locale.ROOT));return category!=null&&selected(category);}
 private ZombieShootSession session;private Object world;private long tick,index;private com.kuangsanfeng.ultimategarden.client.runtime.world.observation.entity.EntityObservationTracker tracker;private ZombieShootPathAnalyzer pathAnalyzer;private ZombieShootPathCollector paths;private ZombieShootBlockCollector blocks;
 private final ExecutorService analysis=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"UG-ZombieShoot-Analysis");t.setDaemon(true);return t;});
 private volatile boolean analyzing;private volatile String modelStatus="Not Ready";private String status="Stopped";private long[] baseline=new long[4];
 public boolean enabled(){return status.equals("Recording");} public String status(){return session!=null&&session.logger.error!=null?"Error":status;}
 public String modelStatus(){return modelStatus;}
 private long total(int i){String[] prefix={"zombies/","arrows/","paths/raw/","environment/blocks/"};return session.logger.count(prefix[i])+(i==0?session.logger.count("diagnostics/zombies/"):0);}
 public long count(int i){if(session==null)return 0;String[] prefix={"zombies/","arrows/","paths/raw/","environment/blocks/"};return Math.max(0,total(i)-baseline[i]);}
 public void resetCounters(){if(session!=null){String[] p={"zombies/","arrows/","paths/raw/","environment/blocks/"};for(int i=0;i<4;i++)baseline[i]=total(i);}}
 private void startSession(){
  if(enabled()||analyzing||session!=null&&!session.logger.finished())return;
  var c=Minecraft.getInstance();if(c.player==null||c.level==null){status="Error";return;}
  var p=c.player;var eye=p.getEyePosition();
  session=new ZombieShootSession(FabricLoader.getInstance().getGameDir().resolve("logs/ultimategarden/zombie shoot"),new ZombieShootCoordinateSystem(p.getX(),p.getY(),p.getZ(),eye.x,eye.y,eye.z,p.getYRot(),p.getXRot()),c.level.dimension().identifier().toString(),FabricLoader.getInstance().getModContainer("ultimate_garden").orElseThrow().getMetadata().getVersion().getFriendlyString(),gate.categories());
  world=c.level;tick=0;index=0;baseline=new long[4];tracker=new com.kuangsanfeng.ultimategarden.client.runtime.world.observation.entity.EntityObservationTracker();paths=new ZombieShootPathCollector();pathAnalyzer=new ZombieShootPathAnalyzer();blocks=new ZombieShootBlockCollector();modelStatus="Collecting";status="Recording";
 }
 public void stop(){
  var closing=session;
  try{if(closing!=null&&enabled()){
   if(selected(TAGS)&&tracker!=null)for(var identity:tracker.observedAt(tick))closing.emit("tags",map("tag",identity.tag(),"entityId",identity.entityId(),"entityUuid",identity.entityUuid().toString(),"entityType",identity.entityType(),"classification",identity.classification(),"firstSeenTick",identity.firstSeenTick(),"lastSeenTick",identity.lastSeenTick(),"trackingState","SESSION_ENDED"));
   closing.stop(tick);
  }}catch(RuntimeException error){failed=true;if(closing!=null)closing.logger.close();com.kuangsanfeng.ultimategarden.Ultimate_Garden.LOGGER.error("Zombie Shoot developer log close failed",error);}
  finally{status=failed?"Error":"Stopped";if(modelStatus.equals("Collecting"))modelStatus="Insufficient Data";world=null;blocks=null;paths=null;tracker=null;pathAnalyzer=null;}
 }

 public void shutdown(){
  stop();analysis.shutdown();try{analysis.awaitTermination(3,TimeUnit.SECONDS);if(session!=null)session.logger.awaitClose(3000);}catch(InterruptedException e){Thread.currentThread().interrupt();}
 }
 private void tick(){
  if(!enabled())return;var c=Minecraft.getInstance();if(c.player==null||c.level==null||c.level!=world){stop();return;}
  if(session.logger.error!=null){com.kuangsanfeng.ultimategarden.Ultimate_Garden.LOGGER.error("Zombie Shoot recording stopped: {}",session.logger.error);stop();failed=true;status="Error";return;}
  try{
   tick++;boolean needPaths=selected(PATH_POINTS)||selected(PATH_MODELS)||selected(TURN_EVENTS);
   boolean needBlocks=selected(BLOCKS)||selected(BLOCK_SCANS)||selected(OCCLUSION);
   var origin=session.origin;double distance=origin.displacement(c.player.getX(),c.player.getY(),c.player.getZ());boolean stable=distance<=session.stabilityTolerance;session.moved|=!stable;
   session.emit("diagnostics",map("event","PLAYER_STABILITY","clientTick",tick,"playerOffsetX",c.player.getX()-origin.x(),"playerOffsetY",c.player.getY()-origin.y(),"playerOffsetZ",c.player.getZ()-origin.z(),"playerOffset",origin.relative(c.player.getX(),c.player.getY(),c.player.getZ()),"playerDisplacementFromOrigin",distance,"originStable",stable));
   var seen=new HashSet<Integer>();var targets=new ArrayList<Map<String,Object>>();int sampled=0;
   for(var e:c.level.entitiesForRendering()){
    if(!(e instanceof Zombie)&&!(e instanceof AbstractArrow))continue;
    if(e instanceof AbstractArrow&&!selected(ARROWS)&&!selected(TAGS))continue;
    if(e instanceof Zombie&&!needPaths&&!needBlocks&&!selected(TAGS)&&!selected(DIAGNOSTICS)&&gate.categories().stream().noneMatch(ZombieShootLogCategory::zombie))continue;
    if(!origin.contains(e.getX(),e.getY(),e.getZ()))continue;
    var equipment=e instanceof Zombie z?ZombieShootClassifier.items(z):List.<String>of();
    String classification=e instanceof Zombie z?ZombieShootClassifier.classify(equipment,z.isBaby()):"ARROW";
    if(e instanceof Zombie&&!needPaths&&!needBlocks&&!selected(TAGS)&&!(classification.equals("UNCLASSIFIED")&&selected(DIAGNOSTICS))&&!selectedZombie(classification))continue;
    if(sampled++>=256){session.logger.dropped.incrementAndGet();continue;}
    seen.add(e.getId());
    var identity=tracker.identify(session.id,e.getId(),e.getUUID(),BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString(),e,tick,classification);
    var track=tracker.observe(e.getId(),e.getUUID(),tick,e.getX(),e.getY(),e.getZ(),session.discontinuityDistance);var now=track.current();var prev=track.previous();session.registerTag(now.id(),identity.tag());
    session.emit("tags",map("tag",identity.tag(),"entityTrackId",now.id(),"entityId",identity.entityId(),"entityUuid",identity.entityUuid().toString(),"entityType",identity.entityType(),"classification",classification,"firstSeenTick",identity.firstSeenTick(),"lastSeenTick",tick,"trackingState","OBSERVED"));
    if(e instanceof AbstractArrow&&!selected(ARROWS))continue;
    if(e instanceof Zombie&&!needPaths&&!needBlocks&&!selectedZombie(classification)&&!(classification.equals("UNCLASSIFIED")&&selected(DIAGNOSTICS)))continue;
boolean continuous=prev!=null&&!track.discontinuity();
    Double dx=continuous?e.getX()-prev.x():null,dy=continuous?e.getY()-prev.y():null,dz=continuous?e.getZ()-prev.z():null;
    var velocity=e.getDeltaMovement();var box=e.getBoundingBox();
    var data=map("sampleIndex",++index,"clientTick",tick,"entityTrackId",now.id(),"entityId",e.getId(),"entityType",BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString(),"worldX",e.getX(),"worldY",e.getY(),"worldZ",e.getZ(),"relativePosition",origin.relative(e.getX(),e.getY(),e.getZ()),"previousWorldX",prev==null?null:prev.x(),"previousWorldY",prev==null?null:prev.y(),"previousWorldZ",prev==null?null:prev.z(),"velocityX",velocity.x,"velocityY",velocity.y,"velocityZ",velocity.z,"deltaX",dx,"deltaY",dy,"deltaZ",dz,"horizontalSpeed",continuous?Math.hypot(dx,dz):null,"totalSpeed",continuous?Math.sqrt(dx*dx+dy*dy+dz*dz):null,"yaw",e.getYRot(),"pitch",e.getXRot(),"previousYaw",e.yRotO,"previousPitch",e.xRotO,"onGround",e.onGround(),"boundingBox",List.of(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ),"boundingBoxWidth",box.maxX-box.minX,"boundingBoxHeight",box.maxY-box.minY,"ticksSinceFirstSeen",tick-now.first(),"ticksSinceLastSample",prev==null?null:tick-prev.tick(),"positionDiscontinuity",track.discontinuity(),"trajectorySegment",now.segment(),"distanceFromOrigin",origin.displacement(e.getX(),e.getY(),e.getZ()),"collectionRadius",ZombieShootCoordinateSystem.SCAN_RADIUS);
    var relative=origin.relative(e.getX(),e.getY(),e.getZ());
    data.putAll(map("relativeX",relative.get(0),"relativeY",relative.get(1),"relativeZ",relative.get(2),"boundingBoxMinX",box.minX,"boundingBoxMinY",box.minY,"boundingBoxMinZ",box.minZ,"boundingBoxMaxX",box.maxX,"boundingBoxMaxY",box.maxY,"boundingBoxMaxZ",box.maxZ));
    if(e instanceof Zombie zombie){
     var items=equipment;data.putAll(map("classification",classification,"isBaby",zombie.isBaby(),"helmetItemId",items.get(0),"chestplateItemId",items.get(1),"leggingsItemId",items.get(2),"bootsItemId",items.get(3),"bodyYaw",zombie.yBodyRot,"headYaw",zombie.getYHeadRot()));
     session.emit(classification.equals("UNCLASSIFIED")?"diagnostics/zombies":"zombies/"+classification.toLowerCase(Locale.ROOT),data);
     var rel=origin.relative(e.getX(),e.getY(),e.getZ());var point=new ZombieShootPathCollector.Point(now.id(),now.segment(),tick,classification,rel.get(0),rel.get(1),rel.get(2),continuous?Math.hypot(dx,dz):null,now.heading());if(needPaths)paths.add(point);
     String scan=needBlocks?blocks.request(e.blockPosition(),now.id(),rel,tick,session):null;
     var path=new LinkedHashMap<>(data);path.putAll(map("verticalSpeed",dy,"heading",now.heading(),"headingChange",now.heading()!=null&&prev!=null&&prev.heading()!=null?Math.atan2(Math.sin(now.heading()-prev.heading()),Math.cos(now.heading()-prev.heading())):null,"pathCandidateId","UNCLASSIFIED","blockScanId",scan));session.emit("paths/raw",path);
     if(selected(OCCLUSION)&&targets.size()<8)targets.add(map("entityTrackId",now.id(),"target",List.of(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ),"scanId",scan));
    }else if(e instanceof AbstractArrow arrow){var owner=arrow.getOwner();var eye=c.player.getEyePosition();data.putAll(ZombieShootArrowAngles.sample(e.getYRot(),e.getXRot(),c.player.getYRot(),c.player.getXRot(),origin.yaw(),origin.pitch(),velocity.x,velocity.y,velocity.z,eye.x,eye.y,eye.z,e.getX(),e.getY(),e.getZ()));data.putAll(map("arrowYawChange",ZombieShootArrowAngles.wrap(e.getYRot()-e.yRotO),"arrowPitchChange",e.getXRot()-e.xRotO,"inGround",null,"inWater",arrow.isInWater(),"noGravity",arrow.isNoGravity(),"ownerEntityId",owner==null?null:owner.getId(),"collisionInformation",null));session.emit("arrows",data);}
   }
   tracker.retain(seen);for(var identity:tracker.markMissing(tick))session.emit("tags",map("tag",identity.tag(),"entityId",identity.entityId(),"entityUuid",identity.entityUuid().toString(),"entityType",identity.entityType(),"classification",identity.classification(),"firstSeenTick",identity.firstSeenTick(),"lastSeenTick",identity.lastSeenTick(),"trackingState",identity.trackingState()));if(needBlocks)blocks.tick(c.level,tick,session);
   if(tick%100==0&&!analyzing&&(selected(PATH_MODELS)||selected(TURN_EVENTS)||selected(OCCLUSION))){analyzing=true;var input=paths.snapshot();var environment=blocks.snapshot();var targetCopy=targets.stream().map(t->new ZombieShootSnapshot(t).data()).toList();var s=session;var analyzer=pathAnalyzer;boolean analyzePaths=selected(PATH_MODELS)||selected(TURN_EVENTS);long observedTick=tick;
    analysis.execute(()->{try{
     var result=analyzer.analyze(analyzePaths?input:List.of());if(session==s&&enabled())modelStatus=result.status();for(var model:result.models()){var m=new LinkedHashMap<>(model);m.put("clientTick",observedTick);m.put("sessionPlayerMoved",s.moved);s.emit("paths/models",m);}for(var turn:result.turns())s.emit("paths/turns",turn);
     for(var target:targetCopy){@SuppressWarnings("unchecked") var b=(List<Double>)target.get("target");double[] bounds=b.stream().mapToDouble(Double::doubleValue).toArray();var report=new ZombieShootOcclusionAnalyzer().analyze(new double[]{s.origin.eyeX(),s.origin.eyeY(),s.origin.eyeZ()},bounds,environment,observedTick);report.putAll(map("clientTick",observedTick,"entityTrackId",target.get("entityTrackId"),"pathId","UNCLASSIFIED","blockScanId",target.get("scanId"),"originPlayerPosition",List.of(s.origin.x(),s.origin.y(),s.origin.z())));s.emit("environment/occlusion",report);}
    }catch(RuntimeException ex){s.emit("diagnostics",map("event","ANALYSIS_ERROR","reason",ex.toString()));}finally{analyzing=false;}});
   }
  }catch(RuntimeException ex){session.emit("diagnostics",map("event","COLLECTION_ERROR","clientTick",tick,"reason",ex.toString()));session.logger.errors.incrementAndGet();stop();failed=true;status="Error";}
 }
}
