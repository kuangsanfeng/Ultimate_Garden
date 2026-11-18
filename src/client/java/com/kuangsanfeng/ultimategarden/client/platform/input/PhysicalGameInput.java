package com.kuangsanfeng.ultimategarden.client.platform.input;

import com.kuangsanfeng.ultimategarden.client.mixin.ShootoutMouseAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/** Client-thread owners of the physical game input gate. GUI and virtual input bypass it. */
public final class PhysicalGameInput {
    private static boolean focusReleased;
    private static Object pathOwner;
    private static boolean filteringKeyboard;

    private PhysicalGameInput() {}

    public static boolean focusReleased() { return focusReleased; }
    public static boolean pathBlocked() { return pathOwner != null; }

    public static boolean blocksGame(Minecraft client) {
        return (focusReleased || pathOwner != null) && client.screen == null && client.getOverlay() == null;
    }

    public static void setFocusReleased(Minecraft client, boolean released) {
        if (released && !focusReleased && pathOwner == null) releasePhysicalHolds(client);
        focusReleased = released;
    }

    public static boolean beginPath(Minecraft client, Object owner) {
        if (pathOwner == owner) return false;
        if (pathOwner != null) throw new IllegalStateException("Path input is already blocked by another owner");
        if (!focusReleased) releasePhysicalHolds(client);
        pathOwner = owner;
        return true;
    }

    public static boolean endPath(Object owner) {
        if (pathOwner != owner) return false;
        pathOwner = null;
        return true;
    }

    /** Keep the entry state through a physical stop key that releases the path before vanilla opens its screen. */
    public static void keyboardDispatch(boolean filter, Runnable dispatch) {
        boolean previous = filteringKeyboard;
        filteringKeyboard = filter;
        try { dispatch.run(); }
        finally { filteringKeyboard = previous; }
    }

    public static boolean blocksMapping(KeyMapping mapping) {
        return filteringKeyboard && isBlockedMapping(Minecraft.getInstance().options, mapping);
    }

    public static boolean isBlockedMapping(Options options, KeyMapping mapping) {
        if (mapping == options.keyUp || mapping == options.keyDown || mapping == options.keyLeft
                || mapping == options.keyRight || mapping == options.keyJump
                || mapping == options.keyAttack || mapping == options.keyUse) return true;
        for (KeyMapping slot : options.keyHotbarSlots) {
            if (mapping == slot) return true;
        }
        return false;
    }

    private static void releasePhysicalHolds(Minecraft client) {
        if (client.screen != null) return;
        Options options = client.options;
        for (KeyMapping mapping : options.keyMappings) {
            if (!isBlockedMapping(options, mapping)) continue;
            mapping.setDown(false);
            while (mapping.consumeClick()) {}
        }
        var mouse = (ShootoutMouseAccessor) client.mouseHandler;
        mouse.shootout$setDX(0);
        mouse.shootout$setDY(0);
        mouse.shootout$setLeftPressed(false);
        mouse.shootout$setRightPressed(false);
        mouse.shootout$setMiddlePressed(false);
        mouse.shootout$setActiveButton(null);
        mouse.shootout$setPressedTime(0);
    }
}
