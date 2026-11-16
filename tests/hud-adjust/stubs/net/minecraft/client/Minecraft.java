package net.minecraft.client;

public final class Minecraft {
 public static final Minecraft INSTANCE=new Minecraft();
 public Object level=new Object(), screen;public Player player=new Player();
 public static class Player {public float getYRot(){return 0;}public float getXRot(){return 0;}}
 public final Font font=new Font();public final Options options=new Options();
 public static final class Font {public int width(String value){return value.length()*6;}}
 public static final class Options {public boolean hideGui;}
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