package com.kuangsanfeng.ultimategarden.client.window;

import com.kuangsanfeng.ultimategarden.client.hint.HintManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;


/**
 * Controls Minecraft input capture.
 *
 * This does NOT change OS window focus.
 *
 * It only releases/restores Minecraft mouse grab.
 */
public final class WindowsFocusController {


    private final Minecraft client;

    private final WindowsState state;

    private final Runnable restoreInputState;



    public WindowsFocusController(
            Minecraft client,
            WindowsState state,
            Runnable restoreInputState
    ) {

        this.client = client;

        this.state = state;

        this.restoreInputState = restoreInputState;

    }



    // =========================================================
    // Release
    // =========================================================

    public void releaseFocusWithHint(){

        HintManager.printHint("FocusRelease");

        releaseFocus();
    }
    public void releaseFocus() {


        if (state.isMouseReleased()) {

            return;

        }


        //client.mouseHandler.releaseMouse();
        long windowHandle = client.getWindow().handle();
        GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);

        state.setMouseReleased(true);

    }



    // =========================================================
    // Restore
    // =========================================================

    public void restoreFocusWithHint(){

        HintManager.printHint("FocusRestore");

        restoreFocus();
    }

    public void restoreFocus() {


        if (!state.isMouseReleased()) {

            return;

        }


        //client.mouseHandler.grabMouse();
        long windowHandle = client.getWindow().handle();
        GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);

        state.setMouseReleased(false);

        restoreInputState.run();
    }



    // =========================================================
    // Toggle
    // =========================================================

    public void toggle() {


        if (state.isMouseReleased()) {

            restoreFocus();

        }
        else {

            releaseFocus();

        }

    }



    /** Restore native mouse capture without restoring macro input. */
    public boolean restoreGameMouseAfterMenu() {
        if (client.screen != null || client.player == null || client.level == null) {
            return false;
        }
        state.setMouseReleased(false);
        // A native grab alone may return early after a direct GLFW cursor release.
        client.mouseHandler.releaseMouse();
        client.mouseHandler.grabMouse();
        return client.mouseHandler.isMouseGrabbed();
    }

    /** Reapply the recorded cursor mode after a vanilla screen transition. */
    public void reapplyCursorMode() {
        GLFW.glfwSetInputMode(client.getWindow().handle(), GLFW.GLFW_CURSOR,
                state.isMouseReleased() ? GLFW.GLFW_CURSOR_NORMAL : GLFW.GLFW_CURSOR_DISABLED);
    }

    public boolean isReleased() {

        return state.isMouseReleased();

    }

}
