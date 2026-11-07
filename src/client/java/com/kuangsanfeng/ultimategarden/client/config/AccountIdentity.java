package com.kuangsanfeng.ultimategarden.client.config;

import java.util.UUID;

/** Public session identity only; never stores authentication material. */
public record AccountIdentity(UUID uuid, String name) {
    public AccountIdentity {
        if (uuid == null || uuid.equals(new UUID(0,0)) || name == null || !name.matches("[A-Za-z0-9_]{1,16}"))
            throw new IllegalArgumentException("Invalid Minecraft account identity");
        if (name.matches("(?i)CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) throw new IllegalArgumentException("Reserved account directory name");
    }
}
