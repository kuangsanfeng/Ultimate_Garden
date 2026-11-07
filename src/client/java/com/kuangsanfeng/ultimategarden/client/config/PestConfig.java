package com.kuangsanfeng.ultimategarden.client.config;

/** Event automation preferences, never an active countdown. */
public final class PestConfig {
    private boolean autoPestLoadout = true;
    private boolean pestESPEnabled;
    public boolean isPestESPEnabled() { return pestESPEnabled; }
    public void setPestESPEnabled(boolean value) { pestESPEnabled = value; }
    private int pestLoadoutDelayTicks = 2500;
    public int getSchemaVersion() { return 1; }
    public boolean isAutoPestLoadout() { return autoPestLoadout; }
    public void setAutoPestLoadout(boolean value) { autoPestLoadout = value; }
    public int getPestLoadoutDelayTicks() { return pestLoadoutDelayTicks; }
    public void setPestLoadoutDelayTicks(int value) {
        if (value < 1) throw new IllegalArgumentException("Pest delay must be positive");
        pestLoadoutDelayTicks = value;
    }
    public void resetToDefault() { autoPestLoadout = true; pestLoadoutDelayTicks = 2500; pestESPEnabled = false; }
}
