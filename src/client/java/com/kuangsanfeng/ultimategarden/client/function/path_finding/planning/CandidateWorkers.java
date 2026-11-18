package com.kuangsanfeng.ultimategarden.client.function.path_finding.planning;

import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/** A process-wide bounded pool. Only the caller owns queues, budgets and route decisions. */
final class CandidateWorkers {
    private static final int MAX_THREADS=Runtime.getRuntime().availableProcessors();
    private static final AtomicInteger sequence=new AtomicInteger();
    private static final ExecutorService POOL=Executors.newFixedThreadPool(MAX_THREADS,task->{
        var thread=new Thread(task,"ug-path-candidate-"+sequence.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });
    private final int workers;

    CandidateWorkers(int workers) {
        if(workers<1||workers>MAX_THREADS) throw new IllegalArgumentException("Invalid path candidate worker count");
        this.workers=workers;
    }

    void check(int size,int minimumBatch,IntConsumer check) {
        if(workers==1||size<minimumBatch) {
            for(int i=0;i<size;i++) check.accept(i);
            return;
        }
        int batches=Math.min(workers,size);
        var tasks=new ArrayList<Callable<Void>>(batches);
        for(int i=0;i<batches;i++) {
            int first=i*size/batches,end=(i+1)*size/batches;
            tasks.add(()->{
                for(int at=first;at<end&&!Thread.currentThread().isInterrupted();at++) check.accept(at);
                return null;
            });
        }
        try {
            for(var task:POOL.invokeAll(tasks)) task.get();
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Path candidate batch interrupted");
        } catch(ExecutionException failure) {
            throw new IllegalStateException("Path candidate query failed",failure.getCause());
        }
    }
}
