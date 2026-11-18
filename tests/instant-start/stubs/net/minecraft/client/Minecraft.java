package net.minecraft.client;
import net.minecraft.world.phys.Vec3;
public final class Minecraft {
 public static final Minecraft INSTANCE=new Minecraft();public static Minecraft getInstance(){return INSTANCE;}
 public Player player=new Player();public Level level=new Level();public Object connection=new Object(),screen,overlay;
 public Mouse mouseHandler=new Mouse();public Options options=new Options();public boolean paused,focused=true;
 public Object getConnection(){return connection;}public Object getOverlay(){return overlay;}public boolean isPaused(){return paused;}public boolean isWindowActive(){return focused;}
 public Server getCurrentServer(){return null;}public static class Server {public String ip="test";}
 public static class Level {public net.minecraft.world.phys.BlockHitResult clip(net.minecraft.world.level.ClipContext c){return net.minecraft.world.phys.BlockHitResult.miss(Vec3.ZERO,net.minecraft.core.Direction.UP,net.minecraft.core.BlockPos.ZERO); }public String dimension(){return "test";}}
 public static class Mouse {public boolean grabbed=true,right;public boolean isMouseGrabbed(){return grabbed;}public boolean isRightPressed(){return right;}}
 public static class Key {public boolean down;public boolean isDown(){return down;}}
 public static class Options {public Key keyUse=new Key(),keyUp=new Key(),keyDown=new Key(),keyLeft=new Key(),keyRight=new Key(),keyJump=new Key(),keyShift=new Key();}
 public static class Inventory {public int getSelectedSlot(){return 1;}}
 public static class Player {
  public double x=.5,y=71,z=.5,vx,vy,vz;public float yaw,pitch;public boolean ground=true,alive=true;public boolean onGround(){return ground;}public boolean isAlive(){return alive;}
  public float getYRot(){return yaw;}public float getXRot(){return pitch;}public double getX(){return x;}public double getY(){return y;}public double getZ(){return z;}
  public Vec3 position(){return new Vec3(x,y,z);}public Vec3 getDeltaMovement(){return new Vec3(vx,vy,vz);}public Inventory getInventory(){return new Inventory();}
 }
}
