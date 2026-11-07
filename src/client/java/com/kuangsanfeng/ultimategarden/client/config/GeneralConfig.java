package com.kuangsanfeng.ultimategarden.client.config;

public final class GeneralConfig {
    private boolean showModChatHints = true;
    private boolean autoResumeFarmAfterGuiClose;
    public boolean isAutoResumeFarmAfterGuiClose() { return autoResumeFarmAfterGuiClose; }
    public void setAutoResumeFarmAfterGuiClose(boolean value) { autoResumeFarmAfterGuiClose = value; }
    public int getSchemaVersion() { return 1; }
    public boolean isShowModChatHints() { return showModChatHints; }
    public void setShowModChatHints(boolean value) { showModChatHints = value; }
    public void resetToDefault() { showModChatHints = true; autoResumeFarmAfterGuiClose = false; }
}
