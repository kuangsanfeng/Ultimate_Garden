package com.kuangsanfeng.ultimategarden.client.platform.ui.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class RoundedRect {
    private RoundedRect() { }
    public static void fill(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color) {
        if ((color >>> 24) == 0 || width <= 0 || height <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        graphics.fill(x, y + r, x + width, y + height - r, color);
        for (int row = 0; row < r; row++) {
            double dy = r - row - .5;
            int inset = (int)Math.ceil(r - Math.sqrt(r * r - dy * dy));
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            graphics.fill(x + inset, y + height - row - 1, x + width - inset, y + height - row, color);
        }
    }
    /** A border ring, never a full opaque plate behind a translucent surface. */
    public static void outline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color) {
        if ((color >>> 24) == 0 || width < 2 || height < 2) return;
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        for (int row = 0; row < height; row++) {
            int outer = inset(row, height, r);
            if (row == 0 || row == height - 1) graphics.fill(x + outer, y + row, x + width - outer, y + row + 1, color);
            else {
                int inner = 1 + inset(row - 1, height - 2, Math.max(0, r - 1));
                graphics.fill(x + outer, y + row, x + inner, y + row + 1, color);
                graphics.fill(x + width - inner, y + row, x + width - outer, y + row + 1, color);
            }
        }
    }
    private static int inset(int row, int height, int radius) {
        int edge = Math.min(row, height - 1 - row);
        if (edge >= radius) return 0;
        double dy = radius - edge - .5;
        return (int)Math.ceil(radius - Math.sqrt(radius * radius - dy * dy));
    }
}
