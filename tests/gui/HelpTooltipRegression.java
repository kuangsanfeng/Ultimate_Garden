
import com.kuangsanfeng.ultimategarden.client.platform.ui.widget.HelpTooltip.Motion;

/** Exercises the production animation using deterministic monotonic frame times. */
public final class HelpTooltipRegression {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Help tooltip check " + checks); }
    private static void alpha(Motion motion, float value) { check(Math.abs(motion.alpha() - value) < 0.001f); }
    private static void frame(Motion motion, Object owner, long ms) { motion.update(owner, ms * 1_000_000L); }
    public static void main(String[] args) {
        Motion motion = new Motion(); Object a = new Object(), b = new Object();
        frame(motion,a,0); alpha(motion,0);
        frame(motion,a,199); alpha(motion,0);
        frame(motion,a,200); alpha(motion,0);
        frame(motion,a,300); alpha(motion,0.5f); check(motion.displayed() == a);
        frame(motion,a,400); alpha(motion,1);
        frame(motion,null,400); alpha(motion,1);
        frame(motion,null,450); alpha(motion,0.75f);
        frame(motion,a,450); alpha(motion,0.75f);
        frame(motion,a,500); alpha(motion,1);
        frame(motion,null,500); frame(motion,null,700); alpha(motion,0);
        motion.clear(); check(motion.displayed() == null); alpha(motion,0);
        frame(motion,a,1000); frame(motion,null,1100); frame(motion,null,1400); alpha(motion,0);
        frame(motion,a,2000); frame(motion,a,2400); alpha(motion,1);
        frame(motion,b,2400); alpha(motion,1); check(motion.displayed() == a);
        frame(motion,b,2500); alpha(motion,0.5f);
        frame(motion,b,2600); alpha(motion,0);
        frame(motion,b,2700); alpha(motion,0.5f); check(motion.displayed() == b);
        frame(motion,b,2800); alpha(motion,1);
        Motion fast = new Motion(), slow = new Motion();
        frame(fast,a,0); for (int i=1; i<=400; i++) frame(fast,a,i);
        frame(slow,a,0); frame(slow,a,400);
        alpha(fast,1); alpha(slow,1);
        fast.clear(); alpha(fast,0); check(fast.displayed() == null);
        System.out.println("PASS: " + checks + " tooltip timing checks: 200ms delay, cancellation, fades, reversal, single-owner switching, frame rates and cleanup.");
    }
}
