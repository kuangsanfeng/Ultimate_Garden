package com.kuangsanfeng.ultimategarden.client.platform.persistence.config;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.storage.AccountDirectories;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.storage.AccountIdentity;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.storage.ConfigIO;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.farm.CropConfig;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.ESPConfig;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.farm.FarmConfig;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.GeneralConfig;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.HudConfig;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.farm.PestConfig;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.RandomDelayConfig;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.display.UIConfig;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.ViewDeviationConfig;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootoutConfig;


import com.google.gson.*;
import com.kuangsanfeng.ultimategarden.Ultimate_Garden;

import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.util.*;

/** One instance is loaded before controllers are constructed. */
public final class ConfigManager {
    public enum Module { GENERAL, FARM, UI, CROPS, PEST, ESP, RANDOM_DELAY, HUD, ZOMBIE_SHOOTOUT, ANTI_CHECK }
    private Path directory;
    private AccountDirectories accounts;
    private AccountDirectories.Profile profile;
    private AccountIdentity identity;
    private boolean startupCorrectionPending = true;
    private boolean correctingStartup;
    private long accountRevision;
    private java.util.function.BooleanSupplier currentAccountCopySafe = () -> false;
    private final Set<Module> pendingRestart = EnumSet.noneOf(Module.class);
    private final ConfigIO io = new ConfigIO();
    private com.kuangsanfeng.ultimategarden.client.function.path_finding.PathSettings pathSettings = new com.kuangsanfeng.ultimategarden.client.function.path_finding.PathSettings();
    public com.kuangsanfeng.ultimategarden.client.function.path_finding.PathSettings getPathSettings() { return pathSettings; }
    private final GeneralConfig general = new GeneralConfig();
    private final FarmConfig farm = new FarmConfig();
    private final UIConfig ui = new UIConfig();
    private final PestConfig pest = new PestConfig();
    private final ESPConfig esp = new ESPConfig();
    private final ViewDeviationConfig viewDeviation=new ViewDeviationConfig();
    public ViewDeviationConfig getViewDeviationConfig(){return viewDeviation;}
    private final ZombieShootoutConfig shootout = new ZombieShootoutConfig();
    public ZombieShootoutConfig getZombieShootoutConfig(){return shootout;}
    private final HudConfig hud = new HudConfig();
    private final RandomDelayConfig randomDelays = new RandomDelayConfig();
    private boolean repairedField;
    private final CropConfig crops = new CropConfig();
    private final Map<Module, JsonObject> documents = new EnumMap<>(Module.class);
    private final Set<Module> protectedFiles = EnumSet.noneOf(Module.class);

    public ConfigManager() {
        this(userSettingsRoot(), null);
    }
    public static Path userSettingsRoot() { return FabricLoader.getInstance().getConfigDir().resolve("ultimategarden").resolve("user-setting"); }
    /** Explicit module directory is for isolated tests, never the runtime root. */
    public ConfigManager(Path directory) {
        this.directory = directory;
        for (Module module : Module.values()) load(module);
    }
    public ConfigManager(Path root, AccountIdentity identity) {
        accounts = new AccountDirectories(root, io);
        activateAccount(identity);
    }
    public synchronized boolean activateAccount(AccountIdentity next) {
        if (accounts == null) throw new IllegalStateException("Account storage is not enabled");
        accountRevision++; identity = next; profile = null; directory = null;
        documents.clear(); protectedFiles.clear(); pendingRestart.clear();
        for (Module module : Module.values()) { defaults(module); documents.put(module,new JsonObject()); }
        esp.clearForAccount();
        if(next == null) { protectedFiles.addAll(EnumSet.allOf(Module.class)); return false; }
        try (var guard = accounts.lock()) {
            if (next == null) throw new IOException("Minecraft account is not initialized");
            profile = accounts.resolve(next); directory = accounts.verified(profile);
            correctingStartup = startupCorrectionPending;
            try { for (Module module : Module.values()) load(module); }
            finally { correctingStartup = false; startupCorrectionPending = false; }
            return true;
        } catch (IOException | RuntimeException error) {
            profile = null; directory = null;
            for (Module module : Module.values()) { defaults(module); documents.put(module,new JsonObject()); }
            esp.clearForAccount(); protectedFiles.addAll(EnumSet.allOf(Module.class));
            Ultimate_Garden.LOGGER.error("Account configuration unavailable; using unsaved memory defaults",error);
            return false;
        }
    }
    public synchronized boolean matchesAccount(AccountIdentity candidate) { return Objects.equals(identity,candidate); }
    public synchronized AccountDirectories.Profile currentAccount() { return profile; }
    public synchronized long accountRevision() { return accountRevision; }
    public synchronized void setCurrentAccountCopyGuard(java.util.function.BooleanSupplier guard) { currentAccountCopySafe = Objects.requireNonNull(guard); }
    public synchronized boolean isPendingRestart(Module module) { return pendingRestart.contains(module); }
    public synchronized Set<Module> pendingRestartModules() { return Set.copyOf(pendingRestart); }
    public synchronized List<AccountDirectories.Profile> accountProfiles() throws IOException { return accounts == null ? List.of() : accounts.list(); }
    public synchronized boolean hasLegacyConfiguration() throws IOException { return accounts != null && accounts.hasLegacy(); }
    private void verifyStorage(Module module) throws IOException {
        if (accounts != null) {
            if (profile == null) throw new IOException("No verified account selected");
            if (!accounts.file(profile,module).equals(file(module))) throw new IOException("Account directory changed");
        }
    }
    public HudConfig getHudConfig() { return hud; }
    public RandomDelayConfig getRandomDelayConfig() { randomDelays.setParentEnabled(()->viewDeviation.enabled); return randomDelays; }
    public PestConfig getPestConfig() { return pest; }
    public ESPConfig getESPConfig() { return esp; }
    public GeneralConfig getGeneralConfig() { return general; }
    public FarmConfig getFarmConfig() { return farm; }
    public CropConfig getCropConfig() { return crops; }
    public UIConfig getUIConfig() { return ui; }
    private Path file(Module module) { return directory.resolve(module.name().toLowerCase(Locale.ROOT) + ".json"); }

