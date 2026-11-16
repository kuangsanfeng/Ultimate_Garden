package com.kuangsanfeng.ultimategarden.client.runtime.world.observation.query;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import java.util.function.Consumer;
/** Enforces the caller's loaded/range contract even for collision shape neighbor queries. */
public final class LoadedWorldView implements BlockGetter {
 private final BlockGetter world;private final Consumer<BlockPos> requireKnown;
 public LoadedWorldView(BlockGetter world,Consumer<BlockPos> requireKnown){this.world=world;this.requireKnown=requireKnown;}
 public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos p){requireKnown.accept(p);return world.getBlockState(p);}
 public net.minecraft.world.level.material.FluidState getFluidState(BlockPos p){requireKnown.accept(p);return world.getFluidState(p);}
 public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos p){requireKnown.accept(p);return world.getBlockEntity(p);}
 public int getHeight(){return world.getHeight();}public int getMinY(){return world.getMinY();}
}
