package com.kuangsanfeng.ultimategarden.client.runtime.world.esp.style;
import com.kuangsanfeng.ultimategarden.client.runtime.world.esp.style.ESPStyle;


/** Immutable per-target component styles for one render frame. */
public record ESPTargetStyle(ESPStyle box, ESPStyle fill, ESPStyle tracer) {
    public boolean hasVisibleComponent() {
        return box.enabled() && box.alpha() > 0 || fill.enabled() && fill.alpha() > 0 || tracer.enabled() && tracer.alpha() > 0;
    }
}
