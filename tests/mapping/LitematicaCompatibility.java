import com.kuangsanfeng.ultimategarden.client.function.mapping.*;
import net.minecraft.nbt.*;
import java.nio.file.*;
import java.util.*;

/** Optional external oracle: unmodified Litematica 0.27.14 + MaLiLib 0.28.11, validation only. */
public final class LitematicaCompatibility {
    public static void main(String[] args)throws Exception{
        Class<?> bitArray=Class.forName("fi.dy.masa.litematica.schematic.container.LitematicaBitArray");
        for(int bits=2;bits<=12;bits++){
            int[] values=new int[769];var array=bitArray.getConstructor(int.class,long.class).newInstance(bits,(long)values.length);
            for(int i=0;i<values.length;i++){values[i]=(i*37)&((1<<bits)-1);bitArray.getMethod("setAt",long.class,int.class).invoke(array,(long)i,values[i]);}
            long[] external=(long[])bitArray.getMethod("getBackingLongArray").invoke(array);
            if(!Arrays.equals(external,LitematicIO.pack(values,bits)))throw new AssertionError("Target bitstream mismatch at "+bits);
            var reader=bitArray.getConstructor(int.class,long.class,long[].class).newInstance(bits,(long)values.length,LitematicIO.pack(values,bits));
            for(int i=0;i<values.length;i++)if((int)bitArray.getMethod("getAt",long.class).invoke(reader,(long)i)!=values[i])throw new AssertionError("Target read mismatch");
        }
        System.out.println("Target Litematica 0.27.14 independent bitstream: 2..12 bits including cross-long entries passed");
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        Path dir=Path.of("build/mapping-audit/compatibility");Files.createDirectories(dir);Path export=dir.resolve("Mapping 0 -1.litematic");
        var palette=List.of(ChunkSnapshot.AIR,new ChunkSnapshot.State("minecraft:stone",Map.of()),new ChunkSnapshot.State("minecraft:oak_stairs",Map.of("facing","east","half","top","shape","straight","waterlogged","false")));
        int[] values=new int[256*3];values[1]=1;values[16+3]=2;values[256+32+7]=1;values[512+15*16+14]=2;
        var snapshot=new ChunkSnapshot(3,palette,values);LitematicIO.write(export,snapshot,net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version());
        // Minimal headless client context for MaLiLib's config-directory discovery only.
        // No client constructor, network, renderer, world, or screen is started.
        var unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);var unsafe=(sun.misc.Unsafe)unsafeField.get(null);
        var client=(net.minecraft.client.Minecraft)unsafe.allocateInstance(net.minecraft.client.Minecraft.class);
        var gameDir=net.minecraft.client.Minecraft.class.getDeclaredField("gameDirectory");gameDir.setAccessible(true);gameDir.set(client,dir.toFile());
        var singleton=net.minecraft.client.Minecraft.class.getDeclaredField("instance");singleton.setAccessible(true);singleton.set(null,client);
        var handler=Class.forName("fi.dy.masa.litematica.world.SchematicWorldHandler");
        handler.getMethod("setDynamicRegistryManager",net.minecraft.core.RegistryAccess.Frozen.class).invoke(handler.getField("INSTANCE").get(null),net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        Class<?> schematic=Class.forName("fi.dy.masa.litematica.schematic.LitematicaSchematic"),fileType=Class.forName("fi.dy.masa.litematica.util.FileType");
        Object type=Arrays.stream(fileType.getEnumConstants()).filter(v->v.toString().contains("LITEMATICA")).findFirst().orElseThrow();
        var target=schematic.getConstructor(Path.class,CompoundTag.class,fileType).newInstance(export,NbtIo.readCompressed(export,NbtAccounter.unlimitedHeap()),type);
        var read=schematic.getDeclaredMethod("readFromNBT",CompoundTag.class);read.setAccessible(true);
        if(!(boolean)read.invoke(target,NbtIo.readCompressed(export,NbtAccounter.unlimitedHeap())))throw new AssertionError("Target rejected exported schematic");
        var containers=schematic.getDeclaredField("blockContainers");containers.setAccessible(true);var container=((Map<?,?>)containers.get(target)).get("Chunk");if(container==null)throw new AssertionError("Target did not create chunk region");
        var get=container.getClass().getMethod("get",int.class,int.class,int.class);
        if(!get.invoke(container,3,0,1).toString().contains("facing=east")||!get.invoke(container,14,2,15).toString().contains("half=top"))throw new AssertionError("Target axes/properties mismatch");
        // Generate a sample with the real target serializer, then load it with Mapping.
        var output=(CompoundTag)schematic.getMethod("writeToNBT").invoke(target);Path sample=dir.resolve("target-generated.litematic");NbtIo.writeCompressed(output,sample);
        if(!LitematicIO.read(sample).sameBlocks(snapshot))throw new AssertionError("Target-generated sample mismatch");
        Files.writeString(dir.resolve("PASS.txt"),"Target Litematica 0.27.14 serializer/parser and bitstream verified headlessly. Game GUI not exercised.");
        System.out.println("Target Litematica parser accepted Mapping export; target serializer sample accepted by Mapping, asymmetric XYZ and stair properties preserved");
    }
}
