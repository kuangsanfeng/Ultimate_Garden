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
    private static net.minecraft.client.User observedUser;
    private static com.kuangsanfeng.ultimategarden.client.macro.esp.ESPManager espManager;


	// Compatibility: this pre-existing mouse-click queue has no tick driver.
    // Do not merge it with farm actions or start ticking it in a structural refactor.
    // No current farm flow calls VirtualMouse.mouseClick(). See Code_Structure.md.
    private static ActionScheduler legacyMouseClickScheduler;


	private static WindowsState windowsState;

	private static WindowsFocusController windowsFocusController;

	private static WindowsInputInterceptor windowsInputInterceptor;



	@Override
	public void onInitializeClient() {

        observedUser = Minecraft.getInstance().getUser();
        configManager = new ConfigManager(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("ultimategarden"), accountIdentity(observedUser));
        espManager = new com.kuangsanfeng.ultimategarden.client.macro.esp.ESPManager(configManager);
        var pestESP = new com.kuangsanfeng.ultimategarden.client.macro.farm.pest.PestESP(configManager.getPestConfig());
        espManager.register(com.kuangsanfeng.ultimategarden.client.config.ESPConfig.SILVERFISH, pestESP::collect);
        espManager.initialize();


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



        windowsInputInterceptor.setUiInputAllowed(() -> client.screen instanceof com.kuangsanfeng.ultimategarden.client.ui.screen.UltimateGardenScreen);

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
        var macroHud = new com.kuangsanfeng.ultimategarden.client.ui.hud.MacroStatusHud(configManager, farmController, windowsState);
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                com.kuangsanfeng.ultimategarden.Ultimate_Garden.id("macro_status"), (graphics, delta) -> macroHud.render(graphics));
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register(
                (game, screen, width, height) -> farmController.onPlayerScreenOpened(screen));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.register(game -> com.kuangsanfeng.ultimategarden.client.ui.hud.HudAdjustmentController.tick());
        configManager.setCurrentAccountCopyGuard(farmController::canEditRouteConfiguration);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.register(game -> {
            var session = game.getUser();
            if (session == observedUser) return;
            observedUser = session;
            var identity = accountIdentity(session);
            if (configManager.matchesAccount(identity)) return;
            com.kuangsanfeng.ultimategarden.client.ui.hud.HudAdjustmentController.finish(false);
            farmController.abortForWorldChange(); legacyMouseClickScheduler.clear(); espManager.invalidateFrames();
            if (game.screen instanceof com.kuangsanfeng.ultimategarden.client.ui.screen.UltimateGardenScreen) game.setScreen(null);
            configManager.activateAccount(identity);
            farmController.abortForWorldChange();
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, game) -> { com.kuangsanfeng.ultimategarden.client.ui.hud.HudAdjustmentController.finish(false); farmController.abortForWorldChange(); });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(game -> { com.kuangsanfeng.ultimategarden.client.ui.hud.HudAdjustmentController.finish(false); farmController.abortForWorldChange(); });

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
    private static com.kuangsanfeng.ultimategarden.client.config.AccountIdentity accountIdentity(net.minecraft.client.User user) {
        if (user == null) return null;
        try { return new com.kuangsanfeng.ultimategarden.client.config.AccountIdentity(user.getProfileId(), user.getName()); }
        catch (IllegalArgumentException error) { return null; }
    }

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
