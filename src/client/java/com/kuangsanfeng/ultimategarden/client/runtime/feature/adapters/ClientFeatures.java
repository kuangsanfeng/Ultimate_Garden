package com.kuangsanfeng.ultimategarden.client.runtime.feature.adapters;
import com.kuangsanfeng.ultimategarden.client.runtime.feature.FeatureRegistry;
import com.kuangsanfeng.ultimategarden.client.macro.farm.FarmController;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController;
import com.kuangsanfeng.ultimategarden.client.function.collection.zombie_shoot.ZombieShootCollector;
import com.kuangsanfeng.ultimategarden.client.platform.ui.hud.HudAdjustmentController;
import java.util.Map;
/** Business lifecycle and snapshot assembly; no additional event or tick registration. */
public final class ClientFeatures {
 private ClientFeatures(){}
 public static void install(FeatureRegistry registry,FarmController farm,ZombieShootoutController shootout){
  registry.register("farm",farm::startMacro,reason->{if(reason.equals("DISCONNECTED")||reason.equals("CLIENT_STOPPING"))farm.abortForWorldChange();else farm.pauseMacro();},
   ()->new FeatureRegistry.State(farm.getState().isRunning(),farm.getRunStatus(),farm.getHudStopReason(),Map.of("lineTick",farm.getState().getTick(),"totalTicks",farm.getState().getTotalTicks())));
  registry.register("zombie_shootout",shootout::start,shootout::stop,shootout::hudSnapshot);
  var command=farm.getCommand();
  registry.register("pest_timer",command::startPestLoadoutTimer,reason->command.cancelPestLoadoutTimer(),
   ()->new FeatureRegistry.State(command.isPestLoadoutTimerRunning(),command.isPestLoadoutTimerRunning()?"RUNNING":"STOPPED","NONE",Map.of("remainingTicks",command.getPestLoadoutRemainingTicks())));
  var collector=ZombieShootCollector.INSTANCE;
  registry.register("developer_collection",()->collector.setLogsEnabled(true),reason->{if(reason.equals("MANUAL"))collector.setLogsEnabled(false);else collector.stop();},
   ()->new FeatureRegistry.State(collector.enabled(),collector.status(),"NONE",Map.of("logsEnabled",collector.logsEnabled(),"modelStatus",collector.modelStatus())));
  registry.register("hud_layout",null,reason->HudAdjustmentController.finish(false),
   ()->new FeatureRegistry.State(HudAdjustmentController.isActive(),HudAdjustmentController.isActive()?"EDITING":"STOPPED","NONE",Map.of()));
 }
}
