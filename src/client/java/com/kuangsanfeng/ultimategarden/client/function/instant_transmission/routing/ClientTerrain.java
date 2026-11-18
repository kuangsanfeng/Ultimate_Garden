package com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing;
import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import com.kuangsanfeng.ultimategarden.client.function.observation.query.LoadedWorldView;
import net.minecraft.client.multiplayer.ClientLevel;import net.minecraft.core.BlockPos;import net.minecraft.world.phys.AABB;
/** Client-thread adapter; unloaded cells stay UNKNOWN, never assumed to be air. */
public final class ClientTerrain implements Terrain {
 private final ClientLevel level;private int cells;
 public ClientTerrain(ClientLevel level){this.level=level;}
 private static final class Unknown extends RuntimeException{}
 private boolean occupied(AABB body){
  var view=new LoadedWorldView(level,p->{if(++cells>20000||!level.hasChunkAt(p))throw new Unknown();});
  for(int x=(int)Math.floor(body.minX);x<=(int)Math.floor(body.maxX);x++)for(int y=(int)Math.floor(body.minY);y<=(int)Math.floor(body.maxY);y++)for(int z=(int)Math.floor(body.minZ);z<=(int)Math.floor(body.maxZ);z++){
   var pos=new BlockPos(x,y,z);var state=view.getBlockState(pos);for(var box:state.getCollisionShape(view,pos).toAabbs())if(box.move(x,y,z).intersects(body))return true;
  }return false;
 }
 private AABB body(Point p){return new AABB(p.x()-.3,p.y()+.0001,p.z()-.3,p.x()+.3,p.y()+1.8,p.z()+.3);}
 private Check sweep(Point a,Point b){
  if(level==null)return new Check(Knowledge.UNKNOWN,"WORLD_UNAVAILABLE");cells=0;int n=(int)Math.ceil(a.distance(b)*4);if(n>4096)return new Check(Knowledge.UNKNOWN,"SCAN_BUDGET");
  try{for(int i=0;i<=n;i++){double t=n==0?0:(double)i/n;Point p=new Point(a.x()+(b.x()-a.x())*t,a.y()+(b.y()-a.y())*t,a.z()+(b.z()-a.z())*t);if(occupied(body(p)))return new Check(Knowledge.BLOCKED,"CLIENT_COLLISION_OR_INTERMEDIATE_PLATFORM");}return new Check(Knowledge.CLEAR,"CLIENT_GEOMETRY_CLEAR_SERVER_RESPONSE_UNVERIFIED");}catch(Unknown e){return new Check(Knowledge.UNKNOWN,"UNLOADED_OR_SCAN_BUDGET");}
 }
 /** Actual top of a nearby loaded collision shape, including thin surfaces. */
 public java.util.Optional<Point> supportSurface(Point top,double maxDrop){
  if(level==null)return java.util.Optional.empty();
  var view=new LoadedWorldView(level,p->{if(!level.hasChunkAt(p))throw new Unknown();});
  double best=Double.NEGATIVE_INFINITY;
  try {
   for(int y=(int)Math.floor(top.y());y>=Math.floor(top.y()-maxDrop)-1;y--)
    for(int x=(int)Math.floor(top.x()-.299);x<=Math.floor(top.x()+.299);x++)
     for(int z=(int)Math.floor(top.z()-.299);z<=Math.floor(top.z()+.299);z++){
      var pos=new BlockPos(x,y,z);
      for(var box:view.getBlockState(pos).getCollisionShape(view,pos).toAabbs()){
       double height=y+box.maxY;
       if(height>top.y()+.001||height<top.y()-maxDrop||height<=best
           ||x+box.maxX<=top.x()-.299||x+box.minX>=top.x()+.299||z+box.maxZ<=top.z()-.299||z+box.minZ>=top.z()+.299)continue;
       var feet=new Point(top.x(),height,top.z());
       if(descent(feet,feet).knowledge()==Knowledge.CLEAR)best=height;
      }
     }
  } catch(Unknown missing){return java.util.Optional.empty();}
  return Double.isFinite(best)?java.util.Optional.of(new Point(top.x(),best,top.z())):java.util.Optional.empty();
 }
 public Check transfer(Point a,Point b){return sweep(a,b);}
 public Check descent(Point a,Point b){var path=sweep(a,b);if(path.knowledge()!=Knowledge.CLEAR)return path;
  try{return occupied(new AABB(b.x()-.3,b.y()-.05,b.z()-.3,b.x()+.3,b.y(),b.z()+.3))?path:new Check(Knowledge.BLOCKED,"NO_TARGET_SUPPORT");}catch(Unknown e){return new Check(Knowledge.UNKNOWN,"UNKNOWN_SUPPORT");}
 }
}
