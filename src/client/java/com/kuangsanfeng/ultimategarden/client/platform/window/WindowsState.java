package com.kuangsanfeng.ultimategarden.client.platform.window;


/**
 * Runtime state of window interaction.
 */
public final class WindowsState {


    // Mod cursor override; native GUI cursor release is independent.
    private boolean mouseReleased;

    private boolean inputIntercepting;



    // =========================================================
    // Mouse
    // =========================================================


    public boolean isMouseReleased() {

        return mouseReleased;

    }


    public void setMouseReleased(
            boolean mouseReleased
    ) {

        this.mouseReleased = mouseReleased;

    }



    // =========================================================
    // Input Interceptor
    // =========================================================


    public boolean isInputIntercepting() {

        return inputIntercepting;

    }


    public void setInputIntercepting(
            boolean inputIntercepting
    ) {

        this.inputIntercepting = inputIntercepting;

    }

}
