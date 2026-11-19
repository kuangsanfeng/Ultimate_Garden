package com.kuangsanfeng.ultimategarden.client.platform.network;

import java.util.function.LongConsumer;

/** Single-flight native Ping/Pong session. All mutations occur on the client thread. */
public class NativeRttService {
 public enum Status { STOPPED,MEASURING,AVAILABLE,UNAVAILABLE }
 private static final long PREFIX=0x5547525400000000L, MASK=0xffffffff00000000L, TIMEOUT=5_000_000_000L;
 private final long prefix;
 public NativeRttService(){this(PREFIX);}
 NativeRttService(long prefix){this.prefix=prefix;}
 private volatile long sequence;
 private Object connection;
 private boolean active,pending;
 private long request,sent,sampledAt;
 private Double milliseconds;
 private Status status=Status.STOPPED;
 public long sampledAtNanos(){return sampledAt;}
 public Status status(){return status;}
 public Double milliseconds(){return milliseconds;}
 public boolean pending(){return pending;}
 public boolean owns(long token){long n=token&0xffffffffL;return (token&MASK)==prefix&&n>0&&n<=sequence;}
 public void stop(){active=false;pending=false;connection=null;milliseconds=null;status=Status.STOPPED;request=0;sent=0;sampledAt=0;}
 public void start(Object connection,long now,LongConsumer sender){
  stop();if(connection==null){status=Status.UNAVAILABLE;return;}this.connection=connection;active=true;send(now,sender);
 }
 public boolean request(Object connection,long now,LongConsumer sender){
  if(connection==null){status=Status.UNAVAILABLE;return false;}
  if(this.connection!=connection||!active){start(connection,now,sender);return true;}
  if(pending)return false;
  send(now,sender);return true;
 }
 private void send(long now,LongConsumer sender){
  if(sequence>=0xffffffffL){status=Status.UNAVAILABLE;active=false;return;}
  request=prefix|++sequence;sent=now;pending=true;if(milliseconds==null)status=Status.MEASURING;
  try{sender.accept(request);}catch(RuntimeException e){pending=false;milliseconds=null;status=Status.UNAVAILABLE;}
 }
 public void tick(Object connection,long now,double intervalSeconds,LongConsumer sender){
  if(!active)return;if(this.connection!=connection){stop();return;}
  if(pending){if(now-sent>=TIMEOUT){pending=false;milliseconds=null;status=Status.UNAVAILABLE;}return;}
  long interval=(long)Math.min(Long.MAX_VALUE,intervalSeconds*1e9);
  if(now-sent>=interval)send(now,sender);
 }
 public boolean pong(Object connection,long token,long received){
  if(!active||!pending||this.connection!=connection||request!=token||received<sent||received-sent>=TIMEOUT)return false;
  milliseconds=(received-sent)/1_000_000.;sampledAt=received;pending=false;status=Status.AVAILABLE;return true;
 }
}
