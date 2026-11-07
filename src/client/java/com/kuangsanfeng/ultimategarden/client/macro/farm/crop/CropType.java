package com.kuangsanfeng.ultimategarden.client.macro.farm.crop;

public enum CropType {
    WHEAT(1), FLOWER(2), COCOA(3), MELON(4), PUMPKIN(5), SUGARCANE(6), MUSHROOM(7), CACTUS(8), CARROT(9), POTATO(10), NETHER_WART(11), TIMETESTER(12), CUSTOM1(13), CUSTOM2(14), CUSTOM3(15);
    private final int value;
    CropType(int value) { this.value = value; }
    public int getValue() { return value; }
    public boolean isCustom() { return name().startsWith("CUSTOM"); }
    public static CropType fromValue(int value) {
        for (CropType type : values()) if (type.value == value) return type;
        throw new IllegalArgumentException("Unknown crop type: " + value);
    }
}
