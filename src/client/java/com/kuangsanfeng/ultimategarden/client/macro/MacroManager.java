package com.kuangsanfeng.ultimategarden.client.macro;

import com.kuangsanfeng.ultimategarden.client.macro.farm.FarmController;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

// =========================================================
// Automation Module Lifecycle
// =========================================================
public final class MacroManager {

    public static final String FARM = "farm";

    /** A small explicit contract; modules retain their own state and algorithms. */
    public interface Module {
        void startMacro();
        void pauseMacro();
        void resetMacro();
        void tick();

        default void startMacroByHotkey() { startMacro(); }
        default void pauseMacroByHotkey() { pauseMacro(); }
        default void resetMacroByHotkey() { resetMacro(); }
        default void restoreInputState() { }
        default boolean handleInitializeInput(String message) { return false; }
        default boolean automateTaskByMonitorMessage(String message) { return false; }
    }

    private final Map<String, Module> modules = new LinkedHashMap<>();
    private final FarmController farmController;

    public MacroManager(FarmController farmController) {
        this.farmController = Objects.requireNonNull(farmController);
        register(FARM, farmController);
    }

    /** Registration happens during initialization, before event callbacks run. */
    public void register(String id, Module module) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(module);
        if (modules.containsKey(id) || modules.values().stream().anyMatch(value -> value == module)) {
            throw new IllegalArgumentException("Module already registered: " + id);
        }
        modules.put(id, module);
    }

    private Module module(String id) {
        Module module = modules.get(id);
        if (module == null) {
            throw new IllegalArgumentException("Unknown module: " + id);
        }
        return module;
    }

    public FarmController getFarmController() { return farmController; }

    public void start(String id) { module(id).startMacro(); }
    public void pause(String id) { module(id).pauseMacro(); }
    public void reset(String id) { module(id).resetMacro(); }

    public void startByHotkey(String id) { module(id).startMacroByHotkey(); }
    public void pauseByHotkey(String id) { module(id).pauseMacroByHotkey(); }
    public void resetByHotkey(String id) { module(id).resetMacroByHotkey(); }

    public void tick() {
        // Paused modules must still advance their existing command queues/timers.
        for (Module module : modules.values()) {
            module.tick();
        }
    }

    public void restoreInputState() {
        for (Module module : modules.values()) {
            module.restoreInputState();
        }
    }

    public boolean handleInitializeInput(String message) {
        for (Module module : modules.values()) {
            if (module.handleInitializeInput(message)) {
                return true;
            }
        }
        return false;
    }

    public boolean automateTaskByMonitorMessage(String message) {
        boolean handled = false;
        for (Module module : modules.values()) {
            handled |= module.automateTaskByMonitorMessage(message);
        }
        return handled;
    }
}
