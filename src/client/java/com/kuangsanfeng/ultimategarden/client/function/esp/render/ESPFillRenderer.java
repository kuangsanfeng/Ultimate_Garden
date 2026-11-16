package com.kuangsanfeng.ultimategarden.client.function.esp.render;
import com.kuangsanfeng.ultimategarden.client.function.esp.style.ESPColorProvider;
import com.kuangsanfeng.ultimategarden.client.function.esp.style.ESPStyle;


import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ESPFillRenderer {
    private static final int[][] FACES = {{0,1,3,2}, {4,6,7,5}, {0,4,5,1}, {2,3,7,6}, {0,2,6,4}, {1,5,7,3}};
    private ESPFillRenderer() { }
    public static void draw(VertexConsumer buffer, AABB box, Vec3 camera, ESPStyle style, double time, int cycle) {
        for (int[] face : FACES) for (int corner : face) {
            double x = (corner & 1) == 0 ? box.minX : box.maxX;
            double y = (corner & 2) == 0 ? box.minY : box.maxY;
            double z = (corner & 4) == 0 ? box.minZ : box.maxZ;
            buffer.addVertex((float)(x - camera.x), (float)(y - camera.y), (float)(z - camera.z))
                    .setColor(ESPColorProvider.color(style, time, cycle, x, y, z));
        }
    }
}
