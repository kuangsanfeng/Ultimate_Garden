package com.kuangsanfeng.ultimategarden.client.macro.farm.crop;

import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.farm.CropDefinition;

/** Compatibility adapter; contains no static crop parameters. */
public final class CropDatabase {
    private CropDatabase() { }
    public static CropDefinition get(int id) {
        return Ultimate_GardenClient.getConfigManager().getCropConfig().get(id);
    }
    public static boolean exists(int id) {
        try { return get(id).isExecutable(); } catch (IllegalArgumentException e) { return false; }
    }
}
