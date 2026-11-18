package com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model;

public final class GeneralConfig {
    private final InstantTransmissionConfig instantTransmission=new InstantTransmissionConfig();
    public InstantTransmissionConfig instantTransmission(){return instantTransmission;}
    public enum Hint { macroStart, macroPause, macroReset, macroInitialized, lineCompleted, timeTesterTicks,
        pestTimerStart, pestTimerReset, pestTimerDone, pestLoadoutChange, focusReleased, focusRestored,
        cropSelectionInitialization, hotkeyHelp, commandHelpErrors }
    private final java.util.EnumSet<Hint> hints = java.util.EnumSet.allOf(Hint.class);
    private final java.util.Map<String,Boolean> hintGroups = new java.util.HashMap<>();
    public boolean hintGroupEnabled(String group) { return hintGroups.getOrDefault(group,true); }
    public void setHintGroupEnabled(String group, boolean enabled) { hintGroups.put(group,enabled); }
    public boolean hintSelected(Hint hint) { return hints.contains(hint); }
    public boolean hintEnabled(Hint hint) {
        String group=hint.ordinal()<=Hint.timeTesterTicks.ordinal()?"macro":hint.ordinal()<=Hint.pestLoadoutChange.ordinal()?"pest":hint.ordinal()<=Hint.focusRestored.ordinal()?"focus":"other";
        return hintSelected(hint) && hintGroupEnabled(group);
    }
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
        if(values.has("groups")) {
            if(!values.get("groups").isJsonObject()) repaired=true;
            else for(String group:new String[]{"macro","pest","focus"}) {
                var v=values.getAsJsonObject("groups").get(group);
                if(v==null)continue;
                if(v.isJsonPrimitive()&&v.getAsJsonPrimitive().isBoolean())setHintGroupEnabled(group,v.getAsBoolean());else repaired=true;
            }
        }
        return repaired;
    }
    public com.google.gson.JsonObject hintsToJson(com.google.gson.JsonObject document) {
        var values=document.has("hint") && document.get("hint").isJsonObject() ? document.getAsJsonObject("hint").deepCopy() : new com.google.gson.JsonObject();
        for (Hint hint:Hint.values()) values.addProperty(hint.name(),hintSelected(hint));
        var groups=new com.google.gson.JsonObject();
        for(String group:new String[]{"macro","pest","focus"})groups.addProperty(group,hintGroupEnabled(group));
        values.add("groups",groups);
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
    public void resetToDefault() { instantTransmission.reset(); hintGroups.clear(); hints.addAll(java.util.EnumSet.allOf(Hint.class)); showModChatHints = true; autoPauseFarmWhenOpeningMenu = true; autoResumeFarmAfterGuiClose = false; }
}
