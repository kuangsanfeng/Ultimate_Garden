package net.minecraft.client;


import org.lwjgl.glfw.GLFW;

/** Models the verified setScreen/grabMouse recursion; no native window. */
public final class Minecraft {
    public Object screen;
    public Object player = new Object();
    public Object level = new Object();
    public final Mouse mouseHandler = new Mouse();
    public Runnable afterScreen = () -> { };
    public Window getWindow() { return new Window(); }
    public static final class Window { public long handle() { return 1; } }
    public void setScreen(Object next) {
        screen = next;
        if (next != null) mouseHandler.releaseMouse();
        else mouseHandler.grabMouse();
        afterScreen.run();
    }
    public final class Mouse {
        private boolean grabbed;
        public int grabs;
        public int releases;
        public boolean isMouseGrabbed() { return grabbed; }
        public void releaseMouse() {
            if (grabbed) {
                grabbed = false;
                releases++;
                GLFW.mode = GLFW.GLFW_CURSOR_NORMAL;
            }
        }
        public void grabMouse() {
            if (!grabbed) {
                grabbed = true;
                grabs++;
                GLFW.mode = GLFW.GLFW_CURSOR_DISABLED;
                setScreen(null);
            }
        }
    }
}
