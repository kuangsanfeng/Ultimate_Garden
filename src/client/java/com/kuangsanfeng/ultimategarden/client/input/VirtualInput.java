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



    public VirtualInput(
            VirtualKeyboard keyboard,
            VirtualMouse mouse
    ) {

        this.keyboard = keyboard;

        this.mouse = mouse;

    }



    // =========================================================
    // Keyboard
    // =========================================================


    //KeyDown()
    public void keyDown(
            String key
    ) {

        keyboard.keyDown(key);

    }



    //KeyUp()
    public void keyUp(
            String key
    ) {

        keyboard.keyUp(key);

    }



    //KeyClick()
    public void keyClick(
            String key
    ) {

        keyboard.keyClick(key);

    }

    //SlotClick
    public void slot(int slot) {

        keyboard.slot(slot);

    }



    //SendChar()
    public void typeCharacter(
            char character
    ) {

        keyboard.typeCharacter(character);

    }



    // =========================================================
    // Mouse
    // =========================================================


    //MouseDown()
    public void mouseDown() {

        mouse.mouseDown();

    }



    //MouseUp()
    public void mouseUp() {

        mouse.mouseUp();

    }



    public void mouseClick() {

        mouse.mouseClick();

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

        keyboard.releaseAll();

        mouse.releaseAll();

    }



}