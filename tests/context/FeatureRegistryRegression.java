
import com.kuangsanfeng.ultimategarden.client.runtime.feature.FeatureRegistry;
import java.util.*;
public class FeatureRegistryRegression {
 public static void main(String[] args){
  var r=new FeatureRegistry();int[] starts={0},stops={0},reads={0};
  r.register("one",()->starts[0]++,reason->stops[0]++,()->{reads[0]++;return new FeatureRegistry.State(starts[0]>stops[0],"STATE","NONE",Map.of("count",reads[0]));});
  r.snapshot("one",FeatureRegistry.State.class);if(reads[0]!=1||starts[0]!=0||stops[0]!=0)throw new AssertionError();
  r.start("one");r.stop("one","DISCONNECTED");if(starts[0]!=1||stops[0]!=1)throw new AssertionError();
  try{r.register("one",null,null,()->"x");throw new AssertionError();}catch(IllegalArgumentException expected){}
  try{r.snapshots().clear();throw new AssertionError();}catch(UnsupportedOperationException expected){}
  r.register("contextual",null,null,()->false);
  try{r.start("contextual");throw new AssertionError();}catch(UnsupportedOperationException expected){}
  if(r.ids().size()!=2)throw new AssertionError();
  System.out.println("PASS feature registration, read-only queries, one-shot lifecycle delegation and contextual start guards");
 }
}
