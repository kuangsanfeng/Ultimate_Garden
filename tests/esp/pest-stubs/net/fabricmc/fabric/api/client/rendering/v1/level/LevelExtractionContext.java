package net.fabricmc.fabric.api.client.rendering.v1.level;
import java.util.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.*;
public record LevelExtractionContext(List<Body> bodies) {
 public LevelExtractionContext level(){return this;}
 public List<Body> entitiesForRendering(){return bodies;}
 public LevelExtractionContext deltaTracker(){return this;}
 public float getGameTimeDeltaPartialTick(boolean ignored){return .5f;}
 public record Body(EntityType type,boolean alive,boolean removed) {
  public EntityType getType(){return type;}
  public boolean isAlive(){return alive;} public boolean isRemoved(){return removed;}
  public Vec3 getPosition(float delta){return new Vec3(delta,0,0);}
  public Vec3 position(){return Vec3.ZERO;}
  public AABB getBoundingBox(){return new AABB(0,0,0,1,1,1);}
 }
}
