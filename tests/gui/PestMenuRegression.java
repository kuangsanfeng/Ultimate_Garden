
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.farm.PestConfig;
import com.kuangsanfeng.ultimategarden.client.macro.farm.FarmState;
import com.kuangsanfeng.ultimategarden.client.macro.farm.action.FarmCommandController;
import java.util.*;

/** Executes the real command/timer code; game operations are never dispatched. */
public final class PestMenuRegression {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Check " + checks); }
    public static void main(String[] args) {
        var state = new FarmState(); state.setModType(1);
        var config = new PestConfig(); config.setAutoPestLoadout(false); config.setPestLoadoutDelayTicks(3);
        var command = new FarmCommandController(null, null, state, null, null, null, config);
        List<String> actions = new ArrayList<>();
        command.getScheduler().schedule(0, () -> actions.add("existing"));
        command.startPestLoadoutTimer(); // Manual timer still works when automation is disabled.
        check(command.isPestLoadoutTimerRunning());
        command.tick(true, true);
        check(command.getPestLoadoutRemainingTicks() == 2);
        for (int i = 0; i < 20; i++) command.tick(true, true);
        check(!command.isPestLoadoutTimerRunning());
        check(command.isPestLoadoutPending());
        check(actions.isEmpty());
        check(command.getScheduler().isPaused());
        command.tick(false, true);
        check(actions.equals(List.of("existing")));
        check(command.isPestLoadoutPending());
        check(command.getWorkflow() == FarmCommandController.Workflow.PEST_LOADOUT);
        check(command.getScheduler().isRunning()); // Loadout is queued after the existing action.
        command.abortWorld();
        check(!command.hasPendingWork());
        check(command.getWorkflow() == FarmCommandController.Workflow.IDLE);
        check(!command.isPestLoadoutTimerRunning());

        command.startPestLoadoutTimer();
        for (int i = 0; i < 3; i++) command.tick(true, true);
        command.cancelPestLoadoutTimer();
        check(!command.isPestLoadoutPending());
        check(command.hasActionFailed()); // Cancelling deferred work blocks automatic route recovery.
        for (boolean running : new boolean[] {false, true}) {
            state.setRunning(running);
            int[] pauses = {0};
            var independent = new FarmCommandController(null, null, state,
                    () -> { pauses[0]++; state.setRunning(false); }, null, null, config);
            independent.startPestLoadoutTimer();
            for (int i = 0; i < 3; i++) independent.tick();
            check(!independent.isPestLoadoutTimerRunning());
            check(independent.getScheduler().isRunning());
            check(independent.getWorkflow() == FarmCommandController.Workflow.PEST_LOADOUT);
            check(!independent.getScheduler().isPaused());
            check(independent.isBusinessResumePending() == running);
            check(pauses[0] == (running ? 1 : 0));
            independent.abortWorld(); // Stop before dispatching game operations.
        }
        System.out.println("PASS: " + checks + " real command/timer boundary checks: GUI expiry, frozen queue, deferred dispatch, manual timer, cancellation and world cleanup.");
    }
}
