import com.kuangsanfeng.ultimategarden.client.function.mapping.*;
import net.minecraft.nbt.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Isolated temporary stores, real NBT, no connection or world required. */
public final class MappingRegression {
    static int checks;
    static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    static void fails(Throwing r)throws Exception{try{r.run();throw new AssertionError("Expected rejection");}catch(java.io.IOException|IllegalArgumentException expected){checks++;}}
    interface Throwing{void run()throws Exception;}
    static ChunkSnapshot sample(boolean different){
        var palette=new ArrayList<ChunkSnapshot.State>();palette.add(ChunkSnapshot.AIR);
        for(int i=0;i<17;i++)palette.add(new ChunkSnapshot.State("minecraft:oak_stairs",Map.of("facing",i%2==0?"north":"south","half",i%3==0?"top":"bottom","shape","straight","waterlogged","false")));
        int[] values=new int[256*3];for(int i=0;i<values.length;i++)values[i]=i%palette.size();if(different)values[1]=0;
        return new ChunkSnapshot(3,palette,values);
    }
    public static void main(String[] args)throws Exception{
        var loaded=new HashSet<MappingController.Chunk>();for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)loaded.add(new MappingController.Chunk(x,z));
        var processed=new HashSet<MappingController.Chunk>();var player=new MappingController.Chunk(1,-1);
        check(MappingCandidates.next(loaded,player,processed,Set.of(),Set.of()).orElseThrow().equals(player),"player chunk first");
        while(true){var next=MappingCandidates.next(loaded,player,processed,Set.of(),Set.of());if(next.isEmpty())break;check(loaded.contains(next.get()),"only cached candidates");processed.add(next.get());}
        check(processed.size()==25,"25 loaded chunks produce exactly 25 candidates");
        check(MappingCandidates.next(loaded,player,Set.of(),Set.of(player),new HashSet<>(loaded)).isEmpty(),"deleted or pending chunks are not auto-filled");
        var root=Path.of("build/mapping-audit/stores").toAbsolutePath();Files.createDirectories(root);root=Files.createTempDirectory(root,"case-");
        var repo=new MapRepository(root,"Hub");var c=new MapConfig(1,"Hub",new MapConfig.Origin(-32,-64,16),-61,Set.of(Festival.NONE),Festival.NONE);var first=sample(false);Object gate=new Object();
        check(MappingNames.key("Hub").equals("hub"),"stable Hub key");fails(()->MappingNames.key("../Hub"));fails(()->MappingNames.key("CON"));
        repo.initialize(c,first,4811,()->true,gate);check(repo.load().equals(c),"config round trip");
        check(repo.chunkPath(c,Festival.NONE,-1,0).getFileName().toString().equals("Hub 1 -1.litematic"),"negative chunk coordinates");
        byte[] configBytes=Files.readAllBytes(repo.directory().resolve("map.json"));repo.initialize(c, sample(true),4811,()->true,gate);
        check(Arrays.equals(configBytes,Files.readAllBytes(repo.directory().resolve("map.json"))),"repeat init no change");check(repo.read(c,Festival.NONE,-2,1).orElseThrow().sameBlocks(first),"repeat init keeps first chunk");
        check(repo.read(c,Festival.NONE,999,999).isEmpty(),"missing is unknown");
        var f=repo.select(c,Festival.SPOOKY,true);fails(()->repo.select(f,Festival.SPOOKY,true));
        repo.write(f,Festival.SPOOKY,-2,1,first,4811,()->true,gate);check(!repo.exists(f,Festival.SPOOKY,-2,1),"equal festival omitted");
        repo.write(f,Festival.SPOOKY,-2,1,sample(true),4811,()->true,gate);check(repo.read(f,Festival.SPOOKY,-2,1).orElseThrow().sameBlocks(sample(true)),"overlay wins");
        // Same states, different palette numbering: must compare resolved states.
        var original=first.palette();var reversed=new ArrayList<>(original);Collections.reverse(reversed.subList(1,reversed.size()));int[] mapped=first.indices();
        for(int i=0;i<mapped.length;i++)if(mapped[i]!=0)mapped[i]=original.size()-mapped[i];
        check(first.sameBlocks(new ChunkSnapshot(3,reversed,mapped)),"semantic comparison independent of palette IDs");
        repo.write(f,Festival.SPOOKY,-2,1,new ChunkSnapshot(3,reversed,mapped),4811,()->true,gate);check(!repo.exists(f,Festival.SPOOKY,-2,1),"equal redraw removes stale overlay");
        fails(()->repo.write(f,Festival.SPOOKY,3,3,first,4811,()->true,gate));
        repo.write(f,Festival.NONE,-1,1,first,4811,()->true,gate);repo.write(f,Festival.SPOOKY,-1,1,sample(true),4811,()->true,gate);
        fails(()->repo.resetOrigin(f,3,-2,()->false,gate));check(repo.load().equals(f),"cancelled rename restores config");check(repo.read(f,Festival.SPOOKY,-1,1).orElseThrow().sameBlocks(sample(true)),"cancelled rename rolls back every layer");
        var reset=repo.resetOrigin(f,-1,1);check(reset.origin().x()==-16&&reset.origin().y()==-64,"reset shared origin and Y");
        check(repo.read(reset,Festival.NONE,-2,1).orElseThrow().sameBlocks(first),"base world position preserved");
        check(repo.read(reset,Festival.SPOOKY,-1,1).orElseThrow().sameBlocks(sample(true)),"festival world position preserved");
        repo.deleteChunk(reset,Festival.NONE,-1,1);check(repo.load().origin().equals(reset.origin()),"deleting 00 keeps origin");check(repo.exists(reset,Festival.SPOOKY,-1,1),"base delete keeps overlay");
        repo.deleteChunk(reset,Festival.SPOOKY,-1,1);repo.write(reset,Festival.SPOOKY,-2,1,sample(true),4811,()->false,gate);check(!repo.exists(reset,Festival.SPOOKY,-2,1),"cancelled commit cannot resurrect");
        check(new MapRepository(root,"Hub").load().equals(reset),"Hub reopen reuses origin and created festivals");
        // Cancel while a real compressed write is blocked at its final commit fence.
        var allowed=new AtomicBoolean(true);var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var writer=new Thread(()->{try{repo.write(reset,Festival.NONE,-2,1,sample(true),4811,allowed::get,gate);}catch(Throwable e){failure.set(e);}});
        synchronized(gate){writer.start();long deadline=System.nanoTime()+5_000_000_000L;while(writer.getState()!=Thread.State.BLOCKED&&writer.isAlive()&&System.nanoTime()<deadline)Thread.onSpinWait();check(writer.getState()==Thread.State.BLOCKED,"worker reaches commit fence");allowed.set(false);}
        writer.join(5000);check(!writer.isAlive()&&failure.get()==null,"cancelled writer drains cleanly");check(repo.read(reset,Festival.NONE,-2,1).orElseThrow().sameBlocks(first),"cancel at commit preserves original file");
        var garden=new MapRepository(root,"Garden");garden.initialize(new MapConfig(1,"Garden",new MapConfig.Origin(0,0,0),3,Set.of(Festival.NONE),Festival.NONE),first,4811,()->true,gate);repo.deleteWorld();check(garden.load()!=null,"delete restricted to current world");
        var secondHub=new MapRepository(root,"Hub");check(secondHub.directory().equals(repo.directory()),"another Hub connection shares directory");
        var confirmation=new MappingConfirmation();Object context=new Object();check(!confirmation.click(context,"reset",0),"first click arms");check(!confirmation.click(context,"reset",100),"first click not counted in pair");check(confirmation.click(context,"reset",200),"fresh pair executes");
        check(!confirmation.click(context,"reset",300),"rearm");confirmation.validate(new Object());check(!confirmation.click(context,"reset",400),"context change cancels");check(!confirmation.click(context,"delete",500),"other dangerous action cancels");confirmation.cancel();check(!confirmation.armed(context,"delete"),"menu exit cancels");
        // Target file shape and lossless long boundaries.
        Path file=root.resolve("fixture.litematic");LitematicIO.write(file,first,4811);check(LitematicIO.read(file).sameBlocks(first),"lossless native NBT roundtrip");var nbt=NbtIo.readCompressed(file,NbtAccounter.unlimitedHeap());
        check(nbt.getIntOr("Version",0)==7&&nbt.getIntOr("SubVersion",0)==1,"target version");check(nbt.getCompoundOrEmpty("Metadata").getLongOr("TimeCreated",-1)==0,"no real timestamp");
        for(int bits=2;bits<=12;bits++){int[] values=new int[257];for(int i=0;i<values.length;i++)values[i]=(i*17)&((1<<bits)-1);check(Arrays.equals(values,LitematicIO.unpack(LitematicIO.pack(values,bits),values.length,bits,1<<bits)),"cross-long "+bits);}
        fails(()->LitematicIO.unpack(new long[1],100,5,18));
        String json=Files.readString(garden.directory().resolve("map.json"));for(String prohibited:List.of("server","instance","mode","subArea","date","time","dimension"))check(!json.contains("\""+prohibited+"\""),"no volatile identity "+prohibited);
        System.out.println("Mapping regression: "+checks+" checks passed; fixtures: "+root);
    }
}
