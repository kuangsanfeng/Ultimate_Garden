
import com.kuangsanfeng.ultimategarden.client.platform.gui.GuiSessionState;
import com.kuangsanfeng.ultimategarden.client.platform.gui.GuiWait;

public class GuiWaitRegression {
 public static void main(String[] args){
  var wait=new GuiWait();long token=wait.generation();if(!wait.current(token)||wait.exhausted(39,40)||!wait.exhausted(40,40))throw new AssertionError();
  if(!wait.matches("inventory",s->s.equals("inventory"))||!wait.matches(null,s->s==null))throw new AssertionError();
  wait.cancel();if(wait.current(token))throw new AssertionError();
  var state=new GuiSessionState();state.open(true,false);state.close(true);if(state.finish())throw new AssertionError();
  state.open(true,true);state.pause(false);state.close(true);if(state.finish())throw new AssertionError();
  state.open(true,true);state.close(true);if(!state.finish()||state.finish())throw new AssertionError();
  state.open(true,true);state.abort();if(state.isActive())throw new AssertionError();
  System.out.println("PASS GUI wait predicate, timeout/cancel generation, explicit resume eligibility and manual-stop priority");
 }
}
