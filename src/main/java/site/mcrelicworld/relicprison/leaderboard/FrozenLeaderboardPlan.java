package site.mcrelicworld.relicprison.leaderboard;

import site.mcrelicworld.relicprison.reward.FrozenRewardPlanCodec;
import site.mcrelicworld.relicprison.reward.RewardLedgerService.ComponentDraft;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public final class FrozenLeaderboardPlan {
    private static final String VERSION = "RPLB1";
    private static final int MAX_PACKAGES = 2_000;

    private FrozenLeaderboardPlan() { }

    public static String encode(List<Entry> entries) {
        List<Entry> safe = List.copyOf(entries == null ? List.of() : entries);
        if (safe.size() > MAX_PACKAGES) throw new IllegalArgumentException("Frozen leaderboard plan is too large");
        StringBuilder result = new StringBuilder(VERSION).append('\n');
        for (Entry entry : safe) {
            result.append(field(entry.playerId().toString())).append('\t')
                    .append(field(entry.playerName())).append('\t').append(entry.position()).append('\t')
                    .append(entry.value().toPlainString()).append('\t').append(field(entry.rewardId())).append('\t')
                    .append(field(entry.packageId())).append('\t').append(field(entry.packagePayload())).append('\t')
                    .append(entry.intentionallyEmpty()).append('\t').append(field(entry.validationFailure())).append('\t')
                    .append(field(FrozenRewardPlanCodec.encode(entry.components()))).append('\n');
        }
        return result.toString();
    }

    public static List<Entry> decode(String payload) {
        if (payload == null || payload.isBlank()) throw new IllegalArgumentException("Frozen leaderboard plan is missing");
        String[] lines = payload.split("\\R", -1);
        if (lines.length == 0 || !VERSION.equals(lines[0])) {
            throw new IllegalArgumentException("Unsupported frozen leaderboard plan version");
        }
        List<Entry> entries = new ArrayList<>();
        for (int index = 1; index < lines.length; index++) {
            if (lines[index].isBlank()) continue;
            if (entries.size() >= MAX_PACKAGES) throw new IllegalArgumentException("Frozen leaderboard plan is too large");
            String[] fields = lines[index].split("\\t", -1);
            if (fields.length != 10) throw new IllegalArgumentException("Malformed frozen leaderboard package");
            try {
                entries.add(new Entry(UUID.fromString(unfield(fields[0])), unfield(fields[1]),
                        Integer.parseInt(fields[2]), new BigDecimal(fields[3]), unfield(fields[4]),
                        unfield(fields[5]), unfield(fields[6]), Boolean.parseBoolean(fields[7]),
                        unfield(fields[8]), FrozenRewardPlanCodec.decode(unfield(fields[9]))));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("Malformed frozen leaderboard package", error);
            }
        }
        return List.copyOf(entries);
    }

    private static String field(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String unfield(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    public record Entry(UUID playerId, String playerName, int position, BigDecimal value, String rewardId,
                        String packageId, String packagePayload, boolean intentionallyEmpty,
                        String validationFailure, List<ComponentDraft> components) {
        public Entry { components = List.copyOf(components == null ? List.of() : components); }
        public Entry(UUID playerId, String playerName, int position, BigDecimal value, String rewardId,
                     String packageId, String packagePayload, boolean intentionallyEmpty,
                     List<ComponentDraft> components) {
            this(playerId, playerName, position, value, rewardId, packageId, packagePayload,
                    intentionallyEmpty, "", components);
        }
    }
}
