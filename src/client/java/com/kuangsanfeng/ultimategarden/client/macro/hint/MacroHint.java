package com.kuangsanfeng.ultimategarden.client.macro.hint;

import com.kuangsanfeng.ultimategarden.client.hotkey.MacroKeyBindings;
import com.kuangsanfeng.ultimategarden.client.macro.crop.CropType;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;


// =========================================================
// Macro Hint
// =========================================================

public final class MacroHint {


    private static final String PREFIX =
            "[UltimateGarden] ";


    private MacroHint() {
    }



    // =========================================================
    // Hint Dispatcher
    // =========================================================

    // =========================================================
    //Non parameters
    // =========================================================
    public static void printHint(
            String type
    ) {

        printNull();

        switch (type) {


            case "Hotkey" -> {

                printHotkeyHint();

                printNull();

            }


            case "CropSelect" -> {

                printCropSelectHint();

                printNull();

            }


            case "ModeSelect" -> {

                printModeSelectHint();

                printNull();

            }


            case "SimulateCropSelect" -> {

                printSimulateCropSelectHint();

            }


            case "InitializeDone" -> {

                printMacroInitializeDoneHint();

            }


            case "MacroStart" -> {

                printMacroStartHint();

            }


            case "ProgramPause" -> {

                printProgramPauseHint();

            }


            case "ProgramReset" -> {

                printProgramResetHint();

            }


            case "FocusRelease" -> {

                printFocusReleaseHint();

            }


            case "FocusRestore" -> {

                printFocusRestoreHint();

            }


            case "Helper" -> {

                printModeHelperHint();

                printNull();

            }


            case "WrongCommand" -> {

                printWrongCommandHint();

                printNull();

            }


            case "PestLoadoutTimerStart" -> {

                printPestLoadoutTimerStart();

            }


            case "PestLoadoutTimerReset" -> {

                printPestLoadoutTimerReset();

            }


            case "PestLoadoutTimeUp" -> {

                printPestLoadoutTimeUp();

            }




            default -> {

            }

        }

        printNull();
    }


    // =========================================================
    //Single parameters
    // =========================================================
    public static void printHint(
            String type,
            Object arg
    ) {

        printNull();

        switch (type) {


            case "CropInfo" -> {


                if (arg instanceof CropType cropType) {

                    printCropInfo(
                            cropType
                    );

                    printNull();

                }

            }



            case "LineFinished" -> {


                if (arg instanceof Integer line) {

                    printLineFinished(
                            line
                    );

                }

            }



            case "TimeTesterCount" -> {


                if (arg instanceof Integer ticks) {

                    printTimeTesterCount(
                            ticks
                    );

                }

            }



            default -> {

            }

        }

        printNull();
    }



    // =========================================================
    // Key Hint
    // =========================================================

    private static void printHotkeyHint() {

        print(
                MacroKeyBindings.getKeyName("toggle")
                        + " -> Pause/Start macro"
        );

        print(
                MacroKeyBindings.getKeyName("reset")
                        + " -> Reset macro"
        );

        print(
                MacroKeyBindings.getKeyName("next_line")
                        + " -> Move to next line"
        );

        print(
                MacroKeyBindings.getKeyName("previous_line")
                        + " -> Move to previous line"
        );

        print(
                MacroKeyBindings.getKeyName("spawn")
                        + " -> Set spawn"
        );

        print(
                MacroKeyBindings.getKeyName("warp")
                        + " -> /warp garden"
        );

        print(
                MacroKeyBindings.getKeyName("pest_loadout")
                        + " -> Auto Pest Loadout"
        );

        print(
                MacroKeyBindings.getKeyName("pest_timer_cancel")
                        + " -> Cancel pest loadout timer"
        );

        print(
                MacroKeyBindings.getKeyName("loadout")
                        + " -> /loadout"
        );

        print(
                MacroKeyBindings.getKeyName("crop_change")
                        + " -> Change crop type"
        );

        print(
                MacroKeyBindings.getKeyName("windows_focus")
                        + " -> Shift Windows focus state"
        );
    }



    // =========================================================
    // Runtime Hint
    // =========================================================

    private static void printLineFinished(
            int line
    ) {

        print("Now done line " + line);

    }



    private static void printPestLoadoutTimerStart() {

        print("PestLoadout Ticker Start");

    }



    private static void printPestLoadoutTimerReset() {

        print("PestLoadout Ticker Reset");

    }



    private static void printPestLoadoutTimeUp() {

        print("PestLoadout Ticker Done");
        print("Run The Loadout Change Macro");

    }



    private static void printTimeTesterCount(long ticks) {
        print("Ticks costed: " + ticks);
    }



    // =========================================================
    // Crop Info
    // =========================================================

