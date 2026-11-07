import com.google.gson.*;
import com.kuangsanfeng.ultimategarden.client.config.*;
import com.kuangsanfeng.ultimategarden.client.config.RandomDelayConfig.Key;
import com.kuangsanfeng.ultimategarden.client.scheduler.ActionScheduler;
import java.nio.file.*;
import java.util.*;

public final class RandomDelayRegression {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Random delay check " + checks); }
    private static final class Ticket extends Random {
        long ticket;
        @Override public long nextLong(long bound) { check(ticket >= 0 && ticket < bound); return ticket; }
    }
    public static void main(String[] args) throws Exception {
        var config = new RandomDelayConfig();
        Key[] keys = {Key.VIEW_ATTACK, Key.VIEW_RELEASE, Key.VIEW_TOOL_SLOT, Key.VIEW_RESUME_ATTACK,
                Key.WARP, Key.SPAWN_POINT, Key.SPAWN_MUSHROOM, Key.PEST_OPEN_WAIT, Key.PEST_SELECT, Key.PEST_SETTLE,
                Key.SPAWN_LOADOUT_OPEN_WAIT, Key.SPAWN_LOADOUT_SELECT, Key.SPAWN_LOADOUT_SETTLE, Key.SPAWN_INVENTORY_KEY};
        int[][] legacy = {{100,150},{100,150},{100,150},{100,150},{800,800},{300,150},{150,150},
                {50,50},{150,200},{100,50},{50,50},{150,200},{100,50},{150,250}};
        var ticket = new Ticket();
        for (int i = 0; i < keys.length; i++) {
            for (int offset = 0; offset < legacy[i][1]; offset++) {
                ticket.ticket = offset;
                int expected = (legacy[i][0] + offset + 49) / 50;
                check(config.sample(keys[i], ticket) == expected);
            }
        }
        for (Key key : Key.values()) {
            config.set(key, 0, 0); check(config.sample(key) == 0);
            config.set(key, 7, 7); check(config.sample(key) == 7);
            config.set(key, 3, 8);
            Set<Integer> observed = new HashSet<>();
            var random = new Random(123);
            for (int i = 0; i < 1000; i++) { int value = config.sample(key, random); check(value >= 3 && value <= 8); observed.add(value); }
            check(observed.size() == 6);
            var before = config.get(key);
            for (int[] invalid : new int[][]{{-1,2},{2,1}}) {
                try { config.set(key, invalid[0], invalid[1]); throw new AssertionError("Invalid range accepted"); }
                catch (IllegalArgumentException expected) { check(config.get(key).equals(before)); }
            }
        }
        // Legacy millisecond -> tick countdown equivalence, including the execution tick.
        for (int ticks = 0; ticks < 34; ticks++) {
            var oldQueue = new ActionScheduler(); var tickQueue = new ActionScheduler();
            List<Integer> oldEvents = new ArrayList<>(), newEvents = new ArrayList<>(); int[] now = {0};
            oldQueue.schedule(ticks * 50L, () -> oldEvents.add(now[0]));
            tickQueue.scheduleDelayTicks(ticks, () -> newEvents.add(now[0]));
            for (; now[0] < 40; now[0]++) { oldQueue.tick(); tickQueue.tick(); }
            check(oldEvents.equals(newEvents));
        }
        Path root = Files.createTempDirectory("ug-random-delay-");
        var manager = new ConfigManager(root);
        check(Files.exists(root.resolve("random_delay.json")));
        manager.getRandomDelayConfig().set(Key.WARP, 0, 0);
        manager.getRandomDelayConfig().set(Key.VIEW_ATTACK, 5, 12);
        check(manager.save(ConfigManager.Module.RANDOM_DELAY));
        var loaded = new ConfigManager(root);
        check(loaded.getRandomDelayConfig().get(Key.WARP).equals(new RandomDelayConfig.Range(0,0)));
        check(loaded.getRandomDelayConfig().get(Key.VIEW_ATTACK).equals(new RandomDelayConfig.Range(5,12)));
        JsonObject partial = new JsonObject(); JsonObject warp = new JsonObject(); warp.addProperty("minTicks", 19);
        partial.add("WARP",warp); partial.addProperty("extra", "preserved");
        check(!config.load(partial)); check(config.get(Key.WARP).equals(new RandomDelayConfig.Range(19,32)));
        check(config.toJson(partial).get("extra").getAsString().equals("preserved"));
        warp.addProperty("maxTicks", -1); check(config.load(partial)); check(config.get(Key.WARP).equals(Key.WARP.defaults()));
        check(config.get(Key.VIEW_ATTACK).equals(Key.VIEW_ATTACK.defaults()));
        var future = new JsonObject(); future.addProperty("schemaVersion",99); Files.writeString(root.resolve("random_delay.json"),future.toString());
        loaded = new ConfigManager(root); check(!loaded.save(ConfigManager.Module.RANDOM_DELAY));
        check(Files.readString(root.resolve("random_delay.json")).equals(future.toString()));
        System.out.println("PASS: " + checks + " random Tick range, exact default distribution, queue equivalence and persistence checks.");
    }
}
