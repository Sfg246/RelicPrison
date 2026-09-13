package site.mcrelicworld.relicprison.gang;

import site.mcrelicworld.relicprison.reward.RewardLedgerService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GangRewardComponents {
    private GangRewardComponents() { }

    public static List<RewardLedgerService.ComponentDraft> parse(List<String> definitions, String prefix) {
        List<RewardLedgerService.ComponentDraft> result = new ArrayList<>();
        long now = System.currentTimeMillis();
        int index = 0;
        for (String definition : definitions) {
            String[] parts = definition.split(":", 2);
            if (parts.length != 2) throw new IllegalArgumentException("Invalid gang reward component: " + definition);
            String id = prefix + '-' + (++index);
            switch (parts[0].toLowerCase(Locale.ROOT)) {
                case "money" -> result.add(RewardLedgerService.ComponentDraft.money(id,
                        new BigDecimal(parts[1]), now));
                case "experience" -> result.add(RewardLedgerService.ComponentDraft.experience(id,
                        Integer.parseInt(parts[1]), now));
                case "command" -> result.add(RewardLedgerService.ComponentDraft.consoleCommand(id, parts[1], now));
                case "announcement" -> result.add(RewardLedgerService.ComponentDraft.announcement(id, parts[1], now));
                default -> throw new IllegalArgumentException("Unsupported gang reward component: " + parts[0]);
            }
        }
        return List.copyOf(result);
    }

    public static List<String> forPosition(String rewardPlan, int position) {
        if (rewardPlan == null || rewardPlan.isBlank()) return List.of();
        for (String tier : rewardPlan.split(";")) {
            String[] parts = tier.split("=", 2);
            if (parts.length != 2) continue;
            boolean matches = false;
            for (String rawPosition : parts[0].split(",")) {
                if (Integer.parseInt(rawPosition.trim()) == position) matches = true;
            }
            if (matches) return List.of(parts[1].split("\\|"));
        }
        return List.of();
    }
}
