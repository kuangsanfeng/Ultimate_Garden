import com.google.gson.*;
import com.kuangsanfeng.ultimategarden.client.config.*;
import com.kuangsanfeng.ultimategarden.client.ui.language.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import javax.imageio.ImageIO;

/** Independent GUI dictionaries and isolated configuration transactions. */
public final class LanguageRegression {
    private static int checks;
    private static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("Language check " + checks); }
    private static JsonObject dictionary(LanguageType type) throws Exception {
        try (var stream = LanguageRegression.class.getResourceAsStream("/assets/ultimate_garden/lang/" + type.code() + ".json")) {
            return JsonParser.parseString(new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    public static void main(String[] args) throws Exception {
        JsonObject english = dictionary(LanguageType.ENGLISH), chinese = dictionary(LanguageType.CHINESE);
        check(english.keySet().equals(chinese.keySet()));
        check(chinese.get("ui.ultimategarden.account.title").getAsString().equals("账号配置"));
        for (String key : english.keySet()) {
            check(!chinese.get(key).getAsString().isBlank());
            check(!chinese.get(key).getAsString().contains("??"));
            if(!Set.of("ESP","Ultimate","Garden","UG","Ultimate Garden","%d%%").contains(chinese.get(key).getAsString()))
                check(Pattern.compile("[\\p{IsHan}]").matcher(chinese.get(key).getAsString()).find());
            String en = english.get(key).getAsString().replace("%%", "");
            String zh = chinese.get(key).getAsString().replace("%%", "");
            check(en.chars().filter(c -> c == '%').count() == zh.chars().filter(c -> c == '%').count());
        }
        check(LanguageManager.translate(LanguageType.CHINESE,"ui.ultimategarden.home").equals("主页"));
        check(LanguageManager.translate(LanguageType.ENGLISH,"ui.ultimategarden.home").equals("Home"));
        check(LanguageManager.translate(LanguageType.CHINESE,"missing.key").equals("missing.key"));
        check(LanguageType.CHINESE.optionLabel().equals("Chinese (中文)"));
        check(LanguageType.ENGLISH.optionLabel().equals("English (English)"));
        Path dir=Files.createTempDirectory("ultimate-garden-language-"); ConfigManager configs=new ConfigManager(dir);
        check(configs.getUIConfig().getLanguage()==LanguageType.ENGLISH);
        configs.getUIConfig().setUiThemeMode(UIConfig.Theme.DARK); configs.getUIConfig().setAccentColor("#123456"); configs.saveUIConfig();
        Map<String,String> before=new HashMap<>();
        for(String file:List.of("farm","crops","esp","general","pest")) before.put(file,Files.readString(dir.resolve(file+".json")));
        int[] refresh={0}; LanguageManager manager=new LanguageManager(configs);
        check(manager.select(LanguageType.CHINESE,()->refresh[0]++)); check(refresh[0]==1);
        check(manager.current()==LanguageType.CHINESE);
        check(new ConfigManager(dir).getUIConfig().getLanguage()==LanguageType.CHINESE);
        check(configs.getUIConfig().getUiThemeMode()==UIConfig.Theme.DARK && configs.getUIConfig().getAccentColor().equals("#123456"));
        for(var item:before.entrySet()) check(Files.readString(dir.resolve(item.getKey()+".json")).equals(item.getValue()));
        check(manager.text("ui.ultimategarden.state.timer",40,2.0).equals("40 游戏刻／2.0 秒"));
        check(manager.localize("Temporarily paused for menu").equals("因菜单打开而临时暂停"));
        check(manager.cropName("CARROT","Carrot").equals("胡萝卜"));
        check(manager.cropName("CARROT","My custom name").equals("My custom name"));
        check(manager.text("ui.ultimategarden.appearance").equals("外观"));
        check(manager.text("ui.ultimategarden.menu_transparency").equals("菜单透明度"));
        check(manager.text("ui.ultimategarden.tracer").equals("引导线"));
        check(manager.keyName("key.keyboard.left.control","Left Control").contains("左控制键"));
        check(manager.keyName("key.keyboard.keypad.3","Keypad 3").equals("小键盘 3"));
        check(manager.keyName("key.mouse.7","Mouse Button 7").equals("鼠标按键 7"));
        check(manager.keyName("key.keyboard.m","M").equals("M"));
        check(manager.errorKey(new IllegalArgumentException("pitchA: invalid")).equals("ui.ultimategarden.error.field.pitchA"));
        check(manager.text(manager.errorKey(new IllegalArgumentException("pitchA: invalid"))).contains("俯仰角"));
        for(String group:List.of("box","fill","tracer")) for(String part:List.of("enabled","mode","color","alpha","width")) {
            String key=LanguageKeys.component(group,part); check(english.has(key) && chinese.has(key));
        }
        check(manager.select(LanguageType.ENGLISH,()->refresh[0]++)); check(refresh[0]==2);
        check(new ConfigManager(dir).getUIConfig().getLanguage()==LanguageType.ENGLISH);
        Files.writeString(dir.resolve("ui.json"),"{\"language\":\"INVALID\",\"uiThemeMode\":\"DARK\",\"accentColor\":\"#112233\",\"extra\":true}");
        ConfigManager repaired=new ConfigManager(dir);
        check(repaired.getUIConfig().getLanguage()==LanguageType.ENGLISH);
        check(repaired.getUIConfig().getUiThemeMode()==UIConfig.Theme.DARK && repaired.getUIConfig().getAccentColor().equals("#112233"));
        check(JsonParser.parseString(Files.readString(dir.resolve("ui.json"))).getAsJsonObject().get("extra").getAsBoolean());
        for (LanguageType type:LanguageType.values()) {
            try(var stream=LanguageRegression.class.getResourceAsStream("/assets/ultimate_garden/"+type.flagResource())) {
                var image=ImageIO.read(Objects.requireNonNull(stream));check(image.getWidth()==32 && image.getHeight()==20);
                Set<Integer> colors=new HashSet<>();for(int y=0;y<20;y++)for(int x=0;x<32;x++)colors.add(image.getRGB(x,y));check(colors.size()>3);
            }
        }
        Path screen=Path.of("src/client/java/com/kuangsanfeng/ultimategarden/client/ui/screen/UltimateGardenScreen.java");
        Matcher refs=Pattern.compile("\"(ui\\.ultimategarden\\.[^\"]+)\"").matcher(Files.readString(screen));
        Set<String> prefixes=Set.of("ui.ultimategarden.","ui.ultimategarden.mode.","ui.ultimategarden.field.","ui.ultimategarden.account.file.","ui.ultimategarden.random_delay.","ui.ultimategarden.hud.mode.","ui.ultimategarden.hud.item.","ui.ultimategarden.shootout.","ui.ultimategarden.view_check.","ui.ultimategarden.developer.shootout.log.");
        while(refs.find())if(!prefixes.contains(refs.group(1)) && !refs.group(1).equals("ui.ultimategarden.hint."))check(english.has(refs.group(1)));
        for(var category:com.kuangsanfeng.ultimategarden.client.config.ZombieShootLogCategory.values()){
            check(english.has("ui.ultimategarden.developer.shootout.log."+category.name()));
            check(english.has("ui.ultimategarden.developer.shootout.log."+category.name()+".help"));
        }
        for (var hint : GeneralConfig.Hint.values()) {
            check(english.has("ui.ultimategarden.hint." + hint.name()));
            check(english.has("ui.ultimategarden.hint." + hint.name() + ".help"));
        }
        for (String group : List.of("macro","pest","focus")) check(english.has("ui.ultimategarden.hint." + group));
        for (var item : HudConfig.Item.values()) check(english.has("ui.ultimategarden.hud.item." + item.name()));
        for (var workflow : com.kuangsanfeng.ultimategarden.client.macro.farm.action.FarmCommandController.Workflow.values())
            check(english.has("ui.ultimategarden.hud.workflow." + workflow.name()));
        for (var phase : com.kuangsanfeng.ultimategarden.client.macro.farm.FarmState.LineChangePhase.values())
            check(english.has("ui.ultimategarden.hud.phase." + phase.name()));
        for (var key : RandomDelayConfig.Key.values()) check(english.has("ui.ultimategarden.random_delay." + key.name()));
        for (var module : ConfigManager.Module.values()) check(english.has("ui.ultimategarden.account.file." + module.name()));
        String[] materials={"LEATHER","IRON","GOLD","DIAMOND"}, names={"皮革","铁","金","钻石"};
        var shootoutConfig=new ZombieShootoutConfig();
        for(int i=0;i<materials.length;i++)for(boolean baby:new boolean[]{false,true}){
            String age=baby?"BABY":"ADULT",key="ui.ultimategarden.shootout."+materials[i]+"_"+age;
            String material=materials[i].substring(0,1)+materials[i].substring(1).toLowerCase(java.util.Locale.ROOT);
            check(english.get(key).getAsString().equals(material+" "+(baby?"Baby":"Adult")+" Speed Window"));
            check(chinese.get(key).getAsString().equals(names[i]+(baby?"幼年体":"成年体")+"速度采样窗口"));
            check(english.get(key+".help").getAsString().equals("Movement speed averaging window in seconds."));
            check(chinese.get(key+".help").getAsString().equals("用于计算平均移动速度的采样时间窗口，单位为秒。"));
            check(shootoutConfig.get(ZombieShootoutConfig.Setting.valueOf(materials[i]+"_"+age))==.3);
        }
        check(english.get("ui.ultimategarden.hud.show").getAsString().equals("Show Farm Macro HUD"));
        check(chinese.get("ui.ultimategarden.hud.show").getAsString().equals("显示农业宏 HUD"));
        System.out.println("PASS: "+checks+" GUI language checks: dictionary parity, placeholders, persistence, invalid values, module isolation, flags and UI references.");
    }
}
