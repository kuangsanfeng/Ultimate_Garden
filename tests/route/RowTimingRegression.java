import com.kuangsanfeng.ultimategarden.client.config.*;
import com.kuangsanfeng.ultimategarden.client.input.VirtualInput;
import com.kuangsanfeng.ultimategarden.client.macro.farm.*;
import com.kuangsanfeng.ultimategarden.client.macro.farm.action.FarmCommandController;
import com.kuangsanfeng.ultimategarden.client.macro.farm.crop.CropType;
import com.kuangsanfeng.ultimategarden.client.macro.farm.route.RouteController;
import java.util.List;

/** Executes production route, command, state and scheduler code with input/config boundaries. */
public final class RowTimingRegression {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Check " + checks); }
    public static void main(String[] args) {
        var state = new FarmState();
        state.setCropType(CropType.CACTUS.getValue());
        state.setModType(1);
        state.setRunning(true);
        var input = new VirtualInput();
        var pest = new PestConfig();
        pest.setPestLoadoutDelayTicks(10000);
        var command = new FarmCommandController(null, input, state, null, null, null, pest);
        var route = new RouteController(state, input, command, null, state::reset, new CropConfig());
        command.startPestLoadoutTimer();
        var workflowState = new FarmState();
        var tracked = new FarmCommandController(null, new VirtualInput(), workflowState, null, null, null, pest);
        int[] completed = {0};
        tracked.spawnMushroomMovement(false, () -> {
            completed[0]++;
            tracked.spawnMushroomMovement(true, () -> completed[0]++);
        });
        check(tracked.getWorkflow() == FarmCommandController.Workflow.SPAWN);
        tracked.pestLoadout(null); // Busy rejection must not replace the active workflow.
        check(tracked.getWorkflow() == FarmCommandController.Workflow.SPAWN);
        for (int i = 0; tracked.hasPendingWork() && i < 100; i++) {
            tracked.tick();
            if (tracked.hasPendingWork()) check(tracked.getWorkflow() == FarmCommandController.Workflow.SPAWN);
        }
        check(completed[0] == 2);
        check(tracked.getWorkflow() == FarmCommandController.Workflow.IDLE);
        tracked.markSpawnScheduled(); check(tracked.getWorkflow() == FarmCommandController.Workflow.SPAWN);
        tracked.cancel(); check(tracked.getWorkflow() == FarmCommandController.Workflow.IDLE);
        tracked.getScheduler().schedule(100, () -> { });
        check(tracked.getWorkflow() == FarmCommandController.Workflow.OTHER);
        tracked.abortWorld(); check(tracked.getWorkflow() == FarmCommandController.Workflow.IDLE);
        for (int line = 1; line <= 5; line++) {
            state.setTick(2000); // Exceed the configured threshold regardless of random jitter.
            route.tick(false);
            check(state.getTick() == 0);
            check(state.getEventCount() == line);
            check(state.isCommandRunning());
            check(state.getLineChangePhase() == FarmState.LineChangePhase.MOVEMENT);
            check(command.getWorkflow() == FarmCommandController.Workflow.LINE_CHANGE);
            int waiting = 0;
            while (state.isCommandRunning() && waiting++ < 100) {
                int remaining = command.getPestLoadoutRemainingTicks();
                command.tick();
                route.tick(false); // Same ordering as FarmController.tick.
                check(command.getPestLoadoutRemainingTicks() == remaining - 1);
                check(state.getTick() == (state.isCommandRunning() ? 0 : 1));
            }
            check(waiting < 100);
            check(state.getLineChangePhase() == FarmState.LineChangePhase.IDLE);
            check(input.transitions.equals(List.of("down:W", "up:W")));
            input.transitions.clear();
            route.tick(false);
            check(state.getTick() == 2);
        }
        // A GUI hold freezes the queue and row time, but not the independent Pest timer.
        state.setTick(17);
        command.lineChangeMovement(10, null);
        for (int i = 0; i < 20; i++) { command.tick(true, true); route.tick(false); check(state.getTick() == 17); }
        check(state.isCommandRunning());
        for (int i = 0; i < 100 && state.isCommandRunning(); i++) { command.tick(); route.tick(false); }
        check(!state.isCommandRunning());
        check(state.getTick() == 18);
        // Manual pause cancels the move; starting again cannot retain a stale row-time hold.
        command.lineChangeMovement(10, null);
        state.setRunning(false);
        command.cancel();
        check(!state.isCommandRunning());
        check(state.getLineChangePhase() == FarmState.LineChangePhase.IDLE);
        int before = state.getTick();
        route.tick(false);
        check(state.getTick() == before);
        state.setRunning(true);
        route.tick(false);
        check(state.getTick() == before + 1);
        // Discard/world reset also release the hold.
        command.lineChangeMovement(10, null); command.discardDeferredActions(); check(!state.isCommandRunning());
        command.lineChangeMovement(10, null); command.abortWorld(); check(!state.isCommandRunning());
        // Wheat and a stale eventChange from manual initialization still count normally.
        state.setCropType(CropType.WHEAT.getValue());
        state.setEventChange(true);
        state.setTick(0);
        for (int i = 0; i < 40; i++) { command.tick(); route.tick(false); }
        check(state.getTick() == 40);
        check(!command.isRunning());
        for (int duration : new int[]{1, 10, 25}) {
            for (int mask = 0; mask < 4; mask++) {
                var settings = new CropConfig();
                settings.updateCrop(CropType.WHEAT, java.util.Map.of(
                        "lineChangeMovement.enabled", Boolean.toString((mask & 1) != 0),
                        "lineChangeMovement.durationTicks", Integer.toString(duration),
                        "lineChangeYawPitch.enabled", Boolean.toString((mask & 2) != 0),
                        "lineChangeYawPitch.slot1", "7", "lineChangeYawPitch.slot2", "8"));
                var row = new FarmState(); row.setCropType(CropType.WHEAT.getValue()); row.setRunning(true);
                var controls = new VirtualInput();
                var work = new FarmCommandController(null, controls, row, null, null, null, pest);
                var routes = new RouteController(row, controls, work, null, row::reset, settings);
                // An already queued unrelated action must not cause a stage to be skipped.
                if (mask != 0) work.getScheduler().schedule(0, () -> controls.actions.add("existing"));
                for (int line = 1; line <= 2; line++) {
                    controls.actions.clear();
                    row.setTick(10000);
                    routes.tick(false);
                    check(row.getTick() == 0);
                    check(row.isCommandRunning() == (mask != 0));
                    for (int tick = 0; work.hasPendingWork() && tick < 150; tick++) {
                        controls.clock++;
                        if (row.getLineChangePhase() == FarmState.LineChangePhase.YAW_PITCH) check((mask & 2) != 0);
                        if (row.getLineChangePhase() == FarmState.LineChangePhase.MOVEMENT) check((mask & 1) != 0);
                        work.tick();
                        routes.tick(false);
                        if (row.isCommandRunning()) {
                            check(row.getTick() == 0);
                            if ((mask & 2) != 0 && controls.actions.contains("slot:" + (line % 2 == 0 ? 7 : 8)))
                                check(controls.keys.isEmpty());
                        }
                    }
                    check(!work.hasPendingWork());
                    check(!row.isCommandRunning());
                    if ((mask & 1) != 0) {
                        check(controls.upTick - controls.downTick == duration);
                        check(controls.actions.indexOf("down:W") < controls.actions.indexOf("up:W"));
                    } else check(!controls.actions.contains("down:W"));
                    if ((mask & 2) != 0) {
                        int selection = controls.actions.indexOf("slot:" + (line % 2 == 0 ? 7 : 8));
                        check(selection >= 0);
                        check(controls.actions.subList(selection + 1, selection + 5)
                                .equals(List.of("attack", "release", "slot:1", "attack")));
                        if ((mask & 1) != 0) check(controls.actions.indexOf("up:W") < selection);
                    } else check(!controls.actions.contains("attack"));
                    check(row.getTick() == (mask == 0 ? 0 : 1));
                    routes.tick(false);
                    check(row.getTick() == (mask == 0 ? 1 : 2));
                }
                if (mask != 0) {
                    row.setTick(10000); routes.tick(false);
                    // Model the existing manual-pause input release before command.cancel.
                    row.setRunning(false); routes.stopKeyboardMouse(); work.cancel();
                    int count = controls.actions.size();
                    for (int tick = 0; tick < 150; tick++) { work.tick(); routes.tick(false); }
                    check(!row.isCommandRunning());
                    check(controls.keys.isEmpty());
                    check(controls.actions.size() == count);
                }
            }
        }
        System.out.println("PASS: " + checks + " production row/command/scheduler/config checks; native input/window are stubbed.");
    }
}
