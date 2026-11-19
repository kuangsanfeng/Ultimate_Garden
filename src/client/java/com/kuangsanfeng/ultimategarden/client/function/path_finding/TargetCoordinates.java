package com.kuangsanfeng.ultimategarden.client.function.path_finding;

import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;
import java.util.Optional;
import java.util.regex.Pattern;

public final class TargetCoordinates {
    private static final Pattern NUMBER = Pattern.compile("-?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)");
    private static final String VALUE = "(-?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+))";
    private static final Pattern LABELED = Pattern.compile("(?i)(?<![\\p{L}\\p{N}])x\\s*[:=]\\s*"+VALUE
            +"[,\uff0c]?\\s+y\\s*[:=]\\s*"+VALUE+"[,\uff0c]?\\s+z\\s*[:=]\\s*"+VALUE+"(?![.\\p{L}\\p{N}])");
    private TargetCoordinates() {}

    public static Point centered(Point requested){
        return new Point(Math.floor(requested.x())+.5,requested.y(),Math.floor(requested.z())+.5);
    }

    public static Optional<Point> extractReceived(String input) {
        var labeled=LABELED.matcher(input);
        if (!labeled.find()) return extract(input);
        try {
            var point=new Point(Double.parseDouble(labeled.group(1)),Double.parseDouble(labeled.group(2)),Double.parseDouble(labeled.group(3)));
            return labeled.find()?Optional.empty():Optional.of(point);
        } catch (IllegalArgumentException invalid) {return Optional.empty();}
    }

    public static Optional<Point> extract(String input) {
        var cleaned = new StringBuilder();
        input.codePoints().forEach(cp -> {
            if (cp < 128 && "xXyYzZ".indexOf(cp) >= 0) return;
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) cleaned.append(' ');
            else if (Character.isLetterOrDigit(cp) || Character.getType(cp) == Character.NON_SPACING_MARK || cp == '.' || cp == '-')
                cleaned.appendCodePoint(cp);
        });
        String[] words = cleaned.toString().strip().split(" +");
        Point found = null;
        for (int i = 0; i < words.length;) {
            if (!NUMBER.matcher(words[i]).matches()) { i++; continue; }
            int start = i;
            while (i < words.length && NUMBER.matcher(words[i]).matches()) i++;
            if (i-start != 3 || (start>0 && words[start-1].equals("-"))) continue;
            if (found != null) return Optional.empty();
            try {
                found = new Point(Double.parseDouble(words[start]), Double.parseDouble(words[start+1]), Double.parseDouble(words[start+2]));
            } catch (IllegalArgumentException malformed) { return Optional.empty(); }
        }
        return Optional.ofNullable(found);
    }
}
