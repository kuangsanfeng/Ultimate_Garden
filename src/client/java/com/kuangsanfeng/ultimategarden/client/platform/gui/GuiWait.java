package com.kuangsanfeng.ultimategarden.client.platform.gui;
import java.util.function.Predicate;
/** Polling phase and cadence belong to the caller's existing scheduler. */
public final class GuiWait {
 private long generation;
 public boolean exhausted(int attempts,int maximum){return attempts>=maximum;}
 public <T> boolean matches(T screen,Predicate<T> expected){return expected.test(screen);}
 public long generation(){return generation;}
 public boolean current(long token){return token==generation;}
 public void cancel(){generation++;}
}
