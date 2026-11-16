
import com.kuangsanfeng.ultimategarden.client.platform.ui.SettingBinding;
public class SettingBindingRegression {
 public static void main(String[] args){
  boolean[] value={true},editable={false};int[] writes={0};
  var b=new SettingBinding<Boolean>(()->value[0],v->{writes[0]++;value[0]=v;},()->editable[0]);
  if(!b.value()||b.set(false)||!value[0]||writes[0]!=0)throw new AssertionError();
  editable[0]=true;if(!b.set(false)||b.value()||writes[0]!=1)throw new AssertionError();
  editable[0]=false;if(b.set(true)||b.value())throw new AssertionError();
  System.out.println("PASS setting binding retains saved value while disabled and delegates enabled edits once");
 }
}
