package com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
public interface Terrain {
 enum Knowledge{CLEAR,BLOCKED,UNKNOWN}
 record Check(Knowledge knowledge,String reason){}
 Check transfer(Point from,Point to);
 Check descent(Point from,Point landing);
 Terrain UNKNOWN=new Terrain(){public Check transfer(Point a,Point b){return new Check(Knowledge.UNKNOWN,"NO_WORLD_INFORMATION");}public Check descent(Point a,Point b){return new Check(Knowledge.UNKNOWN,"NO_SUPPORT_OR_CLEARANCE_INFORMATION");}};
}
