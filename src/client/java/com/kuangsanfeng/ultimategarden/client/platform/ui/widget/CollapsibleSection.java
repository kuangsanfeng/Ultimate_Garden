package com.kuangsanfeng.ultimategarden.client.platform.ui.widget;
import com.kuangsanfeng.ultimategarden.client.platform.ui.widget.HelpTooltip;
import com.kuangsanfeng.ultimategarden.client.platform.ui.widget.StyledButton;


import com.kuangsanfeng.ultimategarden.client.platform.ui.render.RoundedRect;
import com.kuangsanfeng.ultimategarden.client.platform.ui.theme.ThemePalette;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.function.Supplier;
import net.minecraft.client.input.MouseButtonEvent;

/** Full-width header with session-local state and measured content layout. */
public final class CollapsibleSection extends StyledButton {
    public static final int NESTED_INDENT = 14, CONTENT_GAP = 10, SECTION_GAP = 8;
    public static final class State {
        private final State parent;
        private boolean expanded;
        private int contentStart, contentEnd;
        public State() { this(null); }
        public State(State parent) { this.parent = parent; }
        public int depth() { return parent == null ? 0 : parent.depth() + 1; }
        public int inset() { return depth() * NESTED_INDENT; }
        public int inset(int availableWidth) { return Math.min(inset(), Math.max(0,availableWidth - 80)); }
        public boolean isExpanded() { return expanded; }
        public boolean isContentVisible() { return expanded && (parent == null || parent.isContentVisible()); }
        public void toggle() { expanded = !expanded; }
        public void measure(int headerY, int headerHeight, int end) {
            contentStart = headerY + headerHeight;
            contentEnd = Math.max(contentStart, end);
        }
        // A collapsed ancestor already removes this entire subtree from the layout.
        public int hiddenHeight() { return expanded || parent != null && !parent.isContentVisible() ? 0 : contentEnd - contentStart; }
        public int hiddenHeightBefore(int logicalY) { return logicalY >= contentEnd ? hiddenHeight() : 0; }
    }

    private final State state;
    private final int logicalY;
    private final HelpTooltip help;
    private final String description;
    private final int rightControlsWidth;
    public CollapsibleSection(int x, int y, int width, int logicalY, String title, State state,
                              Supplier<ThemePalette> palette, HelpTooltip help, String description, Runnable relayout) {
        this(x,y,width,logicalY,title,state,palette,help,description,relayout,0);
    }
    public CollapsibleSection(int x,int y,int width,int logicalY,String title,State state,
                              Supplier<ThemePalette> palette,HelpTooltip help,String description,Runnable relayout,int rightControlsWidth) {
        super(x + state.inset(width), y, Math.max(40, width - state.inset(width)), Math.max(29, Minecraft.getInstance().font.split(
                Component.literal(title).withStyle(ChatFormatting.BOLD), Math.max(20, width - state.inset(width) - 40 - rightControlsWidth - (description.isBlank() ? 0 : HelpTooltip.SIZE + HelpTooltip.GAP))).size() * 11 + 16),
                title, false, palette, () -> { state.toggle(); relayout.run(); });
        this.state = state; this.logicalY = logicalY;
        this.help = help; this.description = description;
        this.rightControlsWidth=rightControlsWidth;
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        if(rightControlsWidth>0&&event.x()>=getRight()-rightControlsWidth)return false;
        return super.mouseClicked(event,doubleClick);
    }
    public boolean isExpanded() { return state.isExpanded(); }
    public boolean isContentVisible() { return state.isContentVisible(); }
    public State state() { return state; }
    public void finishContent(int end) { state.measure(logicalY, getHeight(), end); }
    public int hiddenHeight() { return state.hiddenHeight(); }
    public int hiddenHeightBefore(int y) { return state.hiddenHeightBefore(y); }

    @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        var p = palette.get();
        float opacity=active ? 1f : .4f;
        int surface = state.depth() == 0 ? p.surface() : ThemePalette.blend(p.surface(), p.sidebar(), .55f);
        RoundedRect.fill(g, getX(), getY(), getWidth(), getHeight(), 10, p.bg(isHoveredOrFocused() && active ? p.selected() : surface, opacity));
        var title = getMessage().copy().withStyle(ChatFormatting.BOLD);
        int textWidth = Math.max(20, getWidth() - 40 - rightControlsWidth - (description.isBlank() ? 0 : HelpTooltip.SIZE + HelpTooltip.GAP));
        g.textWithWordWrap(Minecraft.getInstance().font, title, getX() + 10, getY() + 8, textWidth, p.fg(p.text(), opacity), false);
        help.iconAfterText(g, Minecraft.getInstance().font, this, title, getX() + 10, getY() + 8, textWidth, description, p, opacity);
        int cx = getX() + getWidth() - 16 - rightControlsWidth, cy = getY() + getHeight() / 2;
        // Rasterized triangles, independent of the font's symbol coverage.
        for (int i = 0; i < 5; i++) {
            if (isExpanded()) g.fill(cx - 4 + i, cy - 2 + i, cx + 5 - i, cy - 1 + i, p.fg(p.text(), opacity));
            else g.fill(cx - 2 + i, cy - 4 + i, cx - 1 + i, cy + 5 - i, p.fg(p.text(), opacity));
        }
    }
}
