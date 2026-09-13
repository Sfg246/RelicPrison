package site.mcrelicworld.relicprison.util;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern PART = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);
    private DurationParser() {}

    public static Duration parse(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("Duration is required");
        String normalized = input.toLowerCase(Locale.ROOT).replace(" ", "");
        Matcher matcher = PART.matcher(normalized);
        long seconds = 0;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) throw new IllegalArgumentException("Invalid duration: " + input);
            long value = Long.parseLong(matcher.group(1));
            seconds = Math.addExact(seconds, Math.multiplyExact(value, switch (matcher.group(2)) {
                case "s" -> 1L;
                case "m" -> 60L;
                case "h" -> 3600L;
                case "d" -> 86400L;
                case "w" -> 604800L;
                default -> throw new IllegalArgumentException("Invalid duration unit");
            }));
            end = matcher.end();
        }
        if (end != normalized.length() || seconds <= 0) throw new IllegalArgumentException("Invalid duration: " + input);
        return Duration.ofSeconds(seconds);
    }

    public static String format(long millis) {
        long seconds = Math.max(0, millis / 1000L);
        long days = seconds / 86400; seconds %= 86400;
        long hours = seconds / 3600; seconds %= 3600;
        long minutes = seconds / 60; seconds %= 60;
        StringBuilder out = new StringBuilder();
        if (days > 0) out.append(days).append('d');
        if (hours > 0) out.append(hours).append('h');
        if (minutes > 0) out.append(minutes).append('m');
        if (seconds > 0 || out.isEmpty()) out.append(seconds).append('s');
        return out.toString();
    }
}
