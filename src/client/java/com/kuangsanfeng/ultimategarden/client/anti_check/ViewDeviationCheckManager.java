package com.kuangsanfeng.ultimategarden.client.anti_check;
import com.kuangsanfeng.ultimategarden.client.Ultimate_GardenClient;
import com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.*;
import net.minecraft.client.Minecraft;import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;import net.minecraft.client.resources.sounds.SimpleSoundInstance;import net.minecraft.sounds.SoundEvents;
/** Zombie-only observer; no view history or angles are logged or persisted. */
public final class ViewDeviationCheckManager implements ShootoutMouseInput.ViewObserver {
 private final ExpectedViewTracker tracker=new ExpectedViewTracker();private final Runnable stop;
 private final SystemToast.SystemToastId toastId=new SystemToast.SystemToastId(5000);
 private SystemToast toast;private SimpleSoundInstance sound;private final ViewWarningClock clock=new ViewWarningClock();
 public ViewDeviationCheckManager(Runnable stop){this.stop=stop;}
 private com.kuangsanfeng.ultimategarden.client.config.ViewDeviationConfig.Settings settings(){return Ultimate_GardenClient.getConfigManager().getViewDeviationConfig().zombie();}
 public void clear(){tracker.clear();cancelNotice();}
 private void cancelNotice(){var c=Minecraft.getInstance();try{if(sound!=null)c.getSoundManager().stop(sound);}catch(RuntimeException unavailable){}try{if(toast!=null)toast.forceHide();}catch(RuntimeException unavailable){}sound=null;toast=null;clock.clear();}

 public boolean poll(float yaw,float pitch){return !tracker.active()||before(yaw,pitch);}
 public boolean before(float yaw,float pitch){if(!settings().enabled){clear();return true;}if(!tracker.active())tracker.begin(yaw,pitch);return evaluate(tracker.stationary(yaw,pitch));}
 public void submitted(double dx,double dy,double sensitivity,boolean invertX,boolean invertY){if(tracker.active())tracker.submit(dx,dy,sensitivity,invertX,invertY);}
 public void consumed(float yaw,float pitch){if(tracker.active())evaluate(tracker.consumed(yaw,pitch));}
 private boolean evaluate(Double error){int result=tracker.evaluate(error,settings().warning(),settings().stop());var c=Minecraft.getInstance();if(result==2){stop.run();try{SystemToast.add(c.getToastManager(),new SystemToast.SystemToastId(5000),Component.literal("Zombie Shootout Stopped"),Component.literal("View Deviation Exceeded"));}catch(RuntimeException unavailable){}return false;}if(result==1){cancelNotice();toast=new SystemToast(toastId,Component.literal("Potential View Check"),Component.literal("Local View Deviation Warning"));try{c.getToastManager().addToast(toast);}catch(RuntimeException unavailable){}clock.begin(System.nanoTime(),settings().duration());}return true;}
 public void tick(){if(!settings().enabled){clear();return;}if(!clock.active())return;int pulse=clock.pulse(System.nanoTime());if(pulse<0){cancelNotice();return;}if(pulse>0){var c=Minecraft.getInstance();try{if(sound!=null)c.getSoundManager().stop(sound);sound=SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL,1);c.getSoundManager().play(sound);}catch(RuntimeException unavailable){}}}
}
