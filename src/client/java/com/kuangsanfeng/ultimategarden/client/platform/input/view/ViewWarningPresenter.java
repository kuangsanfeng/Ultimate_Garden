package com.kuangsanfeng.ultimategarden.client.platform.input.view;
import com.kuangsanfeng.ultimategarden.client.platform.input.view.ViewWarningClock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
public final class ViewWarningPresenter {
 private final SystemToast.SystemToastId toastId=new SystemToast.SystemToastId(5000);
 private SystemToast toast;private SimpleSoundInstance sound;
 private final ViewWarningClock clock=new ViewWarningClock();
 public void clear(){var c=Minecraft.getInstance();try{if(sound!=null)c.getSoundManager().stop(sound);}catch(RuntimeException unavailable){}try{if(toast!=null)toast.forceHide();}catch(RuntimeException unavailable){}sound=null;toast=null;clock.clear();}
 public void warn(String title,String detail,double duration){
  clear();toast=new SystemToast(toastId,Component.literal(title),Component.literal(detail));
  try{Minecraft.getInstance().getToastManager().addToast(toast);}catch(RuntimeException unavailable){}
  clock.begin(System.nanoTime(),duration);
 }
 public void stopped(String title,String detail){try{SystemToast.add(Minecraft.getInstance().getToastManager(),new SystemToast.SystemToastId(5000),Component.literal(title),Component.literal(detail));}catch(RuntimeException unavailable){}}
 public void tick(){if(!clock.active())return;int pulse=clock.pulse(System.nanoTime());if(pulse<0){clear();return;}if(pulse>0){var c=Minecraft.getInstance();try{if(sound!=null)c.getSoundManager().stop(sound);sound=SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL,1);c.getSoundManager().play(sound);}catch(RuntimeException unavailable){}}}
}
