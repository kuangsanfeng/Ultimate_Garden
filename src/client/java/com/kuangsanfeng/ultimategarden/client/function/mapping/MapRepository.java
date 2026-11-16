package com.kuangsanfeng.ultimategarden.client.function.mapping;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/** Instance-local store. All methods run on the recorder's single bounded IO worker. */
public final class MapRepository {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private final Path root, directory;
    private final String worldName;
    public MapRepository(Path root,String worldName)throws IOException{
        this.root=root.toAbsolutePath().normalize();this.worldName=worldName;
        directory=this.root.resolve(MappingNames.key(worldName)).normalize();safe(directory);
    }
    public Path directory(){return directory;}
    public record Coordinate(int x,int z) {}
    /** Disk index only; no NBT reads. Coordinates are relative to the persisted origin. */
    public Set<Coordinate> index(MapConfig c,Festival layer)throws IOException{
        Path folder=layer==Festival.NONE?directory:directory.resolve(layer.folder);safe(folder);
        var result=new HashSet<Coordinate>();if(!Files.exists(folder))return Set.of();
        var pattern=Pattern.compile(Pattern.quote(c.worldName())+" (-?\\d+) (-?\\d+)\\.litematic");
        try(var files=Files.list(folder)){for(Path file:files.toList()){
            var match=pattern.matcher(file.getFileName().toString());if(!match.matches())continue;safe(file);
            if(Files.isRegularFile(file))try{result.add(new Coordinate(Integer.parseInt(match.group(1)),Integer.parseInt(match.group(2))));}catch(NumberFormatException e){throw new IOException("INVALID_CHUNK_COORDINATE: "+file.getFileName(),e);}
        }}return Set.copyOf(result);
    }
    private void safe(Path path)throws IOException{
        Path p=path.toAbsolutePath().normalize();if(!p.startsWith(root)||p.equals(root))throw new IOException("UNSAFE_MAP_PATH");
        for(Path q=p;q!=null;q=q.getParent())if(Files.isSymbolicLink(q))throw new IOException("MAP_SYMLINK_REJECTED");
    }
    public MapConfig load()throws IOException{
        safe(directory);
        if(!Files.exists(directory))return null;
        if(!Files.exists(directory.resolve("map.json"))){try(var entries=Files.list(directory)){if(entries.findAny().isPresent())throw new IOException("MAP_CONFIG_MISSING_WITH_EXISTING_DATA");}return null;}
        try(var reader=Files.newBufferedReader(directory.resolve("map.json"),StandardCharsets.UTF_8)){
            var j=JsonParser.parseReader(reader).getAsJsonObject();var o=j.getAsJsonObject("origin");var layers=EnumSet.noneOf(Festival.class);
            j.getAsJsonArray("festivals").forEach(v->layers.add(Festival.valueOf(v.getAsString())));
            var c=new MapConfig(j.get("schemaVersion").getAsInt(),j.get("worldName").getAsString(),new MapConfig.Origin(o.get("x").getAsInt(),o.get("y").getAsInt(),o.get("z").getAsInt()),j.get("maxY").getAsInt(),layers,Festival.valueOf(j.get("selectedFestival").getAsString()));
            if(!c.worldName().equals(worldName))throw new IOException("MAP_WORLD_NAME_COLLISION");
            if(Files.exists(directory.resolve(".origin-transaction")))throw new IOException("INCOMPLETE_ORIGIN_TRANSACTION");return c;
        }catch(RuntimeException e){throw new IOException("MAP_CONFIG_INVALID: "+e.getMessage(),e);}
    }
    private static void replace(Path from,Path to)throws IOException{try{Files.move(from,to,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(from,to,StandardCopyOption.REPLACE_EXISTING);}}
    public void saveConfig(MapConfig config)throws IOException{
        safe(directory);Files.createDirectories(directory);Path tmp=Files.createTempFile(directory,".map-",".tmp");
        try{Files.writeString(tmp,JSON.toJson(config),StandardCharsets.UTF_8);replace(tmp,directory.resolve("map.json"));}finally{Files.deleteIfExists(tmp);}
    }
    public MapConfig initialize(MapConfig c,ChunkSnapshot first,int dataVersion,BooleanSupplier valid,Object gate)throws IOException{
        var existing=load();if(existing!=null)return existing;
        Files.createDirectories(root);Path stage=Files.createTempDirectory(root,".mapping-init-");
        try{
            LitematicIO.write(stage.resolve(worldName+" 0 0.litematic"),first,dataVersion);
            Files.writeString(stage.resolve("map.json"),JSON.toJson(c),StandardCharsets.UTF_8);
            synchronized(gate){if(!valid.getAsBoolean())return null;safe(directory);if(Files.exists(directory)){try(var e=Files.list(directory)){if(e.findAny().isPresent())throw new IOException("MAP_ALREADY_EXISTS");}Files.delete(directory);}Files.move(stage,directory);}
            return c;
        }finally{if(Files.exists(stage))deleteTree(stage);}
    }
    public Path chunkPath(MapConfig c,Festival layer,int cx,int cz)throws IOException{
        Path parent=layer==Festival.NONE?directory:directory.resolve(layer.folder);Path path=parent.resolve(worldName+" "+c.relativeX(cx)+" "+c.relativeZ(cz)+".litematic");safe(path);return path;
    }
    public Optional<ChunkSnapshot> read(MapConfig c,Festival layer,int cx,int cz)throws IOException{
        Path path=chunkPath(c,layer,cx,cz);if(layer!=Festival.NONE&&!Files.exists(path))path=chunkPath(c,Festival.NONE,cx,cz);
        return Files.exists(path)?Optional.of(LitematicIO.read(path)):Optional.empty();
    }
    public boolean exists(MapConfig c,Festival layer,int cx,int cz)throws IOException{return Files.isRegularFile(chunkPath(c,layer,cx,cz));}
    public void write(MapConfig c,Festival layer,int cx,int cz,ChunkSnapshot snapshot,int dataVersion,BooleanSupplier valid,Object gate)throws IOException{
        Path target=chunkPath(c,layer,cx,cz);boolean equal=false;
        if(layer!=Festival.NONE){var base=read(c,Festival.NONE,cx,cz).orElseThrow(()->new IOException("BASE_CHUNK_REQUIRED"));equal=base.sameBlocks(snapshot);}
        if(equal){synchronized(gate){if(valid.getAsBoolean()){safe(target);Files.deleteIfExists(target);}}return;}
        Files.createDirectories(target.getParent());safe(target);Path temp=Files.createTempFile(target.getParent(),".chunk-",".tmp");
        try{LitematicIO.write(temp,snapshot,dataVersion);synchronized(gate){if(valid.getAsBoolean()){safe(target);replace(temp,target);}}}finally{Files.deleteIfExists(temp);}
    }
    public MapConfig select(MapConfig c,Festival f,boolean create)throws IOException{
        if(create&&c.festivals().contains(f))throw new IOException("FESTIVAL_ALREADY_CREATED");
        if(!create&&!c.festivals().contains(f))throw new IOException("CREATE_FESTIVAL_FIRST");
        var next=c.layer(f,create);saveConfig(next);return next;
    }
    public void deleteChunk(MapConfig c,Festival f,int cx,int cz)throws IOException{Files.deleteIfExists(chunkPath(c,f,cx,cz));}
    public void deleteWorld()throws IOException{safe(directory);if(Files.exists(directory))deleteTree(directory);}
    private void deleteTree(Path target)throws IOException{
        safe(target);try(var paths=Files.walk(target)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList()){safe(p);Files.delete(p);}}
    }
    public MapConfig resetOrigin(MapConfig c,int cx,int cz)throws IOException{return resetOrigin(c,cx,cz,()->true,this);}
    public MapConfig resetOrigin(MapConfig c,int cx,int cz,BooleanSupplier valid,Object gate)throws IOException{
        var next=c.origin(cx,cz);if(next.origin().equals(c.origin()))return c;
        Path stage=directory.resolve(".origin-transaction");safe(stage);Files.createDirectory(stage);
        record Move(Path original,Path staged,Path target){}
        var moves=new ArrayList<Move>();var locations=new HashMap<Move,Path>();
        var pattern=Pattern.compile(Pattern.quote(worldName)+" (-?\\d+) (-?\\d+)\\.litematic");
        try{
            for(Festival f:c.festivals()){
                Path folder=f==Festival.NONE?directory:directory.resolve(f.folder);if(!Files.exists(folder))continue;safe(folder);
                try(var stream=Files.list(folder)){for(Path file:stream.toList()){
                    var m=pattern.matcher(file.getFileName().toString());if(!m.matches())continue;safe(file);
                    int wx=Math.addExact(Integer.parseInt(m.group(1)),Math.floorDiv(c.origin().x(),16)),wz=Math.addExact(Integer.parseInt(m.group(2)),Math.floorDiv(c.origin().z(),16));
                    var move=new Move(file,stage.resolve(Integer.toString(moves.size())),chunkPath(next,f,wx,wz));moves.add(move);locations.put(move,file);
                }}
            }
            for(var m:moves){Files.move(m.original,m.staged);locations.put(m,m.staged);}
            for(var m:moves){Files.move(m.staged,m.target);locations.put(m,m.target);}
            synchronized(gate){if(!valid.getAsBoolean())throw new IOException("CANCELLED_ORIGIN_TRANSACTION");saveConfig(next);}
            Files.delete(stage);return next;
        }catch(Exception failure){
            try{
                // Stage every currently placed file again before restoring names (including swaps).
                for(int i=0;i<moves.size();i++){var m=moves.get(i);Path recovery=stage.resolve("rollback-"+i);Files.move(locations.get(m),recovery);locations.put(m,recovery);}
                for(var m:moves)Files.move(locations.get(m),m.original);saveConfig(c);Files.delete(stage);
            }catch(Exception rollback){failure.addSuppressed(rollback);}
            throw new IOException("RESET_ORIGIN_FAILED",failure);
        }
    }
}
