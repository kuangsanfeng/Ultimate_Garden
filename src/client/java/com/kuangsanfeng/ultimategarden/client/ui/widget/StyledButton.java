package com.kuangsanfeng.ultimategarden.client.ui.widget;

import com.kuangsanfeng.ultimategarden.client.ui.render.RoundedRect;
import com.kuangsanfeng.ultimategarden.client.ui.theme.ThemePalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import java.util.function.Supplier;

public class StyledButton extends AbstractButton {
    protected final Supplier<ThemePalette> palette;
    private final Runnable action;
    private final boolean primary;
    private HelpTooltip help;
    private String description;
    private float hover;
    private long lastFrame = System.nanoTime();
    public StyledButton(int x, int y, int width, int height, String label, boolean primary,
                        Supplier<ThemePalette> palette, Runnable action) {
        super(x, y, width, height, Component.literal(label));
        this.palette = palette; this.action = action; this.primary = primary;
    }
    @Override public void onPress(InputWithModifiers input) { if (active) action.run(); }
    public StyledButton withHelp(HelpTooltip help, String description) {
        this.help = help; this.description = description; return this;
    }
    @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        ThemePalette p = palette.get();
        long now = System.nanoTime();
        float step = Math.min(1, (now - lastFrame) / 120_000_000f); lastFrame = now;
        hover += ((isHoveredOrFocused() && active ? 1 : 0) - hover) * step;
        int base = primary ? p.accent() : p.selected();
        int color = active ? ThemePalette.blend(base, p.text(), hover * .08f) : p.border();
        RoundedRect.fill(g, getX(), getY(), width, height, 9, p.bg(color));
        var font = Minecraft.getInstance().font;
        boolean hasHelp = help != null && description != null && !description.isBlank();
        String label = font.plainSubstrByWidth(getMessage().getString(), Math.max(10, width - (hasHelp ? 36 : 12)));
        g.text(font, label, getX() + (width - font.width(label)) / 2, getY() + (height - 9) / 2,
            p.fg(active ? primary ? p.onAccent() : p.text() : p.muted()), false);
        if (hasHelp) help.iconAfterText(g, font, this, Component.literal(label), getX() + (width - font.width(label)) / 2,
                getY() + (height - 9) / 2, Math.max(10, width - 36), description, p);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
