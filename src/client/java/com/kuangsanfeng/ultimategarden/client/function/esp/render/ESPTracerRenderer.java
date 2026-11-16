package com.kuangsanfeng.ultimategarden.client.function.esp.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.kuangsanfeng.ultimategarden.client.function.esp.style.ESPStyle;
import com.kuangsanfeng.ultimategarden.client.function.esp.style.ESPColorProvider;
import net.minecraft.world.phys.Vec3;

/** Supplies interpolated target positions to the screen-space tracer shader. */
public final class ESPTracerRenderer {
    private ESPTracerRenderer() { }
    public static void draw(VertexConsumer buffer, Vec3 center, Vec3 camera, ESPStyle style, double time, int cycle) {
        // Subtract in double precision before uploading camera-relative floats.
        float x = (float)(center.x - camera.x), y = (float)(center.y - camera.y), z = (float)(center.z - camera.z);
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) return;
        // Both endpoints carry the target; the shader anchors the first at viewport center.
        for (int endpoint = 0; endpoint < 2; endpoint++) {
            Vec3 colorPosition = endpoint == 0 ? camera : center;
            buffer.addVertex(x, y, z)
                    .setColor(ESPColorProvider.color(style, time, cycle, colorPosition.x, colorPosition.y, colorPosition.z))
                    .setNormal(0, 0, 0).setLineWidth(style.lineWidth());
        }
    }
}
