package com.kuangsanfeng.ultimategarden.client;

import com.kuangsanfeng.ultimategarden.client.hotkey.MacroKeyBindings;
import com.kuangsanfeng.ultimategarden.client.input.VirtualInput;
import com.kuangsanfeng.ultimategarden.client.input.VirtualKeyboard;
import com.kuangsanfeng.ultimategarden.client.input.VirtualMouse;
import com.kuangsanfeng.ultimategarden.client.interaction.command.CommandScheduler;
import com.kuangsanfeng.ultimategarden.client.interaction.command.CommandTree;
import com.kuangsanfeng.ultimategarden.client.interaction.windows.WindowsFocusController;
import com.kuangsanfeng.ultimategarden.client.interaction.windows.WindowsInputInterceptor;
import com.kuangsanfeng.ultimategarden.client.interaction.windows.WindowsState;
import com.kuangsanfeng.ultimategarden.client.macro.MacroController;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;


// =========================================================
// Client Initializer
// =========================================================

public class Ultimate_GardenClient implements ClientModInitializer {


	private static MacroController macroController;


	private static com.kuangsanfeng.ultimategarden.client.macro.command.CommandScheduler macroScheduler;


	private static WindowsState windowsState;

	private static WindowsFocusController windowsFocusController;

	private static WindowsInputInterceptor windowsInputInterceptor;



	@Override
	public void onInitializeClient() {


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

		macroScheduler =
				new com.kuangsanfeng.ultimategarden.client.macro.command.CommandScheduler();



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
						macroScheduler
				);



		// =====================================================
		// Windows interaction
		// =====================================================

		windowsFocusController =
				new WindowsFocusController(
						client,
						windowsState
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

		macroController =
				new MacroController(
						client,
						input,
						windowsInputInterceptor,
						windowsFocusController
				);



		// =====================================================
		// Register
		// =====================================================

		MacroKeyBindings.register(
				macroController
		);


		CommandTree.register(
				macroController
		);


		CommandScheduler.register(
				macroController
		);

	}



	public static MacroController getMacroController() {

		return macroController;

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