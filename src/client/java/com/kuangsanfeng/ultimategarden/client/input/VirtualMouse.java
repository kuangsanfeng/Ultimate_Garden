package com.kuangsanfeng.ultimategarden.client.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;


// =========================================================
// Virtual Mouse
// =========================================================

public final class VirtualMouse {


    private final Minecraft minecraft;

    private final com.kuangsanfeng.ultimategarden.client.macro.command.CommandScheduler scheduler;

    private boolean leftDown;



    public VirtualMouse(
            Minecraft minecraft,
            com.kuangsanfeng.ultimategarden.client.macro.command.CommandScheduler scheduler
    ) {

        this.minecraft = minecraft;

            this.scheduler = scheduler;

    }



    // =========================================================
    // MouseDown
    // =========================================================


    // MouseDown()
    public void mouseDown() {

        leftDown = true;

        minecraft.options.keyAttack.setDown(true);

    }



    // =========================================================
    // MouseUp
    // =========================================================


    // MouseUp()
    public void mouseUp() {

        if (!leftDown) {
            return;
        }


        leftDown = false;


        minecraft.options.keyAttack.setDown(false);

    }



    // =========================================================
    // MouseClick
    // =========================================================


    // MouseClick()
    public void mouseClick() {

        mouseDown();

        scheduler.schedule(
                100,
                this::mouseUp
        );

    }



    // =========================================================
    // MouseMove
    // =========================================================


    public void mouseMove(
            double deltaX,
            double deltaY
    ) {


        MouseHandler mouse =
                minecraft.mouseHandler;


        /*
         * Placeholder.
         *
         * Current macro only requires
         * fixed Yaw/Pitch.
         *
         * Rotation control should be
         * implemented through Player rotation,
         * not raw mouse events.
         */

    }



    // =========================================================
    // Stop
    // =========================================================


    // MouseStop()
    public void releaseAll() {

        leftDown = false;


        minecraft.options.keyAttack.setDown(false);

    }


    // =========================================================
    // Restore
    // =========================================================

    public void restoreState() {

        leftDown = false;

        minecraft.options.keyAttack.setDown(false);

    }


    public boolean isLeftDown() {

        return leftDown;

    }

}