import com.kuangsanfeng.ultimategarden.client.config.*;
import com.kuangsanfeng.ultimategarden.client.ui.theme.ThemePalette;
import com.kuangsanfeng.ultimategarden.client.ui.language.LanguageType;
import java.nio.file.*;
import java.util.*;

/** Checks the specified opacity table and isolated UI persistence. */
public final class TransparencyRegression {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Transparency check " + checks); }
    public static void main(String[] args) throws Exception {
        UIConfig ui=new UIConfig(); check(ui.getMenuTransparency()==0);
        int[] percentages={0,25,50,75,100}, backgrounds={255,191,128,64,0}, foregrounds={255,223,191,159,128};
        for (UIConfig.Theme theme:UIConfig.Theme.values()) {
            ui.setUiThemeMode(theme); ThemePalette original=ThemePalette.from(ui);
            for(int i=0;i<percentages.length;i++) {
                ui.setMenuTransparency(percentages[i]); var palette=ThemePalette.from(ui);
                check(palette.bg(0xff345678)>>>24==backgrounds[i]);
                check(palette.fg(0xff345678)>>>24==foregrounds[i]);
                check((palette.bg(0xff345678)&0xffffff)==0x345678 && (palette.fg(0xff345678)&0xffffff)==0x345678);
                check(palette.text()==original.text() && palette.background()==original.background());
            }
            ui.setMenuTransparency(0);
        }
        ui.setMenuTransparency(50); var palette=ThemePalette.from(ui);
        check(palette.bg(0x80345678)>>>24==64); check(palette.fg(0x80345678)>>>24==96);
        check(palette.bg(0x80345678,.5f)>>>24==32); check(palette.fg(0x80345678,.5f)>>>24==48);
        check(palette.fg(0xff345678,0)==0x00345678);
        check(ThemePalette.blend(0x20345678,0x60345678,.5f)==0x40345678);
        for(int invalid:new int[]{-1,101}) {
            boolean rejected=false; try { ui.setMenuTransparency(invalid); } catch(IllegalArgumentException e) {rejected=true;}
            check(rejected); check(ui.getMenuTransparency()==50);
        }
        Path dir=Files.createTempDirectory("ultimate-garden-transparency-"); ConfigManager configs=new ConfigManager(dir);
        Map<String,String> business=new HashMap<>();
        for(String file:List.of("general","farm","crops","pest","esp"))business.put(file,Files.readString(dir.resolve(file+".json")));
        configs.getUIConfig().setLanguage(LanguageType.CHINESE); configs.getUIConfig().setUiThemeMode(UIConfig.Theme.DARK);
        configs.getUIConfig().setAccentColor("#123456");
        for(int value:percentages) {
            configs.getUIConfig().setMenuTransparency(value); check(configs.saveUIConfig());
            var restored=new ConfigManager(dir).getUIConfig(); check(restored.getMenuTransparency()==value);
            check(restored.getLanguage()==LanguageType.CHINESE && restored.getUiThemeMode()==UIConfig.Theme.DARK && restored.getAccentColor().equals("#123456"));
        }
        for(var entry:business.entrySet())check(Files.readString(dir.resolve(entry.getKey()+".json")).equals(entry.getValue()));
        for(String invalid:List.of("-1","101","1.5","\"50\"","null")) {
            Files.writeString(dir.resolve("ui.json"),"{\"menuTransparency\":"+invalid+",\"language\":\"CHINESE\",\"uiThemeMode\":\"DARK\",\"accentColor\":\"#123456\",\"extra\":true}");
            var restored=new ConfigManager(dir).getUIConfig(); check(restored.getMenuTransparency()==0);
            check(restored.getLanguage()==LanguageType.CHINESE && restored.getUiThemeMode()==UIConfig.Theme.DARK && restored.getAccentColor().equals("#123456"));
            check(Files.readString(dir.resolve("ui.json")).contains("\"extra\": true"));
        }
        ui.resetToDefault(); check(ui.getMenuTransparency()==0);
        System.out.println("PASS: "+checks+" transparency checks: opacity table, alpha multiplication, animations, themes, persistence, validation and unrelated-module isolation.");
    }
}
