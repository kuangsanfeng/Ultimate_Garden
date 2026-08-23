package com.kuangsanfeng.ultimategarden.client.macro.crop;

import java.util.Map;

/**
 * Crop macro database.
 */
public final class CropDatabase {

    private static final Map<Integer, CropData> CROPS =
            Map.of(

                    // =================================================
                    // 1
                    // Carrot / Wheat / Potato / Nether Wart
                    // =================================================

                    1,
                    new CropData(
                            1,
                            "Carrot/Wheat/Potato/NetherWart",

                            806,
                            15,

                            // eventSelect = true
                            // W + D
                            new boolean[]{
                                    true,
                                    false,
                                    false,
                                    true
                            },

                            // eventSelect = false
                            // W + A
                            new boolean[]{
                                    true,
                                    false,
                                    true,
                                    false
                            },

                            -90.0,
                            -3.0,

                            -90.0,
                            -3.0
                    ),


                    // =================================================
                    // 2
                    // Flower
                    // =================================================

                    2,
                    new CropData(
                            2,
                            "Flower",

                            544,
                            16,

                            // W + A
                            new boolean[]{
                                    true,
                                    false,
                                    true,
                                    false
                            },

                            // W + D
                            new boolean[]{
                                    true,
                                    false,
                                    false,
                                    true
                            },

                            90.0,
                            -3.0,

                            90.0,
                            -3.0
                    ),


                    // =================================================
                    // 3
                    // Cocoa
                    // =================================================

                    3,
                    new CropData(
                            3,
                            "Cocoa",

                            797,
                            45,

                            // W + D
                            new boolean[]{
                                    true,
                                    false,
                                    false,
                                    true
                            },

                            // S + D
                            new boolean[]{
                                    false,
                                    true,
                                    false,
                                    true
                            },

                            0.0,
                            -45.0,

                            0.0,
                            -45.0
                    ),


                    // =================================================
                    // 4
                    // Melon / Pumpkin
                    // =================================================

                    4,
                    new CropData(
                            4,
                            "Melon/Pumpkin",

                            560,
                            23,

                            // W + A
                            new boolean[]{
                                    true,
                                    false,
                                    true,
                                    false
                            },

                            // W + D
                            new boolean[]{
                                    true,
                                    false,
                                    false,
                                    true
                            },

                            90.0,
                            -61.0,

                            90.0,
                            -61.0
                    ),


                    // =================================================
                    // 5
                    // Sugar Cane
                    // =================================================

                    5,
                    new CropData(
                            5,
                            "SugarCane",

                            475,
                            30,

                            // W
                            new boolean[]{
                                    true,
                                    false,
                                    false,
                                    false
                            },

                            // W
                            new boolean[]{
                                    true,
                                    false,
                                    false,
                                    false
                            },

                            45.0,
                            -3.0,

                            135.0,
                            -3.0
                    ),


                    // =================================================
                    // 6
                    // Mushroom
                    // =================================================

                    6,
                    new CropData(
                            6,
                            "Mushroom",

                            800,
                            15,

                            // W
                            new boolean[]{
                                    true,
                                    false,
                                    false,
                                    false
                            },

                            // W
                            new boolean[]{
                                    true,
                                    false,
                                    false,
                                    false
                            },

                            -65.0,
                            -3.0,

                            -115.0,
                            -3.0
                    ),


                    // =================================================
                    // 7
                    // Cactus
                    // =================================================

                    7,
                    new CropData(
                            7,
                            "Cactus",

                            272,
                            30,

                            // D
                            new boolean[]{
                                    false,
                                    false,
                                    false,
                                    true
                            },

                            // A
                            new boolean[]{
                                    false,
                                    false,
                                    true,
                                    false
                            },

                            -90.0,
                            0.0,

                            -90.0,
                            0.0
                    ),


                    // =================================================
                    // 8
                    // Time Tester
                    // =================================================

                    8,
                    new CropData(
                            8,
                            "TimeTester",

                            99999,
                            99999,

                            // W + A
                            new boolean[]{
                                    true,
                                    false,
                                    true,
                                    false
                            },

                            // W + D
                            new boolean[]{
                                    true,
                                    false,
                                    false,
                                    true
                            },

                            66.66,
                            66.66,

                            66.66,
                            66.66
                    )
            );


    private CropDatabase() {
    }


    public static CropData get(
            int cropType
    ) {
        CropData data =
                CROPS.get(cropType);

        if (data == null) {
            throw new IllegalArgumentException(
                    "Unknown crop type: "
                            + cropType
            );
        }

        return data;
    }


    public static boolean exists(
            int cropType
    ) {
        return CROPS.containsKey(
                cropType
        );
    }
}