import com.kuangsanfeng.ultimategarden.client.function.mapping.*;
import com.kuangsanfeng.ultimategarden.client.function.mapping.MapRepository.Coordinate;
import com.kuangsanfeng.ultimategarden.client.function.mapping.replay.*;
import com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.mapping.ReplayMaterials;
import java.nio.file.*;
import java.util.*;

public final class ReplayRegression {
    static int checks;
    static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    static final ReplayMesh.Materials COLORS=s->new ReplayMesh.Material(0xFF93B178,true,List.of(new ReplayMesh.Box(0,0,0,1,1,1)));
    static ChunkSnapshot terrain(int height,int... positions){int[] values=new int[height*256];for(int p:positions)values[p]=1;return new ChunkSnapshot(height,List.of(ChunkSnapshot.AIR,new ChunkSnapshot.State("minecraft:stone",Map.of())),values);}
    static double wall(ReplayMesh.Part part,int side,double plane){return part.faces().stream().filter(f->f.side()==side&&Math.abs(f.x()-plane)<1e-9).mapToDouble(f->f.h()*f.d()).sum();}
    public static void main(String[] args)throws Exception{
        check(ReplayNumbers.commit("", "2", java.math.BigInteger.ONE, java.math.BigInteger.valueOf(16)).intValue()==2,"invalid draft restores last valid");
        check(ReplayNumbers.commit("100", "2", java.math.BigInteger.ONE, java.math.BigInteger.valueOf(16)).intValue()==16,"denominator clamps upper bound");
        check(ReplayNumbers.wheel("16","2",java.math.BigInteger.ONE,java.math.BigInteger.valueOf(16),1).intValue()==16,"wheel stays consumed at upper bound");
        check(ReplayNumbers.wheel("1","2",java.math.BigInteger.ONE,java.math.BigInteger.valueOf(16),-1).intValue()==1,"wheel stays consumed at lower bound");
        var coords=new HashSet<Coordinate>();for(int z=-20;z<=20;z++)for(int x=-20;x<=20;x++)coords.add(new Coordinate(x,z));
        var included=ReplaySelection.withinDistance(coords,0,0,"16");
        check(included.contains(new Coordinate(16,0))&&included.contains(new Coordinate(0,16)),"R16 includes axial edges");
        check(!included.contains(new Coordinate(16,16))&&!included.contains(new Coordinate(17,0)),"R16 excludes corner and strict-radius boundary");
        check(ReplaySelection.withinDistance(coords,0,0,"1").size()==9,"R1 uses vanilla one-chunk axis buffer, not exact count");
        check(ReplaySelection.withinDistance(coords,0,0,"999999999999999999999999999999999999999999999999999").size()==coords.size(),"huge radius visits only real availability");
        check(ReplaySelection.withinDistance(List.of(new Coordinate(16,0),new Coordinate(17,0)),17,0,"1").size()==2,"recentering changes temporary footprint");
        check(ReplaySelection.withinDistance(List.of(new Coordinate(0,0),new Coordinate(100,100)),0,0,"16").size()==1,"missing chunks are not invented");
        for(int radius:new int[]{1,2,4,16})for(var coordinate:coords)
            check(ReplaySelection.inViewDistance(coordinate,0,0,java.math.BigInteger.valueOf(radius))==net.minecraft.server.level.ChunkTrackingView.isWithinDistance(0,0,radius,coordinate.x(),coordinate.z(),false),"matches actual 26.1.2 vanilla ordinary boundary");
        check(!ReplaySelection.inViewDistance(new Coordinate(Integer.MAX_VALUE,Integer.MIN_VALUE),Integer.MIN_VALUE,Integer.MAX_VALUE,java.math.BigInteger.valueOf(16)),"extreme coordinates do not overflow");
        for(var current:ReplayMesh.Mode.values()){
            var options=ReplayDepth.options(current);check(options.getFirst()==current,"depth current option first");
            check(options.size()==3&&new HashSet<>(options).size()==3,"stable unique depth IDs");
            check(options.subList(1,3).equals(Arrays.stream(ReplayMesh.Mode.values()).filter(m->m!=current).toList()),"remaining depth options keep original order");
        }
        var camera=new ReplayCamera();check(Math.abs(Math.toDegrees(camera.yaw())+55.927)<.1,"default azimuth");check(Math.abs(Math.toDegrees(camera.pitch())-31.31)<.1,"default pitch");
        var bx=camera.basis(1,0,0);check(bx.x()>0&&bx.y()<0,"reference X basis");var by=camera.basis(0,1,0);check(by.y()<0&&by.depth()>0,"reference Y basis");
        double oldX=camera.x,oldZ=camera.z;camera.wheel(4);camera.advance(40);check(camera.zoom>1&&camera.x==oldX&&camera.z==oldZ,"wheel only zoom");camera.wheel(1e9);check(camera.targetZoom==4.6,"zoom max");camera.wheel(-1e9);check(camera.targetZoom==.4,"zoom min");
        for(int i=0;i<100;i++){camera.axis(0,9);camera.axis(2,-9);camera.rotate(i*.3);check(Math.hypot(camera.x,camera.z)<=2.50000001,"horizontal vector bound");}
        camera.axis(1,-200);check(camera.y==-3&&camera.pitch()<0,"negative Y minimum looks up");camera.axis(1,0);check(camera.y==0&&camera.pitch()==0,"Y zero is horizontal");camera.axis(1,.016);check(camera.y==.02,"Y step .01");camera.axis(1,200);check(camera.y==3,"vertical maximum");camera.axis(0,0);camera.axis(2,0);check(Double.isFinite(camera.yaw())&&Double.isFinite(camera.basis(1,2,3).x()),"vertical view remembers heading");
        var selection=new ReplaySelection();var a=new Coordinate(-2,-1);var b=new Coordinate(1,1);var c=new Coordinate(0,0);selection.click(a,false,false,List.of(a,b,c));selection.click(b,false,true,List.of(a,b,c));check(selection.selected.equals(Set.of(a,b,c)),"real-coordinate rectangular selection");selection.click(c,true,false,List.of(a,b,c));check(!selection.selected.contains(c),"Ctrl toggles");selection.click(b,true,true,List.of(a,b,c));check(selection.selected.contains(c),"Ctrl Shift appends rectangle");selection.click(null,false,false,List.of());check(selection.selected.isEmpty(),"blank click clears");
        var remember=new ReplayCamera();var before=remember.basis(1,2,3);remember.x=remember.y=remember.z=0;check(remember.basis(1,2,3).equals(before),"zero vector preserves last valid full projection");
        var key=new Coordinate(-1,0);var snapshot=terrain(8,2*256,7*256+1*16+1,256+2);var data=Map.of(key,snapshot);
        var winner=ReplaySampler.sample(snapshot,0,0,2,2,0,7);
        check(winner.x()==1&&winner.z()==1&&winner.y()==7,"whole NxN cell selects isolated highest block, not lower min column");
        check(ReplaySampler.sample(terrain(8,7*256+17),0,0,2,2,0,7).y()==7,"air at minimum XZ cannot hide isolated high block");
        var ties=ReplaySampler.sample(terrain(8,6*256+16,6*256+1,6*256+2),0,0,3,3,0,7);
        check(ties.y()==6&&ties.x()==0&&ties.z()==1,"empty highest layer descends; ties use X then Z");
        check(ReplaySampler.sample(terrain(8),0,0,2,2,0,7)==null,"entire cell air returns no geometry");
        check(ReplaySampler.sample(terrain(8,255+7*256),15,15,18,18,0,7).x()==15,"edge cell clips to chunk");
        var deep=terrain(8,7*256+17,5*256+1,4*256,0);
        check(ReplaySampler.depthSamples(deep,0,0,2,2,ReplayMesh.Mode.SURFACE).size()==1,"surface sample space highest");
        var two=ReplaySampler.depthSamples(deep,0,0,2,2,ReplayMesh.Mode.TWO_LAYERS);
        check(two.size()==2&&two.get(1).y()==5,"two layers count original Y including air gaps");
        check(ReplaySampler.depthSamples(deep,0,0,2,2,ReplayMesh.Mode.ALL).size()==4,"all samples original valid Y with same priority");
        var mesh=ReplayMesh.build(data,List.of(key),2,ReplayMesh.Mode.SURFACE,COLORS,()->false,List.of());
        check(mesh.parts().size()==1,"one decoded chunk");
        check(mesh.parts().getFirst().faces().stream().anyMatch(f->f.side()==3&&f.y()==.5),"isolated high cell appears at true source height");
        var empty=ReplayMesh.build(Map.of(key,terrain(8)),List.of(key),16,ReplayMesh.Mode.ALL,COLORS,()->false,List.of());check(empty.parts().getFirst().faces().isEmpty(),"all-air cell has no substitute");
        for(int n:new int[]{1,2,3,16})for(var mode:ReplayMesh.Mode.values()){var m=ReplayMesh.build(data,List.of(key),n,mode,COLORS,()->false,List.of());check(m.parts().size()==1,"all sampling modes retain actual chunk");}
        int[] solidValues=new int[256*3];Arrays.fill(solidValues,1);var solid=new ChunkSnapshot(3,snapshot.palette(),solidValues);var left=new Coordinate(0,0);var right=new Coordinate(1,0);
        var seam=ReplayMesh.build(Map.of(left,solid,right,solid),List.of(left,right),1,ReplayMesh.Mode.SURFACE,COLORS,()->false,List.of());
        check(wall(seam.parts().getFirst(),1,1)==0,"solid adjacent chunk seam culled");
        var boundary=ReplayMesh.build(Map.of(left,solid,right,solid),List.of(left),1,ReplayMesh.Mode.SURFACE,COLORS,()->false,List.of());
        check(Math.abs(wall(boundary.parts().getFirst(),1,1)-3/16.)<1e-9,"outside-preview stored neighbor cannot hide full boundary wall");
        var missing=ReplayMesh.build(Map.of(left,solid),List.of(left,right),1,ReplayMesh.Mode.SURFACE,COLORS,()->false,List.of());
        check(Math.abs(wall(missing.parts().getFirst(),1,1)-3/16.)<1e-9,"missing preview neighbor exposes full wall");
        int[] shortValues=new int[256];Arrays.fill(shortValues,1);var shortChunk=new ChunkSnapshot(1,snapshot.palette(),shortValues);
        var uneven=ReplayMesh.build(Map.of(left,solid,right,shortChunk),List.of(left,right),1,ReplayMesh.Mode.SURFACE,COLORS,()->false,List.of());
        check(Math.abs(wall(uneven.parts().getFirst(),1,1)-2/16.)<1e-9,"unequal-height seam clips covered portion only");
        check(boundary.parts().getFirst().faces().stream().anyMatch(f->f.side()==2&&f.y()==0),"surface retains exposed bottom");
        int[] tallValues=new int[8*256];Arrays.fill(tallValues,1);var tall=new ChunkSnapshot(8,snapshot.palette(),tallValues);
        var surface=ReplayMesh.build(Map.of(left,tall),List.of(left),16,ReplayMesh.Mode.SURFACE,COLORS,()->false,List.of());
        var extra=ReplayMesh.build(Map.of(left,tall),List.of(left),16,ReplayMesh.Mode.TWO_LAYERS,COLORS,()->false,List.of());
        var all=ReplayMesh.build(Map.of(left,tall),List.of(left),16,ReplayMesh.Mode.ALL,COLORS,()->false,List.of());
        check(surface.parts().getFirst().depthSamples()==1&&extra.parts().getFirst().depthSamples()==3&&all.parts().getFirst().depthSamples()==8,"runtime depth uses its own sampling space");
        check(surface.parts().getFirst().faces().size()<extra.parts().getFirst().faces().size()&&extra.parts().getFirst().faces().size()<all.parts().getFirst().faces().size(),"additional depth submits interior candidates without x-ray");
        var depthCamera=new ReplayCamera();
        check(Arrays.equals(ReplayRaster.render(surface,depthCamera,0,0,400,300,Set.of(),()->false).argb(),ReplayRaster.render(all,depthCamera,0,0,400,300,Set.of(),()->false).argb()),"opaque interiors stay normally occluded in All");
        var borders=ReplayRaster.render(boundary,depthCamera,0,0,400,300,Set.of(),true,()->false);
        var noBorders=ReplayRaster.render(boundary,depthCamera,0,0,400,300,Set.of(),false,()->false);
        check(!Arrays.equals(borders.argb(),noBorders.argb()),"border switch changes grid pixels");
        check(Arrays.equals(borders.owners(),noBorders.owners())&&borders.chunks().equals(noBorders.chunks()),"border switch preserves geometry ownership and loaded chunks");
        check(borders.outlines().size()==noBorders.outlines().size(),"hidden borders retain outline hit fallback");
        for(int y=38;y<270;y+=13)for(int x=0;x<400;x+=13)check(Objects.equals(borders.hit(x,y),noBorders.hit(x,y)),"same hit testing with borders hidden");
        var selectedNoBorders=ReplayRaster.render(boundary,depthCamera,0,0,400,300,Set.of(left),false,()->false);
        check(!Arrays.equals(selectedNoBorders.argb(),noBorders.argb())&&Arrays.equals(selectedNoBorders.owners(),noBorders.owners()),"selection fill survives hidden borders");
        var under=new ReplayCamera();under.axis(0,0);under.axis(2,0);under.axis(1,-3);
        var underside=ReplayRaster.render(boundary,under,0,0,400,300,Set.of(),()->false);
        check(Arrays.stream(underside.owners()).anyMatch(id->id>=0),"negative-Y camera rasterizes exterior bottom");
        camera=new ReplayCamera();double h=Math.hypot(camera.x,camera.z),pitch=camera.pitch(),vx=camera.x/h*Math.cos(pitch),vy=Math.sin(pitch),vz=camera.z/h*Math.cos(pitch);
        var front=new ReplayMesh.Part(right,List.of(new ReplayMesh.Face(vx,vy,vz,1,0,1,3,0xFFFF0000)),0);var back=new ReplayMesh.Part(left,List.of(new ReplayMesh.Face(0,0,0,1,0,1,3,0xFF0000FF)),0);
        var frame=ReplayRaster.render(new ReplayMesh(List.of(front,back),0,List.of()),camera,0,0,400,300,Set.of(),()->false);check(right.equals(frame.hit(200,165)),"front depth wins overlapping hit");check(frame.hit(200,10)==null,"reserved top drag band contains no model hits");
        var glass=new ReplayMesh.Part(right,List.of(new ReplayMesh.Face(vx,vy,vz,1,0,1,3,0xB4FF0000)),0);
        var transparent=ReplayRaster.render(new ReplayMesh(List.of(glass,back),0,List.of()),camera,0,0,400,300,Set.of(),()->false);
        int mixed=transparent.argb()[165*400+200];check((mixed>>16&255)>100&&(mixed&255)>20,"transparent material preserves normally occluded background");
        int[] pixels={0xFFFF0000,0xFF000000,0xFFFFFFFF};var gray=ReplayBackground.filter(pixels,3,1,0);for(int color:gray)check((color>>16&255)==(color>>8&255)&&(color>>8&255)==(color&255),"zero blur still grayscale");check(!Arrays.equals(gray,ReplayBackground.filter(pixels,3,1,2)),"Gaussian changes background");
        var cfg=new MapConfig(1,"Hub",new MapConfig.Origin(-16,-64,32),-56,Set.of(Festival.NONE,Festival.SPOOKY),Festival.SPOOKY);var status=new MappingRecordingSnapshot("Hub",cfg,a,true,Set.of(a),Set.of(b),Set.of(a,b),Set.of(c));check(status.currentState()==MappingRecordingSnapshot.ChunkState.PROCESSING,"active takes precedence over saved");
        status=new MappingRecordingSnapshot("Hub",cfg,c,true,Set.of(),Set.of(),Set.of(),Set.of(c));check(status.currentState()==MappingRecordingSnapshot.ChunkState.BASE_FALLBACK,"festival base fallback");check(MappingRecordingSnapshot.EMPTY.currentState()==MappingRecordingSnapshot.ChunkState.UNKNOWN,"unknown world clears state");
        var languageConfigs=new com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager(Files.createTempDirectory(Path.of("build/mapping-audit"),"hud-labels-"));
        var language=new com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageManager(languageConfigs);
        check(com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.mapping.MappingRecordingContent.value("recording_origin",MappingRecordingSnapshot.EMPTY,language).equals("\u2014"),"unknown HUD value is dash");
        var uninitialized=new MappingRecordingSnapshot("Hub",null,null,false,Set.of(),Set.of(),Set.of(),Set.of());
        check(com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.mapping.MappingRecordingContent.value("recording_status",uninitialized,language).equals("Not Initialized"),"recognized uninitialized status");
        var queued=new MappingRecordingSnapshot("Hub",cfg,c,true,Set.of(),Set.of(a,b),Set.of(),Set.of(c));
        String queueText=com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.mapping.MappingRecordingContent.value("recording_status",queued,language);
        check(queueText.contains("0 Chunks")&&queueText.contains("2 Pending"),"zero active and nonzero queue shown separately");
        check(com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.mapping.MappingRecordingContent.value("recording_saved",queued,language).equals("0 Chunks"),"base fallback not counted as saved overlay");
        Path testInstance=Files.createTempDirectory(Path.of("build/mapping-audit"),"replay-");Path root=MappingPaths.root(testInstance);
        check(root.equals(testInstance.toAbsolutePath().resolve("config/ultimategarden/map")),"all runtime maps share new instance-local root");
        var repo=new MapRepository(root,"Hub");repo.initialize(cfg.layer(Festival.NONE,false),snapshot,4811,()->true,new Object());var festival=repo.select(repo.load(),Festival.SPOOKY,false);repo.write(festival,Festival.SPOOKY,-1,2,terrain(8,256),4811,()->true,new Object());
        var index=LocalMapLibrary.list(root);check(index.worlds().size()==1,"local world discovery");var w=index.worlds().getFirst();check(LocalMapLibrary.composed(w,Festival.SPOOKY).size()==1,"base and overlay compose once");
        repo.deleteChunk(festival,Festival.SPOOKY,-1,2);check(repo.read(festival,Festival.SPOOKY,-1,2).orElseThrow().sameBlocks(snapshot),"delete overlay preserves base fallback");check(repo.load().selectedFestival()==Festival.SPOOKY,"preview does not alter saved recording layer");
        check(!Files.exists(testInstance.resolve("config/map")),"new-root read/write/index/delete never creates legacy root");
        try(var session=new ReplaySession(root)){
            var field=ReplaySession.class.getDeclaredField("worker");field.setAccessible(true);var worker=(java.util.concurrent.ThreadPoolExecutor)field.get(session);
            var started=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);worker.execute(()->{started.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});started.await();
            var indexed=new java.util.concurrent.CountDownLatch(1);session.refresh(v->indexed.countDown());session.clear();release.countDown();check(indexed.await(5,java.util.concurrent.TimeUnit.SECONDS),"view reset cannot drop pending library discovery");
        }
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();var nativeMaterial=new ReplayMaterials();
        var upper=nativeMaterial.resolve(new ChunkSnapshot.State("minecraft:oak_stairs",Map.of("facing","east","half","top","shape","straight","waterlogged","false")));var lower=nativeMaterial.resolve(new ChunkSnapshot.State("minecraft:oak_stairs",Map.of("facing","east","half","bottom","shape","straight","waterlogged","false")));check(!upper.boxes().equals(lower.boxes()),"native stair properties affect geometry");
        check(nativeMaterial.resolve(new ChunkSnapshot.State("minecraft:stone",Map.of())).opaque(),"native material opacity");
        System.out.println("Replay regression: "+checks+" checks passed");
    }
}
