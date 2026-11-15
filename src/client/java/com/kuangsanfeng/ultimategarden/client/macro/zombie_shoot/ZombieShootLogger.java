package com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot;
import com.google.gson.*;
import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.io.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public final class ZombieShootLogger {
 private record Entry(String file,ZombieShootSnapshot snapshot,int units,boolean closeFile){}
 private final ArrayBlockingQueue<Entry> queue=new ArrayBlockingQueue<>(4096);
 private final String sessionId;private final Path root;private final Thread worker;private volatile boolean closing;
 private final boolean append;private volatile Entry terminal;
 public volatile String error;public final AtomicLong dropped=new AtomicLong(),written=new AtomicLong(),errors=new AtomicLong();
 private final AtomicLong pendingUnits=new AtomicLong(), offered=new AtomicLong();
 private void backlog(){if(error==null){error="QUEUE_BACKLOG_LIMIT";errors.incrementAndGet();}if(!append)closing=true;}
 private static int units(Object value){if(value instanceof Map<?,?> m)return 1+m.values().stream().mapToInt(ZombieShootLogger::units).sum();if(value instanceof List<?> l)return 1+l.stream().mapToInt(ZombieShootLogger::units).sum();return 1;}
 private final ConcurrentHashMap<String,AtomicLong> counts=new ConcurrentHashMap<>();
 public ZombieShootLogger(Path root,String id){this(root,id,false);}
 public ZombieShootLogger(Path root,String id,boolean append){this.root=root;sessionId=id;this.append=append;worker=new Thread(this::run,"UG-ZombieShoot-Writer");worker.setDaemon(true);worker.start();}
 public synchronized boolean offer(String path,Map<String,Object> value){
  if(!root.resolve(path).normalize().startsWith(root.normalize()))throw new IllegalArgumentException("Log path outside session root");
  offered.incrementAndGet();if(closing||error!=null){dropped.incrementAndGet();return false;}
  var snapshot=new ZombieShootSnapshot(value);int size=units(snapshot.data());
  if(pendingUnits.addAndGet(size)>500000){pendingUnits.addAndGet(-size);dropped.incrementAndGet();backlog();return false;}
  boolean ok=queue.offer(new Entry(path,snapshot,size,false));if(!ok){pendingUnits.addAndGet(-size);dropped.incrementAndGet();backlog();}return ok;
 }
 /** Reserved terminal slot guarantees a normal/backlog END can follow accepted records, even when the queue is full. */
 public synchronized void endBatch(String file,Map<String,Object> value){
  if(!append)throw new IllegalStateException("Append batches only");
  var snapshot=new ZombieShootSnapshot(value);var entry=new Entry(file,snapshot,0,true);
  if(!queue.offer(entry)){terminal=entry;closing=true;}if(error!=null)closing=true;
 }
 public long count(String prefix){return counts.entrySet().stream().filter(e->e.getKey().startsWith(prefix)).mapToLong(e->e.getValue().get()).sum();}
 public synchronized void close(){closing=true;}
 public void awaitClose(long millis)throws InterruptedException{worker.join(millis);}
 public boolean finished(){return !worker.isAlive();}
 private void run(){
  var writers=new LinkedHashMap<String,BufferedWriter>();var gson=new GsonBuilder().serializeNulls().create();long inFlight=0;
  try{
   Files.createDirectories(root);

   while(!closing||!queue.isEmpty()){
    Entry first=queue.poll(100,TimeUnit.MILLISECONDS);if(first==null)continue;
    var batch=new ArrayList<Entry>();batch.add(first);queue.drainTo(batch,127);inFlight=batch.size();
    for(var e:batch)pendingUnits.addAndGet(-e.units());
    for(var e:batch){var w=writers.get(e.file());if(w==null){Path p=root.resolve(e.file());Files.createDirectories(p.getParent());w=Files.newBufferedWriter(p,StandardCharsets.UTF_8,append?StandardOpenOption.CREATE:StandardOpenOption.CREATE_NEW,append?StandardOpenOption.APPEND:StandardOpenOption.WRITE);writers.put(e.file(),w);}
     w.write(gson.toJson(e.snapshot().data()));w.newLine();
     if(e.closeFile()){w.flush();w.close();writers.remove(e.file());}
    }
    for(var w:writers.values())w.flush();
    for(var e:batch){written.incrementAndGet();counts.computeIfAbsent(e.file(),k->new AtomicLong()).incrementAndGet();}inFlight=0;
   }
   if(terminal!=null){var e=terminal;var w=writers.get(e.file());if(w==null){var p=root.resolve(e.file());Files.createDirectories(p.getParent());w=Files.newBufferedWriter(p,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);writers.put(e.file(),w);}w.write(gson.toJson(e.snapshot().data()));w.newLine();w.flush();written.incrementAndGet();}
  }catch(Exception ex){error=ex.getClass().getSimpleName()+": "+ex.getMessage();errors.incrementAndGet();dropped.addAndGet(queue.size()+inFlight);queue.clear();}
  finally{
   if(!append)try { Files.createDirectories(root.resolve("sessions"));Files.writeString(root.resolve("sessions/"+sessionId+"-summary.json"),new GsonBuilder().serializeNulls().create().toJson(ZombieShootSnapshot.map("sessionId",sessionId,"endTimestamp",java.time.Instant.now().toString(),"written",written.get(),"droppedSamples",dropped.get(),"collectionErrors",errors.get(),"failureReason",error,"counts",new TreeMap<>(counts))),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW); }catch(IOException ex){error=ex.toString();errors.incrementAndGet();}
   for(var w:writers.values())try{w.close();}catch(IOException ex){error=ex.toString();errors.incrementAndGet();}}
 }
}

