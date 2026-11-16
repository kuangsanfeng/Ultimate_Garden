package com.kuangsanfeng.ultimategarden.client.platform.ui;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import com.kuangsanfeng.ultimategarden.Ultimate_Garden;
public final class UiRegistry {
 private static final Set<String> hudIds=new HashSet<>();
 private static final Map<String,java.util.function.Supplier<net.minecraft.client.gui.screens.Screen>> menus=new LinkedHashMap<>();
 private UiRegistry(){}
 public static void menu(String id,java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> factory){
  if(menus.putIfAbsent(id,java.util.Objects.requireNonNull(factory))!=null)throw new IllegalArgumentException("Duplicate menu: "+id);
 }
 public static void openMenu(String id){
  var factory=menus.get(id);if(factory==null)throw new IllegalArgumentException("Unknown menu: "+id);
  net.minecraft.client.Minecraft.getInstance().setScreen(factory.get());
 }
 public static void hud(String id,Consumer<GuiGraphicsExtractor> draw){
  if(!hudIds.add(id))throw new IllegalArgumentException("Duplicate HUD: "+id);
  HudElementRegistry.addLast(Ultimate_Garden.id(id),(graphics,delta)->draw.accept(graphics));
 }
}
