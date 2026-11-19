package com.kuangsanfeng.ultimategarden.client.runtime.hotkey;

import org.lwjgl.glfw.GLFW;

/** GLFW sends PRESS once, followed by REPEAT until the key is released. */
public final class PathHotkey {
    private PathHotkey(){}
    public enum Decision { PASS, TOGGLE, SUPPRESS_REPEAT }
    public static Decision decide(boolean matchesBinding,boolean available,int action){
        if(!matchesBinding||!available||action==GLFW.GLFW_RELEASE)return Decision.PASS;
        return action==GLFW.GLFW_PRESS?Decision.TOGGLE:Decision.SUPPRESS_REPEAT;
    }
}
