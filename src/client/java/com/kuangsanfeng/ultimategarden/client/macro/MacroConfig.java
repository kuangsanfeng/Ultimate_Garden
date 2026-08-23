package com.kuangsanfeng.ultimategarden.client.macro;

import com.kuangsanfeng.ultimategarden.client.macro.crop.CropType;

/**
 * Macro configuration.
 *
 * Stores user settings.
 */
public final class MacroConfig {

    // =========================================================
    // Config
    // =========================================================

    private CropType cropType = CropType.CARROT_WHEAT_POTATO_NETHER_WART;

    /*
     * 0 = Farm
     * 1 = Pests
     */
    private int modType = 0;


    public MacroConfig() {
    }


    // =========================================================
    // Crop
    // =========================================================

    public CropType getCropType() {
        return cropType;
    }

    public void setCropType(CropType cropType) {

        if (cropType == null) {
            throw new IllegalArgumentException(
                    "cropType cannot be null"
            );
        }

        this.cropType = cropType;
    }


    // =========================================================
    // Mode
    // =========================================================

    public int getModType() {
        return modType;
    }

    public void setModType(int modType) {

        if (modType != 0 && modType != 1) {
            throw new IllegalArgumentException(
                    "modType must be 0 (Farm) or 1 (Pests)"
            );
        }

        this.modType = modType;
    }

    public boolean isFarmMode() {
        return modType == 0;
    }

    public boolean isPestsMode() {
        return modType == 1;
    }


    // =========================================================
    // Reset
    // =========================================================

    public void resetToDefault() {

        cropType =
                CropType.CARROT_WHEAT_POTATO_NETHER_WART;

        modType = 0;
    }


    // =========================================================
    // Save / Load
    // =========================================================

    public void load() {
    }

    public void save() {
    }
}