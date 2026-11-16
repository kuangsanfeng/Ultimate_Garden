
import com.kuangsanfeng.ultimategarden.client.platform.window.WindowsFocusController;
import com.kuangsanfeng.ultimategarden.client.platform.window.WindowsState;
import com.kuangsanfeng.ultimategarden.client.platform.window.WindowsInputInterceptor;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class FocusRegression {
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("Check " + checks);
    }
    public static void main(String[] args) {
        var game = new Minecraft();
        var state = new WindowsState();
        int[] inputRestores = {0};
        var focus = new WindowsFocusController(game, state, () -> inputRestores[0]++);
        game.afterScreen = () -> { if (game.screen == null) focus.reapplyCursorMode(); };
        check(focus.restoreGameMouseAfterMenu());
        check(game.mouseHandler.isMouseGrabbed());
        focus.releaseFocus();
        check(game.mouseHandler.isMouseGrabbed()); // Preserve vanilla continueAttack eligibility.
        check(state.isMouseReleased());
        check(GLFW.mode == GLFW.GLFW_CURSOR_NORMAL);
        check(GLFW.mode == GLFW.GLFW_CURSOR_NORMAL);
        // Player GUI pause, then the existing session explicitly restores game capture.
        game.setScreen(new Object());
        game.setScreen(null);
        check(GLFW.mode == GLFW.GLFW_CURSOR_NORMAL);
        check(focus.restoreGameMouseAfterMenu());
        check(!state.isMouseReleased());
        check(GLFW.mode == GLFW.GLFW_CURSOR_DISABLED);
        check(inputRestores[0] == 0); // Capture restoration does not resume agriculture.
        // Player GUI provenance is checked by FarmController; exercise its focus entry.
        for (int kind = 0; kind < 3; kind++) {
            var playerGame = new Minecraft();
            var playerState = new WindowsState();
            int[] restores = {0};
            var playerFocus = new WindowsFocusController(playerGame, playerState, () -> restores[0]++);
            var playerInput = new WindowsInputInterceptor(playerState);
            playerGame.afterScreen = () -> { if (playerGame.screen == null) playerFocus.reapplyCursorMode(); };
            playerFocus.restoreGameMouseAfterMenu();
            playerInput.enable(); playerFocus.releaseFocus();
            playerGame.setScreen(new Object()); playerInput.disable();
            playerFocus.useGameFocusForPlayerGui();
            check(!playerState.isMouseReleased());
            check(!playerState.isInputIntercepting());
            check(!playerGame.mouseHandler.isMouseGrabbed());
            check(GLFW.mode == GLFW.GLFW_CURSOR_NORMAL);
            check(restores[0] == 0);
            for (int next = 0; next < 5; next++) {
                playerGame.setScreen(new Object());
                playerFocus.useGameFocusForPlayerGui();
                check(!playerState.isMouseReleased());
                check(!playerGame.mouseHandler.isMouseGrabbed());
                check(GLFW.mode == GLFW.GLFW_CURSOR_NORMAL);
            }
            playerGame.setScreen(null);
            check(playerGame.mouseHandler.isMouseGrabbed());
            check(GLFW.mode == GLFW.GLFW_CURSOR_DISABLED);
            check(!playerState.isMouseReleased());
            check(!playerState.isInputIntercepting());
            check(playerFocus.restoreGameMouseAfterMenu());
            check(restores[0] == 0); // Focus handling cannot restart farming.
        }
        // Automatic Loadout keeps released Focus while input remains blocked.
        state.setInputIntercepting(true);
        focus.releaseFocus();
        for (int i = 0; i < 20; i++) {
            game.setScreen(new Object());
            game.setScreen(null);
            check(state.isMouseReleased());
            check(GLFW.mode == GLFW.GLFW_CURSOR_NORMAL);
            check(GLFW.mode == GLFW.GLFW_CURSOR_NORMAL);
            check(state.isInputIntercepting());
        }
        focus.releaseFocus(); // Same-state calls still reconcile native capture.
        check(GLFW.mode == GLFW.GLFW_CURSOR_NORMAL);
        focus.toggle();
        check(!state.isMouseReleased());
        check(game.mouseHandler.isMouseGrabbed());
        check(inputRestores[0] == 1);
        focus.restoreFocus();
        check(inputRestores[0] == 1);
        focus.toggle();
        check(state.isMouseReleased());
        check(GLFW.mode == GLFW.GLFW_CURSOR_NORMAL);
        game.setScreen(new Object());
        focus.restoreFocus();
        check(game.screen != null); // Focus restoration must not close an open GUI.
        check(GLFW.mode == GLFW.GLFW_CURSOR_NORMAL);
        game.setScreen(null);
        check(game.mouseHandler.isMouseGrabbed());
        check(GLFW.mode == GLFW.GLFW_CURSOR_DISABLED);
        check(state.isInputIntercepting());
        check(!state.isMouseReleased());
        var interceptor = new WindowsInputInterceptor(state);
        boolean[] ownMenu = {false};
        interceptor.setUiInputAllowed(() -> ownMenu[0]);
        interceptor.enable();
        boolean focusBefore = state.isMouseReleased();
        check(interceptor.shouldBlockInput());
        ownMenu[0] = true;
        check(!interceptor.shouldBlockInput());
        check(!interceptor.blockMouseButton());
        check(!interceptor.blockKeyboard(GLFW.GLFW_KEY_W));
        check(state.isInputIntercepting());
        check(state.isMouseReleased() == focusBefore);
        ownMenu[0] = false;
        check(interceptor.shouldBlockInput());
        check(interceptor.blockMouseButton());
        check(interceptor.blockKeyboard(GLFW.GLFW_KEY_W));
        System.out.println("PASS: " + checks + " Focus lifecycle checks using real controller and simulated vanilla mouse/window boundaries.");
    }
}
