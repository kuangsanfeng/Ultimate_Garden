
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.ESPConfig;
import com.kuangsanfeng.ultimategarden.client.runtime.world.esp.render.ESPBoxRenderer;
import com.kuangsanfeng.ultimategarden.client.runtime.world.esp.render.ESPFillRenderer;
import com.kuangsanfeng.ultimategarden.client.runtime.world.esp.render.ESPTracerRenderer;
import com.kuangsanfeng.ultimategarden.client.runtime.world.esp.style.ESPColorMode;
import com.kuangsanfeng.ultimategarden.client.runtime.world.esp.style.ESPColorProvider;
import com.kuangsanfeng.ultimategarden.client.runtime.world.esp.style.ESPStyle;
import com.kuangsanfeng.ultimategarden.client.runtime.world.esp.style.ESPTargetStyle;
import com.google.gson.*;



import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.*;
import java.nio.file.*;
import java.util.*;

/** Actual config, color, geometry and projection code; no GPU or game connection. */
public final class ESPRegression {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("ESP check " + checks); }
    private static void invalid(Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected);
    }
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("ultimate-garden-esp-");
        ConfigManager manager = new ConfigManager(dir);
        check(Files.exists(dir.resolve("esp.json")));
        check(manager.getPestConfig().isPestESPEnabled());
        check(manager.getPestConfig().isAutoPestLoadout() && manager.getPestConfig().getPestLoadoutDelayTicks() == 2500);
        var esp = manager.getESPConfig();
        check(esp.isThroughWalls() && esp.getTarget(ESPConfig.SILVERFISH).getFill().getColorMode() == ESPColorMode.RAINBOW);
        check(esp.getTarget(ESPConfig.SILVERFISH).getBox().getLineWidth() == 2 && esp.getTarget(ESPConfig.SILVERFISH).getTracer().getLineWidth() == 1.5f);
        manager.getPestConfig().setPestESPEnabled(false); manager.save(ConfigManager.Module.PEST);
        esp.getTarget(ESPConfig.SILVERFISH).getBox().setLineWidth(7); esp.getTarget(ESPConfig.SILVERFISH).getFill().setAlpha(0.4f); esp.setCycleDurationMs(7250); manager.save(ConfigManager.Module.ESP);
        manager = new ConfigManager(dir);
        check(!manager.getPestConfig().isPestESPEnabled());
        check(manager.getPestConfig().isAutoPestLoadout() && manager.getPestConfig().getPestLoadoutDelayTicks() == 2500);
        check(manager.getESPConfig().getTarget(ESPConfig.SILVERFISH).getBox().getLineWidth() == 7 && manager.getESPConfig().getTarget(ESPConfig.SILVERFISH).getFill().getAlpha() == 0.4f);
        check(manager.getESPConfig().getCycleDurationMs() == 7250);
        invalid(() -> esp.getTarget(ESPConfig.SILVERFISH).getBox().setAlpha(Float.NaN)); invalid(() -> esp.getTarget(ESPConfig.SILVERFISH).getBox().setAlpha(1.1f));
        invalid(() -> esp.getTarget(ESPConfig.SILVERFISH).getBox().setLineWidth(0)); invalid(() -> esp.getTarget(ESPConfig.SILVERFISH).getBox().setLineWidth(Float.POSITIVE_INFINITY));
        invalid(() -> esp.getTarget(ESPConfig.SILVERFISH).getBox().setColor("red")); invalid(() -> esp.setCycleDurationMs(0));
        String bad = "{\"schemaVersion\":1,\"throughWalls\":false,\"extra\":42,\"box\":{\"color\":\"#123456\",\"alpha\":\"wrong\",\"future\":9},\"fill\":{\"alpha\":0.6},\"tracer\":[],\"rainbow\":{\"cycleDurationMs\":-2}}";
        Files.writeString(dir.resolve("esp.json"), bad);
        manager = new ConfigManager(dir);
        check(!manager.getESPConfig().isThroughWalls()); check(manager.getESPConfig().getTarget(ESPConfig.SILVERFISH).getBox().getColor().equals("#123456"));
        check(manager.getESPConfig().getTarget(ESPConfig.SILVERFISH).getBox().getAlpha() == 1 && manager.getESPConfig().getTarget(ESPConfig.SILVERFISH).getFill().getAlpha() == 0.6f);
        check(manager.getESPConfig().getCycleDurationMs() == 3000);
        try (var files = Files.list(dir)) { check(files.anyMatch(p -> p.getFileName().toString().startsWith("esp.json.migrated-"))); }
        var saved = JsonParser.parseString(Files.readString(dir.resolve("esp.json"))).getAsJsonObject();
        check(saved.get("extra").getAsInt() == 42 && saved.getAsJsonObject("targets").getAsJsonObject("SILVERFISH").getAsJsonObject("box").get("future").getAsInt() == 9);
        String future = "{\"schemaVersion\":9,\"box\":{\"future\":true}}";
        Files.writeString(dir.resolve("esp.json"), future); manager = new ConfigManager(dir);
        check(!manager.save(ConfigManager.Module.ESP)); check(Files.readString(dir.resolve("esp.json")).equals(future));
        Files.writeString(dir.resolve("esp.json"), "{"); manager = new ConfigManager(dir);
        check(manager.getESPConfig().isThroughWalls());
        Files.delete(dir.resolve("esp.json")); manager = new ConfigManager(dir); check(Files.exists(dir.resolve("esp.json")));
        var targetA = manager.getESPConfig().getTarget(ESPConfig.SILVERFISH);
        var targetB = manager.getESPConfig().registerTarget("TEST_TARGET");
        check(targetA != targetB && targetA.getBox() != targetB.getBox() && targetA.getFill() != targetB.getFill() && targetA.getTracer() != targetB.getTracer());
        var captured = targetA.snapshot();
        targetB.getBox().setColor("#ABCDEF"); targetB.getFill().setEnabled(false); targetB.getTracer().setLineWidth(8);
        check(targetA.getBox().getColor().equals("#00FFAA") && targetA.getFill().isEnabled() && targetA.getTracer().getLineWidth() == 1.5f);
        targetA.getBox().setEnabled(false); targetA.getTracer().setColorMode(ESPColorMode.RAINBOW);
        check(captured.box().enabled() && captured.tracer().colorMode() == ESPColorMode.SOLID);
        check(targetB.getBox().isEnabled() && targetB.getTracer().getColorMode() == ESPColorMode.SOLID);
        check(manager.save(ConfigManager.Module.ESP)); manager = new ConfigManager(dir);
        check(manager.getESPConfig().getTarget("TEST_TARGET").getBox().getColor().equals("#ABCDEF"));
        check(!manager.getESPConfig().getTarget("TEST_TARGET").getFill().isEnabled());
        check(!manager.getESPConfig().getTarget(ESPConfig.SILVERFISH).getBox().isEnabled());
        check(manager.getESPConfig().getTarget(ESPConfig.SILVERFISH).getTracer().getColorMode() == ESPColorMode.RAINBOW);
        Path migration = dir.resolve("migration"); Files.createDirectories(migration);
        String legacy = "{\"schemaVersion\":1,\"throughWalls\":false,\"box\":{\"enabled\":false,\"colorMode\":\"RAINBOW\",\"color\":\"#123456\",\"alpha\":0.6,\"lineWidth\":5,\"extra\":9},\"fill\":{\"colorMode\":\"SOLID\",\"alpha\":0.45},\"tracer\":{\"lineWidth\":4.5},\"rainbow\":{\"cycleDurationMs\":1234}}";
        Files.writeString(migration.resolve("esp.json"), legacy);
        ConfigManager migrated = new ConfigManager(migration);
        var silverfish = migrated.getESPConfig().getTarget(ESPConfig.SILVERFISH);
        check(!migrated.getESPConfig().isThroughWalls() && migrated.getESPConfig().getCycleDurationMs() == 1234);
        check(!silverfish.getBox().isEnabled() && silverfish.getBox().getColorMode() == ESPColorMode.RAINBOW);
        check(silverfish.getBox().getColor().equals("#123456") && silverfish.getBox().getAlpha() == 0.6f && silverfish.getBox().getLineWidth() == 5);
        check(silverfish.getFill().getColorMode() == ESPColorMode.SOLID && silverfish.getFill().getAlpha() == 0.45f);
        check(silverfish.getTracer().getLineWidth() == 4.5f);
        try (var files = Files.list(migration)) {
            var backups = files.filter(p -> p.getFileName().toString().startsWith("esp.json.migrated-")).toList();
            check(backups.size() == 1); check(Files.readString(backups.getFirst()).equals(legacy));
        }
        JsonObject canonical = JsonParser.parseString(Files.readString(migration.resolve("esp.json"))).getAsJsonObject();
        check(canonical.get("schemaVersion").getAsInt() == 2);
        check(!canonical.has("box") && !canonical.has("fill") && !canonical.has("tracer") && !canonical.has("throughWalls") && !canonical.has("rainbow"));
        check(canonical.getAsJsonObject("targets").getAsJsonObject("SILVERFISH").getAsJsonObject("box").get("extra").getAsInt() == 9);
        new ConfigManager(migration);
        try (var files = Files.list(migration)) { check(files.filter(p -> p.getFileName().toString().startsWith("esp.json.migrated-")).count() == 1); }
        JsonObject mixed = JsonParser.parseString(legacy).getAsJsonObject();
        mixed.add("general", JsonParser.parseString("{\"throughWalls\":true,\"rainbowCycleDurationMs\":4321}"));
        mixed.add("targets", JsonParser.parseString("{\"SILVERFISH\":{\"box\":{\"color\":\"#FEDCBA\",\"alpha\":0.8,\"lineWidth\":\"bad\"}},\"TEST_TARGET\":{\"box\":{\"color\":\"#010203\"}}}"));
        Files.writeString(migration.resolve("esp.json"), mixed.toString()); migrated = new ConfigManager(migration);
        silverfish = migrated.getESPConfig().getTarget(ESPConfig.SILVERFISH);
        check(migrated.getESPConfig().isThroughWalls() && migrated.getESPConfig().getCycleDurationMs() == 4321);
        check(silverfish.getBox().getColor().equals("#FEDCBA") && silverfish.getBox().getAlpha() == 0.8f);
        check(silverfish.getBox().getLineWidth() == 5);
        check(migrated.getESPConfig().getTarget("TEST_TARGET").getBox().getColor().equals("#010203"));
        var mutableTargets = new ArrayList<com.kuangsanfeng.ultimategarden.client.runtime.world.esp.ESPTarget>();
        mutableTargets.add(new com.kuangsanfeng.ultimategarden.client.runtime.world.esp.ESPTarget(ESPConfig.SILVERFISH, new AABB(0,0,0,1,1,1)));
        var mutableStyles = new HashMap<String, ESPTargetStyle>(); mutableStyles.put(ESPConfig.SILVERFISH, silverfish.snapshot());
        var frame = new com.kuangsanfeng.ultimategarden.client.runtime.world.esp.ESPManager.Frame(mutableTargets, mutableStyles, true, 3000, 0, 0);
        mutableTargets.clear(); mutableStyles.clear(); check(frame.targets().size() == 1 && frame.styles().size() == 1);
        check(frame.styles().get(frame.targets().getFirst().type()).box().rgb() == 0xFEDCBA);
        Files.writeString(migration.resolve("esp.json"), "{\"schemaVersion\":2,\"general\":{\"throughWalls\":false},\"targets\":{\"SILVERFISH\":{\"box\":{\"color\":\"#654321\",\"alpha\":-1}}}}");
        migrated = new ConfigManager(migration);
        check(migrated.getESPConfig().getTarget(ESPConfig.SILVERFISH).getBox().getColor().equals("#654321"));
        check(migrated.getESPConfig().getTarget(ESPConfig.SILVERFISH).getBox().getAlpha() == 1);
        try (var files = Files.list(migration)) { check(files.anyMatch(p -> p.getFileName().toString().startsWith("esp.json.broken-"))); }
        ESPStyle solid = new ESPStyle(true, ESPColorMode.SOLID, 0x123456, 0.25f, 3);
        check(ESPColorProvider.color(solid, 0, 3000, 0, 0, 0) == 0x40123456);
        ESPStyle rainbow = new ESPStyle(true, ESPColorMode.RAINBOW, 0, 1, 3);
        check(ESPColorProvider.color(rainbow, 0, 3000, 0, 0, 0) == ESPColorProvider.color(rainbow, 3000, 3000, 0, 0, 0));
        check(ESPColorProvider.color(rainbow, 0, 3000, 0, 0, 0) != ESPColorProvider.color(rainbow, 0, 3000, 1, 1, 1));
        check(ESPColorProvider.color(rainbow, 500, 3000, 0, 0, 0) != ESPColorProvider.color(rainbow, 0, 3000, 0, 0, 0));
        Capture vertices = new Capture(); AABB bounds = new AABB(10, 20, 30, 11, 22, 33); Vec3 camera = new Vec3(10, 20, 30);
        ESPFillRenderer.draw(vertices, bounds, camera, rainbow, 0, 3000);
        check(vertices.points.size() == 24); check(new HashSet<>(vertices.colors).size() > 1);
        for (int face = 0; face < 6; face++) {
            var quad = vertices.points.subList(face * 4, face * 4 + 4);
            boolean planar = false;
            for (int axis = 0; axis < 3; axis++) { int a = axis; planar |= quad.stream().allMatch(p -> p[a] == quad.getFirst()[a]); }
            check(planar);
        }
        vertices = new Capture(); ESPBoxRenderer.draw(vertices, bounds, camera, solid, 0, 3000);
        check(vertices.points.size() == 24 && vertices.widths.size() == 24);
        check(vertices.widths.stream().allMatch(w -> w == 3));
        Set<String> edges = new HashSet<>();
        for (int i = 0; i < 24; i += 2) edges.add(Arrays.toString(vertices.points.get(i)) + Arrays.toString(vertices.points.get(i + 1)));
        check(edges.size() == 12);
        vertices = new Capture(); ESPTracerRenderer.draw(vertices, new Vec3(2, 1, -5), Vec3.ZERO, solid, 0, 3000);
        check(vertices.points.size() == 2);
        for (float[] point : vertices.points) for (float coordinate : point) check(Float.isFinite(coordinate));
        check(Arrays.equals(vertices.points.get(0), new float[]{2,1,-5}));
        check(Arrays.equals(vertices.points.get(0), vertices.points.get(1)));
        vertices = new Capture(); ESPTracerRenderer.draw(vertices, new Vec3(Double.NaN, 0, -5), Vec3.ZERO, solid, 0, 3000);
        check(vertices.points.isEmpty());
        vertices = new Capture(); ESPTracerRenderer.draw(vertices, new Vec3(30000000.25, 70.125, -29999999.5), new Vec3(30000000, 70, -30000000), solid, 0, 3000);
        check(Arrays.equals(vertices.points.get(0), new float[]{0.25f,0.125f,0.5f}));
        System.out.println("PASS: " + checks + " ESP persistence/color/geometry/target upload checks; no GPU runtime assertions.");
    }
    private static final class Capture implements VertexConsumer {
        final List<float[]> points = new ArrayList<>(); final List<Integer> colors = new ArrayList<>(); final List<Float> widths = new ArrayList<>();
        public VertexConsumer addVertex(float x, float y, float z) { points.add(new float[]{x,y,z}); return this; }
        public VertexConsumer setColor(int color) { colors.add(color); return this; }
        public VertexConsumer setColor(int r, int g, int b, int a) { return setColor(a << 24 | r << 16 | g << 8 | b); }
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
        public VertexConsumer setLineWidth(float value) { widths.add(value); return this; }
        public VertexConsumer setUv(float u, float v) { return this; }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) { return this; }
    }
}
