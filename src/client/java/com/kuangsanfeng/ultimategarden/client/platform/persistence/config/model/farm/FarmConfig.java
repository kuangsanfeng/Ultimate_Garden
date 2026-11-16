package com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.farm;

import com.kuangsanfeng.ultimategarden.client.macro.farm.crop.CropType;
import java.util.Objects;

/** Persistent choices only; controllers retain numeric runtime identities. */
public final class FarmConfig {
    public enum FarmMode { FARM, PESTS }
    private String selectedCropType = "NONE";
    private FarmMode selectedFarmMode = FarmMode.FARM;
    private String selectedSimulatedCropType = "WHEAT";
    public int getSchemaVersion() { return 1; }
    public String getSelectedCropType() { return selectedCropType; }
    public void setSelectedCropType(String value) {
        if (!"NONE".equals(value) && CropType.valueOf(value).isCustom()) throw new IllegalArgumentException("Custom crops are not implemented");
        selectedCropType = Objects.requireNonNull(value);
    }
    public FarmMode getSelectedFarmMode() { return selectedFarmMode; }
    public void setSelectedFarmMode(FarmMode value) { selectedFarmMode = Objects.requireNonNull(value); }
    public String getSelectedSimulatedCropType() { return selectedSimulatedCropType; }
    public void setSelectedSimulatedCropType(String value) {
        CropType type = CropType.valueOf(value);
        if (type.isCustom() || type == CropType.TIMETESTER) throw new IllegalArgumentException("Invalid simulated crop");
        selectedSimulatedCropType = value;
    }
    public int getSelectedCropTypeId() { return "NONE".equals(selectedCropType) ? 0 : CropType.valueOf(selectedCropType).getValue(); }
    public int getSelectedFarmModeId() { return selectedFarmMode == FarmMode.FARM ? 0 : 1; }
    public int getSelectedSimulatedCropTypeId() { return CropType.valueOf(selectedSimulatedCropType).getValue(); }
    public static String cropNameFromId(int id) { return id == 0 ? "NONE" : CropType.fromValue(id).name(); }
    public static FarmMode farmModeFromId(int id) {
        if (id != 0 && id != 1) throw new IllegalArgumentException("Invalid farm mode");
        return id == 0 ? FarmMode.FARM : FarmMode.PESTS;
    }
    public void resetToDefault() { selectedCropType = "NONE"; selectedFarmMode = FarmMode.FARM; selectedSimulatedCropType = "WHEAT"; }
}
