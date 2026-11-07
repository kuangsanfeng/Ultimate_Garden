package com.kuangsanfeng.ultimategarden.client.macro.esp;

import net.minecraft.world.phys.AABB;

/** Interpolated world-space bounds, without a retained entity reference. */
public record ESPTarget(String type, AABB bounds) { }
