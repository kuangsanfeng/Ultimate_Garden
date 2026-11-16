package com.kuangsanfeng.ultimategarden.client.runtime.world.esp.style;
import com.kuangsanfeng.ultimategarden.client.runtime.world.esp.style.ESPColorMode;


/** Immutable drawing parameters captured during extraction. */
public record ESPStyle(boolean enabled, ESPColorMode colorMode, int rgb, float alpha, float lineWidth) { }
