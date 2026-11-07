import com.kuangsanfeng.ultimategarden.client.macro.GuiPauseState;
import com.kuangsanfeng.ultimategarden.client.scheduler.ActionScheduler;
import java.util.*;

/** Standalone tests for the actual menu policy and scheduler, without a game renderer. */
public final class GuiLifecycleRegression {
    private static int assertions;
    private static void check(boolean value) { assertions++; if (!value) throw new AssertionError("Check " + assertions); }
    public static void main(String[] args) {
        GuiPauseState state = new GuiPauseState();
        state.open(true); state.close(false); check(!state.finish());
        state.open(true); state.close(true); check(state.finish()); check(!state.finish());
        state.open(false); state.close(true); check(!state.finish());
        state.open(true); state.pause(false); state.close(true); check(!state.finish());
        state.open(true); state.pause(true); state.close(true); check(!state.finish());
        state.open(false); state.start(); state.close(false); check(state.finish());
        state.open(true); state.start(); state.pause(false); state.close(true); check(!state.finish());
        state.open(true); state.close(true); state.fail(); check(!state.finish());
        state.open(true); state.close(true); state.open(false); check(state.wasRunningAtOpen()); state.close(true); check(state.finish());
        state.open(false); check(state.canChangeRoute()); state.close(false); state.finish();
        state.open(true); check(!state.canChangeRoute()); state.pause(false); check(state.canChangeRoute());
        state.fail(); state.start(); state.close(false); check(state.finish());
        state.open(true); state.close(true); state.abort(); check(!state.isActive()); check(!state.finish());
        state.open(true); check(!state.finish()); check(state.isOpen()); state.abort();

        ActionScheduler queue = new ActionScheduler();
        List<String> actions = new ArrayList<>();
        queue.schedule(100, () -> { actions.add("first"); queue.schedule(0, () -> actions.add("nested")); });
        queue.schedule(0, () -> actions.add("second"));
        queue.tick(); check(queue.getRemainingTicks() == 1);
        queue.setPaused(true);
        for (int i = 0; i < 2500; i++) queue.tick();
        check(actions.isEmpty()); check(queue.getRemainingTicks() == 1); check(queue.isRunning());
        queue.setPaused(false); queue.tick(); check(actions.isEmpty());
        queue.tick(); check(actions.equals(List.of("first")));
        queue.tick(); check(actions.equals(List.of("first", "second")));
        queue.tick(); check(actions.equals(List.of("first", "second", "nested")));
        check(!queue.isRunning());
        queue.schedule(50, () -> actions.add("cancelled")); queue.setPaused(true); queue.clear(); queue.setPaused(false); queue.tick();
        check(actions.size() == 3); check(!queue.isRunning());
        System.out.println("PASS: " + assertions + " GUI intent/queue checks, including 2500 frozen ticks, residual delay, FIFO, reopening, failure, cancellation and one-shot resume.");
    }
}
