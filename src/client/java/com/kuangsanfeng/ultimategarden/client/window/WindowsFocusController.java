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


        state.setMouseReleased(true);
        reapplyCursorMode();

    }



    // =========================================================
    // Restore
    // =========================================================

    public void restoreFocusWithHint(){

        HintManager.printHint("FocusRestore");

        restoreFocus();
    }

    public void restoreFocus() {


        boolean wasReleased = state.isMouseReleased();
        state.setMouseReleased(false);
        reapplyCursorMode();
        if (wasReleased) restoreInputState.run();

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



    /** End the mod override; an open GUI still owns the free cursor. */
    public void useGameFocusForPlayerGui() {
        if (client.screen == null) return;
        state.setMouseReleased(false);
        reapplyCursorMode();
    }

    /** Restore native mouse capture without restoring macro input. */
    public boolean restoreGameMouseAfterMenu() {
        if (client.screen != null || client.player == null || client.level == null) {
            return false;
        }
        state.setMouseReleased(false);
        reapplyCursorMode();
        return client.mouseHandler.isMouseGrabbed();
    }

    /** Reapply the recorded cursor mode after a vanilla screen transition. */
    public void reapplyCursorMode() {
        if (com.kuangsanfeng.ultimategarden.client.ui.hud.HudAdjustmentController.isActive()) {
            com.kuangsanfeng.ultimategarden.client.ui.hud.HudAdjustmentController.freeCursor(); return;
        }
        if (client.screen != null || client.player == null || client.level == null) {
            client.mouseHandler.releaseMouse();
            GLFW.glfwSetInputMode(client.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        } else if (state.isMouseReleased()) {
            // Vanilla continueAttack needs the logical grab; only release the OS cursor.
            GLFW.glfwSetInputMode(client.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        } else {
            // grabMouse sets its native flag before its nested setScreen(null).
            if (!client.mouseHandler.isMouseGrabbed()) client.mouseHandler.grabMouse();
            if (client.mouseHandler.isMouseGrabbed()) {
                GLFW.glfwSetInputMode(client.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
            }
        }
    }

    public boolean isReleased() {

        return state.isMouseReleased();

    }

}
