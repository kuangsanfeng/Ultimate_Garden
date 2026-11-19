package com.kuangsanfeng.ultimategarden.client.function.path_finding.execution;

import java.util.*;
import java.util.function.Consumer;

/** Client-side bounded history. The caller decides when a window is worth exporting. */
public final class PathFailureTrace {
    public static final int CAPACITY=96;
    private final String runId;
    private final String mode;
    private final String mapId;
    private long lastExported;
    private String firstBlocker;
    public String runId(){return runId;}
    public String firstBlocker(){return firstBlocker;}
    public void block(String reason){if(firstBlocker==null)firstBlocker=reason;}
    private final Consumer<Map<String,Object>> output;
    private final ArrayDeque<Map<String,Object>> history=new ArrayDeque<>();
    private long sequence,actionId,candidateId,dropped,replacesActionId;
    private int planAttempt;
    private boolean dueBlocked;
    private Map<String,Object> lastDispatch,lastConfirmation,precedingDispatch,precedingConfirmation;

    public PathFailureTrace(String mode,Consumer<Map<String,Object>> output){this(mode,null,output);}
    public PathFailureTrace(String mode,String mapId,Consumer<Map<String,Object>> output){
        this(mode,mapId,UUID.randomUUID().toString(),output);
    }
    public PathFailureTrace(String mode,String mapId,String runId,Consumer<Map<String,Object>> output){
        this.runId=runId;
        this.mode=mode; this.mapId=mapId;
        this.output=output;
    }

    public void planAttempt(int value){planAttempt=value;}
    public long actionId(){return actionId;}

    public void replaces(long oldActionId){replacesActionId=oldActionId;}

    public void fallbackOutcome(String outcome){
        if(replacesActionId!=0)export(outcome);
    }

    public void begin(){
        actionId++;
        candidateId++;
        dueBlocked=false;
        replacesActionId=0;
        precedingDispatch=lastDispatch;
        precedingConfirmation=lastConfirmation;
    }

    public Map<String,Object> add(String event,String phase,long tick,long confirmationSequence,Map<String,Object> data){
        var row=new LinkedHashMap<String,Object>();
        row.put("sequence",++sequence);
        row.put("monotonicNanos",System.nanoTime());
        row.put("map",mapId);
        row.put("runId",runId);row.put("mode",mode);row.put("planAttempt",planAttempt);
        row.put("actionId",actionId);row.put("candidateId",candidateId);
        if(replacesActionId!=0)row.put("replacesActionId",replacesActionId);
        row.put("confirmationSequence",confirmationSequence);row.put("tick",tick);
        row.put("event",event);row.put("phase",phase);row.put("data",data);
        if(history.size()==CAPACITY){history.removeFirst();dropped++;}
        history.addLast(row);
        if(event.equals("INPUT_SENT"))lastDispatch=row;
        if(event.equals("CONFIRMATION_CONSUMED"))lastConfirmation=row;
        return row;
    }

    public void gate(long tick,long expected,long confirmationSequence,String blocker,Map<String,Object> data){
        add("CAST_GATE","END_CLIENT_TICK",tick,confirmationSequence,data);
        if(tick>=expected&&!blocker.equals("NONE")&&!dueBlocked){
            dueBlocked=true;
            block(blocker);
            export("EXPECTED_TICK_BLOCKED");
        }
    }

    public void export(String reason){
        var packet=new LinkedHashMap<String,Object>();
        packet.put("event","PATH_FAILURE_WINDOW");packet.put("runId",runId);
        packet.put("mode",mode);packet.put("planAttempt",planAttempt);packet.put("actionId",actionId);
        packet.put("candidateId",candidateId);packet.put("reason",reason);
        if(replacesActionId!=0)packet.put("replacesActionId",replacesActionId);
        packet.put("precedingDispatch",precedingDispatch);packet.put("precedingConfirmation",precedingConfirmation);
        packet.put("map",mapId); packet.put("overwrittenSamples",dropped);
        var added=history.stream().filter(row->(long)row.get("sequence")>lastExported).toList();
        packet.put("history",added);
        packet.put("previousExportSequence",lastExported);
        lastExported=sequence;
        output.accept(packet);
    }
}
