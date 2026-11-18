package com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.menu;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.storage.AccountDirectories;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.ESPConfig;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.farm.FarmConfig;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.GeneralConfig;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.HudConfig;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.RandomDelayConfig;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.UIConfig;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootLogCategory;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootoutConfig;
import com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageKeys;
import com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageManager;
import com.kuangsanfeng.ultimategarden.client.platform.ui.widget.CollapsibleSection;
import com.kuangsanfeng.ultimategarden.client.platform.ui.widget.DropdownSelector;
import com.kuangsanfeng.ultimategarden.client.platform.ui.widget.HelpTooltip;
import com.kuangsanfeng.ultimategarden.client.platform.ui.widget.LanguageSelector;
import com.kuangsanfeng.ultimategarden.client.platform.ui.widget.SelectionCheckbox;
import com.kuangsanfeng.ultimategarden.client.platform.ui.widget.StyledButton;
import com.kuangsanfeng.ultimategarden.client.platform.ui.widget.ToggleSwitch;
import com.kuangsanfeng.ultimategarden.client.platform.ui.widget.TransparencySlider;

import com.kuangsanfeng.ultimategarden.client.platform.ui.screen.UltimateGardenScreen;


import com.kuangsanfeng.ultimategarden.client.runtime.hotkey.MacroKeyBindings;
import com.kuangsanfeng.ultimategarden.client.macro.farm.FarmController;
import com.kuangsanfeng.ultimategarden.client.macro.farm.crop.CropType;
import com.kuangsanfeng.ultimategarden.client.platform.ui.MenuController;
import com.kuangsanfeng.ultimategarden.client.platform.ui.render.RoundedRect;
import com.kuangsanfeng.ultimategarden.client.platform.ui.render.MenuInputGraphics;
import com.kuangsanfeng.ultimategarden.client.platform.ui.theme.ThemePalette;

import net.minecraft.ChatFormatting;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.*;

/** Native 26.1.2 screen. Draft fields never become effective until validated. */
public class GardenMenuAdapter extends net.minecraft.client.gui.screens.Screen {
    private enum Page { Home, Farm, Pests, ESP, Hotkeys, Settings, Misc }
    private record Placement(AbstractWidget widget, int y, BooleanSupplier enabled, CollapsibleSection section) { }
    private record Card(int y, int height, CollapsibleSection section, int inset, BooleanSupplier enabled) {
        Card(int y, int height, CollapsibleSection section) { this(y, height, section, 0, () -> true); }
    }
    private record Label(int y, Supplier<String> text, boolean heading, CollapsibleSection section, String help, int inset, BooleanSupplier enabled) {
        Label(int y, Supplier<String> text, boolean heading, CollapsibleSection section, String help) { this(y, text, heading, section, help, 0, () -> true); }
    }
    private record Divider(int y,CollapsibleSection section) { }
    private record Choice(String value, String label, boolean enabled) { }
    private final ConfigManager configs;
    private final FarmController farm;
    private final LanguageManager language;
    private LanguageSelector languageSelector;
    private String queuedOperation;
    private final HelpTooltip help = new HelpTooltip();
    private Page page = Page.Home;
    private CropType editingCrop = CropType.WHEAT;
    private final Map<CropType, Map<String, String>> drafts = new EnumMap<>(CropType.class);
    private final List<AbstractWidget> fixed = new ArrayList<>();
    private final List<Placement> content = new ArrayList<>();
    private final List<Card> cards = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private final List<Divider> dividers = new ArrayList<>();
    private final Map<RandomDelayConfig.Key, String[]> randomDelayDrafts = new EnumMap<>(RandomDelayConfig.Key.class);
    private final List<CollapsibleSection> sections = new ArrayList<>();
    private final Map<Page, Map<String, CollapsibleSection.State>> sectionStates = new EnumMap<>(Page.class);
    private final Map<String, Integer> headingCounts = new HashMap<>();
    private CollapsibleSection currentSection, progressSection;
    private int contentHeight;
    private int left, top, windowWidth, windowHeight, sidebar, contentX, contentTop, contentBottom, contentWidth, cursor, scroll;
    private String message = "ui.ultimategarden.choose_a_section_to_view_its_settings";
    private boolean error;
    private boolean closeHandled;
    private boolean adjustingHud, returningFromHud, enterHeld;
    private final Set<EditBox> routeInputs = Collections.newSetFromMap(new IdentityHashMap<>());
    public void finishHudAdjustment(boolean returning, boolean savedBounds) {
        adjustingHud=false; returningFromHud=returning;
        if (!returning && !closeHandled) { closeHandled=true; if (menuPauseOwned) farm.endUltimateGardenMenu(); }
        page=Page.Settings; choices=null;
        saved(savedBounds);
    }
    private boolean menuPauseOwned;
    private boolean menuKeyHeld;
    private List<Choice> choices;
    private Consumer<String> choose;
    private String choiceTitle;
    private int savedScroll;
    private int progressBarY;
    private String pestDelayDraft;
    private String accentDraft;
    private final Map<String, String> espDrafts = new HashMap<>();
    private AccountDirectories.Profile sourceAccount, targetAccount;
    private final Map<String,AccountDirectories.Profile> accountOptions = new HashMap<>();
    private final EnumSet<ConfigManager.Module> copyFiles = EnumSet.noneOf(ConfigManager.Module.class);
    private ConfigManager.CopyPlan copyConfirmation, copyInProgress;
    private ConfigManager.CopyReport copyReport;
    private int extractedFrames, copyRequestedFrame;

