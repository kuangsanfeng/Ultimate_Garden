package com.kuangsanfeng.ultimategarden.client.macro.farm.action;

import com.kuangsanfeng.ultimategarden.client.runtime.scheduler.InputScheduler;
import com.kuangsanfeng.ultimategarden.client.platform.input.virtual.VirtualInput;

/**
 * Minecraft command input.
 *
 * Replaces original C++:
 *
 * SendChar()
 * SendText()
 * Tap()
 * Return()
 * WarpGarden()
 * SpawnPoint()
 * Loadout()
 */
public class FarmCommandInput {

    private final VirtualInput input;
    private final InputScheduler scheduler;

    private String currentText = null;
    private int currentIndex = 0;
    private boolean commandRunning = false;
    private boolean returnAfterText = false;

    // =========================================================
    // Command
    // =========================================================

    public FarmCommandInput(
            VirtualInput input,
            InputScheduler scheduler
    ) {
        this.input = input;
        this.scheduler = scheduler;
    }

    public boolean isCommandRunning() {
        return commandRunning;
    }

    public void tick() {

        if (!commandRunning) {
            return;
        }

        if (scheduler.tick()) {
            return;
        }

        if (currentText == null || currentIndex >= currentText.length()) {

            if (returnAfterText) {
                Return();
            }

            finishCommand();
            return;
        }

        char c = currentText.charAt(currentIndex);

        SendChar(c);

        currentIndex++;

        scheduler.delayTicks(1);
    }

    // =========================================================
    // Start Command
    // =========================================================

    private boolean startText(
            String text,
            boolean returnAfterText
    ) {

        if (commandRunning) {
            return false;
        }

        currentText = text;
        currentIndex = 0;
        this.returnAfterText = returnAfterText;
        commandRunning = true;

        return true;
    }

    private void finishCommand() {

        commandRunning = false;
        currentText = null;
        currentIndex = 0;
        returnAfterText = false;
    }

    // =========================================================
    // SendChar
    // =========================================================

    //SendChar()
    public void SendChar(char c) {
        input.typeCharacter(c);
    }

    // =========================================================
    // SendText
    // =========================================================

    //SendText()
    public boolean SendText(String text) {
        return startText(text, false);
    }

    // =========================================================
    // Tap
    // =========================================================

    //Tap()
    public void Tap() {
        input.keyClick("TAB");
    }

    // =========================================================
    // Return
    // =========================================================

    //Return()
    public void Return() {
        input.keyClick("ENTER");
    }

    // =========================================================
    // WarpGarden
    // =========================================================

    //WarpGarden()
    public boolean WarpGarden() {
        return startText("/warp garden", true);
    }

    // =========================================================
    // SpawnPoint
    // =========================================================

    //SpawnPoint()
    public boolean SpawnPoint() {
        return startText("/spawnpoint", true);
    }

    // =========================================================
    // Loadout
    // =========================================================

    //Loadout()
    public boolean Loadout() {
        return startText("/loadout", true);
    }

    // =========================================================
    // Cancel
    // =========================================================

    public void cancel() {

        commandRunning = false;
        currentText = null;
        currentIndex = 0;
        returnAfterText = false;
    }
}