package com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model;

public final class GeneralConfig {
    private final InstantTransmissionConfig instantTransmission=new InstantTransmissionConfig();
    public InstantTransmissionConfig instantTransmission(){return instantTransmission;}
    public enum Hint { macroStart, macroPause, macroReset, macroInitialized, lineCompleted, timeTesterTicks,
        pestTimerStart, pestTimerReset, pestTimerDone, pestLoadoutChange, focusReleased, focusRestored,
        cropSelectionInitialization, hotkeyHelp, commandHelpErrors }
    private final java.util.EnumSet<Hint> hints = java.util.EnumSet.allOf(Hint.class);
    public boolean hintEnabled(Hint hint) { return hints.contains(hint); }
    public void setHintEnabled(Hint hint, boolean enabled) { if (enabled) hints.add(hint); else hints.remove(hint); }
    public boolean loadHints(com.google.gson.JsonObject document) {
        if (!document.has("hint")) return false;
        if (!document.get("hint").isJsonObject()) return true;
        boolean repaired=false;
        var values=document.getAsJsonObject("hint");
        for (Hint hint:Hint.values()) if (values.has(hint.name())) {
            var value=values.get(hint.name());
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) setHintEnabled(hint,value.getAsBoolean());
            else repaired=true;
        }
        return repaired;
    }
    public com.google.gson.JsonObject hintsToJson(com.google.gson.JsonObject document) {
        var values=document.has("hint") && document.get("hint").isJsonObject() ? document.getAsJsonObject("hint").deepCopy() : new com.google.gson.JsonObject();
        for (Hint hint:Hint.values()) values.addProperty(hint.name(),hintEnabled(hint));
        return values;
    }

    private boolean showModChatHints = true;
    private boolean autoPauseFarmWhenOpeningMenu = true;
    private boolean autoResumeFarmAfterGuiClose;
    public boolean isAutoPauseFarmWhenOpeningMenu() { return autoPauseFarmWhenOpeningMenu; }
    public void setAutoPauseFarmWhenOpeningMenu(boolean value) { autoPauseFarmWhenOpeningMenu = value; }
    public boolean isAutoResumeFarmAfterGuiClose() { return autoResumeFarmAfterGuiClose; }
    public void setAutoResumeFarmAfterGuiClose(boolean value) { autoResumeFarmAfterGuiClose = value; }
    public int getSchemaVersion() { return 1; }
    public boolean isShowModChatHints() { return showModChatHints; }
    public void setShowModChatHints(boolean value) { showModChatHints = value; }
    public void resetToDefault() { instantTransmission.reset(); hints.addAll(java.util.EnumSet.allOf(Hint.class)); showModChatHints = true; autoPauseFarmWhenOpeningMenu = true; autoResumeFarmAfterGuiClose = false; }
}
