package com.kuangsanfeng.ultimategarden.client.macro.esp.style;

public final class ESPColorProvider {
    private ESPColorProvider() { }

    public static int color(ESPStyle style, double timeMs, int cycleMs, double x, double y, double z) {
        int rgb = style.rgb();
        if (style.colorMode() == ESPColorMode.RAINBOW) {
            double hue = timeMs / cycleMs + x * 0.13 + y * 0.19 + z * 0.11;
            float h = (float)((hue - Math.floor(hue)) * 6);
            int sector = (int)h;
            float f = h - sector, low = 0.15f, down = 1 - 0.85f * f, up = low + 0.85f * f;
            float r, g, b;
            switch (sector) {
                case 0 -> { r = 1; g = up; b = low; }
                case 1 -> { r = down; g = 1; b = low; }
                case 2 -> { r = low; g = 1; b = up; }
                case 3 -> { r = low; g = down; b = 1; }
                case 4 -> { r = up; g = low; b = 1; }
                default -> { r = 1; g = low; b = down; }
            }
            rgb = Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
        }
        return Math.round(style.alpha() * 255) << 24 | rgb;
    }
}
