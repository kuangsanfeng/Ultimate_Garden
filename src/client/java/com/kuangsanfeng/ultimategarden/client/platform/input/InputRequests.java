package com.kuangsanfeng.ultimategarden.client.platform.input;

import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Set;

/** Requests belong to the identity of the client-thread owner. */
public final class InputRequests {
    public enum Channel { MOTION, BUTTONS, WHEEL, KEYBOARD, CAPTURE }
    private final IdentityHashMap<Object,Set<Channel>> requests = new IdentityHashMap<>();

    private final IdentityHashMap<Object,java.util.Map<Channel,java.util.function.BooleanSupplier>> conditional=new IdentityHashMap<>();

    public void bind(Object owner,java.util.Map<Channel,java.util.function.BooleanSupplier> channels){
        conditional.put(owner,java.util.Map.copyOf(channels));
    }

    public void set(Object owner,Channel first,Channel... remaining) {
        requests.put(owner,EnumSet.of(first,remaining));
    }
    public boolean remove(Object owner) { return requests.remove(owner)!=null; }
    public boolean has(Object owner) { return requests.containsKey(owner); }
    public boolean blocked(Channel channel) {
        for (var requested:requests.values()) if (requested.contains(channel)) return true;
        for(var source:conditional.values()){
            var requested=source.get(channel);
            if(requested!=null&&requested.getAsBoolean())return true;
        }
        return false;
    }
}
