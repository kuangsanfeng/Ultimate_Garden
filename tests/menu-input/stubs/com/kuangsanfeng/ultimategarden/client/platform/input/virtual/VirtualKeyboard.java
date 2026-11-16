package com.kuangsanfeng.ultimategarden.client.platform.input.virtual;

import java.util.*;
import java.util.function.BooleanSupplier;
/** Native KeyMapping boundary: tracked holds survive vanilla releaseAll/setAll. */
public final class VirtualKeyboard {
    public final Set<String> held = new HashSet<>(), nativeKeys = new HashSet<>();
    public void setInputAllowed(BooleanSupplier allowed) { }
    public void keyDown(String key) { held.add(key); nativeKeys.add(key); }
    public void keyUp(String key) { held.remove(key); nativeKeys.remove(key); }
    public boolean hasHeldKeys() { return !held.isEmpty(); }
    public void reapplyHeldState() { nativeKeys.addAll(held); }
    public void releaseAll() { held.clear(); nativeKeys.clear(); }
    public void keyClick(String key) { }
    public void slot(int slot) { }
    public void typeCharacter(char character) { }
}
