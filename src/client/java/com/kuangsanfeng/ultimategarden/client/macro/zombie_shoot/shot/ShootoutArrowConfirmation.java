package com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.shot;
import java.util.*;
import net.minecraft.world.phys.Vec3;
/** Bounded session identity history and one pending firing cycle. No world/input access. */
public final class ShootoutArrowConfirmation {
 public enum Detection { CONFIRMED,UNCONFIRMED,NOT_DETECTED }
 public record Observation(UUID uuid,int entityId,String entityType,UUID owner,Vec3 position,Vec3 velocity,long tick,long nanos){}
 public record Attempt(long tick,long nanos,Vec3 eye,Vec3 direction,double flightTicks,double ping){}
 public record Confirmation(Observation arrow,Attempt attempt,long confirmationTick,long confirmationNanos){}
 private static final class Seen {
  final Observation first;boolean used;
  Seen(Observation first){this.first=first;}
 }
 private final Map<UUID,Seen> history=new HashMap<>();private final List<Attempt> attempts=new ArrayList<>();private final ArrayDeque<Attempt> retired=new ArrayDeque<>();
 private UUID player;private long started,timeout;private Confirmation confirmed;private Detection detection=Detection.NOT_DETECTED;
 public void clearSession(){history.clear();cancel();retired.clear();}
 public void reset(UUID localPlayer){history.clear();cancel();retired.clear();player=Objects.requireNonNull(localPlayer);}
 public boolean pending(){return !attempts.isEmpty();}
 public Detection detection(){return detection;}
 public Confirmation confirmation(){return confirmed;}
 public void rememberDispatched(Attempt attempt){retired.addLast(attempt);while(retired.size()>256)retired.removeFirst();}
 public void cancel(){retired.addAll(attempts);while(retired.size()>256)retired.removeFirst();attempts.clear();confirmed=null;detection=Detection.NOT_DETECTED;}
 public void attempted(Attempt attempt,double cooldown,double maxJitter){
  if(confirmed!=null)throw new IllegalStateException("ARROW_ALREADY_CONFIRMED");
  if(attempts.size()>=256)throw new IllegalStateException("SHOT_ATTEMPT_LIMIT");
  if(attempts.isEmpty()){started=attempt.nanos();double seconds=Math.clamp(4*(cooldown+maxJitter)+2*attempt.ping()/1000.+1,5,30);timeout=(long)(seconds*1e9);}
  attempts.add(attempt);
 }
 public boolean timedOut(long now){return pending()&&confirmed==null&&now-started>=timeout;}
 public static boolean supported(String type){return type.equals("minecraft:arrow")||type.equals("minecraft:spectral_arrow");}
 public void attemptedManual(Attempt attempt,double cooldown,double maxJitter){if(attempts.size()>=256)attempts.remove(0);attempted(attempt,cooldown,maxJitter);}
 public void observe(Observation observed){
  if(!supported(observed.entityType()))return;
  Seen seen=history.get(observed.uuid());
  if(seen==null){if(history.size()>=65536)throw new IllegalStateException("ARROW_IDENTITY_CAPACITY");seen=new Seen(observed);history.put(observed.uuid(),seen);}
  if(!pending()||confirmed!=null||seen.used||timedOut(observed.nanos()))return;
  var first=seen.first;
  // One shared UUID history: owner, position and direction never authorize confirmation.
  if(first.entityId()!=observed.entityId()||!first.entityType().equals(observed.entityType()))return;
  for(int i=attempts.size()-1;i>=0;i--){var attempt=attempts.get(i);
   if(first.tick()<attempt.tick()||first.nanos()<attempt.nanos())continue;
   seen.used=true;detection=Detection.CONFIRMED;
   confirmed=new Confirmation(first,attempt,observed.tick(),observed.nanos());return;
  }
 }
}
