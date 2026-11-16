package com.kuangsanfeng.ultimategarden.client.function.mapping;

/** First click arms, two subsequent clicks form a fresh double click. No destructive action here. */
public final class MappingConfirmation {
    private Object context;
    private String operation;
    private long firstPairClick=-1;
    public String operation(){return operation;}
    public boolean armed(Object key,String op){return java.util.Objects.equals(context,key)&&java.util.Objects.equals(operation,op);}
    public boolean click(Object key,String op,long nanos){
        if(!armed(key,op)){context=key;operation=op;firstPairClick=-1;return false;}
        if(firstPairClick>=0&&nanos>=firstPairClick&&nanos-firstPairClick<=500_000_000L){cancel();return true;}
        firstPairClick=nanos;return false;
    }
    public void validate(Object key){if(!java.util.Objects.equals(context,key))cancel();}
    public void cancel(){context=null;operation=null;firstPairClick=-1;}
}
