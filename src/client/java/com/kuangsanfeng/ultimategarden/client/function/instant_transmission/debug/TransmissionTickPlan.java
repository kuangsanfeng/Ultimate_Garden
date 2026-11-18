package com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug;
import java.util.*;
/** Exactly 13 pitches x 40 independent jumps, no height bins or retry queue. */
public final class TransmissionTickPlan {
 public record Key(int pitch,int tick){}
 private int index;private final List<Map<String,Object>> results=new ArrayList<>();
 public Key next(){return index>=520?null:new Key(-90+15*(index/40),index%40);}
 public void result(Key target,Integer actual,boolean paired,boolean obstruction){results.add(Map.of("pitch",target.pitch(),"plannedTick",target.tick(),"actualTick",actual==null?-1:actual,"status",!paired?"MISSING":actual!=null&&actual==target.tick()?"VALID":"MISMATCH","obstructed",obstruction));index++;}
 public List<Map<String,Object>> summary(){var out=new ArrayList<Map<String,Object>>();for(int p=-90;p<=90;p+=15){int pitch=p;var rows=results.stream().filter(r->r.get("pitch").equals(pitch)).toList();out.add(Map.of("pitch",p,"horizontalControl",p==0,"planned",40,"results",rows,"unattempted",40-rows.size(),"valid",rows.stream().filter(r->r.get("status").equals("VALID")).count(),"mismatched",rows.stream().filter(r->r.get("status").equals("MISMATCH")).count(),"missing",rows.stream().filter(r->r.get("status").equals("MISSING")).count()));}return out;}
 public long mismatches(){return results.stream().filter(r->r.get("status").equals("MISMATCH")).count();}public long blocked(){return results.stream().filter(r->r.get("obstructed").equals(true)).count();}
}
