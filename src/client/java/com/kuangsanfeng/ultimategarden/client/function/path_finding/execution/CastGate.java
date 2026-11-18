package com.kuangsanfeng.ultimategarden.client.function.path_finding.execution;

/** Time is measured in monotonic nanoseconds. A confirmation is required before another cast. */
public final class CastGate {
    private boolean dispatched, pending;
    private long last;
    public boolean ready(long now, int cooldownMs) {
        return !pending && (!dispatched || now-last >= cooldownMs*1_000_000L);
    }
    public double remainingMs(long now,int cooldownMs) {
        return dispatched?Math.max(0,cooldownMs-(now-last)/1_000_000.0):0;
    }
    public long dispatch(long now) {
        long interval=dispatched?now-last:-1;
        last=now;
        dispatched=pending=true;
        return interval;
    }
    public void confirm() { pending=false; }
    public boolean pending() { return pending; }
}
