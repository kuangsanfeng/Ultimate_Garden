
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.storage.AccountDirectories;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.storage.AccountIdentity;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.storage.ConfigIO;
import com.google.gson.*;

import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager.Module;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Account isolation and fault-injected selective copy checks. */
public final class AccountRegression {
    private static int checks;
    private static void check(boolean value) { checks++; if(!value) throw new AssertionError("Account check " + checks); }
    private static void refused(Throwing action) throws Exception {
        boolean failed=false; try { action.run(); } catch(IOException | IllegalArgumentException expected) { failed=true; }
        check(failed);
    }
    private interface Throwing { void run() throws Exception; }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static ConfigManager.CopyPlan plan(ConfigManager m, AccountDirectories.Profile a, AccountDirectories.Profile b, Module... files) {
        return new ConfigManager.CopyPlan(a,b,Set.of(files),m.accountRevision());
    }
    private static String contents(Path root, String account, String file) throws Exception { return Files.readString(root.resolve(account).resolve(file+".json")); }
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("ug-accounts-");
        Files.writeString(root.resolve("general.json"),"{\"showModChatHints\":false}");
        String legacy=Files.readString(root.resolve("general.json"));
        var a=new AccountIdentity(UUID.randomUUID(),"FirstAccount");
        var b=new AccountIdentity(UUID.randomUUID(),"SecondAccount");
        ConfigManager m=new ConfigManager(root,a);
        var ap=m.currentAccount(); check(ap.uuid().equals(a.uuid())); check(m.getGeneralConfig().isShowModChatHints());
        var general=m.getGeneralConfig(); var farm=m.getFarmConfig(); var crops=m.getCropConfig(); var esp=m.getESPConfig();
        for(Module module:Module.values()) {
            String name=module.name().toLowerCase(Locale.ROOT)+".json";
            check(Files.isRegularFile(root.resolve(a.name()).resolve(name)));
            if(module!=Module.GENERAL) check(!Files.exists(root.resolve(name)));
        }
        m.getGeneralConfig().setShowModChatHints(false); check(m.save(Module.GENERAL));
        esp.registerTarget("FIRST_ACCOUNT_ONLY");
        check(m.activateAccount(b)); var bp=m.currentAccount();
        check(!esp.hasTarget("FIRST_ACCOUNT_ONLY"));
        check(m.getGeneralConfig()==general && m.getFarmConfig()==farm && m.getCropConfig()==crops && m.getESPConfig()==esp);
        check(general.isShowModChatHints()); check(m.accountProfiles().size()==2);
        check(Files.readString(root.resolve("general.json")).equals(legacy));
        String source=contents(root,a.name(),"general"), untouched=contents(root,b.name(),"ui"), identity=contents(root,b.name(),"account");
        refused(()->m.copyConfiguration(plan(m,ap,bp,Module.GENERAL)));
        m.setCurrentAccountCopyGuard(()->true);
        var copied=m.copyConfiguration(plan(m,ap,bp,Module.GENERAL)); check(copied.success());
        check(!general.isShowModChatHints()); check(m.getGeneralConfig()==general);
        check(contents(root,a.name(),"general").equals(source)); check(contents(root,b.name(),"ui").equals(untouched));
        check(contents(root,b.name(),"account").equals(identity));
        try(var files=Files.list(root.resolve(b.name()))) { check(files.anyMatch(p->p.getFileName().toString().startsWith("general.json.overwrite-"))); }
        check(m.copyConfiguration(plan(m,AccountDirectories.LEGACY,bp,Module.GENERAL)).success());
        check(Files.readString(root.resolve("general.json")).equals(legacy));
        // Safe modules refresh the existing models, preserving unselected files.
        var pest=m.getPestConfig(); var ui=m.getUIConfig();
        Files.writeString(root.resolve(a.name()).resolve("ui.json"),"{\"language\":\"CHINESE\",\"accentColor\":\"#112233\",\"menuTransparency\":31}");
        Files.writeString(root.resolve(a.name()).resolve("pest.json"),"{\"autoPestLoadout\":false,\"pestLoadoutDelayTicks\":123,\"pestESPEnabled\":true}");
        String farmBefore=contents(root,b.name(),"farm"),cropsBefore=contents(root,b.name(),"crops");
        check(m.copyConfiguration(plan(m,ap,bp,Module.UI,Module.PEST,Module.ESP)).success());
        check(m.getPestConfig()==pest && m.getUIConfig()==ui && m.getESPConfig()==esp);
        check(!pest.isAutoPestLoadout() && pest.getPestLoadoutDelayTicks()==123 && pest.isPestESPEnabled());
        check(ui.getAccentColor().equals("#112233") && ui.getMenuTransparency()==31 && ui.getLanguage().name().equals("CHINESE"));
        check(farmBefore.equals(contents(root,b.name(),"farm")) && cropsBefore.equals(contents(root,b.name(),"crops")));
        String targetBefore=contents(root,a.name(),"general");
        m.getGeneralConfig().setShowModChatHints(true); check(m.save(Module.GENERAL));
        check(m.copyConfiguration(plan(m,bp,ap,Module.GENERAL)).success());
        check(general.isShowModChatHints()); check(!targetBefore.equals(contents(root,a.name(),"general")));
        String oldSelection=farm.getSelectedCropType();
        Files.writeString(root.resolve(a.name()).resolve("farm.json"),"{\"schemaVersion\":1,\"selectedCropType\":\"CARROT\",\"selectedFarmMode\":\"FARM\"}");
        var result=m.copyConfiguration(plan(m,ap,bp,Module.FARM,Module.CROPS));
        check(result.success() && result.restartRequired().containsAll(Set.of(Module.FARM,Module.CROPS)));
        check(farm.getSelectedCropType().equals(oldSelection));
        String imported=contents(root,b.name(),"farm"); check(!m.saveFarmConfig()); check(!m.reset(Module.FARM));
        check(contents(root,b.name(),"farm").equals(imported));
        var stale=plan(m,ap,bp,Module.UI); check(m.activateAccount(a)); check(m.pendingRestartModules().isEmpty());
        check(farm.getSelectedCropType().equals("CARROT")); refused(()->m.copyConfiguration(stale));
        // Validate every selected source before any destination is replaced.
        String destination=contents(root,a.name(),"ui");
        for(String invalid:List.of("{broken", "{\"menuTransparency\":\"bad\"}","{\"schemaVersion\":99}","{\"accentColor\":\"red\"}")) {
            Files.writeString(root.resolve(b.name()).resolve("ui.json"),invalid);
            refused(()->m.copyConfiguration(plan(m,bp,ap,Module.GENERAL,Module.UI)));
            check(contents(root,a.name(),"ui").equals(destination));
        }
        Files.delete(root.resolve(b.name()).resolve("ui.json"));
        refused(()->m.copyConfiguration(plan(m,bp,ap,Module.UI)));
        refused(()->m.copyConfiguration(plan(m,ap,ap,Module.GENERAL)));
        // Rename is claimed only by a unique UUID; name conflicts never inherit data.
        var renamed=new AccountIdentity(a.uuid(),"RenamedAccount"); check(m.activateAccount(renamed));
        check(!Files.exists(root.resolve(a.name()))); check(farm.getSelectedCropType().equals("CARROT"));
        check(!m.activateAccount(new AccountIdentity(UUID.randomUUID(),renamed.name())));
        check(m.currentAccount()==null && farm.getSelectedCropType().equals("NONE") && !m.saveAll());
        Files.createDirectory(root.resolve("Unverified")); check(!m.activateAccount(new AccountIdentity(UUID.randomUUID(),"Unverified")));
        check(!m.activateAccount(null)); check(m.currentAccount()==null);
        for(String bad:List.of("../escape","a/b","a\\b","CON","NUL","COM1","", "this_name_is_far_too_long"))
            refused(()->new AccountIdentity(UUID.randomUUID(),bad));
        Path duplicate=root.resolve("Duplicate"); Files.createDirectory(duplicate);
        JsonObject meta=json(Files.readString(root.resolve(renamed.name()).resolve("account.json"))); meta.addProperty("name","Duplicate");
        check(new ConfigIO().write(duplicate.resolve("account.json"),meta)); check(!m.activateAccount(renamed));
        // Failure after the first replacement restores byte-exact destination files.
        Path transaction=Files.createTempDirectory("ug-copy-rollback-"); Path first=transaction.resolve("first.json"),second=transaction.resolve("second.json");
        Files.writeString(first,"{ \"old\": 1 }\n"); Files.writeString(second,"{\"old\":2}\n");
        String firstOld=Files.readString(first),secondOld=Files.readString(second);
        Map<Path,JsonObject> batch=new LinkedHashMap<>(); batch.put(first,json("{\"new\":1}")); batch.put(second,json("{\"new\":2}"));
        int[] calls={0}; var failure=new ConfigIO().replaceTransaction(batch,()-> { if(++calls[0]==6) throw new IOException("Injected second replacement failure"); });
        check(!failure.success() && failure.rollbackFailures().isEmpty());
        check(Files.readString(first).equals(firstOld) && Files.readString(second).equals(secondOld));
        try(var files=Files.list(transaction)) { check(files.filter(p->p.getFileName().toString().contains(".overwrite-")).count()==2); }
        check(new ConfigIO().replaceTransaction(batch,()->{}).success());
        check(json(Files.readString(first)).get("new").getAsInt()==1 && json(Files.readString(second)).get("new").getAsInt()==2);
        // The shared root lock serializes independent managers and is reentrant.
        AccountDirectories directoryLocks=new AccountDirectories(transaction,new ConfigIO());
        var acquired=new java.util.concurrent.CountDownLatch(1);
        try(var lock=directoryLocks.lock(); var nested=directoryLocks.lock()) {
            Thread contender=Thread.ofPlatform().start(()-> {
                try(var other=new AccountDirectories(transaction,new ConfigIO()).lock()) { acquired.countDown(); }
                catch(IOException error) { throw new RuntimeException(error); }
            });
            check(!acquired.await(80,java.util.concurrent.TimeUnit.MILLISECONDS));
        }
        check(acquired.await(5,java.util.concurrent.TimeUnit.SECONDS));
        Path allRoot=Files.createTempDirectory("ug-copy-all-"); ConfigManager all=new ConfigManager(allRoot,a);
        var allSource=all.currentAccount(); all.getUIConfig().setAccentColor("#ABCDEF"); check(all.saveUIConfig());
        check(all.activateAccount(b)); var allTarget=all.currentAccount();
        Map<Module,String> allOriginals=new EnumMap<>(Module.class);
        for(Module file:Module.values()) allOriginals.put(file,contents(allRoot,a.name(),file.name().toLowerCase(Locale.ROOT)));
        all.setCurrentAccountCopyGuard(()->true);
        var allReport=all.copyConfiguration(new ConfigManager.CopyPlan(allSource,allTarget,EnumSet.allOf(Module.class),all.accountRevision()));
        check(allReport.success() && allReport.copied().size()==Module.values().length);
        for(Module file:Module.values()) {
            String name=file.name().toLowerCase(Locale.ROOT);
            check(contents(allRoot,a.name(),name).equals(allOriginals.get(file)));
            check(json(contents(allRoot,b.name(),name)).equals(json(allOriginals.get(file))));
        }
        try(var files=Files.list(allRoot.resolve(b.name()))) { check(files.filter(p->p.getFileName().toString().contains(".overwrite-")).count()==Module.values().length); }
        // Links cannot redirect account or module access outside the root.
        Path linkRoot=Files.createTempDirectory("ug-account-links-"); ConfigManager links=new ConfigManager(linkRoot,a);
        Path external=Files.createTempFile("ug-private-",".json"); Files.writeString(external,"{}");
        Path module=linkRoot.resolve(a.name()).resolve("general.json");
        try {
            Files.delete(module); Files.createSymbolicLink(module,external); check(!links.save(Module.GENERAL));
            check(Files.readString(external).equals("{}"));
        } catch(FileSystemException | UnsupportedOperationException unavailable) {
            System.out.println("SKIP: symbolic-link creation unavailable: " + unavailable.getClass().getSimpleName());
        }
        System.out.println("PASS: "+checks+" account checks: identity, isolation, legacy import, selective copy, guards, cache protection, validation and rollback.");
    }
}
