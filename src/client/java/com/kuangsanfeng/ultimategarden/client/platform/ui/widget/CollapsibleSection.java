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
import java.util.function.BooleanSupplier;
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
        public int contentEnd() { return contentEnd; }
        public boolean isContentVisible() { return expanded && (parent == null || parent.isContentVisible()); }
        public boolean ancestorsVisible() { return parent == null || parent.isContentVisible(); }
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
    private int rightControlsWidth;
    private ToggleSwitch toggle;
    private BooleanSupplier toggleEditable = () -> true;
    public void attachToggle(BooleanSupplier value, Runnable action, BooleanSupplier editable) {
        rightControlsWidth = 58;
        toggleEditable = editable;
        toggle = new ToggleSwitch(0, 0, getMessage().getString(), palette, value, action);
        int textWidth=Math.max(20,getWidth()-40-rightControlsWidth-(description.isBlank()?0:HelpTooltip.SIZE+HelpTooltip.GAP));
        setHeight(Math.max(29,Minecraft.getInstance().font.split(getMessage().copy().withStyle(ChatFormatting.BOLD),textWidth).size()*11+16));
        layoutToggle();
    }
    private void layoutToggle() {
        if (toggle == null) return;
        toggle.setX(getRight() - 54);
        toggle.setY(getY() + (getHeight() - 24) / 2);
        toggle.active = active && toggleEditable.getAsBoolean();
        toggle.visible = visible;
    }
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
        layoutToggle();
        if (toggle != null && toggle.isMouseOver(event.x(), event.y())) {
            // The Screen dispatches only to the first child at this position.
            // Handle the owned switch here; never fall through to folding.
            toggle.mouseClicked(event, doubleClick);
            return true;
        }
        return super.mouseClicked(event,doubleClick);
    }
    public boolean isExpanded() { return state.isExpanded(); }
    public boolean isContentVisible() { return state.isContentVisible(); }
    public State state() { return state; }
    public void finishContent(int end) { state.measure(logicalY, getHeight(), end); }
    public int hiddenHeight() { return state.hiddenHeight(); }
    public int hiddenHeightBefore(int y) { return state.hiddenHeightBefore(y); }

    public static int headerSurface(ThemePalette palette,boolean hovered){return hovered?palette.selected():0xffffffff;}
    private record PanelLayer(RoundedRect.Geometry geometry,int offset,int color) { }
    private PanelLayer[] panelLayers;
    private int panelWidth,panelHeight,panelEdge;
    private float panelAlpha;
    /** Cached local geometry; changing scroll position only changes its translation. */
    public void drawPanel(GuiGraphicsExtractor g, int bottom) {
        var p=palette.get();int h=Math.max(getHeight(),bottom-getY());
        int edge=state.depth()==0?0xffffffff:p.background()==0xfff3f8f6?0xfff3f8f6:0xff141d18;
        if(panelLayers==null||panelWidth!=getWidth()||panelHeight!=h||panelEdge!=edge||panelAlpha!=p.backgroundAlpha()) {
            panelWidth=getWidth();panelHeight=h;panelEdge=edge;panelAlpha=p.backgroundAlpha();
            int layers=edge==0xffffffff?0:Math.min(2,Math.min(panelWidth,h)/2);
            panelLayers=new PanelLayer[layers+1];
            for(int i=0;i<layers;i++)panelLayers[i]=new PanelLayer(RoundedRect.geometry(panelWidth-2*i,h-2*i,Math.max(0,10-i),true),i,p.bg(ThemePalette.blend(edge,0xffffffff,i/2f)));
            panelLayers[layers]=new PanelLayer(RoundedRect.geometry(panelWidth-2*layers,h-2*layers,Math.max(0,10-layers),false),layers,p.bg(ThemePalette.blend(edge,0xffffffff,layers/2f)));
        }
        for(var layer:panelLayers)layer.geometry().draw(g,getX()+layer.offset(),getY()+layer.offset(),layer.color());
    }
    @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        var p = palette.get();
        float opacity=active ? 1f : .4f;
        boolean hovered=isMouseOver(mouseX,mouseY)&&active;
        int surface=headerSurface(p,hovered);
        if (hovered) RoundedRect.fill(g, getX(), getY(), getWidth(), getHeight(), 10, p.bg(surface, opacity));
        int titleColor=hovered?p.text():0xff172c26;
        var title = getMessage().copy().withStyle(ChatFormatting.BOLD);
        int textWidth = Math.max(20, getWidth() - 40 - rightControlsWidth - (description.isBlank() ? 0 : HelpTooltip.SIZE + HelpTooltip.GAP));
        g.textWithWordWrap(Minecraft.getInstance().font, title, getX() + 10, getY() + 8, textWidth, p.fg(titleColor, opacity), false);
        help.iconAfterText(g, Minecraft.getInstance().font, this, title, getX() + 10, getY() + 8, textWidth, description, p, opacity);
        int cx = getX() + getWidth() - 16 - rightControlsWidth, cy = getY() + getHeight() / 2;
        // Rasterized triangles, independent of the font's symbol coverage.
        for (int i = 0; i < 5; i++) {
            if (isExpanded()) g.fill(cx - 4 + i, cy - 2 + i, cx + 5 - i, cy - 1 + i, p.fg(titleColor, opacity));
            else g.fill(cx - 2 + i, cy - 4 + i, cx - 1 + i, cy + 5 - i, p.fg(titleColor, opacity));
        }
        layoutToggle();
        if (toggle != null) toggle.extractRenderState(g, mouseX, mouseY, delta);
    }
}
