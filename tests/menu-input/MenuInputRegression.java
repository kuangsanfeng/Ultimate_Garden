
import com.kuangsanfeng.ultimategarden.client.platform.input.virtual.VirtualInput;
import com.kuangsanfeng.ultimategarden.client.platform.input.virtual.VirtualKeyboard;
import com.kuangsanfeng.ultimategarden.client.platform.input.virtual.VirtualMouse;

import com.kuangsanfeng.ultimategarden.client.runtime.scheduler.ActionScheduler;
import net.minecraft.client.Minecraft;
import java.util.Set;

/** Production VirtualInput/VirtualMouse; native KeyMapping and window are boundaries. */
public final class MenuInputRegression {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Menu input check " + checks); }
    public static void main(String[] args) {
        var game = new Minecraft(); var keys = new VirtualKeyboard();
        var mouse = new VirtualMouse(game,new ActionScheduler());
        var input = new VirtualInput(keys,mouse);
        check(!input.hasHeldInput());
        input.keyDown("W"); input.keyDown("D"); input.mouseDown();check(input.hasHeldInput());
        for (int transition = 0; transition < 20; transition++) {
            keys.nativeKeys.clear(); game.options.keyAttack.setDown(false); // Native Screen/open or grab/setAll.
            check(mouse.isLeftDown());
            input.reapplyHeldState();
            check(keys.nativeKeys.equals(Set.of("W","D")));
            check(game.options.keyAttack.down);
        }
        input.actionMouseUp(); input.actionKeyDown("S");
        keys.nativeKeys.clear(); input.reapplyHeldState();
        check(!game.options.keyAttack.down); // Never force attack during intentional Mousemat release.
        check(keys.nativeKeys.equals(Set.of("W","D","S")));
        input.keyUp("W"); input.keyUp("D"); input.actionKeyUp("S"); input.mouseUp();
        keys.nativeKeys.clear(); input.reapplyHeldState();
        check(keys.nativeKeys.isEmpty()); check(!game.options.keyAttack.down);check(!input.hasHeldInput());
        input.keyDown("A"); input.actionMouseDown();
        input.suspendForMenu();
        input.reapplyHeldState();
        check(keys.nativeKeys.isEmpty()); check(!game.options.keyAttack.down);check(!input.hasHeldInput());
        input.resumeAfterMenu(null,false); input.reapplyHeldState();
        check(keys.nativeKeys.isEmpty()); check(!game.options.keyAttack.down);check(!input.hasHeldInput());
        System.out.println("PASS: " + checks + " real virtual-input hold/release/Screen-boundary checks; native mappings/window are stubbed.");
    }
}
