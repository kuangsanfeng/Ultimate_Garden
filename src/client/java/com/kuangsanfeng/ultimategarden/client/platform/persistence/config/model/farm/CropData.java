package com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.farm;

/**
 * Crop macro data.
 */
public final class CropData {

    private final int cropType;
    private final String name;

    private final int tickLimit;
    private final int lines;

    private final boolean[] movementA;
    private final boolean[] movementB;

    private final double yawA;
    private final double pitchA;

    private final double yawB;
    private final double pitchB;

    public CropData(
            int cropType,
            String name,
            int tickLimit,
            int lines,
            boolean[] movementA,
            boolean[] movementB,
            double yawA,
            double pitchA,
            double yawB,
            double pitchB
    ) {
        this.cropType = cropType;
        this.name = name;

        this.tickLimit = tickLimit;
        this.lines = lines;

        this.movementA = movementA;
        this.movementB = movementB;

        this.yawA = yawA;
        this.pitchA = pitchA;

        this.yawB = yawB;
        this.pitchB = pitchB;
    }

    // =========================================================
    // Basic data
    // =========================================================

    public int getCropType() {
        return cropType;
    }

    public String getName() {
        return name;
    }

    public int getTickLimit() {
        return tickLimit;
    }

    public int getLines() {
        return lines;
    }

    // =========================================================
    // Movement
    // =========================================================

    /**
     * Key order:
     *
     * [W, S, A, D]
     */
    public boolean[] getMovementKeys(
            boolean eventSelect
    ) {
        return eventSelect
                ? movementA.clone()
                : movementB.clone();
    }

    // =========================================================
    // Rotation
    // =========================================================

    public double getYaw(
            boolean eventSelect
    ) {
        return eventSelect
                ? yawA
                : yawB;
    }

    public double getPitch(
            boolean eventSelect
    ) {
        return eventSelect
                ? pitchA
                : pitchB;
    }

    public double getYawA() {
        return yawA;
    }

    public double getPitchA() {
        return pitchA;
    }

    public double getYawB() {
        return yawB;
    }

    public double getPitchB() {
        return pitchB;
    }
}