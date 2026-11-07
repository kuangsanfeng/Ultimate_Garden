package com.kuangsanfeng.ultimategarden.client.macro.farm;

/**
 * Runtime state of the macro.
 */
public final class FarmState {

    private boolean running;

    private boolean eventChange;
    private boolean eventSelect = true;

    public enum LineChangePhase { IDLE, MOVEMENT, YAW_PITCH }
    private LineChangePhase lineChangePhase = LineChangePhase.IDLE;
    public LineChangePhase getLineChangePhase() { return lineChangePhase; }
    public void setLineChangePhase(LineChangePhase phase) { lineChangePhase = phase; }
    private boolean commandRunning;

    private int cropType;
    private int modType;

    private int tick;
    private long totalTicks;
    public long getTotalTicks() { return totalTicks; }
    private int eventCount;

    // =========================================================
    // Running
    // =========================================================

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    // =========================================================
    // Event
    // =========================================================

    public boolean isEventChange() {
        return eventChange;
    }

    public void setEventChange(boolean eventChange) {
        this.eventChange = eventChange;
    }

    public boolean isEventSelect() {
        return eventSelect;
    }

    public void setEventSelect(boolean eventSelect) {
        this.eventSelect = eventSelect;
    }

    // =========================================================
    // Command
    // =========================================================

    public boolean isCommandRunning() {
        return commandRunning;
    }

    public void setCommandRunning(boolean commandRunning) {
        this.commandRunning = commandRunning;
        if (!commandRunning) lineChangePhase = LineChangePhase.IDLE;
    }

    // =========================================================
    // Crop
    // =========================================================

    public int getCropType() {
        return cropType;
    }

    public void setCropType(int cropType) {
        this.cropType = cropType;
    }

    // =========================================================
    // Mod
    // =========================================================

    public int getModType() {
        return modType;
    }

    public void setModType(int modType) {
        this.modType = modType;
    }

    public boolean isPestsMode() {
        return modType == 1;
    }

    public boolean isFarmMode() {
        return modType == 0;
    }

    // =========================================================
    // Tick
    // =========================================================

    public int getTick() {
        return tick;
    }

    public void setTick(int tick) {
        this.tick = tick;
    }

    public void incrementTick() {
        tick++;
        totalTicks++;
    }

    public void resetTick() {
        tick = 0;
    }

    // =========================================================
    // Event count
    // =========================================================

    public int getEventCount() {
        return eventCount;
    }

    public void setEventCount(int eventCount) {
        this.eventCount = eventCount;
    }

    public void incrementEventCount() {
        eventCount++;
    }

    public void decrementEventCount() {
        eventCount--;
    }

    // =========================================================
    // Route
    // =========================================================

    /**
     * Reset after /warp garden.
     */
    public void resetRoute() {
        lineChangePhase = LineChangePhase.IDLE;
        tick = 0;
        eventCount = 0;
        eventSelect = true;
        eventChange = false;
    }

    // =========================================================
    // Full reset
    // =========================================================

    public void reset() {
        lineChangePhase = LineChangePhase.IDLE;
        running = false;
        totalTicks = 0;

        eventChange = false;
        eventSelect = true;

        commandRunning = false;

        tick = 0;
        eventCount = 0;
    }
}