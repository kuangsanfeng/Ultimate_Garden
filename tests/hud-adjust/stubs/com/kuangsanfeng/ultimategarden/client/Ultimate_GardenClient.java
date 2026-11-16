package com.kuangsanfeng.ultimategarden.client;

import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
public final class Ultimate_GardenClient {
 public static ConfigManager configs;
 public static final Focus focus=new Focus();
 public static ConfigManager getConfigManager(){return configs;}
 public static Focus getWindowsFocusController(){return focus;}
 public static final class Focus { public int reapplied; public void reapplyCursorMode(){reapplied++;} }
}