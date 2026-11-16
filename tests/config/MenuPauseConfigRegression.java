
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;
import com.google.gson.*;

import com.kuangsanfeng.ultimategarden.client.platform.gui.GuiPauseState;
import java.nio.file.*;

public final class MenuPauseConfigRegression {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Menu pause config check " + checks); }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("ug-menu-pause-");
        var configs = new ConfigManager(root);
        check(configs.getGeneralConfig().isAutoPauseFarmWhenOpeningMenu());
        var original = JsonParser.parseString(Files.readString(root.resolve("general.json"))).getAsJsonObject();
        original.remove("autoPauseFarmWhenOpeningMenu");
        original.addProperty("autoResumeFarmAfterGuiClose",true);
        original.addProperty("userExtra","keep");
        Files.writeString(root.resolve("general.json"),original.toString());
        configs = new ConfigManager(root);
        check(configs.getGeneralConfig().isAutoPauseFarmWhenOpeningMenu());
        check(configs.getGeneralConfig().isAutoResumeFarmAfterGuiClose());
        for (boolean resume : new boolean[]{false,true}) {
            var general = configs.getGeneralConfig();
            general.setAutoResumeFarmAfterGuiClose(resume);
            general.setAutoPauseFarmWhenOpeningMenu(false);
            check(general.isAutoResumeFarmAfterGuiClose() == resume);
            check(configs.save(ConfigManager.Module.GENERAL));
            configs = new ConfigManager(root);
            check(!configs.getGeneralConfig().isAutoPauseFarmWhenOpeningMenu());
            check(configs.getGeneralConfig().isAutoResumeFarmAfterGuiClose() == resume);
            configs.getGeneralConfig().setAutoPauseFarmWhenOpeningMenu(true);
            check(configs.getGeneralConfig().isAutoResumeFarmAfterGuiClose() == resume);
        }
        check(JsonParser.parseString(Files.readString(root.resolve("general.json"))).getAsJsonObject().get("userExtra").getAsString().equals("keep"));
        var session = new GuiPauseState();
        session.open(true); session.close(false); check(!session.finish());
        session.open(true); session.close(true); check(session.finish());
        // Disabling automatic recovery does not erase an explicit start or pause.
        session.open(true); session.start(); session.close(false); check(session.finish());
        session.open(true); session.pause(false); session.close(true); check(!session.finish());
        session.open(true); session.pause(true); session.close(true); check(!session.finish());
        configs.getGeneralConfig().resetToDefault();
        check(configs.getGeneralConfig().isAutoPauseFarmWhenOpeningMenu());
        check(!configs.getGeneralConfig().isAutoResumeFarmAfterGuiClose());
        System.out.println("PASS: " + checks + " menu auto-pause persistence, migration and recovery-intent checks.");
    }
}
