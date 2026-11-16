package org.lwjgl.glfw;

public final class GLFW {
    public static final int GLFW_KEY_W = 87, GLFW_KEY_A = 65, GLFW_KEY_S = 83, GLFW_KEY_D = 68;
    public static final int GLFW_KEY_SPACE = 32, GLFW_KEY_SLASH = 47;
    public static final int GLFW_CURSOR = 0x33001;
    public static final int GLFW_CURSOR_NORMAL = 0x34001;
    public static final int GLFW_CURSOR_DISABLED = 0x34003;
    public static int mode;
    public static void glfwSetInputMode(long window, int option, int value) { mode = value; }
}
