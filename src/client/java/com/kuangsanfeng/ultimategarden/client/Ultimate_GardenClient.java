package com.kuangsanfeng.ultimategarden.client;

import com.kuangsanfeng.ultimategarden.client.hotkey.MacroKeyBindings;
import com.kuangsanfeng.ultimategarden.client.input.VirtualInput;
import com.kuangsanfeng.ultimategarden.client.input.VirtualKeyboard;
import com.kuangsanfeng.ultimategarden.client.input.VirtualMouse;
import com.kuangsanfeng.ultimategarden.client.command.CommandListener;
import com.kuangsanfeng.ultimategarden.client.command.CommandTree;
import com.kuangsanfeng.ultimategarden.client.window.WindowsFocusController;
import com.kuangsanfeng.ultimategarden.client.window.WindowsInputInterceptor;
import com.kuangsanfeng.ultimategarden.client.window.WindowsState;
import com.kuangsanfeng.ultimategarden.client.macro.farm.FarmController;
import com.kuangsanfeng.ultimategarden.client.macro.MacroManager;
import com.kuangsanfeng.ultimategarden.client.scheduler.ActionScheduler;
import net.fabricmc.api.ClientModInitializer;
import com.kuangsanfeng.ultimategarden.client.config.ConfigManager;
import net.minecraft.client.Minecraft;


// =========================================================
// Client Initializer
// =========================================================

public class Ultimate_GardenClient implements ClientModInitializer {


	private static FarmController farmController;

    private static ConfigManager configManager;

    private static MacroManager macroManager;


	// Compatibility: this pre-existing mouse-click queue has no tick driver.
    // Do not merge it with farm actions or start ticking it in a structural refactor.
    // No current farm flow calls VirtualMouse.mouseClick(). See Code_Structure.md.
    private static ActionScheduler legacyMouseClickScheduler;


	private static WindowsState windowsState;

	private static WindowsFocusController windowsFocusController;

	private static WindowsInputInterceptor windowsInputInterceptor;



	@Override
	public void onInitializeClient() {

        configManager = new ConfigManager();


		Minecraft client =
				Minecraft.getInstance();



		// =====================================================
		// Windows State
		// =====================================================

		windowsState =
				new WindowsState();



		// =====================================================
		// Macro Scheduler
		// =====================================================

		legacyMouseClickScheduler =
				new ActionScheduler();



		// =====================================================
		// Virtual Input
		// =====================================================

		VirtualKeyboard keyboard =
				new VirtualKeyboard(
						client
				);


		VirtualMouse mouse =
				new VirtualMouse(
						client,
						legacyMouseClickScheduler
				);



		// =====================================================
		// Windows interaction
		// =====================================================

		windowsFocusController =
				new WindowsFocusController(
						client,
						windowsState,
                        () -> {
                            if (macroManager != null) {
                                macroManager.restoreInputState();
                            }
                        }
				);


		windowsInputInterceptor =
				new WindowsInputInterceptor(
						windowsState
				);



		VirtualInput input =
				new VirtualInput(
						keyboard,
						mouse
				);



		// =====================================================
		// Macro
		// =====================================================

		farmController =
				new FarmController(
						client,
						input,
						windowsInputInterceptor,
						windowsFocusController,
                        configManager
				);



		// =====================================================
		// Register
		// =====================================================

        macroManager = new MacroManager(farmController);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, game) -> farmController.abortForWorldChange());
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(game -> farmController.abortForWorldChange());

		MacroKeyBindings.register(
				macroManager
		);


		CommandTree.register(
				farmController
		);


		CommandListener.register(
				macroManager
		);

	}



	public static ConfigManager getConfigManager() { return configManager; }

    public static MacroManager getMacroManager() {
        return macroManager;
    }

    public static FarmController getFarmController() {

		return farmController;

	}



	public static WindowsFocusController getWindowsFocusController() {

		return windowsFocusController;

	}



	public static WindowsInputInterceptor getWindowsInputInterceptor() {

		return windowsInputInterceptor;

	}



	public static WindowsState getWindowsState() {

		return windowsState;

	}

}