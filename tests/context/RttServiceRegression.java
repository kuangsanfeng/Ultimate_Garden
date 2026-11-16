
import com.kuangsanfeng.ultimategarden.client.platform.network.NativeRttService;
public class RttServiceRegression {
 public static void main(String[] args){
  var s=new NativeRttService();Object c=new Object();long[] token={0};int[] sends={0};
  s.start(c,100,n->{token[0]=n;sends[0]++;});
  s.tick(c,101,.5,n->{sends[0]++;});
  if(sends[0]!=1||s.pong(new Object(),token[0],200)||s.pong(c,token[0]+1,200))throw new AssertionError();
  if(!s.pong(c,token[0],1_000_100)||s.milliseconds()!=1.0||s.sampledAtNanos()!=1_000_100)throw new AssertionError();
  long old=token[0];s.stop();if(s.pong(c,old,2_000_100))throw new AssertionError();
  s.start(c,3_000_100,n->token[0]=n);if(token[0]==old||s.pong(c,old,4_000_100))throw new AssertionError();
  s.tick(c,5_003_000_100L,.5,n->{throw new AssertionError();});
  if(s.status()!=NativeRttService.Status.UNAVAILABLE||s.milliseconds()!=null)throw new AssertionError();
  s.tick(new Object(),5_003_000_101L,.5,n->{throw new AssertionError();});
  if(s.status()!=NativeRttService.Status.STOPPED)throw new AssertionError();
  System.out.println("PASS native RTT pairing, sample time, timeout, stale replies and connection replacement");
 }
}
