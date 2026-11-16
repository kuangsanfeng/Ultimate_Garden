package com.kuangsanfeng.ultimategarden.client.function.mapping.replay;

import com.kuangsanfeng.ultimategarden.client.function.mapping.MapRepository.Coordinate;
import java.math.BigInteger;
import java.util.*;

public final class ReplaySelection {
    public final Set<Coordinate> selected=new LinkedHashSet<>();
    private Coordinate anchor;
    /** Vanilla ordinary view-distance footprint (includeNeighbors=false), over stored coordinates only. */
    public static boolean inViewDistance(Coordinate c,int cx,int cz,BigInteger radius){
        long dx=Math.max(0,Math.abs((long)c.x()-cx)-1),dz=Math.max(0,Math.abs((long)c.z()-cz)-1);
        var x=BigInteger.valueOf(dx);var z=BigInteger.valueOf(dz);
        return x.multiply(x).add(z.multiply(z)).compareTo(radius.multiply(radius))<0;
    }
    public static List<Coordinate> withinDistance(Collection<Coordinate> available,int cx,int cz,String requested){
        var radius=ReplayNumbers.commit(requested,"16",BigInteger.ONE,null);
        return available.stream().filter(c->inViewDistance(c,cx,cz,radius)).sorted(Comparator.comparingInt(Coordinate::z).thenComparingInt(Coordinate::x)).toList();
    }
    public void click(Coordinate c,boolean ctrl,boolean shift,Collection<Coordinate> rendered){
        if(c==null){if(!ctrl&&!shift)clear();return;}
        if(shift&&anchor!=null){if(!ctrl)selected.clear();for(var p:rendered)if(p.x()>=Math.min(anchor.x(),c.x())&&p.x()<=Math.max(anchor.x(),c.x())&&p.z()>=Math.min(anchor.z(),c.z())&&p.z()<=Math.max(anchor.z(),c.z()))selected.add(p);}
        else if(ctrl){if(!selected.remove(c))selected.add(c);anchor=c;}else{selected.clear();selected.add(c);anchor=c;}
    }
    public void clear(){selected.clear();anchor=null;}
}
