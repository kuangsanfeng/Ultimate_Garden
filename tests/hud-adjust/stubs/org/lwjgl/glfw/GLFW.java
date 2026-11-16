package org.lwjgl.glfw;

public final class GLFW {
 public static final int GLFW_CURSOR=0,GLFW_CURSOR_NORMAL=1,GLFW_MOUSE_BUTTON_LEFT=0,GLFW_PRESS=1,GLFW_RELEASE=0,
 GLFW_RESIZE_ALL_CURSOR=2,GLFW_HRESIZE_CURSOR=3,GLFW_VRESIZE_CURSOR=4,GLFW_RESIZE_NWSE_CURSOR=5,GLFW_RESIZE_NESW_CURSOR=6,GLFW_ARROW_CURSOR=7;
 public static int live;
 public static void glfwSetInputMode(long w,int key,int mode){}
 public static long glfwCreateStandardCursor(int shape){live++;return live;}
 public static void glfwDestroyCursor(long c){live--;}
 public static void glfwSetCursor(long w,long c){}
}