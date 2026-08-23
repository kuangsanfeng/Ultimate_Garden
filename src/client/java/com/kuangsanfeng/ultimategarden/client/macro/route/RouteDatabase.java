package com.kuangsanfeng.ultimategarden.client.macro.route;

import com.kuangsanfeng.ultimategarden.client.macro.crop.CropType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Crop route database.
 */
public final class RouteDatabase {

    private static final Map<CropType, RouteData> DATA =
            new EnumMap<>(CropType.class);

    static {

        // Carrot / Wheat / Potato / Nether Wart
        DATA.put(
                CropType.CARROT_WHEAT_POTATO_NETHER_WART,
                new RouteData(
                        CropType.CARROT_WHEAT_POTATO_NETHER_WART,
                        Set.of(RouteKey.W, RouteKey.D),
                        Set.of(RouteKey.W, RouteKey.A),
                        false
                )
        );

        // Flower
        DATA.put(
                CropType.FLOWER,
                new RouteData(
                        CropType.FLOWER,
                        Set.of(RouteKey.W, RouteKey.A),
                        Set.of(RouteKey.W, RouteKey.D),
                        false
                )
        );

        // Cocoa
        DATA.put(
                CropType.COCOA,
                new RouteData(
                        CropType.COCOA,
                        Set.of(RouteKey.W, RouteKey.D),
                        Set.of(RouteKey.S, RouteKey.D),
                        false
                )
        );

        // Melon / Pumpkin
        DATA.put(
                CropType.MELON_PUMPKIN,
                new RouteData(
                        CropType.MELON_PUMPKIN,
                        Set.of(RouteKey.W, RouteKey.A),
                        Set.of(RouteKey.W, RouteKey.D),
                        false
                )
        );

        // Sugar Cane
        DATA.put(
                CropType.SUGARCANE,
                new RouteData(
                        CropType.SUGARCANE,
                        Set.of(RouteKey.W),
                        Set.of(RouteKey.W),
                        true
                )
        );

        // Mushroom
        DATA.put(
                CropType.MUSHROOM,
                new RouteData(
                        CropType.MUSHROOM,
                        Set.of(RouteKey.W),
                        Set.of(RouteKey.W),
                        true
                )
        );

        // Cactus
        DATA.put(
                CropType.CACTUS,
                new RouteData(
                        CropType.CACTUS,
                        Set.of(RouteKey.D),
                        Set.of(RouteKey.A),
                        false
                )
        );

        // Time Tester
        DATA.put(
                CropType.TIMETESTER,
                new RouteData(
                        CropType.TIMETESTER,
                        Set.of(RouteKey.W, RouteKey.A),
                        Set.of(RouteKey.W, RouteKey.D),
                        false
                )
        );
    }

    private RouteDatabase() {
    }

    public static RouteData get(CropType type) {
        return DATA.get(type);
    }
}