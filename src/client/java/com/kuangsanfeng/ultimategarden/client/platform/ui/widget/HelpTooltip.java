package com.kuangsanfeng.ultimategarden.client.platform.ui.widget;

import com.kuangsanfeng.ultimategarden.client.platform.ui.render.RoundedRect;
import com.kuangsanfeng.ultimategarden.client.platform.ui.theme.ThemePalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.*;

/** One non-interactive tooltip overlay for all help icons in a screen. */
public final class HelpTooltip {
    public static final int SIZE = 12, GAP = 4;
    private record Anchor(Object owner, String text, int x, int y, int minY, int maxY) {
        boolean contains(double px, double py) { return px >= x && px < x + SIZE && py >= Math.max(y, minY) && py < Math.min(y + SIZE, maxY); }
    }
    /** Millisecond-free, frame-rate-independent delay and reversible fade. */
    public static final class Motion {
        private static final long DELAY = 200_000_000L, FADE = 200_000_000L;
        private Object hovered, displayed;
        private long entered, last;
        private boolean initialized;
        private float alpha;
        public float alpha() { return alpha; }
        public Object displayed() { return displayed; }
        public void clear() { hovered = null; displayed = null; alpha = 0; initialized = false; }
        public void update(Object next, long now) {
            if (!initialized) { last = now; initialized = true; }
            long elapsed = Math.max(0, now - last);
            boolean ready = hovered != null && now - entered >= DELAY;
            if (ready && (displayed == null || displayed == hovered || alpha == 0)) {
                displayed = hovered;
                long visibleTime = Math.min(elapsed, Math.max(0, now - entered - DELAY));
                alpha = Math.min(1, alpha + visibleTime / (float)FADE);
            } else alpha = Math.max(0, alpha - elapsed / (float)FADE);
            last = now;
            if (next != hovered) {
                hovered = next;
                entered = next != null && next == displayed && alpha > 0 ? now - DELAY : now;
            }
        }
    }
    private final List<Anchor> anchors = new ArrayList<>();
    private final Motion motion = new Motion();
    private Anchor displayed;
    private int clipTop, clipBottom, pointerX, pointerY;
    public void clear() { anchors.clear(); motion.clear(); displayed = null; }
    public void beginFrame(int top, int bottom, int mouseX, int mouseY) {
        anchors.clear(); clipTop = top; clipBottom = bottom; pointerX = mouseX; pointerY = mouseY;
    }
    public void clip(int top, int bottom) { clipTop = top; clipBottom = bottom; }
    public boolean isIconAt(double x, double y) { return anchors.stream().anyMatch(anchor -> anchor.contains(x, y)); }
    public void iconAfterText(GuiGraphicsExtractor g, Font font, Object owner, Component title, int x, int y,
                              int textWidth, String description, ThemePalette palette) {
        iconAfterText(g, font, owner, title, x, y, textWidth, description, palette, 1f);
    }
    public void iconAfterText(GuiGraphicsExtractor g, Font font, Object owner, Component title, int x, int y,
                              int textWidth, String description, ThemePalette palette, float opacity) {
        if (description == null || description.isBlank()) return;
        var lines = font.split(title, Math.max(1, textWidth));
        if (lines.isEmpty()) return;
        int iconX = x + font.width(lines.getLast()) + GAP;
        int iconY = y + (lines.size() - 1) * 9 - 2;
        Anchor anchor = new Anchor(owner, description, iconX, iconY, clipTop, clipBottom);
        if (iconY + SIZE <= clipTop || iconY >= clipBottom) return;
        if (opacity >= 1f) anchors.add(anchor);
        boolean hovered = opacity >= 1f && anchor.contains(pointerX, pointerY);
        int background = ThemePalette.blend(palette.surface(), palette.muted(), hovered ? .25f : .12f);
        RoundedRect.fill(g, iconX, iconY, SIZE, SIZE, SIZE / 2, palette.fg(background, opacity));
        g.text(font, "?", iconX + (SIZE - font.width("?")) / 2, iconY + 2,
                palette.fg(hovered ? palette.text() : ThemePalette.blend(palette.surface(), palette.muted(), .65f), opacity), false);
    }
    public void renderOverlay(GuiGraphicsExtractor g, Font font, ThemePalette palette, int width, int height) {
        Anchor hovered = null;
        for (Anchor anchor : anchors) if (anchor.contains(pointerX, pointerY)) { hovered = anchor; break; }
        motion.update(hovered == null ? null : hovered.owner(), System.nanoTime());
        for (Anchor anchor : anchors) if (anchor.owner() == motion.displayed()) { displayed = anchor; break; }
        if (displayed == null || motion.alpha() <= 0) return;
        int wrapWidth = Math.max(1, Math.min(320, width - 32));
        var lines = font.split(Component.literal(displayed.text()), wrapWidth);
        if (lines.size() * 11 + 20 > height - 16) {
            wrapWidth = Math.max(1, width - 32); lines = font.split(Component.literal(displayed.text()), wrapWidth);
        }
        int boxWidth = lines.stream().mapToInt(font::width).max().orElse(1) + 20;
        int boxHeight = lines.size() * 11 + 18;
        float scale = Math.min(1, Math.min((width - 16f) / boxWidth, (height - 16f) / boxHeight));
        scale = Math.max(.1f, scale);
        int drawnWidth = (int)Math.ceil(boxWidth * scale), drawnHeight = (int)Math.ceil(boxHeight * scale);
        int x = displayed.x() + SIZE + 8, y = displayed.y() + SIZE + 5;
        if (x + drawnWidth > width - 8) x = displayed.x() - drawnWidth - 8;
        if (y + drawnHeight > height - 8) y = displayed.y() - drawnHeight - 5;
        x = Math.max(8, Math.min(x, width - drawnWidth - 8)); y = Math.max(8, Math.min(y, height - drawnHeight - 8));
        float alpha = motion.alpha();
        // A new stratum is above ordinary widgets and outside the page scissor.
        g.nextStratum(); g.pose().pushMatrix(); g.pose().translate(x, y); g.pose().scale(scale);
        RoundedRect.fill(g, 2, 3, boxWidth, boxHeight, 10, palette.bg(0x40000000, alpha));
        RoundedRect.outline(g, 0, 0, boxWidth, boxHeight, 10, palette.fg(palette.border(), alpha));
        RoundedRect.fill(g, 1, 1, boxWidth - 2, boxHeight - 2, 9, palette.bg(palette.surface(), alpha));
        for (int i = 0; i < lines.size(); i++) g.text(font, lines.get(i), 10, 9 + i * 11, palette.fg(palette.text(), alpha), false);
        g.pose().popMatrix();
    }
}
