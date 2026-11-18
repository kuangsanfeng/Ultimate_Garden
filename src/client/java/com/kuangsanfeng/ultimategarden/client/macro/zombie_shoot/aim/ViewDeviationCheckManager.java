package com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim.ExpectedViewTracker;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.aim.ShootoutMouseInput;

import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;

import net.minecraft.client.Minecraft;import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;import net.minecraft.client.resources.sounds.SimpleSoundInstance;import net.minecraft.sounds.SoundEvents;
/** Zombie-only observer; no view history or angles are logged or persisted. */
public final class ViewDeviationCheckManager implements ShootoutMouseInput.ViewObserver {
 private final ExpectedViewTracker tracker=new ExpectedViewTracker();private final Runnable stop;
 private final com.kuangsanfeng.ultimategarden.client.platform.input.view.ViewWarningPresenter notice=new com.kuangsanfeng.ultimategarden.client.platform.input.view.ViewWarningPresenter();
 public ViewDeviationCheckManager(Runnable stop){this.stop=stop;}
 private com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.ViewDeviationConfig.Settings settings(){return Ultimate_GardenClient.getConfigManager().getViewDeviationConfig().zombie();}
 public void clear(){tracker.clear();cancelNotice();}
 private void cancelNotice(){notice.clear();}

 public boolean poll(float yaw,float pitch){return !tracker.active()||before(yaw,pitch);}
 public boolean before(float yaw,float pitch){if(!Ultimate_GardenClient.getConfigManager().getViewDeviationConfig().effective(settings())){clear();return true;}if(!tracker.active())tracker.begin(yaw,pitch);return evaluate(tracker.stationary(yaw,pitch));}
 public void submitted(double dx,double dy,double sensitivity,boolean invertX,boolean invertY){if(tracker.active())tracker.submit(dx,dy,sensitivity,invertX,invertY);}
 public void consumed(float yaw,float pitch){if(tracker.active())evaluate(tracker.consumed(yaw,pitch));}
 private boolean evaluate(Double error){
  if(!Ultimate_GardenClient.getConfigManager().getViewDeviationConfig().effective(settings())){clear();return true;}
  int result=tracker.evaluate(error,settings().warning(),settings().stop());
  if(result==2){stop.run();notice.stopped("Zombie Shootout Stopped","View Deviation Exceeded");return false;}
  if(result==1)notice.warn("Potential View Check","Local View Deviation Warning",settings().duration());
  return true;
 }
 public void tick(){if(!Ultimate_GardenClient.getConfigManager().getViewDeviationConfig().effective(settings())){clear();return;}notice.tick();}
}
