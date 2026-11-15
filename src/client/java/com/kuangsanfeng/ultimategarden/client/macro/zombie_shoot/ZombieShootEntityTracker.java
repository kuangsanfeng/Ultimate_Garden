package com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot;
import java.util.*;
public final class ZombieShootEntityTracker {
 public record Previous(String id,long first,long tick,double x,double y,double z,Double heading,int segment){}
 public record Sample(Previous current,Previous previous,boolean discontinuity){}
 public record Identity(String tag,String sessionId,int entityId,UUID entityUuid,String entityType,long firstSeenTick,long lastSeenTick,String classification,String trackingState){}
 private record Known(java.lang.ref.WeakReference<Object> object,Identity identity){}
 private final Map<UUID,Known> known=new HashMap<>();private long nextTag=1;
 public Identity identify(String session,int id,UUID uuid,String type,Object object,long tick,String classification){
  var k=known.get(uuid);var old=k==null?null:k.identity();
  boolean same=k!=null&&k.object().get()==object&&old.entityId()==id&&old.entityType().equals(type)&&old.sessionId().equals(session);
  if(!same){tracks.remove(id);identities.remove(id);known.entrySet().removeIf(e->e.getValue().object().get()==null);if(known.size()>=65536||nextTag>0xffffffffL)throw new IllegalStateException("ENTITY_TAG_SPACE_EXHAUSTED");}
  var now=new Identity(same?old.tag():String.format(Locale.ROOT,"%08X",nextTag++),session,id,uuid,type,same?old.firstSeenTick():tick,tick,classification,"OBSERVED");
  known.put(uuid,new Known(new java.lang.ref.WeakReference<>(object),now));return now;
 }
 public List<Identity> markMissing(long tick){var changed=new ArrayList<Identity>();known.replaceAll((uuid,k)->{var i=k.identity();if(i.lastSeenTick()<tick&&i.trackingState().equals("OBSERVED")){var missing=new Identity(i.tag(),i.sessionId(),i.entityId(),i.entityUuid(),i.entityType(),i.firstSeenTick(),i.lastSeenTick(),i.classification(),"NOT_OBSERVED");changed.add(missing);return new Known(k.object(),missing);}return k;});return changed;}
 public List<Identity> identities(){return known.values().stream().map(Known::identity).toList();}
 public List<Identity> observedAt(long tick){return known.values().stream().map(Known::identity).filter(i->i.lastSeenTick()==tick).limit(256).toList();}
 private final Map<Integer,Previous> tracks=new HashMap<>();private final Map<Integer,UUID> identities=new HashMap<>();
 public Sample observe(int entityId,UUID uuid,long tick,double x,double y,double z,double jump){
  Previous old=tracks.get(entityId);if(!uuid.equals(identities.get(entityId)))old=null;
  boolean gap=old!=null&&(tick-old.tick()!=1||distance(old,x,y,z)>jump);
  Double heading=old==null||gap||distance(old,x,y,z)<.0001?null:Math.atan2(z-old.z(),x-old.x());
  var now=new Previous(old==null?UUID.randomUUID().toString():old.id(),old==null?tick:old.first(),tick,x,y,z,heading,old==null?0:old.segment()+(gap?1:0));
  tracks.put(entityId,now);identities.put(entityId,uuid);return new Sample(now,old,gap);
 }
 private double distance(Previous p,double x,double y,double z){return Math.sqrt(Math.pow(p.x()-x,2)+Math.pow(p.y()-y,2)+Math.pow(p.z()-z,2));}
 public void retain(Set<Integer> seen){tracks.keySet().retainAll(seen);identities.keySet().retainAll(seen);}
}