    private static void printCropInfo(
            CropType cropType
    ) {

        if (cropType == null) {
            return;
        }


        switch (cropType) {


            case CARROT_WHEAT_POTATO_NETHER_WART -> {

                print("Program:Carrot_Wheat_Potato_NetherWart macro");
                print("Inventory:1-Hoe 3-Mousemat");
                print("Yaw:-90.00");
                print("Pitch:-3.00 for Wheat&NetherWart 3.00 for rest");
            }



            case FLOWER -> {

                print("Program:Flower macro");
                print("Inventory:1-Hoe 3-Mousemat");
                print("Yaw:90.00");
                print("Pitch:-3.00");
            }



            case COCOA -> {

                print("Program:Cocoa Beans macro");
                print("Inventory:1-Hoe 3-Mousemat");
                print("Yaw:0.00");
                print("Pitch:-45.00");
            }



            case MELON_PUMPKIN -> {

                print("Program:Melon/Pumpkin macro");
                print("Inventory:1-Hoe 3-Mousemat");
                print("Yaw:90.00");
                print("Pitch:-61.00");
            }



            case SUGARCANE -> {

                print("Program:SugarCane macro");
                print("Inventory:1-Hoe 3-MousematI 4-MousematII");
                print("MousematI: Yaw:45.00 Pitch:-3.0");
                print("MousematII: Yaw:135.00 Pitch:-3.0");
            }



            case MUSHROOM -> {

                print("Program:Mushroom macro");
                print("Inventory:1-Hoe 3-Mousemat");
                print("MousematI: Yaw:-65.00 Pitch:-3.0");
                print("MousematII: Yaw:-115.00 Pitch:-3.0");
            }



            case CACTUS -> {

                print("Program:Cactus macro");
                print("Inventory:1-Hoe 3-Mousemat");
                print("Yaw:-90.00");
                print("Pitch:0.00");
            }



            case TIMETESTER -> {

                print("Program:Time Tester macro");
                print("Inventory:1-Hoe 3-Mousemat");
                print("Yaw:66.66");
                print("Pitch:66.66");
            }
        }

    }



    // =========================================================
    // Crop Select Hint
    // =========================================================

    private static void printCropSelectHint() {

        print("Carrot/Wheat/Potato/NetherWart = 1");
        print("Flower = 2");
        print("Cocoa = 3");
        print("Melon/Pumpkin = 4");
        print("SugarCane = 5");
        print("Mushroom = 6");
        print("Cactus = 7");
        print("Time Tester = 8");
        print("Type the number directly into the chat box. This message will not be collected by server.");
        print("Switch the crop type:");

    }



    // =========================================================
    // Macro Mode Hint
    // =========================================================

    private static void printModeSelectHint() {


        print("Farm mod = 0");
        print("Pests mod = 1");
        print("Type the number directly into the chat box. This message will not be collected by server.");
        print("Switch the macro type:");

    }


    // =========================================================
    // Time Tester Crop Select Hint
    // =========================================================
    private static void printSimulateCropSelectHint() {
        print("Carrot/Wheat/Potato/NetherWart = 1");
        print("Flower = 2");
        print("Cocoa = 3");
        print("Melon/Pumpkin = 4");
        print("SugarCane = 5");
        print("Mushroom = 6");
        print("Cactus = 7");
        print("Type the number directly into the chat box. This message will not be collected by server.");
        print("Switch the simulate crop type:");
    }



    // =========================================================
    // Macro Initialize Done Hint
    // =========================================================

    private static void printMacroInitializeDoneHint() {

        print("Macro Initialized");

    }



    // =========================================================
    // Macro Start Hint
    // =========================================================

    private static void printMacroStartHint() {

        print("Macro Start");

    }



    // =========================================================
    // Program Pause Hint
    // =========================================================

    private static void printProgramPauseHint() {

        print("Program pause");

    }



    // =========================================================
    // Program Reset Hint
    // =========================================================

    private static void printProgramResetHint() {

        print("Program reset");

    }



    // =========================================================
    // Release Focus Hint
    // =========================================================

    private static void printFocusReleaseHint() {

        print("Focus Released");

    }



    // =========================================================
    // Restore Focus Hint
    // =========================================================

    private static void printFocusRestoreHint() {

        print("Focus Restored");

    }



    // =========================================================
    // Helper Hint
    // =========================================================

    private static void printModeHelperHint() {

        print("/ultimategarden help(er): Show help list");
        print("/ultimategarden hotkey: Show hotkeys");
        print("/ultimategarden initialize: Initialize farming crop and mod switch");
        print("/ultimategarden menu: Open UI");

    }



    // =========================================================
    // Wrong Command Hint
    // =========================================================

    private static void printWrongCommandHint() {

        print("Command Not Found.");
        print("Use '/ultimategarden help(er)' to show help list");

    }



    // =========================================================
    // Output
    // =========================================================

    private static void print(
            String text
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();


        if (minecraft.player == null) {
            return;
        }


        minecraft.gui
                .getChat()
                .addClientSystemMessage(
                        Component.literal(
                                PREFIX + text
                        )
                );

    }



    // =========================================================
    // Empty Line
    // =========================================================

    private static void printNull() {

        Minecraft minecraft =
                Minecraft.getInstance();


        if (minecraft.player == null) {
            return;
        }


        minecraft.gui
                .getChat()
                .addClientSystemMessage(
                        Component.literal("")
                );

    }

}