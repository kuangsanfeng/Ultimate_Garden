package com.kuangsanfeng.ultimategarden.client.platform.input;

import com.kuangsanfeng.ultimategarden.client.mixin.ShootoutMouseAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import static com.kuangsanfeng.ultimategarden.client.platform.input.InputRequests.Channel.*;
import com.kuangsanfeng.ultimategarden.client.platform.input.InputRequests.Channel;

/** Client-thread owners of the physical game input gate. GUI and virtual input bypass it. */
public final class PhysicalGameInput {
    private static final Object U = new Object();
    private static final InputRequests REQUESTS = new InputRequests();
    private static Object pathOwner;
    private static boolean filteringKeyboard;

    private PhysicalGameInput() {}

    public static String describe(Minecraft client) {
        var active=new java.util.ArrayList<String>();
        for(var channel:Channel.values())if(channel!=CAPTURE && blocked(channel))active.add(channel.name());
        String capture=!client.mouseHandler.isMouseGrabbed()||captureReleased()?"Released":"Captured";
        return capture+" / "+(active.isEmpty()?"No Physical Blocks":String.join(", ",active));
    }

    public static boolean focusReleased() { return REQUESTS.has(U); }
    public static boolean captureReleased() { return blocked(CAPTURE); }
    public static boolean pathBlocked() { return pathOwner != null; }
    public static boolean blocked(Channel channel) { return REQUESTS.blocked(channel); }
    public static boolean inGame(Minecraft client, Channel channel) {
        return blocked(channel) && client.screen == null && client.getOverlay() == null;
    }

    public static void setFocusReleased(Minecraft client, boolean released) {
        if (released) {
            REQUESTS.set(U,MOTION,CAPTURE);
            var mouse=(ShootoutMouseAccessor)client.mouseHandler;
            mouse.shootout$setDX(0);
            mouse.shootout$setDY(0);
        } else REQUESTS.remove(U);
    }

    public static void bind(Object owner,java.util.Map<Channel,java.util.function.BooleanSupplier> channels){
        REQUESTS.bind(owner,channels);
    }

    public static void capture(Object owner, boolean released) {
        if (released) REQUESTS.set(owner,CAPTURE); else REQUESTS.remove(owner);
    }

    public static void request(Minecraft client,Object owner,Channel first,Channel... rest) {
        boolean keyboardWasBlocked=blocked(KEYBOARD), buttonsWereBlocked=blocked(BUTTONS);
        REQUESTS.set(owner,first,rest);
        if ((!keyboardWasBlocked && blocked(KEYBOARD)) || (!buttonsWereBlocked && blocked(BUTTONS)))
            releasePhysicalHolds(client);
    }

    public static void release(Object owner) { REQUESTS.remove(owner); }

    public static void beginPath(Minecraft client, Object owner) {
        if (pathOwner != null) throw new IllegalStateException("Path input is already blocked by another owner");
        request(client,owner,MOTION,BUTTONS,WHEEL,KEYBOARD);
        pathOwner=owner;
    }

    public static boolean endPath(Object owner) {
        if (pathOwner != owner) return false;
        REQUESTS.remove(owner);
        pathOwner=null;
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
                || mapping == options.keyAttack || mapping == options.keyUse
                || mapping == options.keyShift || mapping == options.keySprint) return true;
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
