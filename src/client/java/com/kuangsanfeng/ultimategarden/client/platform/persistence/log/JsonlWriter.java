package com.kuangsanfeng.ultimategarden.client.platform.persistence.log;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.log.JsonSnapshot;

import com.google.gson.*;
import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.io.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public class JsonlWriter {
 private record Entry(String file,JsonSnapshot snapshot,int units,boolean closeFile){}
 private final ArrayBlockingQueue<Entry> queue=new ArrayBlockingQueue<>(4096);
 private final String sessionId;private final Path root;private final Thread worker;private volatile boolean closing;
 private final boolean append;
 private final boolean uniqueNames;private volatile Entry terminal;
 public volatile String error;public final AtomicLong dropped=new AtomicLong(),written=new AtomicLong(),errors=new AtomicLong();
 private final AtomicLong pendingUnits=new AtomicLong(), offered=new AtomicLong();
 private void backlog(){if(error==null){error="QUEUE_BACKLOG_LIMIT";errors.incrementAndGet();}if(!append)closing=true;}
 private static int units(Object value){if(value instanceof Map<?,?> m)return 1+m.values().stream().mapToInt(JsonlWriter::units).sum();if(value instanceof List<?> l)return 1+l.stream().mapToInt(JsonlWriter::units).sum();return 1;}
 private final ConcurrentHashMap<String,AtomicLong> counts=new ConcurrentHashMap<>();
 private final java.util.function.Consumer<JsonlWriter> completion;
 public JsonlWriter(Path root,String id,boolean append,java.util.function.Consumer<JsonlWriter> completion){
  this(root,id,append,false,completion);
 }
 public JsonlWriter(Path root,String id,boolean append,boolean uniqueNames,java.util.function.Consumer<JsonlWriter> completion){
  this.uniqueNames=uniqueNames;this.root=root;sessionId=id;this.append=append;this.completion=java.util.Objects.requireNonNull(completion);worker=new Thread(this::run,"UG-ZombieShoot-Writer");worker.setDaemon(true);worker.start();}
 public synchronized boolean offer(String path,Map<String,Object> value){
  if(!root.resolve(path).normalize().startsWith(root.normalize()))throw new IllegalArgumentException("Log path outside session root");
  offered.incrementAndGet();if(closing||error!=null){dropped.incrementAndGet();return false;}
  var snapshot=new JsonSnapshot(value);int size=units(snapshot.data());
  if(pendingUnits.addAndGet(size)>500000){pendingUnits.addAndGet(-size);dropped.incrementAndGet();backlog();return false;}
  boolean ok=queue.offer(new Entry(path,snapshot,size,false));if(!ok){pendingUnits.addAndGet(-size);dropped.incrementAndGet();backlog();}return ok;
 }
 /** Reserved terminal slot guarantees a normal/backlog END can follow accepted records, even when the queue is full. */
 public synchronized void endBatch(String file,Map<String,Object> value){
  var snapshot=new JsonSnapshot(value);var entry=new Entry(file,snapshot,0,true);
  if(!queue.offer(entry)){terminal=entry;closing=true;}if(error!=null)closing=true;
 }
 public Map<String,Long> counts(){var out=new TreeMap<String,Long>();counts.forEach((k,v)->out.put(k,v.get()));return Map.copyOf(out);}
 public void reportFailure(Exception ex){error=ex.toString();errors.incrementAndGet();}
 public long count(String prefix){return counts.entrySet().stream().filter(e->e.getKey().startsWith(prefix)).mapToLong(e->e.getValue().get()).sum();}
 public synchronized void close(){closing=true;}
 public void awaitClose(long millis)throws InterruptedException{worker.join(millis);}
 public boolean finished(){return !worker.isAlive();}
 private BufferedWriter open(String file)throws IOException{
  Path path=root.resolve(file);
  Files.createDirectories(path.getParent());
  if(append)return Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
  for(int attempt=0;;attempt++){
   Path candidate=attempt==0?path:path.resolveSibling(path.getFileName().toString().replaceFirst("\\.jsonl$","")
           +"_"+sessionId.substring(0,8)+(attempt==1?"":"_"+attempt)+".jsonl");
   try{return Files.newBufferedWriter(candidate,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);}
   catch(FileAlreadyExistsException collision){if(!uniqueNames)throw collision;}
  }
 }
 private void run(){
  var writers=new LinkedHashMap<String,BufferedWriter>();var gson=new GsonBuilder().serializeNulls().create();long inFlight=0;
  try{
   Files.createDirectories(root);

   while(!closing||!queue.isEmpty()){
    Entry first=queue.poll(100,TimeUnit.MILLISECONDS);if(first==null)continue;
    var batch=new ArrayList<Entry>();batch.add(first);queue.drainTo(batch,127);inFlight=batch.size();
    for(var e:batch)pendingUnits.addAndGet(-e.units());
    for(var e:batch){var w=writers.get(e.file());if(w==null){w=open(e.file());writers.put(e.file(),w);}
     w.write(gson.toJson(e.snapshot().data()));w.newLine();
     if(e.closeFile()){w.flush();w.close();writers.remove(e.file());}
    }
    for(var w:writers.values())w.flush();
    for(var e:batch){written.incrementAndGet();counts.computeIfAbsent(e.file(),k->new AtomicLong()).incrementAndGet();}inFlight=0;
   }
   if(terminal!=null){var e=terminal;var w=writers.get(e.file());if(w==null){w=open(e.file());writers.put(e.file(),w);writers.put(e.file(),w);}w.write(gson.toJson(e.snapshot().data()));w.newLine();w.flush();written.incrementAndGet();}
  }catch(Exception ex){error=ex.getClass().getSimpleName()+": "+ex.getMessage();errors.incrementAndGet();dropped.addAndGet(queue.size()+inFlight);queue.clear();}
  finally{
   completion.accept(this);
   for(var w:writers.values())try{w.close();}catch(IOException ex){error=ex.toString();errors.incrementAndGet();}}
 }
}

