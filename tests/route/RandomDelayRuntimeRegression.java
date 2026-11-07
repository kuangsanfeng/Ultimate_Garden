import com.kuangsanfeng.ultimategarden.client.config.*;
import com.kuangsanfeng.ultimategarden.client.config.RandomDelayConfig.Key;
import com.kuangsanfeng.ultimategarden.client.input.VirtualInput;
import com.kuangsanfeng.ultimategarden.client.macro.farm.*;
import com.kuangsanfeng.ultimategarden.client.macro.farm.action.FarmCommandController;
import com.kuangsanfeng.ultimategarden.client.macro.farm.route.*;
import com.kuangsanfeng.ultimategarden.client.scheduler.ActionScheduler;
import java.util.*;

public final class RandomDelayRuntimeRegression {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Random call-site check " + checks); }
    public static void main(String[] args) {
        var config = new RandomDelayConfig(); var input = new VirtualInput(); var queue = new ActionScheduler();
        var view = new SetLineYP(input, queue, () -> config);
        Key[] keys = {Key.VIEW_ATTACK,Key.VIEW_RELEASE,Key.VIEW_TOOL_SLOT,Key.VIEW_RESUME_ATTACK};
        for (int i = 0; i < keys.length; i++) config.set(keys[i], i + 2, i + 2);
        boolean[] completed = {false}; view.setAlternate(1, 7, 8, () -> completed[0] = true);
        List<Integer> times = new ArrayList<>(); int count = input.actions.size();
        for (int tick = 1; tick <= 18; tick++) {
            queue.tick();
            if (count != input.actions.size()) { times.add(tick); count = input.actions.size(); }
        }
        check(completed[0]); check(times.equals(List.of(3,7,12,18)));
        check(input.actions.equals(List.of("slot:8","attack","release","slot:1","attack")));
        for (Key key : keys) config.set(key, 0, 0);
        completed[0] = false; input.actions.clear(); view.setAlternate(2, 7, 8, () -> completed[0] = true);
        queue.setPaused(true); for (int i = 0; i < 20; i++) queue.tick();
        check(input.actions.equals(List.of("slot:7"))); check(!completed[0]);
        queue.setPaused(false); for (int i = 0; i < 4; i++) queue.tick();
        check(completed[0]); check(input.actions.equals(List.of("slot:7","attack","release","slot:1","attack")));
        var state = new FarmState(); var commands = new FarmCommandController(null,input,state,null,null,null,new PestConfig());
        commands.setRandomDelays(config); config.set(Key.SPAWN_MUSHROOM,4,4);
        completed[0] = false;
        commands.spawnMushroomMovement(true, () -> completed[0] = true);
        for (int i = 0; i < 4; i++) { commands.tick(); check(!completed[0]); }
        commands.tick(); check(completed[0]); check(!input.keys.contains("S") && !input.keys.contains("D"));
        // The running route reads the same global config, not per-crop jitter settings.
        var crops = new CropConfig();
        crops.updateCrop(com.kuangsanfeng.ultimategarden.client.macro.farm.crop.CropType.WHEAT, Map.of("tickLimit","1"));
        var route = new RouteController(state,input,commands,null,state::reset,crops);
        state.setCropType(1); state.setRunning(true); config.set(Key.FARM_ROW_JITTER,5,5);
        for (int i = 0; i < 5; i++) { route.tick(false); check(state.getEventCount() == 0); }
        route.tick(false); check(state.getEventCount() == 1);
        config.set(Key.FARM_ROW_JITTER,0,0); route.tick(false); check(state.getEventCount() == 2);
        System.out.println("PASS: " + checks + " production random-delay call-site and live-update checks; input/window are stubbed.");
    }
}
