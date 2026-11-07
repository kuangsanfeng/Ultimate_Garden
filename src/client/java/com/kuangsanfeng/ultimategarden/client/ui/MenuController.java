package com.kuangsanfeng.ultimategarden.client.ui;

import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import com.kuangsanfeng.ultimategarden.client.config.*;
import com.kuangsanfeng.ultimategarden.client.macro.farm.FarmController;
import com.kuangsanfeng.ultimategarden.client.macro.farm.crop.CropType;
import com.kuangsanfeng.ultimategarden.client.ui.screen.UltimateGardenScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

/** Shared entry point and configuration transactions; no UI disk access. */
public final class MenuController {
    private MenuController() { }
    public static void open() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        if (client.screen != null && !(client.screen instanceof ChatScreen)) return;
        client.setScreen(new UltimateGardenScreen(Ultimate_GardenClient.getConfigManager(), Ultimate_GardenClient.getFarmController()));
    }
    public static boolean saveFarmSelection(ConfigManager configs, FarmController farm,
                                            String crop, FarmConfig.FarmMode mode, String simulated) {
        if (!farm.canEditRouteConfiguration()) throw new IllegalStateException("ui.ultimategarden.error.pause_or_reset_first_close_the_menu_to_finish_queued_actions");
        if (!"NONE".equals(crop) && !configs.getCropConfig().getCrop(CropType.valueOf(crop)).isExecutable()) throw new IllegalArgumentException("ui.ultimategarden.error.this_crop_is_disabled_or_not_implemented");
        CropType target = CropType.valueOf(simulated);
        if (target == CropType.TIMETESTER || target.isCustom() || (crop.equals("TIMETESTER") && !configs.getCropConfig().getCrop(target).isExecutable())) throw new IllegalArgumentException("ui.ultimategarden.error.choose_an_enabled_simulated_crop_1_11");
        FarmConfig config = configs.getFarmConfig();
        config.setSelectedCropType(crop); config.setSelectedFarmMode(mode); config.setSelectedSimulatedCropType(simulated);
        farm.applySavedSelection();
        return configs.saveFarmConfig();
    }
}
