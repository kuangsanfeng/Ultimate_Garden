package com.kuangsanfeng.ultimategarden.client.interaction.windows;

import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import com.kuangsanfeng.ultimategarden.client.macro.hint.MacroHint;
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



    public WindowsFocusController(
            Minecraft client,
            WindowsState state
    ) {

        this.client = client;

        this.state = state;

    }



    // =========================================================
    // Release
    // =========================================================

    public void releaseFocusWithHint(){

        MacroHint.printHint("FocusRelease");

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

        MacroHint.printHint("FocusRestore");

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

        if (Ultimate_GardenClient.getMacroController() != null) {

            Ultimate_GardenClient
                    .getMacroController()
                    .restoreInputState();

        }
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



    public boolean isReleased() {

        return state.isMouseReleased();

    }

}