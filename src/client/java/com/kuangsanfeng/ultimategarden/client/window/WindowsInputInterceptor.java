package com.kuangsanfeng.ultimategarden.client.window;


/**
 * Intercepts physical player input while macro is running.
 *
 * VirtualInput is not affected.
 */
public final class WindowsInputInterceptor {


    private final WindowsState state;
    private java.util.function.BooleanSupplier externalOwner = () -> false;
    public void setExternalOwner(java.util.function.BooleanSupplier owner) { externalOwner=java.util.Objects.requireNonNull(owner); }
    private java.util.function.BooleanSupplier externalMove=()->externalOwner.getAsBoolean(), externalRight=()->externalOwner.getAsBoolean();
    public void setExternalMouseOwners(java.util.function.BooleanSupplier move,java.util.function.BooleanSupplier right){externalMove=java.util.Objects.requireNonNull(move);externalRight=java.util.Objects.requireNonNull(right);}
    // A real right button held before enabling automation must still be able to release.
    public boolean blockMouseButton(int button,int action){return button==1&&action==0&&!state.isInputIntercepting()?false:blockMouseButton(button);}
    public boolean blockMouseButton(int button){return button==1?(state.isInputIntercepting()||externalRight.getAsBoolean())&&!uiInputAllowed.getAsBoolean():blockMouseButton();}
    private java.util.function.BooleanSupplier uiInputAllowed = () -> false;
    public void setUiInputAllowed(java.util.function.BooleanSupplier allowed) { uiInputAllowed = java.util.Objects.requireNonNull(allowed); }



    public WindowsInputInterceptor(
            WindowsState state
    ) {

        this.state = state;

    }



    // =========================================================
    // Control
    // =========================================================

    public void enable() {

        state.setInputIntercepting(true);

    }


    public void disable() {

        state.setInputIntercepting(false);

    }



    public boolean shouldBlockInput() {

        return (state.isInputIntercepting() || externalOwner.getAsBoolean()) && !uiInputAllowed.getAsBoolean();

    }



    // =========================================================
    // Keyboard
    // =========================================================

    public boolean blockKeyboard(
            int key
    ) {

        if (!shouldBlockInput()) {

            return false;

        }


        return switch (key) {

            case org.lwjgl.glfw.GLFW.GLFW_KEY_W,
                 org.lwjgl.glfw.GLFW.GLFW_KEY_A,
                 org.lwjgl.glfw.GLFW.GLFW_KEY_S,
                 org.lwjgl.glfw.GLFW.GLFW_KEY_D,
                 //org.lwjgl.glfw.GLFW.GLFW_KEY_E,
                 org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE,
                 //org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,
                 //org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,
                 org.lwjgl.glfw.GLFW.GLFW_KEY_SLASH
                    -> true;


            default -> false;

        };

    }



    // =========================================================
    // Mouse Button
    // =========================================================

    public boolean blockMouseButton() {

        return shouldBlockInput();

    }



    // =========================================================
    // Mouse Scroll
    // =========================================================

    public boolean blockMouseScroll() {

        return shouldBlockInput();

    }



    // =========================================================
    // Mouse Movement
    // =========================================================

    public boolean blockMouseMove() {

        return (state.isInputIntercepting()||externalMove.getAsBoolean())&&!uiInputAllowed.getAsBoolean();

    }

}