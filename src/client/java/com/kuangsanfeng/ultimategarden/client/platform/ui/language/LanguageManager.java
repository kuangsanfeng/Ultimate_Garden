package com.kuangsanfeng.ultimategarden.client.platform.ui.language;
import com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageType;


import com.google.gson.*;
import com.kuangsanfeng.ultimategarden.Ultimate_Garden;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** GUI-only dictionaries; never changes Minecraft's selected language. */
public final class LanguageManager {
    public static final String PREFIX = "ui.ultimategarden.";
    private static final Map<LanguageType, Map<String, String>> DICTIONARIES = load();
    private final ConfigManager configs;
    public LanguageManager(ConfigManager configs) { this.configs = configs; }
    public LanguageType current() { return configs.getUIConfig().getLanguage(); }
    public boolean select(LanguageType type, Runnable refresh) {
        configs.getUIConfig().setLanguage(type);
        boolean saved = configs.saveUIConfig();
        refresh.run();
        return saved;
    }
    public String text(String key, Object... args) { return translate(current(), key, args); }
    public String optional(String key) { return DICTIONARIES.get(LanguageType.ENGLISH).containsKey(key) ? text(key) : ""; }
    public String keyName(String key, String fallback) {
        String translatedKey = PREFIX + "binding." + key;
        if (DICTIONARIES.get(LanguageType.ENGLISH).containsKey(translatedKey)) return text(translatedKey);
        if (key.matches("key\\.keyboard\\.keypad\\.[0-9]")) return text(PREFIX + "binding.keypad", key.substring(key.lastIndexOf('.') + 1));
        if (key.matches("key\\.mouse\\.[0-9]+")) return text(PREFIX + "binding.mouse", key.substring(key.lastIndexOf('.') + 1));
        return fallback;
    }
    public static String translate(LanguageType language, String key, Object... args) {
        String value = DICTIONARIES.get(language).getOrDefault(key, DICTIONARIES.get(LanguageType.ENGLISH).getOrDefault(key, key));
        return args.length == 0 ? value : String.format(Locale.ROOT, value, args);
    }
    /** Adapts existing read-only business status text without changing the controller. */
    public String localize(String source) {
        for (var entry : DICTIONARIES.get(LanguageType.ENGLISH).entrySet())
            if (entry.getKey().startsWith(PREFIX) && entry.getValue().equals(source)) return text(entry.getKey());
        return text(source);
    }
    public String errorKey(RuntimeException error) {
        String source = error.getMessage();
        if (source != null && source.startsWith(PREFIX)) return source;
        if (error instanceof NumberFormatException) return PREFIX + "error.number";
        if (source != null) {
            String field = source.split(":", 2)[0].trim();
            String key = PREFIX + "error.field." + field;
            if (DICTIONARIES.get(LanguageType.ENGLISH).containsKey(key)) return key;
        }
        for (var entry : DICTIONARIES.get(LanguageType.ENGLISH).entrySet())
            if (entry.getKey().startsWith(PREFIX + "error.") && entry.getValue().equals(source)) return entry.getKey();
        Ultimate_Garden.LOGGER.warn("Menu setting could not be applied", error);
        return PREFIX + "error.invalid";
    }
    public String cropName(String id, String configuredName) {
        String key = PREFIX + "crop." + id;
        return translate(LanguageType.ENGLISH, key).equals(configuredName) ? text(key) : configuredName;
    }
    private static Map<LanguageType, Map<String, String>> load() {
        Map<LanguageType, Map<String, String>> result = new EnumMap<>(LanguageType.class);
        for (LanguageType type : LanguageType.values()) {
            Map<String, String> values = new HashMap<>();
            try (InputStream stream = Objects.requireNonNull(LanguageManager.class.getResourceAsStream("/assets/ultimate_garden/lang/" + type.code() + ".json"));
                 Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(e -> values.put(e.getKey(), e.getValue().getAsString()));
            } catch (IOException | RuntimeException error) { Ultimate_Garden.LOGGER.error("Cannot load GUI language {}", type.code(), error); }
            result.put(type, Map.copyOf(values));
        }
        return Map.copyOf(result);
    }
}
