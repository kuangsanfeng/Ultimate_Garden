package com.kuangsanfeng.ultimategarden.client;

import com.kuangsanfeng.ultimategarden.client.runtime.hotkey.MacroKeyBindings;
import com.kuangsanfeng.ultimategarden.client.platform.input.virtual.VirtualInput;
import com.kuangsanfeng.ultimategarden.client.platform.input.virtual.VirtualKeyboard;
import com.kuangsanfeng.ultimategarden.client.platform.input.virtual.VirtualMouse;
import com.kuangsanfeng.ultimategarden.client.runtime.command.CommandListener;
import com.kuangsanfeng.ultimategarden.client.runtime.command.CommandTree;
import com.kuangsanfeng.ultimategarden.client.platform.window.WindowsFocusController;
import com.kuangsanfeng.ultimategarden.client.platform.window.WindowsInputInterceptor;
import com.kuangsanfeng.ultimategarden.client.platform.window.WindowsState;
import com.kuangsanfeng.ultimategarden.client.macro.farm.FarmController;
import com.kuangsanfeng.ultimategarden.client.macro.MacroManager;
import com.kuangsanfeng.ultimategarden.client.runtime.scheduler.ActionScheduler;
import net.fabricmc.api.ClientModInitializer;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import net.minecraft.client.Minecraft;


// =========================================================
// Client Initializer
// =========================================================

public class Ultimate_GardenClient implements ClientModInitializer {


	private static FarmController farmController;

    private static ConfigManager configManager;

    private static MacroManager macroManager;
    private static final com.kuangsanfeng.ultimategarden.client.runtime.feature.FeatureRegistry features=new com.kuangsanfeng.ultimategarden.client.runtime.feature.FeatureRegistry();
    public static com.kuangsanfeng.ultimategarden.client.runtime.feature.FeatureRegistry getFeatureRegistry(){return features;}
    private static net.minecraft.client.User observedUser;
    private static com.kuangsanfeng.ultimategarden.client.function.esp.ESPManager espManager;


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
        espManager = new com.kuangsanfeng.ultimategarden.client.function.esp.ESPManager(configManager);
        var pestESP = new com.kuangsanfeng.ultimategarden.client.function.esp.adapters.PestESP(configManager.getPestConfig());
        espManager.register(com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.ESPConfig.SILVERFISH, pestESP::collect);
        new com.kuangsanfeng.ultimategarden.client.function.esp.adapters.ZombieShootoutESP(configManager.getESPConfig()).register(espManager);
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



        windowsInputInterceptor.setUiInputAllowed(() -> client.screen instanceof com.kuangsanfeng.ultimategarden.client.platform.ui.screen.UltimateGardenScreen);

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
        com.kuangsanfeng.ultimategarden.client.runtime.feature.adapters.ClientFeatures.install(features,farmController,com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController.INSTANCE);
        com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController.INSTANCE.register();
        com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.InstantTransmissionController.INSTANCE.register();
        windowsInputInterceptor.setExternalOwner(()->com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController.intercepting()||com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.InstantTransmissionController.ownsInput());
        windowsInputInterceptor.setExternalMouseOwners(
                ()->com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController.interceptingAim()||com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.InstantTransmissionController.ownsInput(),
                ()->com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController.interceptingShoot()||com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.InstantTransmissionController.ownsInput());
        farmController.setExternalInputOwner(()->com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController.INSTANCE.enabled()||com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.InstantTransmissionController.INSTANCE.running());
        var macroHud = new com.kuangsanfeng.ultimategarden.client.platform.ui.hud.MacroStatusHud(configManager, farmController, windowsState);
        com.kuangsanfeng.ultimategarden.client.platform.ui.UiRegistry.hud("macro_status",macroHud::render);
        var shootoutHud = new com.kuangsanfeng.ultimategarden.client.platform.ui.hud.ZombieShootoutHud(configManager);
        com.kuangsanfeng.ultimategarden.client.platform.ui.UiRegistry.hud("zombie_shootout_status",shootoutHud::render);
        com.kuangsanfeng.ultimategarden.client.function.world.WorldInfoService.register();
        com.kuangsanfeng.ultimategarden.client.function.mapping.MappingController.INSTANCE.register();
        var mappingHud=new com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.mapping.MappingHud(configManager);
        com.kuangsanfeng.ultimategarden.client.platform.ui.UiRegistry.hud("mapping",mappingHud::render);
        var transmissionHud=new com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.instant_transmission.InstantTransmissionHud(configManager);
        com.kuangsanfeng.ultimategarden.client.platform.ui.UiRegistry.hud("instant_transmission_status",transmissionHud::render);
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register(
                (game, screen, width, height) -> farmController.onPlayerScreenOpened(screen));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.register(game -> com.kuangsanfeng.ultimategarden.client.platform.ui.hud.HudAdjustmentController.tick());
        com.kuangsanfeng.ultimategarden.client.platform.context.ClientContextEvents.onChange(change -> {
            if(change.change()==com.kuangsanfeng.ultimategarden.client.platform.context.ClientContextEvents.Change.WORLD_CHANGED) {
                com.kuangsanfeng.ultimategarden.client.platform.ui.hud.HudAdjustmentController.finish(false);
                espManager.invalidateFrames();
            }
        });
        configManager.setCurrentAccountCopyGuard(() -> farmController.canEditRouteConfiguration() && !com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController.INSTANCE.enabled());
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.register(game -> {
            var session = game.getUser();
            if (session == observedUser) return;
            observedUser = session;
            var identity = accountIdentity(session);
            if (configManager.matchesAccount(identity)) return;
            com.kuangsanfeng.ultimategarden.client.platform.ui.hud.HudAdjustmentController.finish(false);
            farmController.abortForWorldChange(); legacyMouseClickScheduler.clear(); espManager.invalidateFrames();
            if (game.screen instanceof com.kuangsanfeng.ultimategarden.client.platform.ui.screen.UltimateGardenScreen) game.setScreen(null);
            var previousAccount = configManager.currentAccount();
            com.kuangsanfeng.ultimategarden.client.platform.context.ClientContextEvents.accountBefore(previousAccount,identity);
            configManager.activateAccount(identity);
            com.kuangsanfeng.ultimategarden.client.platform.context.ClientContextEvents.accountAfter(previousAccount,identity);
            farmController.abortForWorldChange();
        });
        com.kuangsanfeng.ultimategarden.client.platform.context.ClientContextEvents.onDisconnect((handler, game) -> { features.stop("hud_layout","DISCONNECTED"); features.stop("farm","DISCONNECTED"); });
        com.kuangsanfeng.ultimategarden.client.platform.context.ClientContextEvents.onStopping(game -> { features.stop("hud_layout","CLIENT_STOPPING"); features.stop("farm","CLIENT_STOPPING"); });

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
    private static com.kuangsanfeng.ultimategarden.client.platform.persistence.config.storage.AccountIdentity accountIdentity(net.minecraft.client.User user) {
        if (user == null) return null;
        try { return new com.kuangsanfeng.ultimategarden.client.platform.persistence.config.storage.AccountIdentity(user.getProfileId(), user.getName()); }
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
