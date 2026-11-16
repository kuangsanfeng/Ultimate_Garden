package com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.mapping;

import com.kuangsanfeng.ultimategarden.client.function.mapping.ChunkSnapshot;
import com.kuangsanfeng.ultimategarden.client.function.mapping.replay.ReplayMesh;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import java.util.*;

/** Immutable native state properties, map material colors and voxel shapes; no live-world reads. */
public final class ReplayMaterials implements ReplayMesh.Materials {
    @Override public ReplayMesh.Material resolve(ChunkSnapshot.State source){
        var id=Identifier.parse(source.name());if(!BuiltInRegistries.BLOCK.containsKey(id))throw new IllegalArgumentException("UNKNOWN_BLOCK: "+source.name());var block=BuiltInRegistries.BLOCK.getValue(id);if(block==null)throw new IllegalArgumentException("UNKNOWN_BLOCK: "+source.name());
        BlockState state=block.defaultBlockState();for(var entry:source.properties().entrySet()){var p=block.getStateDefinition().getProperty(entry.getKey());if(p!=null)state=property(state,p,entry.getValue());}
        int color=state.getMapColor(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).col;if(color==0)color=0x94A8A0;
        var boxes=state.getShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).toAabbs().stream().map(b->new ReplayMesh.Box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ)).toList();
        if(boxes.isEmpty()&&!state.getFluidState().isEmpty())boxes=List.of(new ReplayMesh.Box(0,0,0,1,1,1));
        boolean opaque=state.canOcclude();int alpha=source.name().contains("glass")||!state.getFluidState().isEmpty()?180:255;
        return new ReplayMesh.Material(alpha<<24|color,opaque,boxes);
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static BlockState property(BlockState state,Property property,String value){var parsed=property.getValue(value);return parsed.isPresent()?state.setValue(property,(Comparable)parsed.get()):state;}
}
