import com.kuangsanfeng.ultimategarden.client.ui.widget.CollapsibleSection.State;

/** Checks the actual section measurement model without a game or configuration files. */
public final class CollapsibleSectionRegression {
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("Section check " + checks);
    }
    public static void main(String[] args) {
        State first = new State(), second = new State();
        first.measure(0, 29, 229); second.measure(229, 29, 358);
        check(!first.isExpanded() && !second.isExpanded());
        check(first.hiddenHeight() == 200 && second.hiddenHeight() == 100);
        first.toggle(); second.toggle();
        check(first.isExpanded() && second.isExpanded());
        first.toggle();
        check(!first.isExpanded() && second.isExpanded());
        check(first.hiddenHeight() == 200);
        check(first.hiddenHeightBefore(0) == 0);
        check(first.hiddenHeightBefore(229) == 200);
        check(229 - first.hiddenHeightBefore(229) == 29);
        second.toggle();
        check(!first.isExpanded() && !second.isExpanded());
        check(358 - first.hiddenHeight() - second.hiddenHeight() == 58);
        first.toggle();
        check(first.isExpanded() && !second.isExpanded());
        check(358 - first.hiddenHeight() - second.hiddenHeight() == 258);
        second.toggle(); check(first.isExpanded() && second.isExpanded());
        check(358 - first.hiddenHeight() - second.hiddenHeight() == 358);
        // Resize remeasures a collapsed section without changing its state.
        first.toggle(); first.measure(0, 40, 340);
        check(!first.isExpanded() && first.hiddenHeight() == 300);
        check(340 - first.hiddenHeightBefore(340) == 40);
        // Empty sections have a clickable header and no phantom body height.
        State empty = new State(); empty.measure(0,29,29); empty.toggle();
        check(empty.hiddenHeight() == 0);
        empty.measure(0,29,0); check(empty.hiddenHeight() == 0);
        // Repeated use and remeasurement retain the same session-owned state.
        for (int i = 0; i < 100; i++) { first.toggle(); first.measure(0,40,340); }
        check(!first.isExpanded() && first.hiddenHeight() == 300);
        check(!new State().isExpanded());
        // All parent/child combinations must remove each hidden pixel exactly once.
        for (int mask = 0; mask < 16; mask++) {
            State target = new State();
            State box = new State(target), fill = new State(target), tracer = new State(target);
            target.measure(0,29,398); box.measure(71,29,180); fill.measure(180,29,269); tracer.measure(269,29,398);
            if ((mask & 1) != 0) target.toggle();
            if ((mask & 2) != 0) box.toggle();
            if ((mask & 4) != 0) fill.toggle();
            if ((mask & 8) != 0) tracer.toggle();
            int height = 398 - target.hiddenHeight() - box.hiddenHeight() - fill.hiddenHeight() - tracer.hiddenHeight();
            int expected = (mask & 1) == 0 ? 29 : 158 + ((mask & 2) == 0 ? 0 : 80)
                    + ((mask & 4) == 0 ? 0 : 60) + ((mask & 8) == 0 ? 0 : 100);
            check(height == expected);
            check(box.isContentVisible() == ((mask & 3) == 3));
            check(fill.isContentVisible() == ((mask & 5) == 5));
            check(tracer.isContentVisible() == ((mask & 9) == 9));
            if (target.isExpanded()) {
                check(180 - box.hiddenHeightBefore(180) == (box.isExpanded() ? 180 : 100));
                target.toggle(); check(box.hiddenHeight() == 0 && fill.hiddenHeight() == 0 && tracer.hiddenHeight() == 0);
                target.toggle(); check(box.isExpanded() == ((mask & 2) != 0));
            }
        }
        State root = new State(), child = new State(root);
        check(root.depth() == 0 && root.inset() == 0 && child.depth() == 1 && child.inset() == 14);
        for(int width : new int[]{40,64,80,86,94,160,400}) {
            check(child.inset(width) >= 0 && child.inset(width) <= 14);
            check(width - child.inset(width) >= Math.min(80,width));
            check(root.inset(width) == 0);
        }
        // Gaps inside a collapsed body disappear; sibling header gaps remain.
        for(int mask=0;mask<8;mask++) {
            State parent=new State(), a=new State(parent), b=new State(parent);
            parent.measure(0,29,273); a.measure(39,29,158); b.measure(166,29,265);
            if((mask&1)!=0)parent.toggle(); if((mask&2)!=0)a.toggle(); if((mask&4)!=0)b.toggle();
            int next=281-parent.hiddenHeightBefore(281)-a.hiddenHeightBefore(281)-b.hiddenHeightBefore(281);
            check(next == ((mask&1)==0 ? 37 : 121 + ((mask&2)==0?0:90) + ((mask&4)==0?0:70)));
            if(parent.isExpanded()) {
                int bY=166-a.hiddenHeightBefore(166);
                check(bY == 39+29+8 + (a.isExpanded()?90:0));
            }
        }
        System.out.println("PASS: " + checks + " section layout checks: default collapse, independent panels, nested visibility, no double subtraction, reopening and resize.");
    }
}
