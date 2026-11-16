package com.kuangsanfeng.ultimategarden.client.runtime.world.collection.zombie_shoot;
import com.kuangsanfeng.ultimategarden.client.runtime.world.collection.zombie_shoot.ZombieShootBlockCollector;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat.ZombieShootSnapshot;

import java.util.*;import net.minecraft.core.BlockPos;
import static com.kuangsanfeng.ultimategarden.client.platform.persistence.log.zombie_shoot.compat.ZombieShootSnapshot.map;
public final class ZombieShootOcclusionAnalyzer {
 public static boolean intersects(double[] a,double[] b,double[] box){return com.kuangsanfeng.ultimategarden.client.runtime.world.observation.query.SpatialQueries.intersects(a,b,box);}
 public Map<String,Object> analyze(double[] eye,double[] target,Map<BlockPos,ZombieShootBlockCollector.Cell> cells,long tick){
  int blocked=0,unknown=0,knownSteps=0,totalSteps=0;Set<List<Integer>> blockers=new LinkedHashSet<>();
  var rays=new ArrayList<Object>();
  for(int n=0;n<9;n++){
   double[] end=n==8?new double[]{(target[0]+target[3])/2,(target[1]+target[4])/2,(target[2]+target[5])/2}:new double[]{target[(n&1)==0?0:3],target[(n&2)==0?1:4],target[(n&4)==0?2:5]};
   double length=Math.sqrt(Math.pow(end[0]-eye[0],2)+Math.pow(end[1]-eye[1],2)+Math.pow(end[2]-eye[2],2));int steps=Math.min(512,(int)Math.ceil(length*4)+1);
   boolean missing=length>128,hit=false;Set<BlockPos> visited=new HashSet<>();
   for(int i=0;i<=steps;i++){double t=i/(double)steps;BlockPos p=BlockPos.containing(eye[0]+(end[0]-eye[0])*t,eye[1]+(end[1]-eye[1])*t,eye[2]+(end[2]-eye[2])*t);
    for(int ox=-1;ox<=1;ox++)for(int oy=-1;oy<=1;oy++)for(int oz=-1;oz<=1;oz++){
    BlockPos candidate=p.offset(ox,oy,oz);
    if(!visited.add(candidate))continue;totalSteps++;var cell=cells.get(candidate);if(cell==null||tick-cell.tick()>100){missing=true;continue;}knownSteps++;
    for(var q:cell.boxes()){double[] box={candidate.getX()+q.get(0),candidate.getY()+q.get(1),candidate.getZ()+q.get(2),candidate.getX()+q.get(3),candidate.getY()+q.get(4),candidate.getZ()+q.get(5)};if(intersects(eye,end,box)){hit=true;blockers.add(List.of(candidate.getX(),candidate.getY(),candidate.getZ()));}}
    }
   }
   if(hit)blocked++;else if(missing)unknown++;
   rays.add(map("end",List.of(end[0],end[1],end[2]),"blocked",hit,"unknown",!hit&&missing));
  }
  // Neighbor-expanded <=0.25-block sampling conservatively includes every traversed cell; extra cells can only lower coverage.

  return map("occlusionSampleId",UUID.randomUUID().toString(),"observerOrigin",List.of(eye[0],eye[1],eye[2]),"targetBoundingBox",Arrays.stream(target).boxed().toList(),"blockingBlockPositions",blockers.stream().toList(),"occlusionStatus",unknown>0?"UNKNOWN":blocked==9?"OCCLUDED":blocked>0?"PARTIALLY_OCCLUDED":"CLEAR","sampledRayCount",9,"blockedRayCount",blocked,"unknownRayCount",unknown,"environmentCoverage",totalSteps==0?0:knownSteps/(double)totalSteps,"coverageDefinition","visited cells with observations no older than 100 ticks; not full ray-volume coverage","rays",rays,"geometry","block collision shapes; not visual occlusion or verified projectile launch geometry");
 }
}

