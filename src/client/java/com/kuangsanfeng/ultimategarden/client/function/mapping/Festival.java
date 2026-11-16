package com.kuangsanfeng.ultimategarden.client.function.mapping;

/** Stable storage IDs and language-independent directory names. No network inference. */
public enum Festival {
    NONE("None"), SPOOKY("Spooky Festival"), JERRY("Season of Jerry"),
    JERRY_NEW_YEAR("Season of Jerry&New Year Celebration"), HARVEST("Harvest Feast"),
    SEAL("Years of the Seal"), WITCH("Years of the Witch"), PIG("Years of the Pig");
    public final String folder;
    Festival(String folder) { this.folder = folder; }
}
