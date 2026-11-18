package com.kuangsanfeng.ultimategarden.client.function.esp;
import com.kuangsanfeng.ultimategarden.client.function.esp.ESPRenderer;
import com.kuangsanfeng.ultimategarden.client.function.esp.ESPTarget;


import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.function.esp.style.ESPTargetStyle;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import java.util.*;
import java.util.function.*;

/** One registered manager; new modules register only an extraction callback. */
public final class ESPManager {
    private static final RenderStateDataKey<Frame> FRAME = RenderStateDataKey.create(() -> "ultimate_garden:esp");
    public record Frame(List<ESPTarget> targets, Map<String, ESPTargetStyle> styles, List<ESPGeometry> geometry,
                        boolean throughWalls, int cycleMs, double timeMs, long generation) {
        public Frame { targets = List.copyOf(targets); styles = Map.copyOf(styles); geometry = List.copyOf(geometry); }
    }
    private final ConfigManager configs;
    private final ESPRenderer renderer = new ESPRenderer();
    private final Map<String, BiConsumer<LevelExtractionContext, Consumer<ESPTarget>>> modules = new LinkedHashMap<>();
    private final Map<String,ESPTargetStyle> fixedStyles=new LinkedHashMap<>();
    private final List<Supplier<ESPGeometry>> geometryModules=new ArrayList<>();
    public void registerGeometry(Supplier<ESPGeometry> module) {
        if (registered) throw new IllegalStateException("Register ESP modules before rendering starts");
        geometryModules.add(Objects.requireNonNull(module));
    }
    public void registerFixed(String id,ESPTargetStyle style,BiConsumer<LevelExtractionContext,Consumer<ESPTarget>> module){
        if(registered||modules.containsKey(id))throw new IllegalStateException("ESP registration closed or duplicate");
        fixedStyles.put(id,style);modules.put(id,module);
    }
    private volatile long generation;
    private boolean registered;
    public ESPManager(ConfigManager configs) { this.configs = configs; }
    public void invalidateFrames() { generation++; }
    public void register(String id, BiConsumer<LevelExtractionContext, Consumer<ESPTarget>> module) {
        if (registered) throw new IllegalStateException("Register ESP modules before rendering starts");
        if (modules.containsKey(id)) throw new IllegalArgumentException("Duplicate ESP module: " + id);
        boolean newTarget = !configs.getESPConfig().hasTarget(id);
        configs.getESPConfig().registerTarget(id);
        modules.put(id, module);
        if (newTarget) configs.save(ConfigManager.Module.ESP);
    }
    public void initialize() {
        if (registered) throw new IllegalStateException("ESP events already registered");
        registered = true;
        LevelRenderEvents.END_EXTRACTION.register(this::extract);
        LevelRenderEvents.END_MAIN.register(context -> {
            Frame frame = context.levelState().getData(FRAME);
            if (frame != null && frame.generation() == generation) renderer.draw(context, frame);
        });
        com.kuangsanfeng.ultimategarden.client.platform.context.ClientContextEvents.onDisconnect((handler, client) -> generation++);
        com.kuangsanfeng.ultimategarden.client.platform.context.ClientContextEvents.onStopping(client -> { generation++; renderer.close(); });
    }
    private void extract(LevelExtractionContext context) {
        var config = configs.getESPConfig();
        if (context.level() == null || !context.levelState().cameraRenderState.initialized) {
            context.levelState().setData(FRAME, null); return;
        }
        List<ESPTarget> targets = new ArrayList<>();
        Map<String, ESPTargetStyle> styles = new LinkedHashMap<>();
        for (var module : modules.entrySet()) {
            var style = fixedStyles.containsKey(module.getKey())?fixedStyles.get(module.getKey()):config.registerTarget(module.getKey()).snapshot();
            if (!style.hasVisibleComponent()) continue;
            styles.put(module.getKey(), style);
            module.getValue().accept(context, target -> {
                if (!module.getKey().equals(target.type())) throw new IllegalArgumentException("ESP target type does not match its registered module");
                targets.add(target);
            });
        }
        var geometry=geometryModules.stream().map(Supplier::get).filter(Objects::nonNull).toList();
        context.levelState().setData(FRAME, new Frame(targets, styles, geometry,
                config.isThroughWalls(), config.getCycleDurationMs(), System.nanoTime() / 1_000_000.0, generation));
    }
}
