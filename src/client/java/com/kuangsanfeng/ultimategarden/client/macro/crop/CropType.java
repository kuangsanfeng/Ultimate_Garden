package com.kuangsanfeng.ultimategarden.client.macro.crop;

/**
 * Crop types used by the macro.
 *
 * The seven types correspond directly to the original C++ switchCrop values.
 *
 * 1 = Carrot/Wheat/Potato/NetherWart
 * 2 = Flower
 * 3 = Cocoa
 * 4 = Melon/Pumpkin
 * 5 = SugarCane
 * 6 = Mushroom
 * 7 = Cactus
 */
public enum CropType {

    CARROT_WHEAT_POTATO_NETHER_WART(1),
    FLOWER(2),
    COCOA(3),
    MELON_PUMPKIN(4),
    SUGARCANE(5),
    MUSHROOM(6),
    CACTUS(7),
    TIMETESTER(8);

    private final int value;

    CropType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    /**
     * Convert the original C++ switchCrop value to CropType.
     */
    public static CropType fromValue(int value) {
        for (CropType type : values()) {
            if (type.value == value) {
                return type;
            }
        }

        throw new IllegalArgumentException(
                "Unknown crop type: " + value
        );
    }
}