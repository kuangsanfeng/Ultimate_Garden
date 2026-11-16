
import com.kuangsanfeng.ultimategarden.client.function.observation.query.ObservationCache;
import com.kuangsanfeng.ultimategarden.client.function.observation.query.ScanBudget;
import com.kuangsanfeng.ultimategarden.client.function.observation.query.SpatialQueries;

import java.util.*;
public class SpatialRegression {
 public static void main(String[] args){
  if(!SpatialQueries.inSphere(30,0,0,30)||SpatialQueries.inSphere(30.1,0,0,30))throw new AssertionError();
  if(!SpatialQueries.relative(-10,-5,0,-11,-7,3).equals(List.of(1.,2.,-3.)))throw new AssertionError();
  var all=new HashSet<String>();for(int i=0;i<343;i++)all.add(Arrays.toString(SpatialQueries.cubeOffset(i,3)));
  if(all.size()!=343)throw new AssertionError();
  if(!SpatialQueries.intersects(new double[]{-1,.5,.5},new double[]{2,.5,.5},new double[]{0,0,0,1,1,1}))throw new AssertionError();
  if(SpatialQueries.intersects(new double[]{-1,2,.5},new double[]{2,2,.5},new double[]{0,0,0,1,1,1}))throw new AssertionError();
  var cache=new ObservationCache<Integer,Integer>(2);cache.put(1,1);cache.put(2,2);cache.put(3,3);if(cache.containsKey(1)||cache.size()!=2)throw new AssertionError();cache.invalidate();if(!cache.isEmpty())throw new AssertionError();
  var budget=new ScanBudget();budget.begin(48);int n=0;while(budget.take())n++;if(n!=48||budget.take())throw new AssertionError();
  System.out.println("PASS sphere edge, translation, 343 cells, segment intersection, bounded cache invalidation and scan budget");
 }
}
