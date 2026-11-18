package com.kuangsanfeng.ultimategarden.client.mixin;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.client.gui.screens.Screen;
@Mixin(Screen.class) public interface ScreenRebuildAccess {@Invoker("rebuildWidgets") void ug$rebuildWidgets();}
