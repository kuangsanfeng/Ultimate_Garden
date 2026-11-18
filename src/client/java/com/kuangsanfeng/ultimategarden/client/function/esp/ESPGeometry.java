package com.kuangsanfeng.ultimategarden.client.function.esp;

import com.kuangsanfeng.ultimategarden.client.function.esp.style.ESPStyle;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Immutable world-space lines, outlines and exposed faces collected at ESP extraction. */
public record ESPGeometry(List<Line> lines, List<AABB> outlines, List<Face> faces, Vec3 guide,
                          ESPStyle lineStyle, ESPStyle outlineStyle, ESPStyle faceStyle, ESPStyle guideStyle) {
    public ESPGeometry {
        lines=List.copyOf(lines);
        outlines=List.copyOf(outlines);
        faces=List.copyOf(faces);
    }

    public record Line(Vec3 from, Vec3 to) {}
    public record Face(AABB bounds, Direction outside) {}
}
