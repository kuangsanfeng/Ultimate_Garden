package com.kuangsanfeng.ultimategarden.client.runtime.world.esp;
import com.kuangsanfeng.ultimategarden.client.runtime.world.esp.ESPManager;
import com.kuangsanfeng.ultimategarden.client.runtime.world.esp.render.ESPBoxRenderer;
import com.kuangsanfeng.ultimategarden.client.runtime.world.esp.render.ESPFillRenderer;
import com.kuangsanfeng.ultimategarden.client.runtime.world.esp.render.ESPTracerRenderer;


import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.resources.Identifier;
import java.util.Optional;

/** Uses vanilla-managed GPU uploads and isolated, non-depth-writing pipelines. */
public final class ESPRenderer implements AutoCloseable {
    private static RenderType type(String name, boolean line, boolean through) {
        var builder = RenderPipeline.builder(line ? RenderPipelines.LINES_SNIPPET : RenderPipelines.DEBUG_FILLED_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("ultimate_garden", "pipeline/esp_" + name)).withCull(false);
        // Keep the native screen-width line shader; ESP colors are not fogged.
        if (line) builder.withFragmentShader("core/position_color");
        if (through) builder.withDepthStencilState(Optional.empty());
        else builder.withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(com.mojang.blaze3d.platform.CompareOp.LESS_THAN_OR_EQUAL, false));
        var setup = RenderSetup.builder(RenderPipelines.register(builder.build())).bufferSize(32768);
        if (!line) setup.sortOnUpload();
        return RenderType.create("ultimate_garden_esp_" + name, setup.createRenderSetup());
    }
    private static final RenderType FILL = type("fill_depth", false, false), FILL_THROUGH = type("fill_through", false, true);
    private static final RenderType BOX = type("box_depth", true, false), BOX_THROUGH = type("box_through", true, true);
    private static final RenderType TRACER = RenderType.create("ultimate_garden_esp_tracer",
            RenderSetup.builder(RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("ultimate_garden", "pipeline/esp_tracer"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("ultimate_garden", "core/esp_tracer"))
                    .withFragmentShader("core/position_color")
                    .withDepthStencilState(Optional.empty()).withCull(false).build()))
                    .bufferSize(32768).createRenderSetup());
    private ByteBufferBuilder allocator;
    private MultiBufferSource.BufferSource buffers;

    public void draw(LevelRenderContext context, ESPManager.Frame frame) {
        if (frame.targets().isEmpty()) return;
        if (allocator == null) { allocator = new ByteBufferBuilder(32768); buffers = MultiBufferSource.immediate(allocator); }
        var camera = context.levelState().cameraRenderState;
        RenderType fillType = frame.throughWalls() ? FILL_THROUGH : FILL;
        RenderType boxType = frame.throughWalls() ? BOX_THROUGH : BOX;
        {
            var vertices = buffers.getBuffer(fillType);
            for (var target : frame.targets()) {
                var styles = frame.styles().get(target.type());
                if (styles != null && styles.fill().enabled() && styles.fill().alpha() > 0)
                    ESPFillRenderer.draw(vertices, target.bounds(), camera.pos, styles.fill(), frame.timeMs(), frame.cycleMs());
            }
            buffers.endBatch(fillType);
        }
        {
            var vertices = buffers.getBuffer(boxType);
            for (var target : frame.targets()) {
                var styles = frame.styles().get(target.type());
                if (styles != null && styles.box().enabled() && styles.box().alpha() > 0)
                    ESPBoxRenderer.draw(vertices, target.bounds(), camera.pos, styles.box(), frame.timeMs(), frame.cycleMs());
            }
            buffers.endBatch(boxType);
        }
        {
            var vertices = buffers.getBuffer(TRACER);
            for (var target : frame.targets()) {
                var styles = frame.styles().get(target.type());
                if (styles != null && styles.tracer().enabled() && styles.tracer().alpha() > 0)
                    ESPTracerRenderer.draw(vertices, target.bounds().getCenter(), camera.pos, styles.tracer(), frame.timeMs(), frame.cycleMs());
            }
            buffers.endBatch(TRACER);
        }
    }
    @Override public void close() {
        if (allocator != null) { allocator.close(); allocator = null; buffers = null; }
    }
}
