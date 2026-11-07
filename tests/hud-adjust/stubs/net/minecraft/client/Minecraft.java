package net.minecraft.client;
public final class Minecraft {
 public static final Minecraft INSTANCE=new Minecraft();
 public Object level=new Object(), player=new Object(), screen;
 public final Window window=new Window();
 public static Minecraft getInstance(){return INSTANCE;}
 public Window getWindow(){return window;}
 public void setScreen(Object value){screen=value;}
 public static final class Window {
  public int w=1000,h=700;
  public long handle(){return 1;}
  public int getGuiScaledWidth(){return w;} public int getGuiScaledHeight(){return h;}
  public int getScreenWidth(){return w;} public int getScreenHeight(){return h;}
 }
}