package com.kuangsanfeng.ultimategarden.client.function.path_finding.execution;

import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.log.JsonlWriter;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Consumer;

/** One exclusive, asynchronously written file per accepted path session. */
public final class PathTaskLog {
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("uuuu-MM-dd_HH-mm-ss.SSS");
    private final JsonlWriter writer;
    private final String file;
    public static PathTaskLog openIfEnabled(boolean enabled,Path gameDir,LocalDateTime time,String map,Point target,
                                            String runId,Consumer<JsonlWriter> closed){
        return enabled?new PathTaskLog(gameDir,time,map,target,runId,closed):null;
    }
    public PathTaskLog(Path gameDir,LocalDateTime time,String map,Point target,String runId,Consumer<JsonlWriter> closed){
        String safeMap=map.replaceAll("[^\\p{L}\\p{N} ._-]","_").strip().replaceAll("[. ]+$","");
        if(safeMap.isEmpty())safeMap="map";
        if(safeMap.length()>48)safeMap=safeMap.substring(0,48);
        file=TIME.format(time)+"_"+safeMap+"_to_"+number(target.x())+"_"+number(target.y())+"_"+number(target.z())+".jsonl";
        writer=new JsonlWriter(gameDir.resolve("logs").resolve("ultimategarden"),runId,false,true,closed);
    }
    private static String number(double coordinate){
        var number=BigDecimal.valueOf(coordinate).stripTrailingZeros();
        double magnitude=Math.abs(coordinate);
        return magnitude==0||magnitude>=1e-6&&magnitude<1e9?number.toPlainString():number.toString().replace('+','_');
    }
    public boolean record(Map<String,Object> event){return writer.offer(file,event);}
    public void finish(Map<String,Object> event){writer.endBatch(file,event);writer.close();}
    public void awaitClose(long millis)throws InterruptedException{writer.awaitClose(millis);}
}