    public GardenMenuAdapter(ConfigManager configs, FarmController farm) {
        super(Component.literal(LanguageManager.translate(configs.getUIConfig().getLanguage(), "ui.ultimategarden.title")));
        this.configs = configs; this.farm = farm; this.language = new LanguageManager(configs);
        accentDraft = configs.getUIConfig().getAccentColor();
        pestDelayDraft = Integer.toString(configs.getPestConfig().getPestLoadoutDelayTicks());
    }
    private ThemePalette palette() { return ThemePalette.from(configs.getUIConfig()); }
    public boolean keepsFarmingInput() { return !menuPauseOwned && !farm.isMenuSessionActive() && farm.getState().isRunning(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void added() {
        closeHandled = false;
        menuKeyHeld = MacroKeyBindings.isMenuKeyPhysicallyDown();
        if (returningFromHud) {
            returningFromHud=false;
            if (!menuPauseOwned && configs.getGeneralConfig().isAutoPauseFarmWhenOpeningMenu()) menuPauseOwned=farm.beginUltimateGardenMenu();
        }
        else menuPauseOwned = farm.beginUltimateGardenMenu();
    }
    private final com.kuangsanfeng.ultimategarden.client.function.mapping.MappingConfirmation mappingConfirmation=new com.kuangsanfeng.ultimategarden.client.function.mapping.MappingConfirmation();
    private CollapsibleSection mappingSection;
    private record MappingContext(Object world,String name,Object target) {}
    private Object mappingContext(String operation){
        var m=com.kuangsanfeng.ultimategarden.client.function.mapping.MappingController.INSTANCE;var status=m.snapshot();
        return new MappingContext(m.worldIdentity(),status.worldName(),"reset".equals(operation)?status.current():null);
    }
    private void mappingDanger(String label,String operation,Runnable action){
        var m=com.kuangsanfeng.ultimategarden.client.function.mapping.MappingController.INSTANCE;
        int h=Math.max(48,font.split(Component.literal(language.text("ui.ultimategarden.mapping.confirm")),Math.max(20,sectionWidth(currentSection)-16)).size()*10+12);
        place(new com.kuangsanfeng.ultimategarden.client.platform.ui.widget.MappingConfirmButton(sectionX(currentSection),contentTop+cursor,sectionWidth(currentSection),h,
            language.text(label),language.text("ui.ultimategarden.mapping.confirm"),this::palette,mappingConfirmation,()->mappingContext(operation),operation,()->act(action)),cursor,()->m.snapshot().config()!=null&&!m.snapshot().busy());cursor+=h+8;
    }
    private void mappingSettings(CollapsibleSection parent){
        var m=com.kuangsanfeng.ultimategarden.client.function.mapping.MappingController.INSTANCE;
        var outer=nestedDelaySection("ui.ultimategarden.mapping.title",parent);
        var panel=nestedDelaySection("ui.ultimategarden.mapping.current_status",outer);mappingSection=panel;
        info("ui.ultimategarden.mapping.world",()->m.snapshot().worldName().isBlank()?language.text("ui.ultimategarden.mapping.unknown"):m.snapshot().worldName());
        info("ui.ultimategarden.mapping.origin",()->m.snapshot().config()==null?language.text("ui.ultimategarden.mapping.unknown"):m.snapshot().config().origin().toString());
        info("ui.ultimategarden.mapping.relative",()->{var st=m.snapshot();return st.config()==null||st.current()==null?language.text("ui.ultimategarden.mapping.unknown"):st.config().relativeX(st.current().x())+", "+st.config().relativeZ(st.current().z());});
        info("ui.ultimategarden.mapping.status",()->{var st=m.snapshot();String text=language.optional("ui.ultimategarden.mapping.status."+st.message());return (st.recording()?language.text("ui.ultimategarden.mapping.status.RECORDING")+" / ":"")+(text.isBlank()?st.message():text);});
        info("ui.ultimategarden.mapping.active_layer",()->m.snapshot().config()==null?language.text("ui.ultimategarden.mapping.festival.NONE"):language.text("ui.ultimategarden.mapping.festival."+m.snapshot().config().selectedFestival().name()));
        dropdown("ui.ultimategarden.mapping.festival",m.requestedFestival().name(),Arrays.stream(com.kuangsanfeng.ultimategarden.client.function.mapping.Festival.values()).map(f->new Choice(f.name(),"ui.ultimategarden.mapping.festival."+f.name(),true)).toList(),value->{m.chooseFestival(com.kuangsanfeng.ultimategarden.client.function.mapping.Festival.valueOf(value));build();},()->m.snapshot().config()!=null&&!m.snapshot().busy());
        button("ui.ultimategarden.mapping.create",false,m::createFestival,()->m.snapshot().config()!=null&&!m.snapshot().busy()&&m.requestedFestival()!=com.kuangsanfeng.ultimategarden.client.function.mapping.Festival.NONE&&!m.snapshot().config().festivals().contains(m.requestedFestival()));
        button("ui.ultimategarden.mapping.delete_chunk",false,m::deleteCurrent,()->m.snapshot().config()!=null&&!m.snapshot().busy());
        mappingDanger("ui.ultimategarden.mapping.reset","reset",()->m.resetOrigin(m.snapshot().current()));
        mappingDanger("ui.ultimategarden.mapping.delete_world","delete",m::deleteWorld);
        panel.finishContent(cursor);cursor+=CollapsibleSection.SECTION_GAP;currentSection=outer;
        var replay=nestedDelaySection("ui.ultimategarden.mapping.replay",outer);
        button("ui.ultimategarden.mapping.open_replay",false,()->{mappingConfirmation.cancel();minecraft.setScreen(new com.kuangsanfeng.ultimategarden.client.platform.ui.screen.MappingReplayScreen((UltimateGardenScreen)this,configs));},()->true);
        replay.finishContent(cursor);cursor+=CollapsibleSection.SECTION_GAP;currentSection=outer;outer.finishContent(cursor);cursor+=CollapsibleSection.SECTION_GAP;currentSection=parent;
    }
    @Override public void removed() {
        mappingConfirmation.cancel();
        copyConfirmation = null; copyInProgress = null;
        help.clear();
        if (!adjustingHud && !closeHandled) { closeHandled = true; if (menuPauseOwned) farm.endUltimateGardenMenu(); }
    }
    @Override public void onClose() {
        if (languageSelector != null && languageSelector.isOpen()) { languageSelector.dismiss(); return; }
        if (copyConfirmation != null) { copyConfirmation = null; build(); return; }
        if (choices != null) { dismissChoices(); return; }
        closeMenu();
    }
    private void closeMenu() {
        choices = null;
        MacroKeyBindings.clearMenuClicks();
        minecraft.setScreen(null);
    }
    @Override public void tick() {
        if(mappingSection==null||!mappingSection.state().isContentVisible())mappingConfirmation.cancel();
        else if(mappingConfirmation.operation()!=null)mappingConfirmation.validate(mappingContext(mappingConfirmation.operation()));
        if (!MacroKeyBindings.isMenuKeyPhysicallyDown()) menuKeyHeld = false;
        if (minecraft.player == null || minecraft.level == null) { choices = null; farm.abortForWorldChange(); onClose(); }
        if (copyInProgress != null && extractedFrames > copyRequestedFrame) executeAccountCopy();
        positionContent();
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || event.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            if (enterHeld) return true;
            if (pathBudgetInput!=null && getFocused()==pathBudgetInput && pathBudgetInput.active && pathBudgetInput.visible) {
                enterHeld=true;
                commitPathBudget();
                return true;
            }
            if (getFocused() instanceof EditBox edit && routeInputs.contains(edit) && edit.active && edit.visible) {
                enterHeld=true;
                act(() -> applyCropDefinition(drafts.get(editingCrop)));
                return true;
            }
        }
        if (MacroKeyBindings.matchesMenuKey(event) && !(getFocused() instanceof EditBox)) {
            if (!menuKeyHeld) { menuKeyHeld = true; closeMenu(); }
            return true;
        }
        return super.keyPressed(event);
    }
    @Override public boolean keyReleased(KeyEvent event) {
        if (event.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || event.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) enterHeld=false;
        if (MacroKeyBindings.matchesMenuKey(event)) { menuKeyHeld = false; return true; }
        return super.keyReleased(event);
    }
    @Override protected void init() {
        windowWidth = Math.min(900, width - 16); windowHeight = Math.min(560, height - 16);
        left = (width - windowWidth) / 2; top = (height - windowHeight) / 2;
        sidebar = windowWidth < 540 ? 94 : 138;
        contentX = left + sidebar + 14; contentWidth = windowWidth - sidebar - 28;
        contentTop = top + 54; contentBottom = top + windowHeight - 36;
        build();
    }
    private void build() {
        pathBudgetInput=null;
        mappingConfirmation.cancel();mappingSection=null;
        help.clear(); routeInputs.clear();
        clearFocus(); clearWidgets(); fixed.clear(); content.clear(); cards.clear(); labels.clear(); dividers.clear(); cursor = 0;
        sections.clear(); headingCounts.clear(); currentSection = null; progressSection = null;
        var close = new StyledButton(left + windowWidth - 31, top + 12, 22, 22, "×", false, this::palette, this::closeMenu);
        fixed.add(addWidget(close));
        languageSelector = new LanguageSelector(left + windowWidth - 150, top + 12, this::palette, language, selected -> {
            boolean result = language.select(selected, this::build); saved(result);
        }, help::clear);
        fixed.add(addWidget(languageSelector));
        int navY = top + 78;
        int navStep = Math.min(36, Math.max(21, (windowHeight - 86) / Page.values().length));
        for (Page target : Page.values()) {
            var nav = new StyledButton(left + 9, navY, sidebar - 18, navStep - 5, language.text("ui.ultimategarden." + target.name().toLowerCase(Locale.ROOT)), target == page, this::palette, () -> {
                page = target; choices = null; scroll = 0; build();
            });
            fixed.add(addWidget(nav)); navY += navStep;
            nav.active = copyInProgress == null;
        }
        if (copyInProgress != null) {
            note("ui.ultimategarden.account.copying");
        } else if (copyConfirmation != null) {
            info(() -> language.text("ui.ultimategarden.account.confirm_title"));
            info(() -> language.text("ui.ultimategarden.account.confirm_source", accountName(copyConfirmation.source())));
            info(() -> language.text("ui.ultimategarden.account.confirm_target", accountName(copyConfirmation.target())));
            note(language.text("ui.ultimategarden.account.confirm_files", fileNames(copyConfirmation.modules())));
            note("ui.ultimategarden.account.confirm_warning");
            button("ui.ultimategarden.account.confirm", true, () -> {
                copyInProgress = copyConfirmation; copyConfirmation = null; copyRequestedFrame = extractedFrames; build();
            }, () -> true);
            button("ui.ultimategarden.cancel", false, () -> { copyConfirmation = null; build(); }, () -> true);
        } else if (choices != null) {
            heading(choiceTitle);
            for (Choice item : choices) button(choiceLabel(item), false, () -> {
                Consumer<String> callback = choose;
                dismissChoices();
                act(() -> callback.accept(item.value()));
            }, () -> item.enabled());
            button("ui.ultimategarden.cancel", false, this::dismissChoices, () -> true);
        } else switch (page) {
            case Home -> home();
            case Farm -> farm();
            case Pests -> pests();
            case ESP -> espSettings();
            case Hotkeys -> hotkeys();
            case Settings -> settings();
            case Misc -> { shootoutPageAdapter.shootoutSettings(); pathFindingSettings(); }
        }
        if (currentSection != null) currentSection.finishContent(cursor);
        currentSection = null;
        positionContent();
    }
    private void act(Runnable action) {
        try { action.run(); }
        catch (RuntimeException e) { message = language.errorKey(e); error = true; }
        positionContent();
    }
    private void saved(boolean result) {
        error = !result;
        message = result ? "ui.ultimategarden.saved" : "ui.ultimategarden.changes_applied_but_could_not_be_saved_check_folder_permissions_and_try_again";
    }
    private void heading(String title) { heading(title, helpText(title)); }
    private void heading(String title, String description) {
        if (choices != null) {
            labels.add(new Label(cursor + 8, () -> language.text(title), true, null, "")); cursor += 29; return;
        }
        if (page == Page.Home) {
            int h = Math.max(29, font.split(Component.literal(language.text(title)).withStyle(ChatFormatting.BOLD), Math.max(40, contentWidth - 36)).size() * 11 + 16);
            cards.add(new Card(cursor, h, null));
            labels.add(new Label(cursor + 8, () -> language.text(title), true, null, description)); cursor += h;
            return;
        }
        if (currentSection != null) { currentSection.finishContent(cursor); cursor += CollapsibleSection.SECTION_GAP; }
        currentSection = null;
        String key = title + "#" + headingCounts.merge(title, 1, Integer::sum);
        var state = sectionStates.computeIfAbsent(page, ignored -> new HashMap<>())
                .computeIfAbsent(key, ignored -> new CollapsibleSection.State());
        var section = new CollapsibleSection(contentX, contentTop + cursor, contentWidth, cursor, language.text(title), state, this::palette, help, description, () -> {
            mappingConfirmation.cancel(); help.clear(); clearFocus(); setDragging(false); positionContent();
        });
        place(section, cursor, () -> true);
        sections.add(section); currentSection = section; cursor += section.getHeight() + CollapsibleSection.CONTENT_GAP;
    }
    private int sectionInset(CollapsibleSection section) { return section == null ? 0 : Math.min((section.state().depth()+1)*CollapsibleSection.NESTED_INDENT,Math.max(0,contentWidth-80)); }
    private int sectionX(CollapsibleSection section) { return contentX + sectionInset(section); }
    private int sectionWidth(CollapsibleSection section) { return Math.max(40,contentWidth - sectionInset(section)); }
    private void info(Supplier<String> value) {
        int h = contentWidth < 260 ? 55 : 38;
        cards.add(new Card(cursor, h, currentSection)); labels.add(new Label(cursor + 10, value, false, currentSection, "")); cursor += h + 8;
    }
    private void info(String label,Supplier<String> value) {
        int h=contentWidth<260?55:38;
        cards.add(new Card(cursor,h,currentSection));
        labels.add(new Label(cursor+10,() -> language.text(label)+": "+value.get(),false,currentSection,helpText(label)));
        cursor+=h+8;
    }
    private void note(String text) {
        int lines = Math.max(1, font.split(Component.literal(language.text(text)), Math.max(40, sectionWidth(currentSection) - 20)).size());
        cards.add(new Card(cursor, 18 + lines * 11, currentSection)); labels.add(new Label(cursor + 9, () -> language.text(text), false, currentSection, "")); cursor += 26 + lines * 11;
    }
    private <T extends AbstractWidget> T place(T widget, int logicalY, BooleanSupplier enabled) {
        content.add(new Placement(addWidget(widget), logicalY, enabled, currentSection)); return widget;
    }
    private void button(String label, boolean primary, Runnable action, BooleanSupplier enabled) {
        place(new StyledButton(sectionX(currentSection), contentTop + cursor, sectionWidth(currentSection), 26, language.text(label), primary, this::palette, () -> act(action))
                .withHelp(help, helpText(label)), cursor, enabled);
        cursor += 34;
    }
    private void headerToggle(BooleanSupplier value, Runnable action, BooleanSupplier enabled) {
        int height=currentSection.getHeight();
        currentSection.attachToggle(value, () -> act(action), enabled);
        cursor+=currentSection.getHeight()-height;
    }
    private void toggle(String label, BooleanSupplier value, Runnable action, BooleanSupplier enabled) {
        toggle(label, value, action, enabled, 0);
    }
    private void toggle(String label, BooleanSupplier value, Runnable action, BooleanSupplier enabled, int inset) {
        var binding=new com.kuangsanfeng.ultimategarden.client.platform.ui.SettingBinding<Boolean>(value::getAsBoolean,ignored->action.run(),enabled);
        int h = Math.max(42, font.split(Component.literal(language.text(label)), Math.max(40, sectionWidth(currentSection) - 75 - inset
                - (helpText(label).isBlank() ? 0 : HelpTooltip.SIZE + HelpTooltip.GAP))).size() * 11 + 18);
        cards.add(new Card(cursor, h, currentSection, inset, enabled)); labels.add(new Label(cursor + 10, () -> language.text(label), false, currentSection, helpText(label), inset, enabled));
        place(new ToggleSwitch(contentX + contentWidth - 54, contentTop + cursor + (h - 24) / 2, language.text(label), this::palette, binding::value, () -> act(()->binding.set(!binding.value()))), cursor + (h - 24) / 2, enabled);
        cursor += h + 8;
    }
    private void dropdown(String label, String current, List<Choice> values, Consumer<String> selected, BooleanSupplier enabled) {
        labels.add(new Label(cursor + 5, () -> language.text(label), false, currentSection, helpText(label))); cursor += Math.max(21, font.split(Component.literal(language.text(label)), Math.max(40, sectionWidth(currentSection) - 36)).size() * 11 + 10);
        place(new DropdownSelector(sectionX(currentSection), contentTop + cursor, sectionWidth(currentSection), values.stream().filter(choice -> choice.value().equals(current)).findFirst().map(this::choiceLabel).orElse(language.text(current)), this::palette, () -> {
            choices = values; choose = selected; choiceTitle = label; savedScroll = scroll; scroll = 0; build();
        }), cursor, enabled); cursor += 34;
    }
    private EditBox input(String label, String value, Consumer<String> changed, BooleanSupplier enabled) {
        return input(label,value,changed,enabled,"");
    }
    private EditBox input(String label, String value, Consumer<String> changed, BooleanSupplier enabled,String unit) {
        return input(label,value,changed,enabled,unit,0);
    }
    private EditBox input(String label,String value,Consumer<String> changed,BooleanSupplier enabled,String unit,int inset) {
        String caption=language.text(label)+(unit.isEmpty()?"":" ("+unit+")");
        BooleanSupplier decoration=inset==0?()->true:enabled;
        labels.add(new Label(cursor+5,()->caption,false,currentSection,helpText(label),inset,decoration));
        cursor+=Math.max(21,font.split(Component.literal(caption),Math.max(40,sectionWidth(currentSection)-36-inset)).size()*11+10);
        EditBox box=new EditBox(font,sectionX(currentSection)+6+inset,contentTop+cursor+4,Math.max(20,sectionWidth(currentSection)-12-inset),20,Component.literal(caption));
        box.setMaxLength(256);box.setValue(value);box.setResponder(changed);box.setBordered(false);box.setTextShadow(false);
        cards.add(new Card(cursor,28,currentSection,inset,decoration));place(box,cursor+4,enabled);cursor+=37;
        return box;
    }
    private void divider(){dividers.add(new Divider(cursor+6,currentSection));cursor+=14;}
    private void dismissChoices() { choices = null; scroll = savedScroll; build(); }
    private String choiceLabel(Choice choice) {
        if (choice.value().equals("legacy-root")) return language.text("ui.ultimategarden.account.legacy");
        if (accountOptions.containsKey(choice.value())) {
            var account = accountOptions.get(choice.value()); var current = configs.currentAccount();
            return account.name() + (current != null && current.uuid().equals(account.uuid()) ? " — " + language.text("ui.ultimategarden.account.current") : "");
        }
        for (CropType type : CropType.values()) if (type.name().equals(choice.value())) {
            var crop = configs.getCropConfig().getCrop(type);
            return type.getValue() + ". " + cropName(type) + (type.isCustom() ? " - " + language.text("ui.ultimategarden.coming_soon")
                    : !crop.isExecutable() ? " - " + language.text("ui.ultimategarden.disabled") : "");
        }
        return language.text(choice.label());
    }
    private List<Choice> cropChoices(boolean simulate, boolean editor) {
        List<Choice> result = new ArrayList<>();
        if (!simulate && !editor) result.add(new Choice("NONE", "ui.ultimategarden.none_not_selected", true));
        for (CropType type : CropType.values()) {
            var crop = configs.getCropConfig().getCrop(type);
            boolean enabled = editor || crop.isExecutable() && (!simulate || type != CropType.TIMETESTER);
            result.add(new Choice(type.name(), type.getValue() + ". " + cropName(type) + (type.isCustom() ? " - " + language.text("ui.ultimategarden.coming_soon") : !crop.isExecutable() ? " - " + language.text("ui.ultimategarden.disabled") : ""), enabled));
        }
        return result;
    }
    private void home() {homePageAdapter.home();}
    private int totalLines() { return farm.getState().getCropType() == 0 ? 0 : configs.getCropConfig().get(farm.getState().getCropType()).getLines(); }
    private float progress() { return totalLines() == 0 ? 0 : Math.min(1, (float)farm.getState().getEventCount() / totalLines()); }
    private void farm() {farmPageAdapter.farm();}
    private void cropLineChangeFields(Map<String, String> fields) {farmPageAdapter.cropLineChangeFields(fields);}
    private void cropDefinitionSaveButton(Map<String, String> fields) {farmPageAdapter.cropDefinitionSaveButton(fields);}
    private void applyCropDefinition(Map<String, String> fields) {farmPageAdapter.applyCropDefinition(fields);}
    private String fieldLabel(String key) { return "ui.ultimategarden.field." + key; }
    private String timerText() {
        int ticks = farm.getCommand().getPestLoadoutRemainingTicks();
        return farm.getCommand().isPestLoadoutTimerRunning() ? language.text("ui.ultimategarden.state.timer", ticks, ticks / 20.0)
                : language.text(farm.getCommand().isPestLoadoutPending() ? "ui.ultimategarden.loadout_pending_executing" : "ui.ultimategarden.idle");
    }
    private void pests() {pestPageAdapter.pests();}
    private void hotkeys() {hotkeyPageAdapter.hotkeys();}


    private void settings() {preferencesPageAdapter.settings();}
    private void developerSettings(){developerPageAdapter.developerSettings();}
    private void hintSettings() {hintPageAdapter.hintSettings();}
    private void hintToggle(GeneralConfig.Hint hint,BooleanSupplier enabled) {hintPageAdapter.hintToggle(hint,enabled);}
    private void hudSettings() {hudPageAdapter.hudSettings();}
    private CollapsibleSection nestedDelaySection(String title, CollapsibleSection parent) {
        return nestedDelaySection(title,parent,() -> true);
    }
    private CollapsibleSection nestedDelaySection(String title, CollapsibleSection parent,BooleanSupplier enabled) {
        return nestedDelaySection(title,parent,enabled,0);
    }
    private CollapsibleSection nestedDelaySection(String title,CollapsibleSection parent,BooleanSupplier enabled,int headerOutset){
        var state = sectionStates.computeIfAbsent(page, ignored -> new HashMap<>())
                .computeIfAbsent(title, ignored -> new CollapsibleSection.State(parent.state()));
        var section = new CollapsibleSection(contentX-headerOutset, contentTop + cursor, contentWidth+headerOutset, cursor,
                language.text(title), state, this::palette, help, helpText(title), () -> {
            mappingConfirmation.cancel(); help.clear(); clearFocus(); setDragging(false); positionContent();
        });
        currentSection = parent;
        place(section, cursor, enabled);
        sections.add(section); currentSection = section;
        cursor += section.getHeight() + CollapsibleSection.CONTENT_GAP;
        return section;
    }
    private void viewDeviationSettings(CollapsibleSection antiCheck) {antiCheckPageAdapter.viewDeviationSettings(antiCheck);}
    private void randomDelaySettings() {antiCheckPageAdapter.randomDelaySettings();}
    private boolean canEditFarmConfiguration() {
        return !configs.isPendingRestart(ConfigManager.Module.FARM) && !configs.isPendingRestart(ConfigManager.Module.CROPS) && farm.canEditRouteConfiguration();
    }
    private String accountName(AccountDirectories.Profile account) {
        return account.legacy() ? language.text("ui.ultimategarden.account.legacy") : account.name();
    }
    private String fileNames(Set<ConfigManager.Module> modules) {
        return Arrays.stream(ConfigManager.Module.values()).filter(modules::contains)
                .map(module -> module.name().toLowerCase(Locale.ROOT) + ".json").collect(java.util.stream.Collectors.joining(", "));
    }
    private void accountConfiguration() {accountPageAdapter.accountConfiguration();}
    private boolean hasUnsavedDrafts() {
        if (!accentDraft.equalsIgnoreCase(configs.getUIConfig().getAccentColor())) return true;
        try { if (Integer.parseInt(pestDelayDraft.trim()) != configs.getPestConfig().getPestLoadoutDelayTicks()) return true; }
        catch (NumberFormatException invalid) { return true; }
        for (var entry : drafts.entrySet()) if (!entry.getValue().equals(configs.getCropConfig().editableValues(entry.getKey()))) return true;
        for (var entry : espDrafts.entrySet()) {
            try {
                if (entry.getKey().equals("cycleDurationMs")) {
                    if (Integer.parseInt(entry.getValue()) != configs.getESPConfig().getCycleDurationMs()) return true;
                } else {
                    String[] parts = entry.getKey().split("\\."); var target = configs.getESPConfig().getTarget(parts[0]);
                    var component = switch (parts[1]) { case "box" -> target.getBox(); case "fill" -> target.getFill(); default -> target.getTracer(); };
                    if (parts[2].equals("color")) { if (!entry.getValue().equalsIgnoreCase(component.getColor())) return true; }
                    else if (Float.compare(Float.parseFloat(entry.getValue()),parts[2].equals("alpha") ? component.getAlpha() : component.getLineWidth()) != 0) return true;
                }
            } catch (RuntimeException invalid) { return true; }
        }
        return false;
    }
    private void executeAccountCopy() {accountPageAdapter.executeAccountCopy();}
    private void espSettings() {espPageAdapter.espSettings();}
    private void espTarget(String id, String title, BooleanSupplier enabled, Runnable toggleEnabled) {espPageAdapter.espTarget(id,title,enabled,toggleEnabled);}
    private void espComponent(String targetId, String name, ESPConfig.ComponentConfig component, boolean line) {espPageAdapter.espComponent(targetId,name,component,line);}
    private void espInput(String label, String key, String initial, Consumer<String> update, BooleanSupplier enabled) {espPageAdapter.espInput(label,key,initial,update,enabled);}
    private String helpText(String key) {
        if (key.equals("ui.ultimategarden.open_ultimate_garden_menu"))
            return language.text(key + ".help", MacroKeyBindings.getMenuKeyName());
        return language.optional(key + ".help");
    }
    private String runStatus() {
        String status = farm.getRunStatus();
        if (status.startsWith("Menu open - ")) return language.text("ui.ultimategarden.state.menu_open", language.localize(status.substring(12)));
        return language.localize(status);
    }
    private String cropName(CropType type) { return language.cropName(type.name(), configs.getCropConfig().getCrop(type).getName()); }
    private String statusText() {
        if (message.equals("ui.ultimategarden.status.queued")) return language.text(message, language.text(LanguageKeys.operation(queuedOperation)));
        if (message.equals("ui.ultimategarden.account.success") || message.equals("ui.ultimategarden.account.success_restart")) return language.text(message,fileNames(copyReport.copied()));
        if (message.equals("ui.ultimategarden.account.partial")) return language.text(message,String.join(", ",copyReport.rollbackFailures()));
        return language.text(message);
    }
    private boolean sectionVisible(CollapsibleSection section) { return section == null || section.isContentVisible(); }
    private int displayedY(int logicalY) {
        int result = logicalY;
        for (var section : sections) result -= section.hiddenHeightBefore(logicalY);
        return contentTop + result - scroll;
    }
    private void positionContent() {
        contentHeight = cursor;
        for (var section : sections) contentHeight -= section.hiddenHeight();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - (contentBottom - contentTop))));
        for (Placement item : content) {
            item.widget().setY(displayedY(item.y()));
            boolean expanded = sectionVisible(item.section());
            item.widget().visible = expanded && item.widget().getBottom() > contentTop && item.widget().getY() < contentBottom;
            item.widget().active = expanded && item.enabled().getAsBoolean();
            if (!item.widget().visible && getFocused() == item.widget()) { clearFocus(); setDragging(false); }
            if (item.widget() instanceof EditBox edit) {
                edit.setEditable(item.widget().active); edit.setTextColor(0xff172c26); edit.setTextColorUneditable(0xff60796d);
            }
        }
    }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (languageSelector != null && languageSelector.isOpen()) return true;
        if (x >= contentX && x < contentX + contentWidth && y >= contentTop && y < contentBottom) {
            help.clear();
            scroll -= (int)(sy * 30); positionContent(); return true;
        }
        return super.mouseScrolled(x, y, sx, sy);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (pathBudgetInput!=null && getFocused()==pathBudgetInput && !pathBudgetInput.isMouseOver(event.x(),event.y())) commitPathBudget();
        if (languageSelector != null && languageSelector.handleOverlayClick(event)) return true;
        if (help.isIconAt(event.x(), event.y())) { setDragging(false); return true; }
        for (AbstractWidget widget : fixed) if (widget.isMouseOver(event.x(), event.y())) return super.mouseClicked(event, doubleClick);
        if (event.x() >= contentX && (event.y() < contentTop || event.y() >= contentBottom)) return false;
        return super.mouseClicked(event, doubleClick);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // This custom background bypasses vanilla blur and scales only the existing scrim.
        g.fill(0, 0, width, height, palette().bg(0x8008100d));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        extractedFrames++;
        positionContent(); ThemePalette p = palette();
        help.beginFrame(contentTop, contentBottom, mouseX, mouseY);
        RoundedRect.fill(g, left + 2, top + 4, windowWidth, windowHeight, 16, p.bg(0x60000000));
        RoundedRect.fill(g, left, top, windowWidth, windowHeight, 14, p.bg(p.background()));
        RoundedRect.fill(g, left + 4, top + 4, sidebar - 4, windowHeight - 8, 12, p.bg(p.sidebar()));
        RoundedRect.fill(g, left + 14, top + 15, 25, 25, 8, p.bg(p.accent()));
        g.text(font, language.text("ui.ultimategarden.ug"), left + 20, top + 23, p.fg(p.onAccent()), false);
        g.text(font, Component.literal(language.text("ui.ultimategarden.ultimate")).withStyle(ChatFormatting.BOLD), left + 14, top + 47, p.fg(p.text()), false);
        g.text(font, language.text("ui.ultimategarden.garden"), left + 14, top + 59, p.fg(p.muted()), false);
        g.text(font, Component.literal(choices != null ? language.text("ui.ultimategarden.choose_an_option") : language.text("ui.ultimategarden." + page.name().toLowerCase(Locale.ROOT))).withStyle(ChatFormatting.BOLD), contentX, top + 40, p.fg(p.text()), false);
        for (AbstractWidget widget : fixed) widget.extractRenderState(g, mouseX, mouseY, delta);
        g.enableScissor(contentX - 1, contentTop, contentX + contentWidth + 1, contentBottom);
        for (var section : sections) if (section.state().ancestorsVisible()) {
            int bottom=section.isExpanded()?displayedY(section.state().contentEnd()):section.getBottom();
            if(bottom>contentTop&&section.getY()<contentBottom)section.drawPanel(g,bottom);
        }
        for(var divider:dividers)if(sectionVisible(divider.section())){
            int y=displayedY(divider.y());
            g.fill(sectionX(divider.section())+8,y,sectionX(divider.section())+sectionWidth(divider.section())-8,y+1,p.bg(ThemePalette.blend(p.background(),0xff000000,.18f)));
        }
        for (Card card : cards) if (sectionVisible(card.section())) RoundedRect.fill(g, sectionX(card.section()) + card.inset(), displayedY(card.y()), Math.max(40, sectionWidth(card.section()) - card.inset()), card.height(), 10, p.bg(0xffffffff, card.enabled().getAsBoolean() ? 1f : .4f));
        for (Label label : labels) {
            if (!sectionVisible(label.section())) continue;
            int y = displayedY(label.y());
            if (y > contentBottom || y + 60 < contentTop) continue;
            int max = sectionWidth(label.section()) - 20;
            // Toggle labels reserve the right-hand switch hit area.
            for (Placement item : content) if (item.widget() instanceof ToggleSwitch && item.section() == label.section() && Math.abs(item.y() - label.y()) < 20) max = sectionWidth(label.section()) - 75;
            for (Placement item : content) if (item.widget() instanceof SelectionCheckbox && item.section() == label.section() && Math.abs(item.y() - label.y()) < 20) max = sectionWidth(label.section()) - 55;
            Component text = Component.literal(label.text().get());
            if (label.heading()) text = text.copy().withStyle(ChatFormatting.BOLD);
            if (!label.help().isBlank()) max -= HelpTooltip.SIZE + HelpTooltip.GAP;
            g.textWithWordWrap(font, text, sectionX(label.section()) + 10 + label.inset(), y, Math.max(40, max - label.inset()), p.fg(label.heading() ? 0xff172c26 : 0xff60796d, label.enabled().getAsBoolean() ? 1f : .4f), false);
            help.iconAfterText(g, font, label, text, sectionX(label.section()) + 10 + label.inset(), y, Math.max(40, max - label.inset()), label.help(), p, label.enabled().getAsBoolean() ? 1f : .4f);
        }
        if (choices == null && page == Page.Home && sectionVisible(progressSection)) {
            // Real progress, never an independent UI timer.
            int y = displayedY(progressBarY);
            RoundedRect.fill(g, contentX + 10, y, contentWidth - 20, 3, 1, p.fg(p.border()));
            RoundedRect.fill(g, contentX + 10, y, (int)((contentWidth - 20) * progress()), 3, 1, p.fg(p.accent()));
        }
        MenuInputGraphics inputGraphics = null;
        for (Placement item : content) {
            if (item.widget() instanceof EditBox && item.widget().visible) {
                if (inputGraphics == null) inputGraphics = new MenuInputGraphics(g, p, mouseX, mouseY,
                        contentX - 1, contentTop, contentX + contentWidth + 1, contentBottom);
                item.widget().extractRenderState(inputGraphics, mouseX, mouseY, delta);
            } else item.widget().extractRenderState(g, mouseX, mouseY, delta);
        }
        g.disableScissor();
        if (contentHeight > contentBottom - contentTop) {
            int track = contentBottom - contentTop;
            int thumb = Math.max(12, track * track / contentHeight);
            int y = contentTop + (track - thumb) * scroll / Math.max(1, contentHeight - track);
            RoundedRect.fill(g, contentX + contentWidth + 4, y, 3, thumb, 1, p.fg(p.muted()));
        }
        String status = font.plainSubstrByWidth(statusText(), Math.max(30, contentWidth - 20));
        g.text(font, status, contentX, top + windowHeight - 22, p.fg(error ? 0xffd86b60 : p.muted()), false);
        help.clip(0, height);
        if (!status.equals(statusText())) help.iconAfterText(g, font, this, Component.literal(status), contentX,
                top + windowHeight - 22, Math.max(30, contentWidth - 20), statusText(), p);
        if (!languageSelector.isOpen()) help.renderOverlay(g, font, p, width, height);
        languageSelector.renderOverlay(g, mouseX, mouseY);
    }

    private final FarmPageAdapter farmPageAdapter=new FarmPageAdapter();
    private final class FarmPageAdapter {
    private void farm() {
        heading("ui.ultimategarden.farm_configuration");
        if (!configs.pendingRestartModules().isEmpty()) note("ui.ultimategarden.account.restart");
        var config = configs.getFarmConfig();
        dropdown("ui.ultimategarden.selected_crop", config.getSelectedCropType(), cropChoices(false, false), value -> {
            saved(MenuController.saveFarmSelection(configs, farm, value, config.getSelectedFarmMode(), config.getSelectedSimulatedCropType())); build();
        }, GardenMenuAdapter.this::canEditFarmConfiguration);
        dropdown("ui.ultimategarden.farm_mode", config.getSelectedFarmMode().name(), List.of(new Choice("FARM", "ui.ultimategarden.mode.FARM", true), new Choice("PESTS", "ui.ultimategarden.mode.PESTS", true)), value -> {
            saved(MenuController.saveFarmSelection(configs, farm, config.getSelectedCropType(), FarmConfig.FarmMode.valueOf(value), config.getSelectedSimulatedCropType())); build();
        }, GardenMenuAdapter.this::canEditFarmConfiguration);
        if (config.getSelectedCropType().equals("TIMETESTER")) dropdown("ui.ultimategarden.simulated_crop_1_11", config.getSelectedSimulatedCropType(), cropChoices(true, false), value -> {
            saved(MenuController.saveFarmSelection(configs, farm, config.getSelectedCropType(), config.getSelectedFarmMode(), value)); build();
        }, GardenMenuAdapter.this::canEditFarmConfiguration);
        heading("ui.ultimategarden.existing_farm_operations");
        for (FarmController.MenuAction action : FarmController.MenuAction.values()) button(LanguageKeys.operation(action.name()), false, () -> {
            farm.queueMenuAction(action); queuedOperation = action.name(); message = "ui.ultimategarden.status.queued"; error = false;
        }, () -> farm.getState().getCropType() != 0 && farm.getQueuedMenuActions() == 0);
        heading("ui.ultimategarden.advanced_crop_definition");
        dropdown("ui.ultimategarden.crop_to_inspect_edit", editingCrop.name(), cropChoices(false, true), value -> { editingCrop = CropType.valueOf(value); build(); }, () -> true);
        if (editingCrop.isCustom()) note("ui.ultimategarden.coming_soon_custom_routes_cannot_be_edited_or_started_yet");
        Map<String, String> fields = drafts.computeIfAbsent(editingCrop, type -> new LinkedHashMap<>(configs.getCropConfig().editableValues(type)));
        if (!fields.containsKey("tickLimit")) { cropDefinitionSaveButton(fields); cropLineChangeFields(fields); }
        for (String key : fields.keySet()) {
            if (key.startsWith("lineChange")) continue;
            if (key.equals("tickLimit")) { cropDefinitionSaveButton(fields); cropLineChangeFields(fields); }
            if (key.equals("enabled") || key.endsWith(".enabled")) toggle(fieldLabel(key), () -> Boolean.parseBoolean(fields.get(key)), () -> fields.put(key, Boolean.toString(!Boolean.parseBoolean(fields.get(key)))), GardenMenuAdapter.this::canEditFarmConfiguration);
            else routeInputs.add(input(fieldLabel(key), fields.get(key), value -> fields.put(key, value), GardenMenuAdapter.this::canEditFarmConfiguration));
        }
    }
    private void cropLineChangeFields(Map<String, String> fields) {
        for (String key : fields.keySet()) if (key.startsWith("lineChange")) {
            if (key.endsWith(".enabled")) toggle(fieldLabel(key), () -> Boolean.parseBoolean(fields.get(key)), () -> fields.put(key, Boolean.toString(!Boolean.parseBoolean(fields.get(key)))), GardenMenuAdapter.this::canEditFarmConfiguration);
            else routeInputs.add(input(fieldLabel(key), fields.get(key), value -> fields.put(key, value), GardenMenuAdapter.this::canEditFarmConfiguration));
        }
    }
    private void cropDefinitionSaveButton(Map<String, String> fields) {
        button("ui.ultimategarden.apply_and_save_crop_definition", true, () -> applyCropDefinition(fields), GardenMenuAdapter.this::canEditFarmConfiguration);
    }
    private void applyCropDefinition(Map<String, String> fields) {
        if (!farm.canEditRouteConfiguration()) throw new IllegalStateException("ui.ultimategarden.error.pause_or_reset_first_pending_actions_block_route_edits");
        configs.getCropConfig().updateCrop(editingCrop, fields); saved(configs.save(ConfigManager.Module.CROPS));
        drafts.remove(editingCrop); build();
    }
    }

    private final PestPageAdapter pestPageAdapter=new PestPageAdapter();
    private final class PestPageAdapter {
    private void pests() {
        heading("ui.ultimategarden.pest_loadout");
        var config = configs.getPestConfig();
        headerToggle( config::isAutoPestLoadout, () -> { config.setAutoPestLoadout(!config.isAutoPestLoadout()); saved(configs.save(ConfigManager.Module.PEST)); }, () -> true);
        info(() -> language.text("ui.ultimategarden.state.current_timer", timerText()));
        info(() -> language.text("ui.ultimategarden.state.delay", config.getPestLoadoutDelayTicks(), config.getPestLoadoutDelayTicks() / 20.0));
        input("ui.ultimategarden.delay_ticks_integer_1_2147483647", pestDelayDraft, value -> pestDelayDraft = value, () -> true);
        button("ui.ultimategarden.save_delay_next_countdown", true, () -> { config.setPestLoadoutDelayTicks(Integer.parseInt(pestDelayDraft)); saved(configs.save(ConfigManager.Module.PEST)); }, () -> true);
        button("ui.ultimategarden.start_timer_manually", false, () -> farm.getCommand().startPestLoadoutTimer(), () -> farm.getState().isPestsMode());
        button("ui.ultimategarden.cancel_timer", false, () -> farm.getCommand().cancelPestLoadoutTimer(), () -> true);
    }
    }

    private EditBox pathBudgetInput;
    private String pathFindingLocalMap="Hub";

    private void commitPathBudget() {
        var settings=configs.getPathSettings();
        var box=pathBudgetInput;
        if (box==null || !box.active || !box.visible) return;
        try {
            long value=com.kuangsanfeng.ultimategarden.client.function.path_finding.NodeBudget.parse(box.getValue());
            if (value!=settings.searchNodesPerSegment()) {
                settings.setBudget(value,settings.maximumSearchSegments());
                saved(configs.save(ConfigManager.Module.GENERAL));
            }
        } catch (RuntimeException invalid) {
            error=true;
            message="ui.ultimategarden.path_finding.budget_error";
        }
        box.setValue(com.kuangsanfeng.ultimategarden.client.function.path_finding.NodeBudget.format(settings.searchNodesPerSegment()));
    }
    private void pathFindingSettings() {
        String key = "ui.ultimategarden.path_finding.";
        heading(key+"title");
        var parent = currentSection;
        var settings = configs.getPathSettings();
        BooleanSupplier editable = () -> !configs.isPendingRestart(ConfigManager.Module.GENERAL);
        Runnable save = () -> saved(configs.save(ConfigManager.Module.GENERAL));
        dropdown(key+"mode",settings.mode.name(),List.of(new Choice("NORMAL",key+"normal",true),
                new Choice("WEIGHTED",key+"weighted",true)),value->{
            settings.mode=com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.Mode.valueOf(value);
            save.run();build();
        },editable);
        toggle(key+"transmission",()->settings.transmission,()->{settings.transmission=!settings.transmission;save.run();},editable);
        input(key+"cooldown",Integer.toString(settings.teleportCooldownMs()),value->act(()->{
            settings.setCooldown(Integer.parseInt(value));save.run();
        }),editable,"ms");
        var budgetFormat=com.kuangsanfeng.ultimategarden.client.function.path_finding.NodeBudget.format(settings.searchNodesPerSegment());
        pathBudgetInput=input(key+"search_budget",budgetFormat,value->{},editable);
        input(key+"search_segments",Integer.toString(settings.maximumSearchSegments()),value->act(()->{
            settings.setBudget(settings.searchNodesPerSegment(),Integer.parseInt(value));save.run();
        }),editable);
        input(key+"coordinate_window",Integer.toString(settings.coordinateWindowMs()),value->act(()->{
            settings.setCoordinateWindowMs(Integer.parseInt(value));save.run();
        }),editable,"ms");
        var performance=nestedDelaySection(key+"performance",parent);
        toggle(key+"gpu",settings::gpuAcceleration,()->{settings.setGpuAcceleration(!settings.gpuAcceleration());save.run();},editable);
        info(()->settings.gpuAcceleration()?language.text(key+"gpu_status_"+com.kuangsanfeng.ultimategarden.client.function.path_finding.planning.GpuCollision.status())
                :language.text(key+"gpu_status_disabled"));
        int available=com.kuangsanfeng.ultimategarden.client.function.path_finding.PathSettings.availableWorkers();
        var workerChoices=new ArrayList<Choice>();
        workerChoices.add(new Choice("0",key+"workers_auto",true));
        for(int i=1;i<=available;i++)workerChoices.add(new Choice(Integer.toString(i),Integer.toString(i),true));
        dropdown(key+"workers",Integer.toString(settings.cpuWorkers()==0?0:settings.effectiveWorkers()),workerChoices,value->act(()->{
            settings.setCpuWorkers(Integer.parseInt(value));save.run();build();
        }),editable);
        info(()->language.text(key+"workers_effective")+" "+settings.effectiveWorkers()+" / "+available);
        performance.finishContent(cursor);cursor+=CollapsibleSection.SECTION_GAP;currentSection=parent;
        var aim = nestedDelaySection(key+"aim",parent);
        for (var field : com.kuangsanfeng.ultimategarden.client.function.path_finding.PathSettings.Aim.values())
            input(key+field.field(),Double.toString(settings.aim(field)),value->act(()->{
                settings.aim(field,Double.parseDouble(value));save.run();
            }),editable);
        aim.finishContent(cursor);cursor+=CollapsibleSection.SECTION_GAP;currentSection=parent;
        var weights = nestedDelaySection(key+"weights",parent);
        for (int index=0;index<6;index++) {
            int i=index;
            String name=List.of("tp_weight","walk_weight","jump_weight","fall_weight","turn_weight","turn_exponent").get(i);
            var w=settings.weights;
            double[] defaults={w.transmission(),w.walk(),w.jump(),w.fall(),w.turn(),w.exponent()};
            input(key+name,Double.toString(defaults[i]),value->act(()->{
                var live=settings.weights;
                double[] v={live.transmission(),live.walk(),live.jump(),live.fall(),live.turn(),live.exponent()};
                v[i]=Double.parseDouble(value);
                settings.weights=new com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.Weights(v[0],v[1],v[2],v[3],v[4],v[5]);
                save.run();
            }),editable);
        }
        weights.finishContent(cursor);cursor+=CollapsibleSection.SECTION_GAP;currentSection=parent;
        var detection=nestedDelaySection(key+"detection",parent);
        toggle(key+"speed",()->settings.speedDetection,()->{settings.speedDetection=!settings.speedDetection;save.run();},editable);
        toggle(key+"initial_speed",()->settings.initialSpeed,()->{settings.initialSpeed=!settings.initialSpeed;save.run();},()->editable.getAsBoolean()&&settings.speedDetection,CollapsibleSection.NESTED_INDENT);
        input(key+"sample_distance",Double.toString(settings.speedSampleDistance()),value->act(()->{
            settings.setSpeedSampleDistance(Double.parseDouble(value));save.run();
        }),()->editable.getAsBoolean()&&settings.speedDetection,"blocks");
        toggle(key+"jump",()->settings.jumpDetection,()->{settings.jumpDetection=!settings.jumpDetection;save.run();},editable);
        toggle(key+"initial_jump",()->settings.initialJump,()->{settings.initialJump=!settings.initialJump;save.run();},()->editable.getAsBoolean()&&settings.jumpDetection,CollapsibleSection.NESTED_INDENT);
        detection.finishContent(cursor);cursor+=CollapsibleSection.SECTION_GAP;currentSection=parent;
        info(()->com.kuangsanfeng.ultimategarden.client.function.path_finding.execution.ClientPathFinding.INSTANCE.status());
        button(key+"cancel",false,()->com.kuangsanfeng.ultimategarden.client.function.path_finding.execution.ClientPathFinding.INSTANCE.cancelMenuTask(),
                ()->com.kuangsanfeng.ultimategarden.client.function.path_finding.execution.ClientPathFinding.INSTANCE.menuTaskActive());
        input(key+"local_map",pathFindingLocalMap,value->pathFindingLocalMap=value,()->true);
        button(key+"local_test",false,()->act(()->com.kuangsanfeng.ultimategarden.client.function.path_finding.execution.ClientPathFinding.INSTANCE.startMenuTest(pathFindingLocalMap)),
                ()->com.kuangsanfeng.ultimategarden.client.function.path_finding.execution.ClientPathFinding.INSTANCE.menuTestAvailable());
    }

    private final ShootoutPageAdapter shootoutPageAdapter=new ShootoutPageAdapter();
    private final class ShootoutPageAdapter {
    private void shootoutSettings() {
        heading("ui.ultimategarden.carnival");
        var parent=currentSection;
        var section=nestedDelaySection("ui.ultimategarden.shootout.title",parent);
        var controller=com.kuangsanfeng.ultimategarden.client.macro.zombie_shoot.ZombieShootoutController.INSTANCE;
        headerToggle(controller::enabled,controller::toggle,() -> true);
        var automation=configs.getZombieShootoutConfig();
        toggle("ui.ultimategarden.shootout.auto_aim",automation::isAutoAimEnabled,()->{
            automation.setAutoAimEnabled(!automation.isAutoAimEnabled());saved(configs.save(ConfigManager.Module.ZOMBIE_SHOOTOUT));
        },()->!configs.isPendingRestart(ConfigManager.Module.ZOMBIE_SHOOTOUT));
        toggle("ui.ultimategarden.shootout.auto_shoot",automation::isAutoShootEnabled,()->{
            automation.setAutoShootEnabled(!automation.isAutoShootEnabled());saved(configs.save(ConfigManager.Module.ZOMBIE_SHOOTOUT));
        },()->automation.isAutoAimEnabled()&&!configs.isPendingRestart(ConfigManager.Module.ZOMBIE_SHOOTOUT),CollapsibleSection.NESTED_INDENT);
        toggle("ui.ultimategarden.shootout.occlusion_check",automation::isOcclusionCheckEnabled,()->{
            automation.setOcclusionCheckEnabled(!automation.isOcclusionCheckEnabled());saved(configs.save(ConfigManager.Module.ZOMBIE_SHOOTOUT));
        },()->automation.autoShootEffective()&&!configs.isPendingRestart(ConfigManager.Module.ZOMBIE_SHOOTOUT),2*CollapsibleSection.NESTED_INDENT);
        toggle("ui.ultimategarden.shootout.manual_shot_restriction",automation::isManualShotRestrictionEnabled,()->{
            automation.setManualShotRestrictionEnabled(!automation.isManualShotRestrictionEnabled());saved(configs.save(ConfigManager.Module.ZOMBIE_SHOOTOUT));
        },()->automation.manualShotRestrictionEditable()&&!configs.isPendingRestart(ConfigManager.Module.ZOMBIE_SHOOTOUT),CollapsibleSection.NESTED_INDENT);
        toggle("ui.ultimategarden.shootout.auto_start",configs.getZombieShootoutConfig()::isAutoStartZombieShootout,() -> {
            var setting=configs.getZombieShootoutConfig();setting.setAutoStartZombieShootout(!setting.isAutoStartZombieShootout());
            saved(configs.save(ConfigManager.Module.ZOMBIE_SHOOTOUT));
        },() -> !configs.isPendingRestart(ConfigManager.Module.ZOMBIE_SHOOTOUT));
        toggle("ui.ultimategarden.shootout.arrow_confirmation",configs.getZombieShootoutConfig()::isArrowSpawnConfirmationEnabled,()->{
            var setting=configs.getZombieShootoutConfig();setting.setArrowSpawnConfirmationEnabled(!setting.isArrowSpawnConfirmationEnabled());saved(configs.save(ConfigManager.Module.ZOMBIE_SHOOTOUT));
        },()->!configs.isPendingRestart(ConfigManager.Module.ZOMBIE_SHOOTOUT));
        info("ui.ultimategarden.shootout.status",controller::status);
        shootoutInput(ZombieShootoutConfig.Setting.AIM_SPEED,0);
        shootoutInput(ZombieShootoutConfig.Setting.AIM_CURVATURE,0);
        shootoutInput(ZombieShootoutConfig.Setting.AIM_UPDATE_RATE,0);
        divider();
        shootoutInput(ZombieShootoutConfig.Setting.AIM_TOLERANCE,0);
        shootoutInput(ZombieShootoutConfig.Setting.AIM_CORRECTION_RATIO,CollapsibleSection.NESTED_INDENT);
        shootoutInput(ZombieShootoutConfig.Setting.SHOOT_RATIO,CollapsibleSection.NESTED_INDENT);
        divider();
        shootoutInput(ZombieShootoutConfig.Setting.COOLDOWN,0);
        shootoutInput(ZombieShootoutConfig.Setting.JITTER_MIN,CollapsibleSection.NESTED_INDENT);
        shootoutInput(ZombieShootoutConfig.Setting.JITTER_MAX,CollapsibleSection.NESTED_INDENT);
        divider();
        var sampling=nestedDelaySection("ui.ultimategarden.shootout.speed_sampling",section,
                ()->!configs.isPendingRestart(ConfigManager.Module.ZOMBIE_SHOOTOUT),CollapsibleSection.NESTED_INDENT);
        for(var setting:ZombieShootoutConfig.Setting.values())if(setting.window())shootoutInput(setting,0);
        shootoutInput(ZombieShootoutConfig.Setting.BABY_STALL_WINDOW,0);
        shootoutInput(ZombieShootoutConfig.Setting.BABY_STALL_RATIO,0);
        sampling.finishContent(cursor);cursor+=CollapsibleSection.SECTION_GAP;currentSection=section;
        shootoutInput(ZombieShootoutConfig.Setting.BABY_OFFSET,0);
        divider();
        shootoutInput(ZombieShootoutConfig.Setting.PING_INTERVAL,0);
        info("ui.ultimategarden.shootout.ping",controller::pingText);
        info("ui.ultimategarden.shootout.target",controller::targetText);
        section.finishContent(cursor);currentSection=parent;
    }
    private void shootoutInput(ZombieShootoutConfig.Setting setting,int inset){
        String unit=setting==ZombieShootoutConfig.Setting.AIM_UPDATE_RATE?"Hz":setting==ZombieShootoutConfig.Setting.AIM_TOLERANCE||setting==ZombieShootoutConfig.Setting.AIM_CURVATURE?"\u00b0":setting==ZombieShootoutConfig.Setting.AIM_SPEED?"\u00b0/s":setting.window()||setting==ZombieShootoutConfig.Setting.BABY_STALL_WINDOW||setting==ZombieShootoutConfig.Setting.COOLDOWN||setting==ZombieShootoutConfig.Setting.JITTER_MIN||setting==ZombieShootoutConfig.Setting.JITTER_MAX||setting==ZombieShootoutConfig.Setting.PING_INTERVAL?"s":"";
        input("ui.ultimategarden.shootout."+setting.name(),Double.toString(configs.getZombieShootoutConfig().get(setting)),value->act(()->{
            configs.getZombieShootoutConfig().set(setting,Double.parseDouble(value));saved(configs.save(ConfigManager.Module.ZOMBIE_SHOOTOUT));
        }),()->!configs.isPendingRestart(ConfigManager.Module.ZOMBIE_SHOOTOUT),unit,inset);
    }
    }

    private final PreferencesPageAdapter preferencesPageAdapter=new PreferencesPageAdapter();
    private final class PreferencesPageAdapter {
    private void settings() {
        heading("ui.ultimategarden.preferences");
        var general = configs.getGeneralConfig(); var ui = configs.getUIConfig();
        toggle("ui.ultimategarden.auto_pause_farming_when_opening_menu", general::isAutoPauseFarmWhenOpeningMenu, () -> {
            general.setAutoPauseFarmWhenOpeningMenu(!general.isAutoPauseFarmWhenOpeningMenu()); saved(configs.save(ConfigManager.Module.GENERAL));
        }, () -> true);
        toggle("ui.ultimategarden.auto_resume_farming_after_closing_menu", general::isAutoResumeFarmAfterGuiClose, () -> {
            general.setAutoResumeFarmAfterGuiClose(!general.isAutoResumeFarmAfterGuiClose()); saved(configs.save(ConfigManager.Module.GENERAL));
        }, general::isAutoPauseFarmWhenOpeningMenu, CollapsibleSection.NESTED_INDENT);
        hintSettings();
        hudSettings();
        randomDelaySettings();
        developerSettings();
        heading("ui.ultimategarden.appearance");
        List<Choice> themes = Arrays.stream(UIConfig.Theme.values()).map(theme -> new Choice(theme.name(), "ui.ultimategarden." + theme.name().toLowerCase(Locale.ROOT), true)).toList();
        dropdown("ui.ultimategarden.theme", ui.getUiThemeMode().name(), themes, value -> { ui.setUiThemeMode(UIConfig.Theme.valueOf(value)); saved(configs.saveUIConfig()); build(); }, () -> true);
        input("ui.ultimategarden.accent_color_rrggbb", accentDraft, value -> accentDraft = value, () -> true);
        button("ui.ultimategarden.apply_accent_color", true, () -> { ui.setAccentColor(accentDraft); saved(configs.saveUIConfig()); build(); }, () -> true);
        String label = "ui.ultimategarden.menu_transparency";
        labels.add(new Label(cursor + 5, () -> language.text(label), false, currentSection, helpText(label)));
        cursor += Math.max(21, font.split(Component.literal(language.text(label)), Math.max(40, contentWidth - 36)).size() * 11 + 10);
        place(new TransparencySlider(contentX, contentTop + cursor, contentWidth, ui.getMenuTransparency(), GardenMenuAdapter.this::palette, language, value -> {
            ui.setMenuTransparency(value); saved(configs.saveUIConfig());
        }), cursor, () -> true);
        cursor += 42;
        accountConfiguration();
    }
    }

    private final DeveloperPageAdapter developerPageAdapter=new DeveloperPageAdapter();
    private final class DeveloperPageAdapter {
    private void developerSettings(){
        heading("ui.ultimategarden.developer_options");var developer=currentSection;
        mappingSettings(developer);
        var transmission=nestedDelaySection("ui.ultimategarden.instant.title",developer);
        var instant=nestedDelaySection("ui.ultimategarden.instant.path_test",transmission);
        var test=com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.InstantTransmissionController.INSTANCE;
        button("ui.ultimategarden.instant.toggle",false,()->test.toggle(com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug.InstantTransmissionController.StartSource.MENU),()->true);
        info("ui.ultimategarden.instant.status",test::status);
        var instantConfig=configs.getGeneralConfig().instantTransmission();
        dropdown("ui.ultimategarden.instant.strategy",instantConfig.strategy().name(),Arrays.stream(com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing.RouteStrategy.values()).map(v->new Choice(v.name(),"ui.ultimategarden.instant.strategy."+v.name(),true)).toList(),value->{instantConfig.setStrategy(com.kuangsanfeng.ultimategarden.client.function.instant_transmission.routing.RouteStrategy.valueOf(value));saved(configs.save(ConfigManager.Module.GENERAL));build();},()->!test.running()&&!configs.isPendingRestart(ConfigManager.Module.GENERAL));
        for(var field:com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.InstantTransmissionConfig.Setting.values()){
            input("ui.ultimategarden.instant."+field.name(),Double.toString(instantConfig.get(field)),value->act(()->{
                instantConfig.set(field,Double.parseDouble(value));saved(configs.save(ConfigManager.Module.GENERAL));
            }),()->!test.running()&&!configs.isPendingRestart(ConfigManager.Module.GENERAL),"\u00b0",0);
        }
        input("ui.ultimategarden.instant.distance_bonus_note",instantConfig.distanceBonusNote(),value->act(()->{
            instantConfig.setDistanceBonusNote(value);saved(configs.save(ConfigManager.Module.GENERAL));
        }),()->!test.running()&&!configs.isPendingRestart(ConfigManager.Module.GENERAL),"",0);
        instant.finishContent(cursor);cursor+=CollapsibleSection.SECTION_GAP;currentSection=transmission;
        var rounding=nestedDelaySection("ui.ultimategarden.instant.y_rounding",transmission);
        button("ui.ultimategarden.instant.y_toggle",false,test::toggleYRound,()->true);
        info("ui.ultimategarden.instant.status",test::status);
        rounding.finishContent(cursor);cursor+=CollapsibleSection.SECTION_GAP;currentSection=transmission;
        transmission.finishContent(cursor);cursor+=CollapsibleSection.SECTION_GAP;currentSection=developer;
        var carnival=nestedDelaySection("ui.ultimategarden.developer.carnival",developer);
        var shootout=nestedDelaySection("ui.ultimategarden.developer.shootout",carnival);
        String title="ui.ultimategarden.developer.shootout.logs";
        var state=sectionStates.computeIfAbsent(page,ignored->new HashMap<>()).computeIfAbsent(title,ignored->new CollapsibleSection.State(shootout.state()));
        var logs=new CollapsibleSection(contentX,contentTop+cursor,contentWidth,cursor,language.text(title),state,GardenMenuAdapter.this::palette,help,helpText(title),()->{
            help.clear();clearFocus();setDragging(false);positionContent();
        },58);
        var collector=com.kuangsanfeng.ultimategarden.client.function.collection.zombie_shoot.compat.ZombieShootCollector.INSTANCE;
        currentSection=shootout;place(logs,cursor,()->true);sections.add(logs);
        logs.attachToggle(collector::logsEnabled,()->act(()->collector.setLogsEnabled(!collector.logsEnabled())),()->!configs.isPendingRestart(ConfigManager.Module.ZOMBIE_SHOOTOUT));
        currentSection=logs;cursor+=logs.getHeight()+CollapsibleSection.CONTENT_GAP;
        var config=configs.getZombieShootoutConfig();
        for(var category:com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootLogCategory.values()){
            toggle("ui.ultimategarden.developer.shootout.log."+category.name(),()->config.logEnabled(category),()->{
                config.setLogEnabled(category,!config.logEnabled(category));saved(configs.save(ConfigManager.Module.ZOMBIE_SHOOTOUT));
            },()->collector.logsEnabled()&&!configs.isPendingRestart(ConfigManager.Module.ZOMBIE_SHOOTOUT));
        }
        logs.finishContent(cursor);cursor+=CollapsibleSection.SECTION_GAP;currentSection=shootout;
        shootout.finishContent(cursor);cursor+=CollapsibleSection.SECTION_GAP;currentSection=carnival;
        carnival.finishContent(cursor);cursor+=CollapsibleSection.SECTION_GAP;currentSection=developer;
        developer.finishContent(cursor);
    }
    }

    private final HudPageAdapter hudPageAdapter=new HudPageAdapter();
    private final class HudPageAdapter {
    private void hudSettings() {
        heading("ui.ultimategarden.hud");
        var parent = currentSection;
        var hud = configs.getHudConfig();
        button("ui.ultimategarden.hud.adjust", false, () -> {
            adjustingHud=true;
            com.kuangsanfeng.ultimategarden.client.platform.ui.hud.HudAdjustmentController.begin((UltimateGardenScreen)GardenMenuAdapter.this);
        }, () -> !configs.isPendingRestart(ConfigManager.Module.HUD));
        for(var id:HudConfig.HudId.values()){
            String prefix="ui.ultimategarden.hud.panel."+id.name();
            var panel=nestedDelaySection(prefix,parent);
            String showKey=switch(id){case FARM_MACRO->"ui.ultimategarden.hud.show";case ZOMBIE_SHOOTOUT->"ui.ultimategarden.hud.show_zombie";case INSTANT_TRANSMISSION->"ui.ultimategarden.hud.show_instant_transmission";case MAPPING->"ui.ultimategarden.hud.show_mapping";case PATH_FINDING->"ui.ultimategarden.hud.show_path_finding";};
            headerToggle(()->hud.enabled(id),()->{hud.enabled(id,!hud.enabled(id));saved(configs.save(ConfigManager.Module.HUD));},()->!configs.isPendingRestart(ConfigManager.Module.HUD));
            toggle("ui.ultimategarden.hud.only_running",()->hud.onlyRunning(id),()->{hud.onlyRunning(id,!hud.onlyRunning(id));saved(configs.save(ConfigManager.Module.HUD));},()->hud.enabled(id)&&!configs.isPendingRestart(ConfigManager.Module.HUD),CollapsibleSection.NESTED_INDENT);
            hudWorldFilter(id,panel,hud);
            dropdown("ui.ultimategarden.hud.display_mode",hud.mode(id).name(),Arrays.stream(HudConfig.Mode.values()).map(mode->new Choice(mode.name(),"ui.ultimategarden.hud.mode."+mode.name(),true)).toList(),value->{hud.mode(id,HudConfig.Mode.valueOf(value));saved(configs.save(ConfigManager.Module.HUD));build();},()->!configs.isPendingRestart(ConfigManager.Module.HUD));
            for(boolean compact:new boolean[]{true,false}){
                var section=nestedDelaySection(prefix+(compact?".compact_content":".detail_content"),panel);
                CollapsibleSection world=null;if(id==HudConfig.HudId.MAPPING)world=nestedDelaySection(prefix+(compact?".compact_world":".detail_world"),section);
                for(String key:HudConfig.fields(id)){
                    if(id==HudConfig.HudId.MAPPING&&key.equals("recording_origin")){
                        world.finishContent(cursor);cursor+=CollapsibleSection.SECTION_GAP;currentSection=section;
                        world=nestedDelaySection(prefix+(compact?".compact_recording":".detail_recording"),section);
                    }
                    String label=switch(id){case FARM_MACRO->"ui.ultimategarden.hud.item."+key;case ZOMBIE_SHOOTOUT->"ui.ultimategarden.hud.field.ZOMBIE_SHOOTOUT."+key;case INSTANT_TRANSMISSION->"ui.ultimategarden.hud.field.INSTANT_TRANSMISSION."+key;case MAPPING->"ui.ultimategarden.mapping_hud."+key;case PATH_FINDING->"ui.ultimategarden.path_finding."+key;};
                    toggle(label,()->compact?hud.compact(id,key):hud.detail(id,key),()->{hud.content(id,key,compact,!(compact?hud.compact(id,key):hud.detail(id,key)));saved(configs.save(ConfigManager.Module.HUD));},()->!configs.isPendingRestart(ConfigManager.Module.HUD)&&(compact||!hud.compact(id,key)));
                }
                if(world!=null){world.finishContent(cursor);cursor+=CollapsibleSection.SECTION_GAP;currentSection=section;}
                section.finishContent(cursor);cursor+=CollapsibleSection.SECTION_GAP;currentSection=panel;
            }
            panel.finishContent(cursor);cursor+=CollapsibleSection.SECTION_GAP;currentSection=parent;
        }
    }
    }

    private void hudWorldFilter(HudConfig.HudId id,CollapsibleSection panel,HudConfig hud){
        String title="ui.ultimategarden.hud.panel."+id.name()+".world_filter";
        var state=sectionStates.computeIfAbsent(page,k->new HashMap<>()).computeIfAbsent(title,k->new CollapsibleSection.State(panel.state()));
        var section=new CollapsibleSection(contentX,contentTop+cursor,contentWidth,cursor,language.text(title),state,this::palette,help,helpText(title),()->{help.clear();clearFocus();positionContent();},58);
        currentSection=panel;place(section,cursor,()->true);sections.add(section);
        section.attachToggle(()->hud.worldFilter(id),()->act(()->{hud.worldFilter(id,!hud.worldFilter(id));saved(configs.save(ConfigManager.Module.HUD));}),()->!configs.isPendingRestart(ConfigManager.Module.HUD));
        currentSection=section;cursor+=section.getHeight()+CollapsibleSection.CONTENT_GAP;
        for(String name:com.kuangsanfeng.ultimategarden.client.function.world.WorldIds.NAMES){String key=com.kuangsanfeng.ultimategarden.client.function.world.WorldIds.id(name);String label=language.text("ui.ultimategarden.world."+key);int h=Math.max(42,24+font.split(Component.literal(label),Math.max(40,sectionWidth(section)-55)).size()*11);int checkboxY=cursor+11;
            cards.add(new Card(cursor,h,currentSection));labels.add(new Label(cursor+12,()->label,false,currentSection,""));
            place(new SelectionCheckbox(contentX+contentWidth-32,contentTop+checkboxY,label,this::palette,()->hud.worldSelected(id,key),()->{hud.worldSelected(id,key,!hud.worldSelected(id,key));saved(configs.save(ConfigManager.Module.HUD));}),checkboxY,()->true);cursor+=h+8;
        }
        button("ui.ultimategarden.hud.world_all",false,()->{hud.allWorlds(id,true);saved(configs.save(ConfigManager.Module.HUD));},()->true);
        button("ui.ultimategarden.hud.world_none",false,()->{hud.allWorlds(id,false);saved(configs.save(ConfigManager.Module.HUD));},()->true);
        section.finishContent(cursor);cursor+=CollapsibleSection.SECTION_GAP;currentSection=panel;
    }

    private final EspPageAdapter espPageAdapter=new EspPageAdapter();
    private final class EspPageAdapter {
    private void espSettings() {
        heading("ui.ultimategarden.general_esp_settings");
        var esp = configs.getESPConfig();
        toggle("ui.ultimategarden.through_walls_box_fill", esp::isThroughWalls, () -> {
            esp.setThroughWalls(!esp.isThroughWalls()); saved(configs.save(ConfigManager.Module.ESP));
        }, () -> true);
        espInput("ui.ultimategarden.rainbow_cycle_milliseconds_integer_1_2147483647", "cycleDurationMs", Integer.toString(esp.getCycleDurationMs()),
                value -> esp.setCycleDurationMs(Integer.parseInt(value)), () -> true);
        var pest = configs.getPestConfig();
        espTarget(ESPConfig.SILVERFISH, "ui.ultimategarden.silverfish_esp", pest::isPestESPEnabled, () -> {
            pest.setPestESPEnabled(!pest.isPestESPEnabled()); saved(configs.save(ConfigManager.Module.PEST));
        });
        heading("ui.ultimategarden.carnival");
        var carnival=currentSection;
        var shootout=nestedDelaySection("ui.ultimategarden.shootout_esp",carnival);
        toggle("ui.ultimategarden.show_intercept_box",esp::isShowShootoutInterceptBox,()->{esp.setShowShootoutInterceptBox(!esp.isShowShootoutInterceptBox());saved(configs.save(ConfigManager.Module.ESP));},()->true);
        shootout.finishContent(cursor);currentSection=carnival;
    }
    private void espTarget(String id, String title, BooleanSupplier enabled, Runnable toggleEnabled) {
        heading(title);
        headerToggle( enabled, toggleEnabled, () -> true);
        var target = configs.getESPConfig().getTarget(id);
        espComponent(id, "box", target.getBox(), true);
        espComponent(id, "fill", target.getFill(), false);
        espComponent(id, "tracer", target.getTracer(), true);
    }
    private void espComponent(String targetId, String name, ESPConfig.ComponentConfig component, boolean line) {
        var parent = currentSection;
        String titleKey = "ui.ultimategarden." + name;
        var state = sectionStates.computeIfAbsent(page, ignored -> new HashMap<>())
                .computeIfAbsent("esp.component." + targetId + "." + name, ignored -> new CollapsibleSection.State(parent.state()));
        var section = new CollapsibleSection(contentX, contentTop + cursor, contentWidth, cursor,
                language.text(titleKey), state, GardenMenuAdapter.this::palette, help, helpText(titleKey), () -> {
            mappingConfirmation.cancel(); help.clear(); clearFocus(); setDragging(false); positionContent();
        });
        // The header belongs to the target; the controls belong to the component.
        place(section, cursor, () -> true);
        sections.add(section); currentSection = section; cursor += section.getHeight() + CollapsibleSection.CONTENT_GAP;
        headerToggle( component::isEnabled, () -> {
            component.setEnabled(!component.isEnabled()); saved(configs.save(ConfigManager.Module.ESP));
        }, () -> true);
        var modes = Arrays.stream(com.kuangsanfeng.ultimategarden.client.function.esp.style.ESPColorMode.values())
                .map(mode -> new Choice(mode.name(), "ui.ultimategarden." + mode.name().toLowerCase(Locale.ROOT), true)).toList();
        dropdown(LanguageKeys.component(name, "mode"), component.getColorMode().name(), modes, value -> {
            component.setColorMode(com.kuangsanfeng.ultimategarden.client.function.esp.style.ESPColorMode.valueOf(value));
            saved(configs.save(ConfigManager.Module.ESP)); build();
        }, () -> true);
        espInput(LanguageKeys.component(name, "color"), targetId + "." + name + ".color", component.getColor(), component::setColor,
                () -> component.getColorMode() == com.kuangsanfeng.ultimategarden.client.function.esp.style.ESPColorMode.SOLID);
        espInput(LanguageKeys.component(name, "alpha"), targetId + "." + name + ".alpha", Float.toString(component.getAlpha()),
                value -> component.setAlpha(Float.parseFloat(value)), () -> true);
        if (line) espInput(LanguageKeys.component(name, "width"), targetId + "." + name + ".lineWidth", Float.toString(component.getLineWidth()),
                value -> component.setLineWidth(Float.parseFloat(value)), () -> true);
        section.finishContent(cursor);
        cursor += CollapsibleSection.SECTION_GAP;
        currentSection = parent;
    }
    private void espInput(String label, String key, String initial, Consumer<String> update, BooleanSupplier enabled) {
        input(label, espDrafts.computeIfAbsent(key, ignored -> initial), value -> {
            espDrafts.put(key, value);
            act(() -> { update.accept(value); saved(configs.save(ConfigManager.Module.ESP)); });
        }, enabled);
    }
    }

    private final HomePageAdapter homePageAdapter=new HomePageAdapter();
    private final class HomePageAdapter {
    private void home() {
        heading("ui.ultimategarden.your_garden_at_a_glance");
        info(GardenMenuAdapter.this::runStatus);
        info(() -> language.text("ui.ultimategarden.state.actual_crop", farm.getState().getCropType() == 0 ? language.text("ui.ultimategarden.none") : cropName(CropType.fromValue(farm.getState().getCropType()))));
        info(() -> language.text("ui.ultimategarden.state.mode_direction", language.text("ui.ultimategarden.mode." + (farm.getState().isPestsMode() ? "PESTS" : "FARM")), farm.getState().isEventSelect() ? "A" : "B"));
        info(() -> language.text("ui.ultimategarden.state.lines", farm.getState().getEventCount(), totalLines(), farm.getState().getTick()));
        info(() -> language.text("ui.ultimategarden.state.progress", progress() * 100));
        progressBarY = cursor - 14;
        progressSection = currentSection;
        info(() -> language.text("ui.ultimategarden.state.pest_timer", timerText()));
        info(() -> language.text("ui.ultimategarden.state.pending", language.text(farm.getCommand().hasPendingWork() ? "ui.ultimategarden.yes" : "ui.ultimategarden.no"), farm.getQueuedMenuActions()));
        button(farm.isMenuSessionActive() ? "ui.ultimategarden.start_after_closing_menu" : "ui.ultimategarden.start_farming", true, () -> { farm.requestMenuStart(); message = farm.isMenuSessionActive() ? "ui.ultimategarden.status.start" : "ui.ultimategarden.status.start_now"; error = false; }, () -> farm.getState().getCropType() != 0);
        button("ui.ultimategarden.pause", false, () -> { farm.pauseMacro(); message = "ui.ultimategarden.status.pause"; error = false; }, () -> true);
        button("ui.ultimategarden.reset", false, () -> { farm.resetMacro(); message = "ui.ultimategarden.status.reset"; error = false; }, () -> true);
    }
    }

    private final HotkeyPageAdapter hotkeyPageAdapter=new HotkeyPageAdapter();
    private final class HotkeyPageAdapter {
    private void hotkeys() {
        for(var group:com.kuangsanfeng.ultimategarden.client.runtime.hotkey.HotkeyGroups.Group.values())if(group.parent==null){
            heading(group.label());var section=currentSection;hotkeyGroupContent(group,section);section.finishContent(cursor);
        }
    }
    private void hotkeyGroupContent(com.kuangsanfeng.ultimategarden.client.runtime.hotkey.HotkeyGroups.Group group,CollapsibleSection section){
        headerToggle(()->com.kuangsanfeng.ultimategarden.client.runtime.hotkey.HotkeySwitches.groupEnabled(group),()->saved(com.kuangsanfeng.ultimategarden.client.runtime.hotkey.HotkeySwitches.setGroupEnabled(group,!com.kuangsanfeng.ultimategarden.client.runtime.hotkey.HotkeySwitches.groupEnabled(group))),()->true);
        for(var child:com.kuangsanfeng.ultimategarden.client.runtime.hotkey.HotkeyGroups.Group.values())if(child.parent==group){
            var nested=nestedDelaySection(child.label(),section);hotkeyGroupContent(child,nested);nested.finishContent(cursor);cursor+=CollapsibleSection.SECTION_GAP;currentSection=section;
        }
        for(var binding:MacroKeyBindings.getBindings()){
            String id=binding.mapping().getName().substring(com.kuangsanfeng.ultimategarden.client.runtime.hotkey.HotkeySwitches.PREFIX.length());
            if(com.kuangsanfeng.ultimategarden.client.runtime.hotkey.HotkeyGroups.owner(id)!=group)continue;
            String title=LanguageKeys.hotkey(binding.title());var card=nestedDelaySection(title,section);
            headerToggle(()->com.kuangsanfeng.ultimategarden.client.runtime.hotkey.HotkeySwitches.ownEnabled(binding.mapping()),()->saved(com.kuangsanfeng.ultimategarden.client.runtime.hotkey.HotkeySwitches.setEnabled(binding.mapping(),!com.kuangsanfeng.ultimategarden.client.runtime.hotkey.HotkeySwitches.ownEnabled(binding.mapping()))),()->true);
            info(()->language.text("ui.ultimategarden.state.key",binding.mapping().isUnbound()?language.text("ui.ultimategarden.hotkey.unbound"):language.keyName(((com.kuangsanfeng.ultimategarden.client.mixin.KeyMappingAccessor)binding.mapping()).getKey().getName(),binding.mapping().getTranslatedKeyMessage().getString())));
            card.finishContent(cursor);cursor+=CollapsibleSection.SECTION_GAP;currentSection=section;
        }
    }
    }

    private final HintPageAdapter hintPageAdapter=new HintPageAdapter();
    private final class HintPageAdapter {
    private void hintSettings() {
        heading("ui.ultimategarden.hint");
        var parent=currentSection;
        var general=configs.getGeneralConfig();
        BooleanSupplier editable=() -> !configs.isPendingRestart(ConfigManager.Module.GENERAL);
        BooleanSupplier childEditable=() -> editable.getAsBoolean() && general.isShowModChatHints();
        headerToggle(general::isShowModChatHints,() -> {
            general.setShowModChatHints(!general.isShowModChatHints()); saved(configs.save(ConfigManager.Module.GENERAL));
        },editable);
        for (String group:new String[]{"macro","pest","focus"}) {
            var section=nestedDelaySection("ui.ultimategarden.hint."+group,parent,childEditable);
            headerToggle(()->general.hintGroupEnabled(group),()->{general.setHintGroupEnabled(group,!general.hintGroupEnabled(group));saved(configs.save(ConfigManager.Module.GENERAL));},editable);
            for (var hint:GeneralConfig.Hint.values()) {
                boolean inGroup=switch(group) {
                    case "macro" -> hint.ordinal()<=GeneralConfig.Hint.timeTesterTicks.ordinal();
                    case "pest" -> hint.ordinal()>=GeneralConfig.Hint.pestTimerStart.ordinal() && hint.ordinal()<=GeneralConfig.Hint.pestLoadoutChange.ordinal();
                    default -> hint==GeneralConfig.Hint.focusReleased || hint==GeneralConfig.Hint.focusRestored;
                };
                if (inGroup) hintToggle(hint,childEditable);
            }
            section.finishContent(cursor); cursor+=CollapsibleSection.SECTION_GAP; currentSection=parent;
        }
        for (var hint:new GeneralConfig.Hint[]{GeneralConfig.Hint.cropSelectionInitialization,GeneralConfig.Hint.hotkeyHelp,GeneralConfig.Hint.commandHelpErrors}) hintToggle(hint,childEditable);
    }
    private void hintToggle(GeneralConfig.Hint hint,BooleanSupplier enabled) {
        var general=configs.getGeneralConfig();
        toggle("ui.ultimategarden.hint."+hint.name(),() -> general.hintSelected(hint),() -> {
            general.setHintEnabled(hint,!general.hintSelected(hint)); saved(configs.save(ConfigManager.Module.GENERAL));
        },enabled);
    }
    }

    private final AntiCheckPageAdapter antiCheckPageAdapter=new AntiCheckPageAdapter();
    private final class AntiCheckPageAdapter {
    private void viewDeviationSettings(CollapsibleSection antiCheck) {
        var group=nestedDelaySection("ui.ultimategarden.view_check.title",antiCheck);
        headerToggle(()->configs.getViewDeviationConfig().viewChecksEnabled,()->{configs.getViewDeviationConfig().viewChecksEnabled=!configs.getViewDeviationConfig().viewChecksEnabled;saved(configs.save(ConfigManager.Module.ANTI_CHECK));},()->true);
        for(boolean farmCheck:new boolean[]{true,false}) {
            String type=farmCheck?"farm":"zombie";
            var section=nestedDelaySection("ui.ultimategarden.view_check."+type,group);
            var config=farmCheck?configs.getViewDeviationConfig().farm():configs.getViewDeviationConfig().zombie();
            BooleanSupplier editable=()->!configs.isPendingRestart(ConfigManager.Module.ANTI_CHECK);
            headerToggle(()->config.enabled,()->{config.enabled=!config.enabled;saved(configs.save(ConfigManager.Module.ANTI_CHECK));},editable);
            input("ui.ultimategarden.view_check.warning",Double.toString(config.warning()),value->act(()->{config.warning(Double.parseDouble(value));saved(configs.save(ConfigManager.Module.ANTI_CHECK));}),editable,"°");
            input("ui.ultimategarden.view_check.stop",Double.toString(config.stop()),value->act(()->{config.stop(Double.parseDouble(value));saved(configs.save(ConfigManager.Module.ANTI_CHECK));}),editable,"°");
            input("ui.ultimategarden.view_check.duration",Double.toString(config.duration()),value->act(()->{config.duration(Double.parseDouble(value));saved(configs.save(ConfigManager.Module.ANTI_CHECK));}),editable,"s");
            section.finishContent(cursor);currentSection=group;
        }
        group.finishContent(cursor);currentSection=antiCheck;
    }
    private void randomDelaySettings() {
        heading("ui.ultimategarden.anti_check");
        var antiCheck = currentSection;
        headerToggle(()->configs.getViewDeviationConfig().enabled,()->{configs.getViewDeviationConfig().enabled=!configs.getViewDeviationConfig().enabled;saved(configs.save(ConfigManager.Module.ANTI_CHECK));},()->true);
        viewDeviationSettings(antiCheck);
        var random = nestedDelaySection("ui.ultimategarden.random_delay", antiCheck);
        var delays=configs.getRandomDelayConfig();
        headerToggle(()->delays.enabled,()->{delays.enabled=!delays.enabled;saved(configs.save(ConfigManager.Module.RANDOM_DELAY));},()->true);
        for (RandomDelayConfig.Key key : RandomDelayConfig.Key.values()) {
            var parameter = nestedDelaySection("ui.ultimategarden.random_delay." + key.name(), random);
            headerToggle(()->delays.ownEnabled(key),()->{delays.setEnabled(key,!delays.ownEnabled(key));saved(configs.save(ConfigManager.Module.RANDOM_DELAY));},()->!configs.isPendingRestart(ConfigManager.Module.RANDOM_DELAY));
            String[] draft = randomDelayDrafts.computeIfAbsent(key, ignored -> {
                var range = configs.getRandomDelayConfig().get(key);
                return new String[]{Integer.toString(range.minTicks()), Integer.toString(range.maxTicks())};
            });
            BooleanSupplier editable = () -> !configs.isPendingRestart(ConfigManager.Module.RANDOM_DELAY);
            input("ui.ultimategarden.random_delay.min_ticks", draft[0], value -> draft[0] = value, editable);
            input("ui.ultimategarden.random_delay.max_ticks", draft[1], value -> draft[1] = value, editable);
            button("ui.ultimategarden.random_delay.save", true, () -> {
                configs.getRandomDelayConfig().set(key, Integer.parseInt(draft[0].trim()), Integer.parseInt(draft[1].trim()));
                saved(configs.save(ConfigManager.Module.RANDOM_DELAY));
            }, editable);
            parameter.finishContent(cursor); cursor += CollapsibleSection.SECTION_GAP;
            currentSection = random;
        }
        random.finishContent(cursor); cursor += CollapsibleSection.SECTION_GAP;
        currentSection = antiCheck;
    }
    }

    private final AccountPageAdapter accountPageAdapter=new AccountPageAdapter();
    private final class AccountPageAdapter {
    private void accountConfiguration() {
        heading("ui.ultimategarden.account.title");
        try {
            var current = configs.currentAccount(); var profiles = configs.accountProfiles();
            if (current == null || !profiles.contains(current)) { note("ui.ultimategarden.account.unavailable"); return; }
            boolean legacy = configs.hasLegacyConfiguration();
            if (sourceAccount == null || sourceAccount.legacy() && !legacy || !sourceAccount.legacy() && !profiles.contains(sourceAccount)) sourceAccount = current;
            if (targetAccount != null && !profiles.contains(targetAccount)) targetAccount = null;
            List<Choice> sourceChoices = new ArrayList<>(), targetChoices = new ArrayList<>();
            Map<String,AccountDirectories.Profile> lookup = accountOptions; lookup.clear();
            for (var account : profiles) {
                String id = account.uuid().toString(); lookup.put(id, account);
                String label = account.name() + (account.uuid().equals(current.uuid()) ? " — " + language.text("ui.ultimategarden.account.current") : "");
                sourceChoices.add(new Choice(id,label,true)); targetChoices.add(new Choice(id,label,true));
            }
            if (legacy) { lookup.put("legacy-root",AccountDirectories.LEGACY); sourceChoices.add(new Choice("legacy-root","ui.ultimategarden.account.legacy",true)); }
            dropdown("ui.ultimategarden.account.source", sourceAccount.legacy() ? "legacy-root" : sourceAccount.uuid().toString(), sourceChoices,
                    value -> { sourceAccount = lookup.get(value); build(); }, () -> true);
            dropdown("ui.ultimategarden.account.target", targetAccount == null ? "ui.ultimategarden.account.choose" : targetAccount.uuid().toString(), targetChoices,
                    value -> { targetAccount = lookup.get(value); build(); }, () -> true);
            info(() -> language.text("ui.ultimategarden.account.files"));
            for (ConfigManager.Module module : ConfigManager.Module.values()) {
                String label = language.text("ui.ultimategarden.account.file." + module.name()) + " — " + module.name().toLowerCase(Locale.ROOT) + ".json";
                int rowHeight = Math.max(42,24 + font.split(Component.literal(label),Math.max(40,contentWidth - 55)).size() * 11);
                int checkboxY = cursor + 11;
                cards.add(new Card(cursor,rowHeight,currentSection)); labels.add(new Label(cursor+12,() -> label,false,currentSection,""));
                place(new SelectionCheckbox(contentX + contentWidth - 32,contentTop + checkboxY,label,GardenMenuAdapter.this::palette,() -> copyFiles.contains(module),() -> {
                    if (!copyFiles.remove(module)) copyFiles.add(module);
                }),checkboxY,() -> true); cursor += rowHeight + 8;
            }
            button("ui.ultimategarden.account.all",false,() -> copyFiles.addAll(EnumSet.allOf(ConfigManager.Module.class)),() -> true);
            button("ui.ultimategarden.account.none",false,copyFiles::clear,() -> true);
            if (!configs.pendingRestartModules().isEmpty()) note("ui.ultimategarden.account.restart");
            button("ui.ultimategarden.account.copy",true,() -> {
                if (targetAccount.uuid().equals(current.uuid())) {
                    if (hasUnsavedDrafts()) throw new IllegalStateException("ui.ultimategarden.account.unsaved");
                    if (!farm.canEditRouteConfiguration()) throw new IllegalStateException("ui.ultimategarden.account.pause_first");
                }
                copyConfirmation = new ConfigManager.CopyPlan(sourceAccount,targetAccount,copyFiles,configs.accountRevision());
                choices = null; build();
            },() -> targetAccount != null && sourceAccount != null && !Objects.equals(sourceAccount.uuid(),targetAccount.uuid()) && !copyFiles.isEmpty());
        } catch (java.io.IOException error) {
            com.kuangsanfeng.ultimategarden.Ultimate_Garden.LOGGER.warn("Cannot list verified account configurations",error);
            note("ui.ultimategarden.account.unavailable");
        }
    }
    private void executeAccountCopy() {
        var plan = copyInProgress; copyInProgress = null;
        try {
            if (configs.currentAccount() != null && configs.currentAccount().uuid().equals(plan.target().uuid()) && hasUnsavedDrafts())
                throw new IllegalStateException("ui.ultimategarden.account.unsaved");
            copyReport = configs.copyConfiguration(plan);
            if (copyReport.success()) {
                if (configs.currentAccount().uuid().equals(plan.target().uuid())) {
                    if (plan.modules().contains(ConfigManager.Module.UI)) accentDraft = configs.getUIConfig().getAccentColor();
                    if (plan.modules().contains(ConfigManager.Module.PEST)) pestDelayDraft = Integer.toString(configs.getPestConfig().getPestLoadoutDelayTicks());
                    if (plan.modules().contains(ConfigManager.Module.ESP)) espDrafts.clear();
                    if (plan.modules().contains(ConfigManager.Module.RANDOM_DELAY)) randomDelayDrafts.clear();
                }
                message = copyReport.restartRequired().isEmpty() ? "ui.ultimategarden.account.success" : "ui.ultimategarden.account.success_restart"; error = false;
            } else { message = copyReport.rollbackFailures().isEmpty() ? "ui.ultimategarden.account.failed" : "ui.ultimategarden.account.partial"; error = true; }
        } catch (Exception failure) {
            com.kuangsanfeng.ultimategarden.Ultimate_Garden.LOGGER.error("Selected account configuration copy failed",failure);
            message = failure instanceof IllegalStateException ? language.errorKey((IllegalStateException)failure) : "ui.ultimategarden.account.failed"; error = true;
        }
        build();
    }
    }
}
