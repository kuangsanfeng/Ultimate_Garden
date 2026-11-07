package com.kuangsanfeng.ultimategarden.client.ui.theme;

import com.kuangsanfeng.ultimategarden.client.config.UIConfig;

/** Teal-seeded Material-inspired surfaces. SYSTEM deliberately falls back to light. */
public record ThemePalette(int background, int surface, int sidebar, int text, int muted,
                           int border, int accent, int onAccent, int selected, float backgroundAlpha, float foregroundAlpha) {
    public int bg(int color) { return multiplyAlpha(color, backgroundAlpha); }
    public int fg(int color) { return multiplyAlpha(color, foregroundAlpha); }
    public int bg(int color, float animationAlpha) { return multiplyAlpha(color, backgroundAlpha * animationAlpha); }
    public int fg(int color, float animationAlpha) { return multiplyAlpha(color, foregroundAlpha * animationAlpha); }
    public static int multiplyAlpha(int color, float factor) {
        return Math.round((color >>> 24) * factor) << 24 | color & 0x00ffffff;
    }
    public static ThemePalette from(UIConfig config) {
        boolean dark = config.getUiThemeMode() == UIConfig.Theme.DARK;
        int accent = 0xff000000 | Integer.parseInt(config.getAccentColor().substring(1), 16);
        double lightness = (((accent >> 16) & 255) * .299 + ((accent >> 8) & 255) * .587 + (accent & 255) * .114);
        return new ThemePalette(dark ? 0xff141d1b : 0xfff3f8f6, dark ? 0xff202c29 : 0xffffffff,
            dark ? 0xff192521 : 0xffe9f2ee, dark ? 0xffe3efea : 0xff172c26,
            dark ? 0xffa0b6ac : 0xff60796d, dark ? 0xff344a40 : 0xffdce8e1,
            accent, lightness > 160 ? 0xff10251b : 0xffffffff,
            blend(dark ? 0xff202c29 : 0xffffffff, accent, dark ? .28f : .13f),
            1 - config.getMenuTransparency() / 100f, 1 - config.getMenuTransparency() / 200f);
    }
    public static int blend(int a, int b, float amount) {
        int r = (int)(((a >> 16) & 255) * (1 - amount) + ((b >> 16) & 255) * amount);
        int g = (int)(((a >> 8) & 255) * (1 - amount) + ((b >> 8) & 255) * amount);
        int blue = (int)((a & 255) * (1 - amount) + (b & 255) * amount);
        int alpha = Math.round((a >>> 24) * (1 - amount) + (b >>> 24) * amount);
        return alpha << 24 | r << 16 | g << 8 | blue;
    }
}
