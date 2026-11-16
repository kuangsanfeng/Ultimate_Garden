package com.kuangsanfeng.ultimategarden.client.platform.gui;
/** Farm policy adapter: source eligibility and route editing remain business decisions. */
public final class GuiPauseState extends com.kuangsanfeng.ultimategarden.client.platform.gui.GuiSessionState {
 public enum Source {ULTIMATE_GARDEN_MENU,PLAYER_GUI}
 public void open(boolean running,Source source){super.open(running,source==Source.ULTIMATE_GARDEN_MENU);}
 public boolean canChangeRoute(){return canEditWhileOpen();}
}
