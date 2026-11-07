package com.kuangsanfeng.ultimategarden.client.input;


/**
 * Minecraft internal virtual input interface.
 *
 * Replaces original C++:
 *
 * KeyDown()
 * KeyUp()
 * KeyClick()
 * MouseDown()
 * MouseUp()
 */
public class VirtualInput {


    private final VirtualKeyboard keyboard;

    private final VirtualMouse mouse;
    private boolean suspended;
    private final java.util.Set<String> actionKeys = new java.util.HashSet<>();
    private boolean actionAttack;
    public record HeldActions(java.util.Set<String> keys, boolean attack) { }

    public HeldActions suspendForMenu() {
        HeldActions held = new HeldActions(java.util.Set.copyOf(actionKeys), actionAttack && mouse.isLeftDown());
        suspended = true;
        releaseAll();
        return held;
    }
    public void resumeAfterMenu(HeldActions held, boolean restoreActions) {
        suspended = false;
        if (restoreActions && held != null) {
            for (String key : held.keys()) actionKeyDown(key);
            if (held.attack()) actionMouseDown();
        }
    }
    public void actionKeyDown(String key) {
        if (!suspended) { actionKeys.add(key); keyDown(key); }
    }
    public void actionKeyUp(String key) { actionKeys.remove(key); keyUp(key); }
    public void actionMouseDown() { if (!suspended) { actionAttack = true; mouseDown(); } }
    public void actionMouseUp() { actionAttack = false; mouseUp(); }




    public VirtualInput(
            VirtualKeyboard keyboard,
            VirtualMouse mouse
    ) {

        this.keyboard = keyboard;

        this.mouse = mouse;
        keyboard.setInputAllowed(() -> !suspended);

    }



    // =========================================================
    // Keyboard
    // =========================================================


    //KeyDown()
    public void keyDown(
            String key
    ) {

        if (!suspended) keyboard.keyDown(key);

    }



    //KeyUp()
    public void keyUp(
            String key
    ) {

        actionKeys.remove(key);
        keyboard.keyUp(key);

    }



    //KeyClick()
    public void keyClick(
            String key
    ) {

        if (!suspended) keyboard.keyClick(key);

    }

    //SlotClick
    public void slot(int slot) {

        if (!suspended) keyboard.slot(slot);

    }



    //SendChar()
    public void typeCharacter(
            char character
    ) {

        if (!suspended) keyboard.typeCharacter(character);

    }



    // =========================================================
    // Mouse
    // =========================================================


    //MouseDown()
    public void mouseDown() {

        if (!suspended) mouse.mouseDown();

    }



    //MouseUp()
    public void mouseUp() {

        actionAttack = false;
        mouse.mouseUp();

    }



    public void mouseClick() {

        if (!suspended) mouse.mouseClick();

    }



    /**
     * Mouse movement.
     *
     * Current implementation does not directly modify
     * player rotation.
     *
     * Yaw/Pitch switching is handled through
     * hotbar item switching logic.
     */
    public void mouseMove(
            double deltaX,
            double deltaY
    ) {

        mouse.mouseMove(
                deltaX,
                deltaY
        );

    }



    // =========================================================
    // Stop
    // =========================================================


    //KeyStop() && KeyMouseStop()
    public void releaseAll() {

        actionKeys.clear();
        actionAttack = false;
        keyboard.releaseAll();

        mouse.releaseAll();

    }



}
