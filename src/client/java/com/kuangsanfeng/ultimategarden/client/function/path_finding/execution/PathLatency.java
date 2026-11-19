package com.kuangsanfeng.ultimategarden.client.function.path_finding.execution;

import com.kuangsanfeng.ultimategarden.client.platform.network.NativeRttService;
import java.util.function.BiConsumer;
import java.util.function.LongConsumer;

/** One calculation sample per planning phase, and a separate execution clock. */
public final class PathLatency {
    private static final long INTERVAL=3_000_000_000L;
    private final NativeRttService service;
    private int sampledAttempt=-1;
    private long nextExecution=-1;
    private long reportedAt;
    private NativeRttService.Status reported=NativeRttService.Status.STOPPED;
    private String phase;
    public PathLatency(NativeRttService service){this.service=service;}
    public void beginExecution(long now){if(nextExecution<0)nextExecution=now+INTERVAL;}
    public void tick(Object connection,long now,int calculationAttempt,LongConsumer sender,BiConsumer<String,Long> requested){
        service.tick(connection,now,Double.POSITIVE_INFINITY,sender);
        if(calculationAttempt>sampledAttempt && !service.pending()){
            sampledAttempt=calculationAttempt;
            request(connection,now,sender,requested,"ACTION_CALCULATION");
        }
        if(nextExecution>=0 && now>=nextExecution){
            nextExecution=now+INTERVAL;
            if(!service.pending())request(connection,now,sender,requested,"EXECUTION");
            else requested.accept("EXECUTION_IN_FLIGHT",now);
        }
    }
    private void request(Object connection,long now,LongConsumer sender,BiConsumer<String,Long> requested,String stage){
        phase=stage;
        service.request(connection,now,sender);
        requested.accept(stage,now);
    }
    public boolean newResult(){
        var status=service.status();
        if(status==reported && service.sampledAtNanos()==reportedAt)return false;
        reported=status;reportedAt=service.sampledAtNanos();return true;
    }
    public String phase(){return phase;}
    public NativeRttService.Status status(){return service.status();}
    public Double milliseconds(){return service.milliseconds();}
    public void close(){service.stop();}
}
