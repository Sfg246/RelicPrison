package site.mcrelicworld.relicprison.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RedactionService {
    private static final String SECRET_WORDS = "password|passwd|pwd|username|user-name|token|api[-_]?key|apikey|"
            + "secret|webhook|session[-_]?key|authorization|auth[-_]?header|signed[-_]?url|ingest[-_]?token";
    private static final Pattern SECRET_KEY = Pattern.compile("(?i)(?:" + SECRET_WORDS + ")");
    private static final Pattern KEY_VALUE = Pattern.compile(
            "^([\\s\\\"']*)([A-Za-z0-9_.-]*(?:" + SECRET_WORDS
                    + ")[A-Za-z0-9_.-]*)([\\\"']?\\s*[:=]\\s*)(.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern XML_SECRET = Pattern.compile(
            "(?i)(<[^>]*(?:" + SECRET_WORDS + ")[^>]*>)(.*?)(</[^>]+>)");
    private static final Pattern AUTH_HEADER = Pattern.compile(
            "(?i)(authorization\\s*[:=]\\s*(?:bearer|basic)\\s+)[A-Za-z0-9+/_:.,=@-]+");
    private static final Pattern CONNECTION_SECRET = Pattern.compile(
            "(?i)((?:password|passwd|pwd|token|secret|apiKey|api_key)=)[^&\\s;]+");
    private static final Pattern URL_CREDENTIALS = Pattern.compile("(?i)([a-z][a-z0-9+.-]*://)[^/@\\s:]+:[^/@\\s]+@");
    private static final Pattern SIGNED_URL = Pattern.compile("(?i)((?:X-Amz-Signature|signature|sig)=)[A-Fa-f0-9]{16,}");
    private static final Pattern KNOWN_TOKEN = Pattern.compile(
            "(sk-[A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9_]{20,}|xox[baprs]-[A-Za-z0-9-]{16,}|AKIA[0-9A-Z]{16}|"
                    + "eyJ[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{10,})");
    private static final Pattern IPV4 = Pattern.compile("\\b(\\d{1,3}(?:\\.\\d{1,3}){3})\\b");
    private static final Pattern SUSPICIOUS_HEX = Pattern.compile("\\b[A-Fa-f0-9]{48,}\\b");
    private static final Pattern SUSPICIOUS_BASE64 = Pattern.compile("\\b[A-Za-z0-9+/]{64,}={0,2}\\b");

    private final List<String> filesScanned = new ArrayList<>();
    private final List<Redaction> redactions = new ArrayList<>();
    private final List<SuspiciousValue> suspiciousValues = new ArrayList<>();

    public RedactedFile redactFile(String relativeName, String content) {
        filesScanned.add(relativeName);
        String normalized = content == null ? "" : content;
        String[] lines = normalized.split("\\R", -1);
        StringBuilder output = new StringBuilder(normalized.length());
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) output.append('\n');
            output.append(redactLine(relativeName, index + 1, lines[index]));
        }
        return new RedactedFile(relativeName, output.toString());
    }

    public RedactionReport report() {
        return new RedactionReport(List.copyOf(filesScanned), List.copyOf(redactions),
                List.copyOf(suspiciousValues));
    }

    private String redactLine(String file, int lineNumber, String input) {
        Matcher keyValue = KEY_VALUE.matcher(input);
        if (keyValue.matches()) {
            String field = keyValue.group(2);
            redactions.add(new Redaction(file, lineNumber, safeField(field), "secret-key-name"));
            return keyValue.group(1) + field + keyValue.group(3) + "<redacted>";
        }

        String redacted = replacePattern(file, lineNumber, input, XML_SECRET, "xml-secret-key", 1, 3);
        redacted = replaceSimple(file, lineNumber, redacted, AUTH_HEADER, "authorization-header");
        redacted = replaceSimple(file, lineNumber, redacted, CONNECTION_SECRET, "connection-secret");
        redacted = replaceUrlCredentials(file, lineNumber, redacted);
        redacted = replaceSimple(file, lineNumber, redacted, SIGNED_URL, "signed-url");
        redacted = replaceSimple(file, lineNumber, redacted, KNOWN_TOKEN, "known-token-pattern");
        redacted = redactPublicIps(file, lineNumber, redacted);
        markSuspicious(file, lineNumber, redacted);
        return redacted;
    }

    private String replacePattern(String file, int lineNumber, String input, Pattern pattern, String rule,
                                  int prefixGroup, int suffixGroup) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            redactions.add(new Redaction(file, lineNumber, "xml-field", rule));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(prefixGroup)
                    + "<redacted>" + matcher.group(suffixGroup)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String replaceSimple(String file, int lineNumber, String input, Pattern pattern, String rule) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            redactions.add(new Redaction(file, lineNumber, "value", rule));
            String match = matcher.group();
            int equals = Math.max(match.lastIndexOf('='), match.lastIndexOf(' '));
            String prefix = equals >= 0 ? match.substring(0, equals + 1) : "";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(prefix + "<redacted>"));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String replaceUrlCredentials(String file, int lineNumber, String input) {
        Matcher matcher = URL_CREDENTIALS.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            redactions.add(new Redaction(file, lineNumber, "url-credentials", "url-embedded-credentials"));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1) + "<redacted>@"));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String redactPublicIps(String file, int lineNumber, String input) {
        Matcher matcher = IPV4.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (!publicIpv4(candidate)) continue;
            redactions.add(new Redaction(file, lineNumber, "ip-address", "public-ip-pattern"));
            matcher.appendReplacement(buffer, "<redacted-ip>");
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private void markSuspicious(String file, int lineNumber, String input) {
        if (SUSPICIOUS_HEX.matcher(input).find()) {
            suspiciousValues.add(new SuspiciousValue(file, lineNumber, "long-hex-value"));
        }
        if (SUSPICIOUS_BASE64.matcher(input).find()) {
            suspiciousValues.add(new SuspiciousValue(file, lineNumber, "long-base64-like-value"));
        }
    }

    public static String redactInline(String input) {
        RedactionService service = new RedactionService();
        return service.redactFile("inline", input == null ? "" : input).content();
    }

    private static boolean publicIpv4(String value) {
        String[] parts = value.split("\\.");
        if (parts.length != 4) return false;
        int[] octets = new int[4];
        for (int index = 0; index < parts.length; index++) {
            try { octets[index] = Integer.parseInt(parts[index]); }
            catch (NumberFormatException ex) { return false; }
            if (octets[index] < 0 || octets[index] > 255) return false;
        }
        if (octets[0] == 10 || octets[0] == 127 || octets[0] == 0) return false;
        if (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31) return false;
        if (octets[0] == 192 && octets[1] == 168) return false;
        if (octets[0] >= 224) return false;
        return true;
    }

    private static String safeField(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    public record RedactedFile(String name, String content) { }

    public record RedactionReport(List<String> filesScanned, List<Redaction> redactions,
                                  List<SuspiciousValue> suspiciousValues) {
        public RedactionReport {
            filesScanned = List.copyOf(filesScanned);
            redactions = List.copyOf(redactions);
            suspiciousValues = List.copyOf(suspiciousValues);
        }

        public String format() {
            StringBuilder output = new StringBuilder();
            output.append("Files scanned: ").append(filesScanned.size()).append('\n');
            filesScanned.forEach(file -> output.append("SCAN ").append(file).append('\n'));
            output.append("Fields redacted: ").append(redactions.size()).append('\n');
            redactions.forEach(redaction -> output.append("REDACT ")
                    .append(redaction.file()).append(':').append(redaction.line()).append(' ')
                    .append(redaction.field()).append(" rule=").append(redaction.rule()).append('\n'));
            output.append("Suspicious values requiring manual review: ")
                    .append(suspiciousValues.size()).append('\n');
            suspiciousValues.forEach(value -> output.append("REVIEW ")
                    .append(value.file()).append(':').append(value.line()).append(" rule=")
                    .append(value.rule()).append('\n'));
            return output.toString();
        }
    }

    public record Redaction(String file, int line, String field, String rule) { }

    public record SuspiciousValue(String file, int line, String rule) { }
}
