package com.kuangsanfeng.ultimategarden.client.function.path_finding;

import java.util.Locale;
import java.util.regex.Pattern;

public final class NodeBudget {
    private static final Pattern INPUT = Pattern.compile("([0-9]+)([KMBT]?)");
    private static final long[] UNITS = {1L, 1_000L, 1_000_000L, 1_000_000_000L, 1_000_000_000_000L};
    private static final String SUFFIX = " KMBT";
    private NodeBudget() {}
    public static long parse(String text) {
        var match=INPUT.matcher(text.strip().replace(",","").replace("\uFF0C","").toUpperCase(Locale.ROOT));
        if (!match.matches()) throw new IllegalArgumentException("Invalid search budget");
        var suffix=match.group(2);
        long value=Math.multiplyExact(Long.parseLong(match.group(1)),UNITS[suffix.isEmpty()?0:SUFFIX.indexOf(suffix)]);
        if(value<=0)throw new IllegalArgumentException("Invalid search budget");
        return value;
    }
    public static String format(long nodes) {
        if(nodes<=0)throw new IllegalArgumentException("Invalid search budget");
        for(int i=UNITS.length-1;i>0;i--)if(nodes%UNITS[i]==0)return Long.toString(nodes/UNITS[i])+SUFFIX.charAt(i);
        return Long.toString(nodes);
    }
}
