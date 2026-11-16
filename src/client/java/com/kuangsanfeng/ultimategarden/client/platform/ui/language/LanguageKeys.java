package com.kuangsanfeng.ultimategarden.client.platform.ui.language;
import com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageManager;

public final class LanguageKeys {
    private LanguageKeys() { }
    public static String component(String name, String part) { return LanguageManager.PREFIX + "esp." + name + "." + part; }
    public static String operation(String name) { return switch (name) {
        case "PREVIOUS_LINE" -> "ui.ultimategarden.previous_line";
        case "NEXT_LINE" -> "ui.ultimategarden.next_line";
        case "SET_SPAWN" -> "ui.ultimategarden.set_spawn";
        case "WARP_GARDEN" -> "ui.ultimategarden.warp_garden";
        case "LOADOUT" -> "ui.ultimategarden.loadout";
        default -> name; }; }
    public static String hotkey(String title) { return switch (title) {
        case "Start / Pause" -> "ui.ultimategarden.start_pause";
        case "Reset" -> "ui.ultimategarden.reset";
        case "Next line" -> "ui.ultimategarden.next_line";
        case "Previous line" -> "ui.ultimategarden.previous_line";
        case "Set spawn" -> "ui.ultimategarden.set_spawn";
        case "Warp Garden" -> "ui.ultimategarden.warp_garden";
        case "Pest timer" -> "ui.ultimategarden.pest_timer";
        case "Cancel pest timer" -> "ui.ultimategarden.cancel_pest_timer";
        case "Loadout" -> "ui.ultimategarden.loadout";
        case "Select crop" -> "ui.ultimategarden.select_crop";
        case "Toggle Zombie Shootout" -> "ui.ultimategarden.shootout.hotkey";
        case "Initialize Mapping" -> "ui.ultimategarden.mapping.initialize";
        case "Toggle Mapping Recording" -> "ui.ultimategarden.mapping.toggle";
        case "Redraw Current Chunk" -> "ui.ultimategarden.mapping.redraw";
        case "Toggle HUD" -> "ui.ultimategarden.toggle_hud";
        case "Mouse focus" -> "ui.ultimategarden.mouse_focus";
        case "Open Ultimate Garden Menu" -> "ui.ultimategarden.open_ultimate_garden_menu";
        default -> title; }; }
}
