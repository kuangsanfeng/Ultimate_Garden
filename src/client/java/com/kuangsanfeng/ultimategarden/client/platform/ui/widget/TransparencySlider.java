package com.kuangsanfeng.ultimategarden.client.platform.ui.widget;

import com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageManager;
import com.kuangsanfeng.ultimategarden.client.platform.ui.render.RoundedRect;
import com.kuangsanfeng.ultimategarden.client.platform.ui.theme.ThemePalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import java.util.function.*;

/** Integer percentages; native mouse capture and keyboard focus remain intact. */
public final class TransparencySlider extends AbstractSliderButton {
    private final Supplier<ThemePalette> palette;
    private final LanguageManager language;
    private final IntConsumer changed;
    public TransparencySlider(int x, int y, int width, int initial, Supplier<ThemePalette> palette,
                              LanguageManager language, IntConsumer changed) {
        super(x, y, width, 34, Component.empty(), initial / 100d);
        this.palette = palette; this.language = language; this.changed = changed; updateMessage();
    }
    public static int percentage(double position) { return (int)Math.round(Math.max(0, Math.min(1, position)) * 100); }
    @Override protected void setValue(double position) { super.setValue(percentage(position) / 100d); }
    @Override protected void applyValue() { changed.accept(percentage(value)); }
    @Override protected void updateMessage() { setMessage(Component.literal(language.text("ui.ultimategarden.transparency.value", percentage(value)))); }
    @Override public boolean keyPressed(KeyEvent event) {
        if (canChangeValue && (event.isLeft() || event.isRight())) {
            setValue(value + (event.isLeft() ? -0.01 : 0.01)); return true;
        }
        return super.keyPressed(event);
    }
    @Override public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        var p = palette.get();
        RoundedRect.fill(g, getX(), getY(), width, height, 10, p.bg(p.surface()));
        var font = Minecraft.getInstance().font;
        g.text(font, getMessage(), getX() + (width - font.width(getMessage())) / 2, getY() + 4, p.fg(p.text()), false);
        int knob = getX() + 4 + (int)Math.round(value * (width - 8));
        RoundedRect.fill(g, getX() + 4, getY() + 24, width - 8, 3, 1, p.fg(p.border()));
        RoundedRect.fill(g, getX() + 4, getY() + 24, knob - getX() - 4, 3, 1, p.fg(p.accent()));
        RoundedRect.fill(g, knob - 4, getY() + 20, 8, 11, 4, p.fg(isHoveredOrFocused() ? p.accent() : p.text()));
    }
}
