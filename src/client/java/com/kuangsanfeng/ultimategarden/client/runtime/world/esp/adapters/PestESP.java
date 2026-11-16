package com.kuangsanfeng.ultimategarden.client.runtime.world.esp.adapters;

import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.farm.PestConfig;
import com.kuangsanfeng.ultimategarden.client.runtime.world.esp.ESPTarget;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.minecraft.world.entity.EntityType;
import java.util.function.Consumer;

/** Reads only entities already synchronized into the current client level. */
public final class PestESP {
    private final PestConfig config;
    public PestESP(PestConfig config) { this.config = config; }
    public void collect(LevelExtractionContext context, Consumer<ESPTarget> targets) {
        if (!config.isPestESPEnabled()) return;
        float delta = context.deltaTracker().getGameTimeDeltaPartialTick(false);
        for (var entity : context.level().entitiesForRendering()) {
            if ((entity.getType() != EntityType.SILVERFISH && entity.getType() != EntityType.BAT) || !entity.isAlive() || entity.isRemoved()) continue;
            var offset = entity.getPosition(delta).subtract(entity.position());
            targets.accept(new ESPTarget(com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.ESPConfig.SILVERFISH, entity.getBoundingBox().move(offset)));
        }
    }
}
