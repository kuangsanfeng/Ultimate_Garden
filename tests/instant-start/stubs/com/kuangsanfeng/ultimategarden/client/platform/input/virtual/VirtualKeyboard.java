package com.kuangsanfeng.ultimategarden.client.platform.input.virtual;
import net.minecraft.client.Minecraft;import java.util.*;import java.util.function.BooleanSupplier;
import com.kuangsanfeng.ultimategarden.client.platform.input.view.NativeViewInput;
public final class VirtualKeyboard {
 public static int released;private final Minecraft c;private final Set<String> held=new LinkedHashSet<>();
 public VirtualKeyboard(Minecraft c){this.c=c;}
 private Minecraft.Key key(String k){return switch(k){case "W"->c.options.keyUp;case "S"->c.options.keyDown;case "A"->c.options.keyLeft;case "D"->c.options.keyRight;case "SPACE"->c.options.keyJump;default->c.options.keyShift;};}
 public Map<String,Object> diagnosticState(){var v=new LinkedHashMap<String,Object>();var physical=new LinkedHashMap<String,Object>();for(String k:List.of("W","A","S","D","SPACE","SHIFT")){v.put(k,held.contains(k));physical.put(k,null);}var out=new LinkedHashMap<String,Object>();out.put("virtualHeld",v);out.put("physicalBoundKeys",physical);return out;}
 public void setInputAllowed(BooleanSupplier s){}
 public void slot(int slot){NativeViewInput.trace.add("slot:"+slot);}
 public void keyDown(String k){held.add(k);key(k).down=true;NativeViewInput.trace.add("down:"+k);}
 public void keyUp(String k){held.remove(k);key(k).down=false;NativeViewInput.trace.add("up:"+k);}
 public void releaseAll(){released++;for(String k:held)key(k).down=false;held.clear();NativeViewInput.trace.add("releaseAll");}
}
