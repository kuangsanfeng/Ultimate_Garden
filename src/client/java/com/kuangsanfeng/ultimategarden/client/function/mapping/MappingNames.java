package com.kuangsanfeng.ultimategarden.client.function.mapping;

import java.text.Normalizer;
import java.util.Locale;

public final class MappingNames {
    private MappingNames() {}
    public static String key(String name) {
        if (name == null || name.isBlank() || !name.equals(name.strip()) || name.length()>100
                || name.matches(".*[\\\\/:*?\"<>|\\p{Cntrl}].*") || name.endsWith(".") || name.equals(".") || name.equals(".."))
            throw new IllegalArgumentException("INVALID_WORLD_NAME");
        String key = Normalizer.normalize(name,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+","-").replaceAll("^-|-$", "");
        if (key.isBlank() || key.matches("con|prn|aux|nul|com[0-9]|lpt[0-9]")) throw new IllegalArgumentException("INVALID_WORLD_NAME");
        return key;
    }
}
