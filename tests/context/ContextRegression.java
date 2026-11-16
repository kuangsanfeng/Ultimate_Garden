
import com.kuangsanfeng.ultimategarden.client.platform.context.ContextSignal;
import java.util.*;
public class ContextRegression {
 public static void main(String[] args) {
  var events=new ContextSignal<String>();var order=new ArrayList<String>();
  events.subscribe(e->order.add("first:"+e));events.subscribe(e->order.add("second:"+e));
  for(var e:List.of("account-before","account-after","disconnect","stop"))events.publish(e);
  if(!order.equals(List.of("first:account-before","second:account-before","first:account-after","second:account-after","first:disconnect","second:disconnect","first:stop","second:stop")))throw new AssertionError(order);
  var nested=new ContextSignal<String>();var seen=new ArrayList<String>();
  nested.subscribe(e->{seen.add(e);if(e.equals("a"))nested.subscribe(seen::add);});
  nested.publish("a");nested.publish("b");
  if(!seen.equals(List.of("a","b","b")))throw new AssertionError(seen);
  System.out.println("PASS context ordered notifications and stable dispatch snapshot");
 }
}
