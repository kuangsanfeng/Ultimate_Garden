package com.kuangsanfeng.ultimategarden.client.platform.ui.widget;
import com.kuangsanfeng.ultimategarden.client.platform.ui.widget.StyledButton;


import com.kuangsanfeng.ultimategarden.client.platform.ui.render.RoundedRect;
import com.kuangsanfeng.ultimategarden.client.platform.ui.theme.ThemePalette;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class ToggleSwitch extends StyledButton {
    private final BooleanSupplier value;
    public ToggleSwitch(int x, int y, String label, Supplier<ThemePalette> palette,
                        BooleanSupplier value, Runnable action) {
        super(x, y, 44, 24, label, false, palette, action);
        this.value = value;
    }
    @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        var p = palette.get();
        boolean on = value.getAsBoolean();
        RoundedRect.fill(g, getX(), getY() + 2, 42, 20, 10, p.fg(on ? p.accent() : p.border(), active ? 1f : .4f));
        RoundedRect.fill(g, getX() + (on ? 23 : 3), getY() + 5, 14, 14, 7, p.fg(on ? p.onAccent() : p.muted(), active ? 1f : .4f));
    }
}
