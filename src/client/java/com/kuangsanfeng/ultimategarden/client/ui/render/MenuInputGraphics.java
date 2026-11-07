package com.kuangsanfeng.ultimategarden.client.ui.render;

import com.kuangsanfeng.ultimategarden.client.ui.theme.ThemePalette;
import com.mojang.blaze3d.platform.cursor.CursorType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.util.FormattedCharSequence;

/** Adapts only native text-field drawing, leaving editing and focus behavior untouched. */
public final class MenuInputGraphics extends GuiGraphicsExtractor {
    private final GuiGraphicsExtractor delegate;
    private final ThemePalette palette;
    public MenuInputGraphics(GuiGraphicsExtractor delegate, ThemePalette palette, int mouseX, int mouseY,
                             int left, int top, int right, int bottom) {
        super(Minecraft.getInstance(), delegate.guiRenderState, mouseX, mouseY);
        this.delegate = delegate; this.palette = palette;
        pose().set(delegate.pose()); enableScissor(left, top, right, bottom);
    }
    @Override public void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        delegate.text(font, text, x, y, palette.fg(color), shadow);
    }
    @Override public void fill(int x0, int y0, int x1, int y1, int color) { delegate.fill(x0, y0, x1, y1, palette.fg(color)); }
    @Override public void textHighlight(int x0, int y0, int x1, int y1, boolean invert) {
        if (palette.foregroundAlpha() == 1) delegate.textHighlight(x0, y0, x1, y1, invert);
        // Native inversion is opaque; a normal alpha-blended selection remains readable.
        else delegate.fill(x0, y0, x1, y1, palette.fg(0xff0000ff));
    }
    @Override public void requestCursor(CursorType cursor) { delegate.requestCursor(cursor); }
    @Override public void setPreeditOverlay(Renderable overlay) { delegate.setPreeditOverlay(overlay); }
}
