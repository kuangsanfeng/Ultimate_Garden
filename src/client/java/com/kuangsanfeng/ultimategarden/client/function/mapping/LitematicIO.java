package com.kuangsanfeng.ultimategarden.client.function.mapping;

import net.minecraft.nbt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Litematica 0.27.14: format 7.1, continuous little-bit-order palette stream. */
public final class LitematicIO {
    private LitematicIO() {}
    public static int bits(int paletteSize){return Math.max(2,32-Integer.numberOfLeadingZeros(paletteSize-1));}
    public static long[] pack(int[] values,int bits){
        long[] out=new long[Math.toIntExact(((long)values.length*bits+63)/64)];
        for(int i=0;i<values.length;i++){long bit=(long)i*bits;int word=(int)(bit/64),shift=(int)(bit%64);out[word]|=(long)values[i]<<shift;if(shift+bits>64)out[word+1]|=(long)values[i]>>>(64-shift);}
        return out;
    }
    public static int[] unpack(long[] words,int count,int bits,int paletteSize)throws IOException{
        if(words.length!=((long)count*bits+63)/64)throw new IOException("INVALID_BLOCKSTATES_LENGTH");
        int[] out=new int[count];long mask=(1L<<bits)-1;
        for(int i=0;i<count;i++){long bit=(long)i*bits;int w=(int)(bit/64),s=(int)(bit%64);long v=words[w]>>>s;if(s+bits>64)v|=words[w+1]<<(64-s);out[i]=(int)(v&mask);if(out[i]>=paletteSize)throw new IOException("INVALID_PALETTE_INDEX");}
        return out;
    }
    private static CompoundTag vector(int x,int y,int z){var t=new CompoundTag();t.putInt("x",x);t.putInt("y",y);t.putInt("z",z);return t;}
    public static void write(Path path,ChunkSnapshot s,int dataVersion)throws IOException{
        var root=new CompoundTag();root.putInt("Version",7);root.putInt("SubVersion",1);root.putInt("MinecraftDataVersion",dataVersion);
        var metadata=new CompoundTag();metadata.putString("Name","Mapping Chunk");metadata.putString("Author","");metadata.putString("Description","");
        metadata.putLong("TimeCreated",0);metadata.putLong("TimeModified",0);metadata.putInt("RegionCount",1);metadata.putInt("TotalVolume",256*s.height());
        int[] values=s.indices();long nonAir=Arrays.stream(values).filter(v->!Set.of("minecraft:air","minecraft:cave_air","minecraft:void_air").contains(s.palette().get(v).name())).count();metadata.putInt("TotalBlocks",Math.toIntExact(nonAir));metadata.put("EnclosingSize",vector(16,s.height(),16));root.put("Metadata",metadata);
        var region=new CompoundTag();region.put("Position",vector(0,0,0));region.put("Size",vector(16,s.height(),16));var palette=new ListTag();
        for(var state:s.palette()){var tag=new CompoundTag();tag.putString("Name",state.name());if(!state.properties().isEmpty()){var props=new CompoundTag();state.properties().forEach(props::putString);tag.put("Properties",props);}palette.add(tag);}
        region.put("BlockStatePalette",palette);region.putLongArray("BlockStates",pack(values,bits(s.palette().size())));
        for(String key:List.of("Entities","TileEntities","PendingBlockTicks","PendingFluidTicks"))region.put(key,new ListTag());
        var regions=new CompoundTag();regions.put("Chunk",region);root.put("Regions",regions);NbtIo.writeCompressed(root,path);
    }
    public static ChunkSnapshot read(Path path)throws IOException{
        var root=NbtIo.readCompressed(path,NbtAccounter.create(64L*1024*1024));
        int version=root.getIntOr("Version",0);if(version<5||version>7)throw new IOException("UNSUPPORTED_LITEMATIC_VERSION");
        var regions=root.getCompoundOrEmpty("Regions");if(regions.keySet().size()!=1)throw new IOException("EXPECTED_ONE_REGION");
        var region=regions.getCompoundOrEmpty(regions.keySet().iterator().next());var size=region.getCompoundOrEmpty("Size");var pos=region.getCompoundOrEmpty("Position");
        int h=size.getIntOr("y",0);if(size.getIntOr("x",0)!=16||size.getIntOr("z",0)!=16||h<1||h>4096||pos.getIntOr("x",-1)!=0||pos.getIntOr("y",-1)!=0||pos.getIntOr("z",-1)!=0)throw new IOException("INVALID_LOCAL_REGION");
        var list=region.getListOrEmpty("BlockStatePalette");var palette=new ArrayList<ChunkSnapshot.State>();
        for(Tag item:list){if(!(item instanceof CompoundTag tag))throw new IOException("INVALID_PALETTE");var props=new TreeMap<String,String>();var p=tag.getCompoundOrEmpty("Properties");for(String key:p.keySet())props.put(key,p.getStringOr(key,""));palette.add(new ChunkSnapshot.State(tag.getStringOr("Name",""),props));}
        if(palette.isEmpty())throw new IOException("EMPTY_PALETTE");
        try{return new ChunkSnapshot(h,palette,unpack(region.getLongArray("BlockStates").orElseThrow(()->new IOException("MISSING_BLOCKSTATES")),256*h,bits(palette.size()),palette.size()));}catch(IllegalArgumentException e){throw new IOException("INVALID_SNAPSHOT",e);}
    }
}
