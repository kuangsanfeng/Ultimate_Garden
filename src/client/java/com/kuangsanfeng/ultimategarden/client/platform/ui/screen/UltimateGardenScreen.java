package com.kuangsanfeng.ultimategarden.client.platform.ui.screen;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.macro.farm.FarmController;
/** Stable native-screen identity used by the existing GUI/input lifecycle hooks. */
public final class UltimateGardenScreen extends com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.menu.GardenMenuAdapter {
 public UltimateGardenScreen(ConfigManager configs,FarmController farm){super(configs,farm);}
}
