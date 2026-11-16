package com.kuangsanfeng.ultimategarden.client.platform.input.virtual;

import java.util.*;
public final class VirtualInput {
    public final Set<String> keys = new HashSet<>();
    public final List<String> transitions = new ArrayList<>();
    public int clock;
    public int downTick;
    public int upTick;
    public final List<String> actions = new ArrayList<>();
    public void keyDown(String key) { keys.add(key); }
    public void keyUp(String key) { keys.remove(key); }
    public void actionKeyDown(String key) { keys.add(key); transitions.add("down:" + key); downTick = clock; actions.add("down:" + key); }
    public void actionKeyUp(String key) { keys.remove(key); transitions.add("up:" + key); upTick = clock; actions.add("up:" + key); }
    public void mouseDown() { }
    public void mouseUp() { }
    public void actionMouseDown() { actions.add("attack"); }
    public void actionMouseUp() { actions.add("release"); }
    public void slot(int slot) { actions.add("slot:" + slot); }
    public void keyClick(String key) { }
}
