
import com.kuangsanfeng.ultimategarden.client.platform.gui.GuiPauseState;
import com.kuangsanfeng.ultimategarden.client.runtime.scheduler.ActionScheduler;
import com.kuangsanfeng.ultimategarden.client.platform.gui.PlayerGuiInput;
import java.util.*;

/** Standalone tests for the actual menu policy and scheduler, without a game renderer. */
public final class GuiLifecycleRegression {
    private static int assertions;
    private static void check(boolean value) { assertions++; if (!value) throw new AssertionError("Check " + assertions); }
    public static void main(String[] args) {
        for (var kind : PlayerGuiInput.Kind.values()) {
            PlayerGuiInput.clear();
            check(!PlayerGuiInput.consume(kind));
            check(PlayerGuiInput.request(kind));
            if (kind == PlayerGuiInput.Kind.INVENTORY) {
                PlayerGuiInput.inventoryClick();
                PlayerGuiInput.inventoryConsumed(true);
            }
            check(PlayerGuiInput.consume(kind));
            check(!PlayerGuiInput.consume(kind));
            PlayerGuiInput.virtualDispatch(() -> check(!PlayerGuiInput.request(kind)));
            check(!PlayerGuiInput.consume(kind));
            PlayerGuiInput.request(kind);
            PlayerGuiInput.clear();
            check(!PlayerGuiInput.consume(kind));
        }
        PlayerGuiInput.request(PlayerGuiInput.Kind.INVENTORY);
        check(!PlayerGuiInput.consume(PlayerGuiInput.Kind.CHAT));
        check(!PlayerGuiInput.consume(PlayerGuiInput.Kind.INVENTORY));
        try {
            PlayerGuiInput.virtualDispatch(() -> {
                PlayerGuiInput.virtualDispatch(() -> check(!PlayerGuiInput.request(PlayerGuiInput.Kind.INVENTORY)));
                throw new IllegalStateException("test");
            });
        } catch (IllegalStateException expected) { }
        check(PlayerGuiInput.request(PlayerGuiInput.Kind.INVENTORY));
        PlayerGuiInput.inventoryClick();
        PlayerGuiInput.inventoryConsumed(true);
        check(PlayerGuiInput.consume(PlayerGuiInput.Kind.INVENTORY));
        // Vanilla order: click -> later tick -> consumeClick -> releaseAll -> Screen.init.
        PlayerGuiInput.request(PlayerGuiInput.Kind.INVENTORY);
        PlayerGuiInput.inventoryClick();
        PlayerGuiInput.clearInventoryRequest();
        for (int i = 0; i < 10; i++) PlayerGuiInput.clearTransient();
        PlayerGuiInput.inventoryConsumed(true);
        PlayerGuiInput.inventoryReleased();
        check(PlayerGuiInput.consume(PlayerGuiInput.Kind.INVENTORY));
        check(!PlayerGuiInput.consume(PlayerGuiInput.Kind.INVENTORY));
        PlayerGuiInput.inventoryConsumed(false);
        PlayerGuiInput.virtualDispatch(PlayerGuiInput::inventoryClick);
        PlayerGuiInput.inventoryConsumed(true);
        check(!PlayerGuiInput.consume(PlayerGuiInput.Kind.INVENTORY));
        // Cancelled physical input cannot mark a later virtual opening.
        PlayerGuiInput.request(PlayerGuiInput.Kind.INVENTORY);
        PlayerGuiInput.clearInventoryRequest();
        PlayerGuiInput.virtualDispatch(PlayerGuiInput::inventoryClick);
        PlayerGuiInput.inventoryConsumed(true);
        check(!PlayerGuiInput.consume(PlayerGuiInput.Kind.INVENTORY));
        // Pending physical clicks discarded by vanilla release never leak.
        PlayerGuiInput.request(PlayerGuiInput.Kind.INVENTORY);
        PlayerGuiInput.inventoryClick();
        PlayerGuiInput.inventoryReleased();
        PlayerGuiInput.virtualDispatch(PlayerGuiInput::inventoryClick);
        PlayerGuiInput.inventoryConsumed(true);
        check(!PlayerGuiInput.consume(PlayerGuiInput.Kind.INVENTORY));
        // FIFO preserves mixed sources; repeats are drained on screen opening.
        PlayerGuiInput.request(PlayerGuiInput.Kind.INVENTORY);
        PlayerGuiInput.inventoryClick();
        PlayerGuiInput.virtualDispatch(PlayerGuiInput::inventoryClick);
        PlayerGuiInput.inventoryConsumed(true);
        check(PlayerGuiInput.consume(PlayerGuiInput.Kind.INVENTORY));
        PlayerGuiInput.inventoryConsumed(true);
        check(!PlayerGuiInput.consume(PlayerGuiInput.Kind.INVENTORY));
        PlayerGuiInput.request(PlayerGuiInput.Kind.INVENTORY);
        PlayerGuiInput.inventoryClick();
        PlayerGuiInput.inventoryConsumed(true);
        PlayerGuiInput.inventoryConsumed(false); // Server-controlled branch opened no local screen.
        check(!PlayerGuiInput.consume(PlayerGuiInput.Kind.INVENTORY));
        PlayerGuiInput.clear();
        GuiPauseState state = new GuiPauseState();
        check(state.shouldPauseOnToggle(true));
        check(!state.shouldPauseOnToggle(false));
        for (boolean autoResume : new boolean[] {false, true}) {
            state.open(true);
            check(!state.shouldPauseOnToggle(false)); // A GUI session is not running agriculture.
            state.close(autoResume);
            state.start(); // Num+ wins over the automatic close decision.
            check(state.shouldPauseOnToggle(false));
            check(state.finish());

            state.open(true);
            state.start();
            state.pause(false); // A second Num+ cancels the deferred start.
            check(!state.shouldPauseOnToggle(false));
            state.close(autoResume);
            check(!state.finish());

            state.open(true);
            state.start();
            state.pause(true); // Num- also cancels the deferred start.
            state.close(autoResume);
            check(!state.finish());
        }
        state.open(true); state.close(false); check(!state.finish());
        state.open(true); state.close(true); check(state.finish()); check(!state.finish());
        state.open(false); state.close(true); check(!state.finish());
        state.open(true); state.pause(false); state.close(true); check(!state.finish());
        state.open(true); state.pause(true); state.close(true); check(!state.finish());
        state.open(false); state.start(); state.close(false); check(state.finish());
        state.open(true); state.start(); state.pause(false); state.close(true); check(!state.finish());
        state.open(true); state.close(true); state.fail(); check(!state.finish());
        state.open(true); state.close(true); state.open(false); check(state.wasRunningAtOpen()); state.close(true); check(!state.finish());
        state.open(false); check(state.canChangeRoute()); state.close(false); state.finish();
        state.open(true); check(!state.canChangeRoute()); state.pause(false); check(state.canChangeRoute());
        state.fail(); state.start(); state.close(false); check(state.finish());
        state.open(true); state.close(true); state.abort(); check(!state.isActive()); check(!state.finish());
        state.open(true); check(!state.finish()); check(state.isOpen()); state.abort();

        // Every player GUI ignores the menu preference, including screen-to-screen transitions.
        for (var kind : PlayerGuiInput.Kind.values()) {
            for (boolean autoResume : new boolean[]{false,true}) {
                state.open(true, GuiPauseState.Source.PLAYER_GUI);
                state.close(autoResume); check(!state.finish());
                state.open(true, GuiPauseState.Source.PLAYER_GUI);
                state.close(false); state.open(false, GuiPauseState.Source.ULTIMATE_GARDEN_MENU);
                state.close(autoResume); check(!state.finish());
                state.open(true, GuiPauseState.Source.ULTIMATE_GARDEN_MENU);
                state.close(autoResume); state.open(false, GuiPauseState.Source.PLAYER_GUI);
                state.close(autoResume); check(!state.finish());
                state.open(true, GuiPauseState.Source.PLAYER_GUI);
                state.start(); state.close(autoResume); check(state.finish());
                state.open(true, GuiPauseState.Source.PLAYER_GUI);
                state.start(); state.pause(false); state.close(autoResume); check(!state.finish());
                state.open(true, GuiPauseState.Source.PLAYER_GUI);
                state.start(); state.pause(true); state.close(autoResume); check(!state.finish());
            }
        }

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
