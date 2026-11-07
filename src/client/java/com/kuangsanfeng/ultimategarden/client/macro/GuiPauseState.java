package com.kuangsanfeng.ultimategarden.client.macro;

/** Transient menu intent, independent of route progress and persisted settings. */
public final class GuiPauseState {
    public enum Intent { NONE, START, PAUSE, RESET }
    public enum Source { ULTIMATE_GARDEN_MENU, PLAYER_GUI }
    private boolean active;
    private boolean open;
    private boolean previouslyRunning;
    private boolean resume;
    private boolean failed;
    private boolean menuAutoResumeEligible;
    private Intent intent = Intent.NONE;

    public void open(boolean running) {
        open(running, Source.ULTIMATE_GARDEN_MENU);
    }
    public void open(boolean running, Source source) {
        // Eligibility belongs to this screen, not an earlier GUI in the session.
        menuAutoResumeEligible = source == Source.ULTIMATE_GARDEN_MENU && running;
        if (!active) {
            previouslyRunning = running;
            failed = false;
            intent = Intent.NONE;
            resume = false;
        }
        active = true;
        open = true;
    }
    public void close(boolean autoResume) {
        open = false;
        resume = intent == Intent.START || (intent == Intent.NONE && previouslyRunning && menuAutoResumeEligible && autoResume);
    }
    public void start() { intent = Intent.START; resume = true; failed = false; }
    public void pause(boolean reset) {
        intent = reset ? Intent.RESET : Intent.PAUSE;
        previouslyRunning = false;
        resume = false;
    }
    public void fail() { failed = true; resume = false; }
    public boolean finish() {
        if (!active || open) return false;
        boolean result = resume && !failed;
        active = false;
        resume = false;
        return result;
    }
    public void abort() { active = false; open = false; resume = false; previouslyRunning = false; menuAutoResumeEligible = false; intent = Intent.NONE; }
    public boolean isActive() { return active; }
    public boolean isOpen() { return open; }
    public boolean hasFailed() { return failed; }
    public boolean wasRunningAtOpen() { return previouslyRunning; }
    public Intent getIntent() { return intent; }
    public boolean shouldPauseOnToggle(boolean running) {
        return running || (active && intent == Intent.START);
    }
    public boolean canChangeRoute() { return open && (!previouslyRunning || intent == Intent.PAUSE || intent == Intent.RESET); }
}
