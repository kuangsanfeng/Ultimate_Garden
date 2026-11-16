package com.kuangsanfeng.ultimategarden.client;
import com.kuangsanfeng.ultimategarden.client.macro.farm.FarmController;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
public final class Ultimate_GardenClient {public static ConfigManager configs;public static FarmController farm=new FarmController();public static ConfigManager getConfigManager(){return configs;}public static FarmController getFarmController(){return farm;}}
