package com.kuangsanfeng.ultimategarden.client.macro.esp.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.kuangsanfeng.ultimategarden.client.macro.esp.style.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ESPBoxRenderer {
    private ESPBoxRenderer() { }
    public static void draw(VertexConsumer buffer, AABB box, Vec3 camera, ESPStyle style, double time, int cycle) {
        for (int axis = 0; axis < 3; axis++) for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) {
            double x = axis == 0 ? box.minX : (a == 0 ? box.minX : box.maxX);
            double y = axis == 1 ? box.minY : ((axis == 0 ? a : b) == 0 ? box.minY : box.maxY);
            double z = axis == 2 ? box.minZ : (b == 0 ? box.minZ : box.maxZ);
            line(buffer, x, y, z, axis == 0 ? box.maxX : x, axis == 1 ? box.maxY : y, axis == 2 ? box.maxZ : z,
                    camera, style, time, cycle);
        }
    }
    public static void line(VertexConsumer buffer, double x, double y, double z, double ex, double ey, double ez,
                            Vec3 camera, ESPStyle style, double time, int cycle) {
        double dx = ex - x, dy = ey - y, dz = ez - z, length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(length) || length < 1e-8) return;
        float nx = (float)(dx / length), ny = (float)(dy / length), nz = (float)(dz / length);
        buffer.addVertex((float)(x - camera.x), (float)(y - camera.y), (float)(z - camera.z))
                .setColor(ESPColorProvider.color(style, time, cycle, x, y, z)).setNormal(nx, ny, nz).setLineWidth(style.lineWidth());
        buffer.addVertex((float)(ex - camera.x), (float)(ey - camera.y), (float)(ez - camera.z))
                .setColor(ESPColorProvider.color(style, time, cycle, ex, ey, ez)).setNormal(nx, ny, nz).setLineWidth(style.lineWidth());
    }
}
