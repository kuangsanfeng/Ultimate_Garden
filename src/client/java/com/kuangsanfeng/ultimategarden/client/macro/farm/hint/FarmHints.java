package com.kuangsanfeng.ultimategarden.client.macro.farm.hint;

import com.kuangsanfeng.ultimategarden.client.hint.HintManager;
import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import java.util.Locale;
import com.kuangsanfeng.ultimategarden.client.macro.farm.crop.CropType;



// =========================================================
// Macro Hint
// =========================================================

public final class FarmHints {


    private FarmHints() {
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

        HintManager.printNull();

        switch (type) {





            case "CropSelect" -> {

                printCropSelectHint();

                HintManager.printNull();

            }


            case "ModeSelect" -> {

                printModeSelectHint();

                HintManager.printNull();

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

        HintManager.printNull();
    }


    // =========================================================
    //Single parameters
    // =========================================================
    public static void printHint(
            String type,
            Object arg
    ) {

        HintManager.printNull();

        switch (type) {


            case "CropInfo" -> {


                if (arg instanceof CropType cropType) {

                    printCropInfo(
                            cropType
                    );

                    HintManager.printNull();

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

        HintManager.printNull();
    }



    // =========================================================
    // Key Hint
    // =========================================================





    // =========================================================
    // Runtime Hint
    // =========================================================

    private static void printLineFinished(
            int line
    ) {

        HintManager.print("Now done line " + line);

    }



    private static void printPestLoadoutTimerStart() {

        HintManager.print("PestLoadout Ticker Start");

    }



    private static void printPestLoadoutTimerReset() {

        HintManager.print("PestLoadout Ticker Reset");

    }



    private static void printPestLoadoutTimeUp() {

        HintManager.print("PestLoadout Ticker Done");
        HintManager.print("Run The Loadout Change Macro");

    }



    private static void printTimeTesterCount(long ticks) {
        HintManager.print("Ticks costed: " + ticks);
    }



    // =========================================================
    // Crop Info
    // =========================================================

    private static void printCropInfo(
            CropType cropType
    ) {
        if (cropType == null) return;
        var configs = Ultimate_GardenClient.getConfigManager().getCropConfig();
        var crop = configs.getCrop(cropType);
        HintManager.print("Program:" + crop.getName() + " [" + cropType.name() + "] macro");
        if (!crop.isExecutable()) { HintManager.print("Not implemented / Coming soon or disabled"); return; }
        var view = crop.lineChangeYawPitch();
        HintManager.print(view.enabled() ? "Inventory:1-Hoe " + view.slot1() + "-MousematI " + view.slot2() + "-MousematII" : "Inventory:1-Hoe");
        for (boolean a : new boolean[]{true, false}) {
            HintManager.print(String.format(Locale.ROOT, "%s: Yaw:%.2f Pitch:%.2f",
                a ? "A" : "B", crop.getHintYaw(a), crop.getHintPitch(a)));
        }

    }



    // =========================================================
    // Crop Select Hint
    // =========================================================

    private static void printCropSelectHint() {
        HintManager.print("Select Crop:");
        var configs = Ultimate_GardenClient.getConfigManager().getCropConfig();
        for (CropType type : CropType.values()) {
            var crop = configs.getCrop(type);
            HintManager.print(type.getValue() + ". " + crop.getName()
                + (type.isCustom() ? " (Not implemented / Coming soon)" : crop.isExecutable() ? "" : " (disabled)"));
        }
        HintManager.print("Type the number directly into the chat box. This message will not be collected by server.");
    }



    // =========================================================
    // Macro Mode Hint
    // =========================================================

    private static void printModeSelectHint() {


        HintManager.print("Farm mod = 0");
        HintManager.print("Pests mod = 1");
        HintManager.print("Type the number directly into the chat box. This message will not be collected by server.");
        HintManager.print("Switch the macro type:");

    }


    // =========================================================
    // Time Tester Crop Select Hint
    // =========================================================
    private static void printSimulateCropSelectHint() {
        HintManager.print("Select Crop:");
        var configs = Ultimate_GardenClient.getConfigManager().getCropConfig();
        for (CropType type : CropType.values()) {
            var crop = configs.getCrop(type);
            HintManager.print(type.getValue() + ". " + crop.getName()
                + (type.isCustom() ? " (Not implemented / Coming soon)" : crop.isExecutable() ? "" : " (disabled)"));
        }
        HintManager.print("Type the number directly into the chat box. This message will not be collected by server.");
    }



    // =========================================================
    // Macro Initialize Done Hint
    // =========================================================

    private static void printMacroInitializeDoneHint() {

        HintManager.print("Macro Initialized");

    }



    // =========================================================
    // Macro Start Hint
    // =========================================================

    private static void printMacroStartHint() {

        HintManager.print("Macro Start");

    }



    // =========================================================
    // Program Pause Hint
    // =========================================================

    private static void printProgramPauseHint() {

        HintManager.print("Program pause");

    }



    // =========================================================
    // Program Reset Hint
    // =========================================================

    private static void printProgramResetHint() {

        HintManager.print("Program reset");

    }



    // =========================================================
    // Release Focus Hint
    // =========================================================





    // =========================================================
    // Restore Focus Hint
    // =========================================================





    // =========================================================
    // Helper Hint
    // =========================================================





    // =========================================================
    // Wrong Command Hint
    // =========================================================





    // =========================================================
    // Output
    // =========================================================





    // =========================================================
    // Empty Line
    // =========================================================



}
