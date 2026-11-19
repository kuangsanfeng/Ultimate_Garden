package com.kuangsanfeng.ultimategarden.client.function.path_finding.terrain;

import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing.ClientTerrain;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing.Terrain.Knowledge;
import com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.Terrain;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.*;

/** Client-thread checks. Reuses the live body, chunk and support checks used at dispatch. */
public final class LivePathTerrain implements Terrain {
    private final ClientTerrain live;
    private boolean unknown;

    public LivePathTerrain(ClientLevel level) { live=new ClientTerrain(level); }

    @Override public MotionCheck motionSweep(Point from,Point to) {
        var knowledge=live.transfer(from,to).knowledge();
        unknown|=knowledge==Knowledge.UNKNOWN;
        return switch(knowledge){
            case CLEAR->MotionCheck.CLEAR;
            case BLOCKED->MotionCheck.BLOCKED;
            case UNKNOWN->MotionCheck.UNKNOWN;
        };
    }
    @Override public boolean sweep(Point from,Point to){return motionSweep(from,to)==MotionCheck.CLEAR;}
    @Override public boolean clear(Point feet){return sweep(feet,feet);}
    @Override public boolean supported(Point feet){
        var support=live.descent(feet,feet).knowledge();
        unknown|=support==Knowledge.UNKNOWN;
        return support==Knowledge.CLEAR;
    }
    @Override public Set<String> missing(){return unknown?Set.of("LIVE_TERRAIN_UNKNOWN"):Set.of();}
    @Override public Optional<Point> localSurface(Point top,double maxDrop){return live.supportSurface(top,maxDrop);}
    @Override public Optional<Point> ground(Point feet){
        // Search only for nearby local landings. Never look through a blocked or unknown segment.
        double top=Math.floor(feet.y()*2)/2;
        for(int i=0;i<=8;i++){
            Point at=new Point(feet.x(),top-i*.5,feet.z());
            if(!sweep(feet,at))return Optional.empty();
            if(supported(at))return Optional.of(at);
        }
        return Optional.empty();
    }
}
