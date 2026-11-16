package com.kuangsanfeng.ultimategarden.client.platform.ui.language;

public enum LanguageType {
    ENGLISH("en_us", "English", "English", "us"),
    CHINESE("zh_cn", "Chinese", "中文", "cn");

    private final String code, englishName, nativeName, flag;
    LanguageType(String code, String englishName, String nativeName, String flag) {
        this.code = code; this.englishName = englishName; this.nativeName = nativeName; this.flag = flag;
    }
    public String code() { return code; }
    public String englishName() { return englishName; }
    public String optionLabel() { return englishName + " (" + nativeName + ")"; }
    public String flagResource() { return "textures/gui/language/" + flag + ".png"; }
}
