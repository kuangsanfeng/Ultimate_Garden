
import com.kuangsanfeng.ultimategarden.client.runtime.world.collection.CollectionGate;
import com.kuangsanfeng.ultimategarden.client.runtime.world.collection.CollectionSession;

import java.util.*;
public class CollectionRegression {
 public static void main(String[] args)throws Exception{
  var gate=new CollectionGate<String>();gate.categories(Set.of("a"));if(gate.collecting(true))throw new AssertionError();gate.setEnabled(true);if(!gate.collecting(true)||gate.collecting(false))throw new AssertionError();
  var session=new CollectionSession<String>(Set.of("a"));int[] count={0};
  if(!session.submit("a",false,"x",Map.of("x",1),(p,v)->{count[0]++;return true;}))throw new AssertionError();
  if(session.submit("b",false,"x",Map.of(),(p,v)->{throw new AssertionError();}))throw new AssertionError();
  session.stop();if(session.accepts("a",true)||session.submit("a",false,"x",Map.of(),(p,v)->{throw new AssertionError();}))throw new AssertionError();
  if(new CollectionSession<String>(Set.of("a")).id().equals(session.id()))throw new AssertionError();
  if(count[0]!=1)throw new AssertionError();
  var barrier=new CollectionSession<String>(Set.of("a"));
  var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
  var stopEntered=new java.util.concurrent.CountDownLatch(1);var stopped=new java.util.concurrent.CountDownLatch(1);
  var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
  var writer=new Thread(()->{try{barrier.submit("a",false,"x",Map.of(),(p,v)->{entered.countDown();try{release.await();}catch(InterruptedException e){throw new RuntimeException(e);}return true;});}catch(Throwable e){failure.set(e);}});
  writer.start();entered.await();
  var stopper=new Thread(()->{stopEntered.countDown();barrier.stop();stopped.countDown();});stopper.start();stopEntered.await();
  boolean endedEarly=stopped.await(30,java.util.concurrent.TimeUnit.MILLISECONDS);
  release.countDown();writer.join(2000);stopper.join(2000);
  if(endedEarly||stopped.getCount()!=0||failure.get()!=null||barrier.accepts("a",false))throw new AssertionError();
  System.out.println("PASS runtime gate, category acceptance, stop rejection and independent collection sessions");
 }
}
