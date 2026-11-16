package net.minecraft.client;

import com.kuangsanfeng.ultimategarden.client.mixin.ShootoutMouseAccessor;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim.ShootoutMouseInput;
import net.minecraft.client.input.MouseButtonInfo;
public final class Minecraft {
 public final Options options=new Options();public final Player player=new Player();public final FakeMouse mouseHandler=new FakeMouse();
 public Window getWindow(){return new Window();}public static final class Window{public long handle(){return 1;}}
 public static final class Value<T>{public T value;Value(T v){value=v;}public T get(){return value;}}
 public static final class Key {public boolean down;public int clicks;public boolean isDown(){return down;}public void setDown(boolean v){down=v;}public boolean consumeClick(){if(clicks==0)return false;clicks--;return true;}}
 public static final class Options {public boolean smoothCamera;public final Key keyUse=new Key();public final Value<Double> sensitivity=new Value<>(.5);public final Value<Boolean> x=new Value<>(false),y=new Value<>(false);public Value<Double> sensitivity(){return sensitivity;}public Value<Boolean> invertMouseX(){return x;}public Value<Boolean> invertMouseY(){return y;}}
 public static final class Player {public float yaw,pitch;public float getYRot(){return yaw;}public float getXRot(){return pitch;}public boolean isScoping(){return false;}}
 public final class FakeMouse implements ShootoutMouseAccessor {public double dx,dy;public int presses,releases;public boolean right,failRelease,sourceSeen;
  public boolean isRightPressed(){return right;}public double shootout$getDX(){return dx;}public double shootout$getDY(){return dy;}public void shootout$setDX(double v){dx=v;}public void shootout$setDY(double v){dy=v;}public void shootout$setRightPressed(boolean v){right=v;}
  public void shootout$turn(double elapsed){sourceSeen=ShootoutMouseInput.dispatching();double ss=options.sensitivity.get()*.6F+.2F,scale=ss*ss*ss*8;player.yaw+=(float)(dx*scale*(options.x.get()?-1:1))*.15F;player.pitch+=(float)(dy*scale*(options.y.get()?-1:1))*.15F;}
  public void shootout$button(long window,MouseButtonInfo info,int action){sourceSeen=ShootoutMouseInput.dispatching();if(action==1){right=true;options.keyUse.down=true;options.keyUse.clicks++;presses++;}else{if(failRelease)throw new IllegalStateException("simulated native release failure");right=false;options.keyUse.down=false;releases++;}}
 }
}
