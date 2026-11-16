
import com.kuangsanfeng.ultimategarden.client.function.observation.sampling.TimedWindow;
import java.util.*;
public class WindowRegression {
 record S(long t,double v){}
 public static void main(String[] args){
  var w=new TimedWindow<S>(3,S::t);w.add(new S(0,0));w.add(new S(1,0));var copy=w.snapshot();w.add(new S(2,0));w.add(new S(3,1));if(w.size()!=3||copy.size()!=2||w.getFirst().t()!=1)throw new AssertionError();
  w.expireInclusive(1);if(w.getFirst().t()!=2)throw new AssertionError();
  w.clear();for(int i=0;i<3;i++)w.add(new S(i,0));w.retainBoundary(1.5);if(w.getFirst().t()!=1||w.size()!=2)throw new AssertionError();
  w.resetIf(false);if(w.size()!=2)throw new AssertionError();w.resetIf(true);if(!w.isEmpty())throw new AssertionError();
  System.out.println("PASS bounded timed windows, immutable snapshots, expiry, crossing segment and gap reset");
 }
}