    private static int integer(JsonObject object, String key, int fallback) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Expected integer: " + key);
        return value.getAsBigDecimal().intValueExact();
    }
    private static String string(JsonObject object, String key, String fallback) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected string: " + key);
        return value.getAsString();
    }
    private static String textValue(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected string");
        return value.getAsString();
    }
    private static int intValue(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected integer");
        return value.getAsBigDecimal().intValueExact();
    }
    private static boolean boolValue(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Expected boolean");
        return value.getAsBoolean();
    }
    private void field(JsonObject document, String key, String legacy,
                       java.util.function.Consumer<JsonElement> current,
                       java.util.function.Consumer<JsonElement> previous) {
        if (document.has(key)) {
            try { current.accept(document.get(key)); return; }
            catch (RuntimeException e) {
                repairedField = true;
                Ultimate_Garden.LOGGER.warn("Invalid configuration field {}; trying legacy/default", key, e);
            }
        }
        if (legacy != null && document.has(legacy)) {
            try { previous.accept(document.get(legacy)); }
            catch (RuntimeException e) {
                repairedField = true;
                Ultimate_Garden.LOGGER.warn("Invalid legacy configuration field {}; keeping field default", legacy, e);
            }
        }
    }
    private void defaults(Module module) {
        if (module == Module.GENERAL) { general.resetToDefault(); pathSettings = new com.kuangsanfeng.ultimategarden.client.function.path_finding.PathSettings(); }
        if (module == Module.ANTI_CHECK) viewDeviation.resetToDefault();
        if (module == Module.HUD) hud.resetToDefault();
        if (module == Module.ZOMBIE_SHOOTOUT) shootout.resetToDefault();
        if (module == Module.PEST) pest.resetToDefault();
        if (module == Module.ESP) esp.resetToDefault();
        if (module == Module.RANDOM_DELAY) randomDelays.resetToDefault();
        if (module == Module.FARM) farm.resetToDefault();
        if (module == Module.UI) ui.resetToDefault();
        if (module == Module.CROPS) crops.resetToDefault();
    }
    private void load(Module module) {
        JsonObject document = new JsonObject();
        try {
            verifyStorage(module);
            document = io.read(file(module));
            int version = integer(document, "schemaVersion", 1);
            // Future migrations belong here; never downgrade a newer document.
            if (version > (module == Module.ESP ? ESPConfig.SCHEMA_VERSION : 1)) {
                protectedFiles.add(module);
                Ultimate_Garden.LOGGER.warn("Unsupported config schema {} in {}; using memory defaults, writes disabled", version, file(module));
            } else {
                if (version < 1) throw new IllegalArgumentException("Invalid schemaVersion");
                if (module == Module.CROPS) {
                    crops.load(document);
                    if (crops.wasRepaired() && !io.backup(file(module))) protectedFiles.add(module);
                } else if (module == Module.ANTI_CHECK) {
                    if(viewDeviation.load(document) && !io.backup(file(module))) protectedFiles.add(module);
                } else if (module == Module.ZOMBIE_SHOOTOUT) {
                    if(shootout.load(document) && !io.backup(file(module))) protectedFiles.add(module);
                } else if (module == Module.HUD) {
                    if (hud.load(document) && !io.backup(file(module))) protectedFiles.add(module);
                } else if (module == Module.RANDOM_DELAY) {
                    if (randomDelays.load(document) && !io.backup(file(module))) protectedFiles.add(module);
                } else if (module == Module.ESP) {
                    boolean migration = esp.requiresMigration(document);
                    if (migration && !io.backupForMigration(file(module))) protectedFiles.add(module);
                    if (esp.load(document) && !migration && !io.backup(file(module))) protectedFiles.add(module);
                } else {
                    repairedField = false;
                    readFields(module, document, general, farm, ui, pest);
                    if (module == Module.GENERAL) repairedField |= pathSettings.load(document);
                    if (repairedField && !io.backup(file(module))) protectedFiles.add(module);
                }
            }
        } catch (IOException e) {
            defaults(module);
            protectedFiles.add(module);
            Ultimate_Garden.LOGGER.error("Cannot read config {}; using memory defaults, writes disabled", file(module), e);
        } catch (RuntimeException e) {
            defaults(module);
            Ultimate_Garden.LOGGER.error("Invalid config {}; recovering defaults", file(module), e);
            if (!io.backup(file(module))) protectedFiles.add(module);
            document = new JsonObject();
        }
        documents.put(module, document);
        if (!protectedFiles.contains(module)) {
            if (correctingStartup) {
                JsonObject canonical = encode(module, new JsonObject());
                if (module == Module.CROPS) canonical = ConfigCanonicalizer.knownFields(canonical,new CropConfig().toJson());
                canonical = ConfigCanonicalizer.ordered(canonical,document);
                // Compare ordered compact JSON: JsonObject.equals deliberately ignores key order.
                if (!Files.exists(file(module)) || !canonical.toString().equals(document.toString())) {
                    if (!io.write(file(module),canonical)) { protectedFiles.add(module); return; }
                }
                documents.put(module,canonical);
                if (module == Module.CROPS) crops.load(canonical);
            } else save(module);
        }
    }

    private void readFields(Module module, JsonObject document, GeneralConfig general, FarmConfig farm, UIConfig ui, PestConfig pest) {
                    if (module == Module.FARM) {
                        field(document, "selectedCropType", "cropType",
                            value -> farm.setSelectedCropType(textValue(value)),
                            value -> { throw new IllegalArgumentException("Numeric crop selection requires reselection"); });
                        field(document, "selectedFarmMode", "mode",
                            value -> farm.setSelectedFarmMode(FarmConfig.FarmMode.valueOf(textValue(value))),
                            value -> farm.setSelectedFarmMode(FarmConfig.farmModeFromId(intValue(value))));
                        field(document, "selectedSimulatedCropType", "simulateCropType",
                            value -> farm.setSelectedSimulatedCropType(textValue(value)),
                            value -> { throw new IllegalArgumentException("Numeric simulated crop requires reselection"); });
                    } else if (module == Module.UI) {
                        field(document, "mappingReplayShowChunkBorders", null, value -> ui.setMappingReplayShowChunkBorders(boolValue(value)), null);
                        field(document, "menuTransparency", null, value -> ui.setMenuTransparency(intValue(value)), null);
                        field(document, "language", null, value -> ui.setLanguage(com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageType.valueOf(textValue(value))), null);
                        field(document, "uiThemeMode", "theme", value -> ui.setUiThemeMode(UIConfig.Theme.valueOf(textValue(value))), value -> ui.setUiThemeMode(UIConfig.Theme.valueOf(textValue(value))));
                        field(document, "accentColor", null, value -> ui.setAccentColor(textValue(value)), null);
                    } else if (module == Module.GENERAL) {
                        repairedField |= general.loadHints(document);
                        repairedField |= general.instantTransmission().load(document);
                        field(document, "showModChatHints", null, value -> general.setShowModChatHints(boolValue(value)), null);
                        field(document, "autoPauseFarmWhenOpeningMenu", null, value -> general.setAutoPauseFarmWhenOpeningMenu(boolValue(value)), null);
                        field(document, "autoResumeFarmAfterGuiClose", null, value -> general.setAutoResumeFarmAfterGuiClose(boolValue(value)), null);
                    } else if (module == Module.PEST) {
                        field(document, "pestESPEnabled", null, value -> pest.setPestESPEnabled(boolValue(value)), null);
                        field(document, "autoPestLoadout", null, value -> pest.setAutoPestLoadout(boolValue(value)), null);
                        field(document, "pestLoadoutDelayTicks", null, value -> pest.setPestLoadoutDelayTicks(intValue(value)), null);
                    }
    }

    public synchronized boolean save(Module module) {
        try (var guard = accounts == null ? null : accounts.lock()) { return saveUnlocked(module); }
        catch(IOException | RuntimeException error) { Ultimate_Garden.LOGGER.error("Account configuration save failed",error); return false; }
    }
    private boolean saveUnlocked(Module module) {
        if (directory == null || protectedFiles.contains(module) || pendingRestart.contains(module)) {
            Ultimate_Garden.LOGGER.warn("Config {} is protected or unavailable; changes remain in memory", module);
            return false;
        }
        try { verifyStorage(module); }
        catch (IOException | RuntimeException error) { Ultimate_Garden.LOGGER.error("Refusing to save unverified account configuration",error); return false; }
        JsonObject document = encode(module,documents.get(module));
        if (!io.write(file(module), document)) return false;
        documents.put(module, document);
        return true;
    }
    /** One serialization definition for ordinary saving and startup correction. */
    private JsonObject encode(Module module, JsonObject original) {
        JsonObject document = original.deepCopy();
        document.addProperty("schemaVersion", 1);
        switch (module) {
            case CROPS -> document = crops.toJson();
            case ESP -> document = esp.toJson(document);
            case RANDOM_DELAY -> document = randomDelays.toJson(document);
            case HUD -> document = hud.toJson(document);
            case ANTI_CHECK -> document = viewDeviation.toJson(document);
            case ZOMBIE_SHOOTOUT -> document = shootout.toJson(document);
            case GENERAL -> {
                document.add("hint",general.hintsToJson(document));
                document.add("instantTransmission",general.instantTransmission().toJson());
                document.add("pathFinding",pathSettings.toJson());
                document.addProperty("showModChatHints", general.isShowModChatHints());
                document.addProperty("autoPauseFarmWhenOpeningMenu", general.isAutoPauseFarmWhenOpeningMenu());
                document.addProperty("autoResumeFarmAfterGuiClose", general.isAutoResumeFarmAfterGuiClose());
            }
            case PEST -> {
                document.addProperty("pestESPEnabled", pest.isPestESPEnabled());
                document.addProperty("autoPestLoadout", pest.isAutoPestLoadout());
                document.addProperty("pestLoadoutDelayTicks", pest.getPestLoadoutDelayTicks());
            }
            case FARM -> {
                document.remove("cropType");
                document.addProperty("selectedCropType", farm.getSelectedCropType());
                document.remove("mode");
                document.addProperty("selectedFarmMode", farm.getSelectedFarmMode().name());
                document.remove("simulateCropType");
                document.addProperty("selectedSimulatedCropType", farm.getSelectedSimulatedCropType());
            }
            case UI -> {
                document.addProperty("mappingReplayShowChunkBorders", ui.isMappingReplayShowChunkBorders());
                document.addProperty("menuTransparency", ui.getMenuTransparency());
                document.addProperty("language", ui.getLanguage().name());
                document.remove("theme");
                document.addProperty("uiThemeMode", ui.getUiThemeMode().name());
                document.addProperty("accentColor", ui.getAccentColor());
            }
        }
        return document;
    }
    public boolean saveFarmConfig() { return save(Module.FARM); }
    public boolean saveUIConfig() { return save(Module.UI); }
    public synchronized boolean saveAll() {
        boolean success = true;
        for (Module module : Module.values()) success &= save(module);
        return success;
    }
    public synchronized boolean reset(Module module) {
        if(pendingRestart.contains(module)) return false;
        defaults(module); return save(module);
    }

    /** Uses the ordinary encoders and loading rules for one verified account file. */
    public synchronized boolean restoreAccountDefaults(AccountDirectories.Profile expected,long revision,Module module) {
        if(accounts==null || expected==null || !expected.equals(profile) || revision!=accountRevision
                || pendingRestart.contains(module) || protectedFiles.contains(module)) return false;
        try(var guard=accounts.lock()){
            verifyStorage(module);
            var defaults=new ConfigManager(true);
            defaults.defaults(module);
            var clean=defaults.encode(module,new JsonObject());
            if(!io.write(file(module),clean))return false;
            if(module==Module.ESP)esp.clearForAccount();
            defaults(module);
            load(module);
            return true;
        } catch(IOException | RuntimeException error){
            Ultimate_Garden.LOGGER.error("Selected account defaults could not be restored",error);
            return false;
        }
    }
    private ConfigManager(boolean defaultsOnly) {}

    public record CopyPlan(AccountDirectories.Profile source, AccountDirectories.Profile target, Set<Module> modules, long revision) {
        public CopyPlan { modules = Set.copyOf(modules); }
    }
    public record CopyReport(boolean success, Set<Module> copied, Set<Module> restartRequired, List<String> rollbackFailures) { }
    /** Uses the same setters and model validators as normal loading, with no live-model mutation. */
    private void validateCopy(Module module, JsonObject document) throws IOException {
        int version = integer(document,"schemaVersion",1);
        if (version < 1 || version > (module == Module.ESP ? ESPConfig.SCHEMA_VERSION : 1)) throw new IOException("Unsupported source schema");
        repairedField = false;
        if (module == Module.CROPS) {
            CropConfig candidate = new CropConfig(); candidate.load(document);
            if(candidate.wasRepaired()) throw new IOException("Source crop configuration requires repair");
        } else if (module == Module.ANTI_CHECK) {
            if(new ViewDeviationConfig().load(document)) throw new IOException("Invalid view check configuration");
        } else if (module == Module.ZOMBIE_SHOOTOUT) {
            if(new ZombieShootoutConfig().load(document)) throw new IOException("Invalid Shootout config");
        } else if (module == Module.HUD) {
            if (new HudConfig().load(document)) throw new IOException("Invalid HUD configuration");
        } else if (module == Module.RANDOM_DELAY) {
            if (new RandomDelayConfig().load(document)) throw new IOException("Invalid source random delay configuration");
        } else if (module == Module.ESP) {
            if(new ESPConfig().load(document)) throw new IOException("Invalid source ESP configuration");
        } else {
            readFields(module,document,new GeneralConfig(),new FarmConfig(),new UIConfig(),new PestConfig());
            if (module == Module.GENERAL) repairedField |= new com.kuangsanfeng.ultimategarden.client.function.path_finding.PathSettings().load(document);
            if(repairedField) throw new IOException("Invalid source configuration field");
        }
    }
    public synchronized CopyReport copyConfiguration(CopyPlan plan) throws IOException {
        if(accounts == null) throw new IOException("Account storage is not enabled");
        try(var guard = accounts.lock()) { return copyUnlocked(plan); }
    }
    private CopyReport copyUnlocked(CopyPlan plan) throws IOException {
        if(accounts == null || profile == null || plan.revision() != accountRevision) throw new IOException("Account session changed");
        if(plan.target() == null || plan.source() == null || plan.target().legacy() || plan.modules().isEmpty()
                || Objects.equals(plan.source().uuid(),plan.target().uuid())) throw new IOException("Invalid copy selection");
        if(profile.uuid().equals(plan.target().uuid()) && !currentAccountCopySafe.getAsBoolean()) throw new IOException("Current account is not safely paused");
        Map<Path,JsonObject> selected = new LinkedHashMap<>();
        for(Module module:Module.values()) if(plan.modules().contains(module)) {
            Path source = accounts.file(plan.source(),module); AccountDirectories.regular(source);
            JsonObject document;
            try { document = io.read(source); validateCopy(module,document); }
            catch(RuntimeException error) { throw new IOException("Invalid selected source configuration: "+module.name(),error); }
            selected.put(accounts.file(plan.target(),module),document);
        }
        // Source documents are validated snapshots; rollback only requires safe destinations.
        var result = io.replaceTransaction(selected,() -> {
            accounts.verified(plan.target());
            for(Module module:plan.modules()) accounts.file(plan.target(),module);
        });
        boolean current = profile.uuid().equals(plan.target().uuid());
        if(!result.success()) {
            if(current && !result.rollbackFailures().isEmpty()) pendingRestart.addAll(plan.modules());
            return new CopyReport(false,Set.of(),Set.copyOf(pendingRestart),result.rollbackFailures());
        }
        if(current) {
            for(Module module:plan.modules()) {
                if(module == Module.FARM || module == Module.CROPS) { pendingRestart.add(module); continue; }
                defaults(module); if(module == Module.ESP) esp.clearForAccount();
                protectedFiles.remove(module); load(module);
                if(protectedFiles.contains(module)) pendingRestart.add(module);
            }
        }
        return new CopyReport(true,plan.modules(),current ? Set.copyOf(pendingRestart) : Set.of(),List.of());
    }
}
