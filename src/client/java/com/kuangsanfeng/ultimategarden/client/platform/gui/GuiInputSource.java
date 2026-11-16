package com.kuangsanfeng.ultimategarden.client.platform.gui;

/** Physical GUI input provenance; inventory follows vanilla's click queue. */
public class GuiInputSource {
    public enum Kind { INVENTORY, CHAT, MENU }
    private static Kind requested;
    private static int virtualDepth;
    private static final java.util.Deque<Boolean> inventoryClicks = new java.util.ArrayDeque<>();
    private static boolean inventoryOpening;
    protected GuiInputSource() { }
    public static boolean isVirtualDispatch() { return virtualDepth != 0; }
    public static void virtualDispatch(Runnable action) {
        virtualDepth++;
        try { action.run(); }
        finally { virtualDepth--; }
    }
    public static boolean request(Kind kind) {
        if (virtualDepth != 0) return false;
        requested = kind;
        return true;
    }
    public static boolean consume(Kind kind) {
        if (kind == Kind.INVENTORY) {
            boolean physical = inventoryOpening;
            inventoryOpening = false;
            return physical;
        }
        boolean matches = requested != null && requested == kind;
        requested = null;
        return matches;
    }
    public static void inventoryClick() {
        inventoryClicks.addLast(virtualDepth == 0 && requested == Kind.INVENTORY);
        clearInventoryRequest();
    }
    public static void inventoryConsumed(boolean consumed) {
        inventoryOpening = consumed && !inventoryClicks.isEmpty() && inventoryClicks.removeFirst();
    }
    public static void inventoryReleased() { inventoryClicks.clear(); }
    public static void clearInventoryRequest() {
        if (requested == Kind.INVENTORY) requested = null;
    }
    public static void clearTransient() { requested = null; }
    public static void clear() {
        requested = null;
        inventoryClicks.clear();
        inventoryOpening = false;
    }
}
