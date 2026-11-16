
import com.kuangsanfeng.ultimategarden.client.function.observation.entity.EntityObservationTracker;
import java.util.*;
public class EntityRegression {
 public static void main(String[] args){
  var t=new EntityObservationTracker();var id=UUID.randomUUID();var object=new Object();
  var a=t.identify("s",1,id,"zombie",object,1,"A");
  var b=t.identify("s",1,id,"zombie",object,2,"B");if(!a.tag().equals(b.tag())||b.firstSeenTick()!=1)throw new AssertionError();
  if(t.markMissing(3).size()!=1)throw new AssertionError();
  if(!t.identify("s",1,id,"zombie",object,4,"B").tag().equals(a.tag()))throw new AssertionError();
  var c=t.identify("s",1,id,"zombie",new Object(),5,"B");if(c.tag().equals(a.tag()))throw new AssertionError();
  var d=t.identify("s",1,UUID.randomUUID(),"arrow",new Object(),6,"ARROW");if(d.tag().equals(c.tag()))throw new AssertionError();
  var e=t.identify("other",1,id,"zombie",object,7,"B");if(e.tag().equals(c.tag()))throw new AssertionError();
  t.clear();if(!t.identities().isEmpty())throw new AssertionError();
  System.out.println("PASS entity ID reuse, object replacement, absence/reappearance, classification and session isolation");
 }
}
