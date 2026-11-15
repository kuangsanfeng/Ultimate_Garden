package com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot;
import java.util.*;
public final class ZombieShootPathCollector {
 public record Point(String track,int segment,long tick,String classification,double x,double y,double z,Double speed,Double heading){}
 private final LinkedHashMap<String,List<Point>> segments=new LinkedHashMap<>();
 public void add(Point p){String key=p.track()+":"+p.segment();var list=segments.computeIfAbsent(key,k->new ArrayList<>());list.add(p);if(list.size()>512)list.removeFirst();while(segments.size()>64)segments.remove(segments.keySet().iterator().next());}
 public List<List<Point>> snapshot(){return segments.values().stream().skip(Math.max(0,segments.size()-32)).map(l->List.copyOf(l.subList(Math.max(0,l.size()-256),l.size()))).toList();}
}

