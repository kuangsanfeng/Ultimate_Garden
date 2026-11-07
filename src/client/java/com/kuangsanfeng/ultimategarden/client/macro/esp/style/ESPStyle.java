package com.kuangsanfeng.ultimategarden.client.macro.esp.style;

/** Immutable drawing parameters captured during extraction. */
public record ESPStyle(boolean enabled, ESPColorMode colorMode, int rgb, float alpha, float lineWidth) { }
