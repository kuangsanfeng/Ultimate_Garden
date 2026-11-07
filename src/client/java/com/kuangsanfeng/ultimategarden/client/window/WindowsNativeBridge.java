package com.kuangsanfeng.ultimategarden.client.window;


/**
 * Native Windows bridge.
 *
 * Reserved for Windows API operations.
 *
 * Current implementation does not modify OS focus.
 */
public final class WindowsNativeBridge {


    private WindowsNativeBridge() {

    }



    // =========================================================
    // Window Handle
    // =========================================================


    public static long getWindowHandle() {

        /*
         *
         * Future:
         *
         * GLFW glfwGetWin32Window()
         *
         */

        return 0L;

    }



    // =========================================================
    // OS Focus
    // =========================================================


    public static void setForegroundWindow(
            long handle
    ) {


        /*
         *
         * Future Windows API:
         *
         * User32.SetForegroundWindow()
         *
         */


    }



    // =========================================================
    // Cursor
    // =========================================================


    public static void releaseCursor() {


        /*
         *
         * Future:
         *
         * Windows ClipCursor(null)
         *
         */

    }


}