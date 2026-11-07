package com.kuangsanfeng.ultimategarden.client.config;

import java.util.Objects;
import com.kuangsanfeng.ultimategarden.client.ui.language.LanguageType;

public final class UIConfig {
    public enum Theme { LIGHT, DARK, SYSTEM }
    private Theme uiThemeMode = Theme.SYSTEM;
    private String accentColor = "#1B978C";
    private LanguageType language = LanguageType.ENGLISH;
    private int menuTransparency;
    public int getMenuTransparency() { return menuTransparency; }
    public void setMenuTransparency(int value) {
        if (value < 0 || value > 100) throw new IllegalArgumentException("Menu transparency must be 0..100");
        menuTransparency = value;
    }
    public LanguageType getLanguage() { return language; }
    public void setLanguage(LanguageType language) { this.language = Objects.requireNonNull(language); }
    public int getSchemaVersion() { return 1; }
    public Theme getUiThemeMode() { return uiThemeMode; }
    public void setUiThemeMode(Theme uiThemeMode) { this.uiThemeMode = Objects.requireNonNull(uiThemeMode); }
    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String color) {
        if (color == null || !color.matches("#[0-9a-fA-F]{6}"))
            throw new IllegalArgumentException("Invalid accent color");
        accentColor = color;
    }
    public void resetToDefault() { uiThemeMode = Theme.SYSTEM; accentColor = "#1B978C"; language = LanguageType.ENGLISH; menuTransparency = 0; }
}
