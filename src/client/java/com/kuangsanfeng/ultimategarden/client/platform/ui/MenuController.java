package com.kuangsanfeng.ultimategarden.client.platform.ui;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.farm.FarmConfig;
import com.kuangsanfeng.ultimategarden.client.platform.ui.UiRegistry;


import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;

import com.kuangsanfeng.ultimategarden.client.macro.farm.FarmController;
import com.kuangsanfeng.ultimategarden.client.macro.farm.crop.CropType;
import com.kuangsanfeng.ultimategarden.client.platform.ui.screen.UltimateGardenScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

/** Shared entry point and configuration transactions; no UI disk access. */
public final class MenuController {
    private MenuController() { }
    static { UiRegistry.menu("ultimate_garden",()->new UltimateGardenScreen(Ultimate_GardenClient.getConfigManager(),Ultimate_GardenClient.getFarmController())); }
    public static void open() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        if (client.screen != null && !(client.screen instanceof ChatScreen)) return;
        UiRegistry.openMenu("ultimate_garden");
    }
    public static boolean saveFarmSelection(ConfigManager configs,FarmController farm,String crop,FarmConfig.FarmMode mode,String simulated){
        return com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.farm.FarmSelectionBinding.saveFarmSelection(configs,farm,crop,mode,simulated);
    }
}
