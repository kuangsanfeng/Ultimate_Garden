package com.kuangsanfeng.ultimategarden.client.macro.farm;


public final class MacroConstants {


    private MacroConstants() {
    }


    /*
     * Minecraft 20 TPS
     *
     * 1 tick = 50ms
     */
    public static final long TICK_TIME_MS = 50L;


    /*
     * 原 C++:
     *
     * std::chrono::seconds(125)
     */
    public static final long PEST_LOADOUT_TIME_MS = 125_000L;


}