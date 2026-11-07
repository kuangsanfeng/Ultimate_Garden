package com.kuangsanfeng.ultimategarden.client.ui.widget;

import com.kuangsanfeng.ultimategarden.client.ui.theme.ThemePalette;
import java.util.function.Supplier;

/** Opens an in-screen choice overlay, retaining the same menu lifecycle. */
public final class DropdownSelector extends StyledButton {
    public DropdownSelector(int x, int y, int width, String value, Supplier<ThemePalette> palette, Runnable openChoices) {
        super(x, y, width, 24, value + "  v", false, palette, openChoices);
    }
}
